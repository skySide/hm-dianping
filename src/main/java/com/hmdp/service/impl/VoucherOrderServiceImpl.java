package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.conditions.ChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.*;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private RedisWorker redisWorker;

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;
    @Resource
    private RabbitTemplate rabbitTemplate;

    private static DefaultRedisScript<Long> redisScript;
    private static DefaultRedisScript<Long> rabbitScript;
    static{
        redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("seckill.lua"));
        redisScript.setResultType(Long.class);

        rabbitScript = new DefaultRedisScript<>();
        rabbitScript.setLocation(new ClassPathResource("rabbitmq_seckill.lua"));
        rabbitScript.setResultType(Long.class);
    }

    /*
    从队列中获取消息,然后生成订单
     */
    @RabbitListener(queues = RabbitConstant.SECKILL_QUEUE)
    public void onMessage(Message message, Channel channel) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            //获取消息
            String orderInfo = new String(message.getBody());
            String[] info = orderInfo.split(",");
            //生成订单
            Long userId = Long.parseLong(info[0]);
            Long voucherId = Long.parseLong(info[1]);
            Long orderId = Long.parseLong(info[2]);
            VoucherOrder order = new VoucherOrder();
            order.setUserId(userId);
            order.setVoucherId(voucherId);
            order.setId(orderId);
            voucherOrderHandler(order);
            //发送确认
            channel.basicAck(deliveryTag, false);
        }catch (Exception e){
            try {
                channel.basicNack(deliveryTag, false, true);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * 通过阻塞队列来生成秒杀订单，为了避免redis服务器发生宕机，从而避免
     * 在seckill.lua脚本中对库存数量，以及用户是否已经购买过的判断失效，
     * 因此在当前的方法中利用redisson来实现分布式锁
     *
     * 然后可以获取分布式锁，就去生成订单，这时候需要注意商品超卖的问题，因此
     * 需要利用乐观锁来解决商品超卖的问题
     */
    @Transactional
    public void voucherOrderHandler(VoucherOrder voucherOrder) {
        RLock lock = redissonClient.getLock("lock:voucherOrder:userId:" + voucherOrder.getUserId());
        boolean isLock = lock.tryLock();
        try {
            if (!isLock) {
                //获取锁失败，那么直接返回
                log.error("每个用户限购1件商品");
                return;
            }
            Integer stock = seckillVoucherService.getById(voucherOrder.getVoucherId()).getStock();
            if (stock <= 0) {
                log.error("库存不足");
                return;
            }
            //获取锁成功之后，就可以进行秒杀商品了
            boolean isUpdate = seckillVoucherService.update(new UpdateWrapper<SeckillVoucher>().setSql("stock = stock - 1")
                    .eq("voucher_id", voucherOrder.getVoucherId())
                    .gt("stock", 0));
            if (!isUpdate) {
                log.error("库存不足");
                return;
            }
            //生成秒杀订单
            voucherOrderService.save(voucherOrder);
        }finally {
            lock.unlock();//释放锁
        }
    }

    /**
     * 基于Redis中的stream来实现消息队列，从而实现异步秒杀：
     * 1、在lua脚本中判断是否有资格进行秒杀商品，如果没有资格，那么
     * 返回1或者2
     * 2、返回0的时候，说明有资格进行秒杀操作。那么这时候就在lua脚本返回
     * 之前，将当前秒杀的商品id，用户id以及生成的订单id传递给消息队列
     * 3、异步线程从消息队列中取出消息，从而生成秒杀订单。此时是通过
     * 命令XREADGROUP GROUP group_name consumer_name count block stream key >
     * 来读取未处理的消息，而如果是
     * XREADGROUP GROUP group_name consumer_name count block stream key 0
     * 表示读取的是pendingList中的消息。
     * 如果没有读取到未处理的消息，那么这时候可以尝试到pendingList中获取未处理的消息
     * 否则就处理未处理的消息，然后将消息处理完之后，需要发送确认，将已经处理的信息
     * 从pendingList中移除
     */
//    public Result seckillVoucher(Long voucherId) {
//        //1、获取当前用户的登录id
//        Long userId = UserHolder.getUser().getId();
//        Long orderId = redisWorker.nextId("order");
//        //2、获取订单id
//        Long result = stringRedisTemplate.execute(
//                redisScript,
//                Collections.emptyList(),
//                 voucherId.toString(),userId.toString(), orderId.toString()
//        );
//        //3、如果result不等于0，说明抛出了异常
//        if(result != 0){
//            return Result.fail(result == 1 ? "库存不足" : "每个用户限购一件");
//        }
//        return Result.ok(orderId);
//    }
    public Result seckillVoucher(Long voucherId) {
        //1、获取当前用户的登录id
        Long userId = UserHolder.getUser().getId();
        Long orderId = redisWorker.nextId("order");
        log.debug("voucherId = {}", voucherId);
        //2、获取订单id
        Long result = stringRedisTemplate.execute(
                rabbitScript,
                Collections.emptyList(),
                voucherId.toString(),userId.toString()
        );
        //3、如果result不等于0，说明抛出了异常
        if(result != 0){
            return Result.fail(result == 1 ? "库存不足" : "每个用户限购一件");
        }
        StringBuilder orderInfo = new StringBuilder();
        orderInfo.append(userId.toString()).append(",")
                 .append(voucherId.toString()).append(",")
                 .append(orderId.toString());
        //4、将用户的id,voucherId以及orderId保存到消息队列中
        rabbitTemplate.convertAndSend(RabbitConstant.SECKILL_EXCHANGE, RabbitConstant.SECKILL_ROUTING_KEY, orderInfo.toString());
        return Result.ok(orderId);
    }
    /**
     * 基于stream的消息队列来实现异步秒杀操作，对应的步骤为:
     * 1、通过lua脚本判断用户是否有资格进行秒杀操作(秒杀是否已经开始，库存是否充足,
     * 用户是否已经重复购买)
     * 2、如果用户有资格进行购买，那么就在redis中更新库存，并且将用户添加到对应的订单中
     * 同时将用户id,voucherId以及订单id发送给消息队列，通过执行xadd key field value
     * 来发送消息
     * 3、当执行完毕lua脚本之后，发现用户有资格进行秒杀操作，那么就另外开启线程，
     * 来生成订单，此时就需要从消息队列中取出订单
     */
    /*public Result seckillVoucher(Long voucherId) {
        Long orderId = redisWorker.nextId("order");
        Long result = stringRedisTemplate.execute(
                redisScript,
                Collections.emptyList(),
                voucherId.toString(),
                UserHolder.getUser().getId().toString(),
                orderId.toString()
        );
        if(result != 0){
            //不等于0，说明没有资格进行秒杀操作
            return Result.fail(result == 1 ? "库存不足" : "每个用户限购1件");
        }
        //有资格进行秒杀操作，那么就另外开启线程进行秒杀订单操作
        return Result.ok(orderId);
    }*/
    /**
     * 秒杀商品的优化:
     * 1、将优惠券以及某一个商品的订单保存到redis中，其中优惠券只需要保存它的
     * 开始时间，结束时间以及库存数量，而一个商品的订单只需要保存这个商品的购买
     * 用户，同时为了保证商品一人一单，所以redis中采用的是set数据结构，保证
     * 元素是不重复的。
     * 2、然后我们进行秒杀操作之前，需要从redis中获取开始时间以及结束时间，如果
     * 已经结束秒杀或者没有开始，那么直接返回，否则如果库存数量小于0，返回1
     * 3、如果用户已经购买过了，那么就返回2
     * 4、否则，如果2，3条件都没有满足，那么就更新库存数量，同时将这个用户添加到
     * 商品订单的用户之中。然后返回0
     * 为了保证原子性操作，需要将2，3，4放到lua脚本中进行操作。
     *
     * 经过上面的操作之后，如果返回的是0，说明用户秒杀成功，这时候就需要另外开启线程
     * 生成秒杀订单，否则，返回的不是0，那么就返回错误提示信息。
     */
    /*@Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        //1、执行lua脚本，从而判断库存数量以及用户是否重复购买
        //如果返回的是0，说明用户秒杀成功，否则如果是1，说明库存不足
        //返回是2，表示用户重复购买
        Long result = stringRedisTemplate.execute(redisScript,
                Collections.emptyList(),
                //注意需要将voucherId，userId转成String，
                //因为stringRedisTemplate的key，value都是字符串类型的，否则
                //就抛出long cannot be cast to String
                voucherId.toString(),
                userId.toString()
        );
        if(result != 0){
            return Result.fail(result == 1 ? "库存不足" : "每个用户限购一件商品");
        }
        //2、返回的是0，那么将另外开启线程进行生成订单，并将订单id返回
        Long orderId = redisWorker.nextId("order");
        //3、创建VoucherOrder对象，并将其添加到阻塞队列中
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        queue.add(voucherOrder);
        return Result.ok(orderId);
    }*/
    /**
     * 进行秒杀操作，对应的步骤为:
     * 1、判断秒杀是否已经开始、结束
     * 1.1 秒杀已经结束或者还没有开始，直接返回
     * 2、秒杀进行中，那么判断是否还有库存
     * 2.1 库存不足，直接返回
     * 3、库存充足，判断这个用户是否已经购买过这个商品了，从而实现一人一单的需求
     * 3.1 如果已经买过了，那么直接返回错误信息
     * 4、没有买过，那么就去购买商品，这时候需要解决商品超卖的问题
     * 5、利用全局ID生成器，生成秒杀订单，然后将订单的编号返回给前端

    @Override
    public Result seckillVoucher(Long voucherId) {
        //1、获取优惠券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //1.1 获取开始时间以及结束时间
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        LocalDateTime endTime = seckillVoucher.getEndTime();
        if(beginTime.isAfter(LocalDateTime.now())){
            return Result.fail("秒杀尚未开始");
        }
        if(endTime.isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已经结束");
        }
        //2、获取这个优惠券的库存
        Integer stock = seckillVoucher.getStock();
        if(stock < 1){
            return Result.fail("优惠券剩余0张");
        }
        Long userId = UserHolder.getUser().getId();
//        之所以是在方法调用之前加锁，而不是在createOrder内部加锁，
//        是因为如果在createOrder方法内部加锁，那么会存在这种情况:
//        用户1的线程1执行完createOrder方法之后,此时已经释放了锁，但是还没有
//        提交事务，那么用户1的线程2此时刚好获取了锁，因为没有提交事务，
//        所以线程2的值当前用户1没有购买过这个商品，所以再次进行购买操作。
//        这样再次引起了同一个用户购买同一个商品多次的情况。
//        因此需要保证提交事务之后，同一个用户的其他线程才可以获取锁，所以
//        是在createOrder方法调用之前加锁。
//        synchronized (userId.toString().intern()){
//            //锁是每一个用户的userId,从而给每一个用户都加上了锁，不同的用户是
//            //不影响的，但是之所以不是synchronized(userId)，是因为userId的地址
//            //不同，尽管值相同，但是我们的值相同就是同一个用户了，所以需要时
//            //userId.toString，但是toString方法中最后的返回值依旧是new String(xxx)
//            //所以依旧可能存在地址问题，所以是toString().intern()，返回的是字符串
//            //常量池的数据，也即它的值
//            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
//            return proxy.createOrder(voucherId, userId);
//        }
        //分布式锁，实现一人一单,通过RedisLock自定义分布式锁来实现的
//        ILock lock = new RedisLock("voucherOrder:" + userId, stringRedisTemplate);
//        boolean isLock = lock.tryLock(1200L);
        //通过redisson来实现分布式锁
        //1、获取分布锁
        RLock lock = redissonClient.getLock("hm_dianping:lock:voucher:" + voucherId);
        //2、分布锁调用tryLock，尝试获取锁,其中tryLock方法中可以由三个参数
        //①tryLock(timeout, leaseTime, TimeUnit)，timeout最长获取锁时间，并在这段时间中
        //重试获取锁，leaseTime表示过期时间，TimeUnit表示时间单位
        //②tryLock():这时候的timeout默认是-1，表示不会进行重试，leaseTime为30秒
        boolean isLock = lock.tryLock();
        if(!isLock){
            //如果没有获取到互斥锁
            return Result.fail("一人限购一单,不可重复购买");
        }
        try {

            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createOrder(voucherId, userId);
        }finally {
            //释放锁(redisson中也是通过调用方法unlock来释放锁的)
            lock.unlock();
        }
    }
*/
    /**
     * 生成订单，同时保证一人一单,之所以不是给这个方法添加synchronized
     * 实现，是因为如果给这个方法添加锁，那么所有不同的用户来到这个方法
     * 中需要尝试获取锁，但是我们只需要对不同的用户加锁即可，因此如果
     * 对这个方法加锁，就会导致性能下降。
     * @param voucherId
     * @return
     */
    @Transactional
    public Result createOrder(Long voucherId, Long userId) {
        //3、判断这个用户是否已经购买过这个商品了(只需要查询tb_voucher_order表中存在这个user_id，voucher_id的
        //行数即可，如果count>0,说明已经买过了，否则没有
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if(count > 0){
            return Result.fail("每个用户限购1次");
        }
        //4、更新库存，防止在库存变成负数的时候，先生成订单，然后在更新库存
        boolean isUpdate = seckillVoucherService.update(new UpdateWrapper<SeckillVoucher>().setSql("stock = stock - 1").eq("voucher_id", voucherId).gt("stock", 0));

        /*
        这个代码和上面代码是一样的
        boolean isUpdate = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0) //只要stock大于0,就进行操作
                    .update();
        */
        if(!isUpdate){
            return Result.fail("优惠券剩余0张");
        }
        //4、进行秒杀操作，生成订单
        VoucherOrder voucherOrder = new VoucherOrder();
        Long orderId = redisWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        voucherOrderService.save(voucherOrder);
        return Result.ok(orderId);
    }
}
