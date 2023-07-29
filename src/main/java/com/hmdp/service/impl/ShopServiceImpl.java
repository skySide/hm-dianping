package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisBloomFilter;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import com.sun.scenario.effect.impl.sw.sse.SSEBlend_SRC_OUTPeer;
import io.netty.util.internal.StringUtil;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoLocation;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author MACHENIKE
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisBloomFilter redisBloomFilter;

    @Override
    public Result queryShopById(Long id) {
        log.info("queryShopById begin, id = {}", id);
        //缓存穿透
        Shop shop = queryWithPassThrough(id);
        //通过互斥锁来解决缓存击穿
        //Shop shop = queryWithMutex(id);
        //Shop shop = queryWithLogicalExpire(id);
        if(shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    private final ExecutorService service = Executors.newFixedThreadPool(10);
    /**
     * 通过逻辑过期来解决缓存击穿问题，对应的步骤为:
     * 1、查询缓存，是否命中
     * 2、如果不能命中，说明数据库中并不存在这个数据，直接返回null
     * 3、如果命中，这时候需要判断逻辑过期时间是否已经过期了
     * 4、逻辑过期时间还没有到，那么直接将数据返回
     * 5、逻辑过期时间已经到了，那么判断是否可以获取互斥锁，
     * 6、可以获取互斥锁，那么就另外开一条线程，用来查询数据库，以及重建缓存
     * 业务操作
     * 7、本线程直接将数据返回
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpire(Long id){
        //1、从缓存中取出数据
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        if(StringUtils.isBlank(jsonStr)){
            //数据为空，说明不能命中，说明数据库中并没有这个数据，直接返回null
            return null;
        }
        //2.1 命中，那么获取过期时间，首先将jsonStr转成RedisData类型
        RedisData redisData = JSONUtil.toBean(jsonStr, RedisData.class);
        System.out.println("redisData = " + redisData);
        //2.2 获取逻辑过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        //因为RedisData中的data是一个Object类型，所以当反序列化，也即toBean方法调用之后,
        //就会将这一串数据的jsonStr变成JSONObject返回，此时就不可以强转成为Shop
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        //2.3 判断是否已经过了逻辑时间
        if(expireTime.isAfter(LocalDateTime.now())){
            //2.4 没有过期，直接将数据返回
            return shop;
        }
        //3 已经过期，那么判断是否可以获得互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Boolean isLock = tryLock(lockKey);
        if(isLock){
            //3.1 可以得到互斥锁，那么要另外开启一条线程，进行重建缓存任务，
            //这里通过线程池来完成
            service.execute(() -> {
                try{
                    //3.2 重建缓存
                    save2Shop(id, 20L);
                }catch(RuntimeException e){
                    throw new RuntimeException(e);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    //3.3 释放互斥锁
                    releaseLock(lockKey);
                }
              }
            );
        }
        //4 将数据返回即可
        return shop;
    }

    public void save2Shop(Long id, Long expiredTime) throws InterruptedException {
        //1、查询数据库
        Shop shop = getById(id);
        Thread.sleep(200);
        //2、添加到缓存中，并且为了解决缓存击穿的问题，需要添加一个逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expiredTime));
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));

    }

    /**
     * 通过互斥锁的方式来解决缓存击穿问题,对应的步骤为:
     * 1、查询缓存，判断是否可以命中，如果可以命中，那么将数据返回
     * 2、不能命中，那么调用方法tryLock,从而判断是否可以获得互斥锁
     * 3、如果能够获得互斥锁，那么这个线程就去查询数据库，并且将重建缓存业务操作
     * 4、否则，如果不能获得互斥锁，那么说明已经有其他线程去查询数据库，执行重建
     * 缓存业务操作了，此时这个线程只需要睡眠一段时间，然后再次查询缓存即可
     * 5、当重建缓存业务操作完成之后，就需要释放互斥锁
     * 6、返回数据
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id){
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        //1、查询redis中的数据
        String jsonString = stringRedisTemplate.opsForValue().get(key);
        if(StringUtils.isNotBlank(jsonString)){
            //isNotBlank ：表示只有在字符串不为null，并且长度不为0，并且不含有空白字符的时候，才返回true，否则返回false
            //2、redis中店铺存在，直接返回
            return JSONUtil.toBean(jsonString, Shop.class);
        }
        if(jsonString != null && jsonString.length() <= 0){
            //如果jsonString不等于null，并且长度为0,说明数据库中不存在这个数据
            //但是在缓存中保存这个空串，从而防止缓存穿透
            return null;
        }
        //3、店铺不存在，判断是否可以获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;//每个商铺都有一个互斥锁
        Boolean isLock = tryLock(lockKey);
        try{
            if(!isLock){
                //不能获得互斥锁,将这个线程睡眠一段时间，然后重新查询缓存
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //为了保证重建业务相对较长，所以将这个线程睡眠200ms
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //4、能够获得互斥锁，查询数据库
        Shop shop = getById(id);
        if(shop == null){
            //4.1查询数据为空，将空数据保存到缓存中
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            //4.2 返回null
            return null;
        }
        //4.3 将数据保存到redis中
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //5、释放互斥锁
        releaseLock(lockKey);
        //6、将数据返回
        return shop;
    }

    /**
     * 判断是否可以获取互斥锁，此时只需要通过setnx这个方法来实现即可
     * 如果这个方法能够成功执行，说明可以获取互斥锁，否则不可以。
     * 因为setnx表示的是：只有这个key不存在，才可以添加这个key，否则不可以
     * 返回true，表示可以执行这个方法，表示可以获得互斥锁， 否则不可以获得互斥锁
     * @param lockKey
     * @return
     */
    public Boolean tryLock(String lockKey){
        //这时候的setIfAbsent可能返回的是null，所以需要通过BooleanUtils来解决
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放互斥锁
     * @param lockKey
     */
    public void releaseLock(String lockKey){
        stringRedisTemplate.delete(lockKey);
    }

    /**
     * 缓存穿透代码
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id){
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        //1、查询bloomFilter
        boolean isContained = redisBloomFilter.containsKey(key);
        if(!isContained) {
            return null;
        }
        //1、查询redis中的数据
        String jsonString = stringRedisTemplate.opsForValue().get(key);
        if(StringUtils.isNotBlank(jsonString)){
            //isNotBlank ：表示只有在字符串不为null，并且长度不为0，并且不含有空白字符的时候，才返回true，否则返回false
            //2、redis中店铺存在，直接返回
            return JSONUtil.toBean(jsonString, Shop.class);
        }
        if(jsonString != null && jsonString.length() <= 0){
            //如果jsonString不等于null，并且长度为0,说明数据库中不存在这个数据
            //但是在缓存中保存这个空串，从而防止缓存穿透
            return null;
        }
        //3、店铺不存在，查询数据库
        Shop shop = getById(id);
        if(Objects.isNull(shop)){
            //3.1查询数据为空，将空数据保存到缓存中
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            //3.2 返回null
            return null;
        }
        //3.3 将数据保存到redis中
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //4、将数据返回
        return shop;
    }

    @Override
    @Transactional //保证缓存以及数据库的操作时同时成功以及失败的
    public Result updateShop(Shop shop) {
        //1、更新数据库
        updateById(shop);
        //2、删除缓存
        String key = RedisConstants.CACHE_SHOP_KEY + shop.getId();
        stringRedisTemplate.delete(key);
        return Result.ok();
    }

    /**
     * 如果x，y至少一个为null，那么就是返回的是类型为typeId的所有店铺，并且实现分页。
     * 否则如果x，y都不为空，那么查询x，y附近的类型为typeId的店铺，同时返回对应的距离
     * 实现分页
     * @param typeId
     * @param current
     * @param x
     * @param y
     * @return
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if(x == null || y == null){
            //1、x,y至少有1个为null，那么就是查询所有类型为typeId的店铺
            Page<Shop> page = query()
                    .eq("typeId", typeId)
                    .page(new Page<Shop>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            //1.1 获取第current页的所有记录
            List<Shop> records = page.getRecords();
            return Result.ok(records);
        }
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        //2、查询x,y附近的所有店铺GEOSEARCH key x y with distance
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(key,
                //以x,y这个点为圆心，半径为5千米的店铺
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                //includeDistance表示返回的数据除了携带member信息之外，还包括距离圆心的距离
                //limit用来实现分页的
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
        );
        if(results == null){
            //2.1 如果距离圆心距离为5km的店铺一个都没有
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> contents = results.getContent();
        if(contents.size() <= from){
            //2.2 如果查询到的距离圆心的距离的店铺比current - 1页还要少，那么第current页的记录就是空的
            return Result.ok(Collections.emptyList());
        }
        //2.3 获取第current页的店铺以及每个店铺距离当前圆心的距离
        List<Long> shopIds = new ArrayList<>();
        Map<Long, Distance> map = new HashMap<>();
        contents.stream().skip(from).forEach(result ->{
            //对于每个GeoLocation来说，存在2个属性name以及Point，point表示的是位置
            Long shopId = Long.parseLong(result.getContent().getName());
            shopIds.add(shopId);
            map.put(shopId, result.getDistance());
        });
        //3、获取shopIds中的所有Shop对象，然后将其返回
        String idStr = StrUtil.join(",", shopIds);
        List<Shop> shops = query().in("id", shopIds)
                .last("ORDER BY FIELD (id," + idStr + ")").list();
        //3.1 获取每个shop到当前x,y点的距离
        shops.forEach(shop -> {
            shop.setDistance(map.get(shop.getId()).getValue());
        });
        return Result.ok(shops);

    }
}
