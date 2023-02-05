package com.hmdp;

import cn.hutool.core.util.RandomUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.service.IShopService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.SystemConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private RedisWorker redisWork;
    private static final ExecutorService service = Executors.newFixedThreadPool(500);

    @Autowired
    private IUserService userService;

    @Autowired
    private IShopService shopService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Test
    public void testIDGenerator() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Thread thread = new Thread(() -> {
            for (int i = 0; i < 100; ++i) {
                Long nextId = redisWork.nextId("order");
                System.out.println("nextId = " + nextId);
            }
            latch.countDown();
        });
        long begin = System.currentTimeMillis();
        for(int i = 0; i < 300; ++i){
            service.submit(thread);
        }
        //利用CountDownLatcher调用await，只有在300个线程执行完毕之后，才可以执行主线程
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("执行了 " + (end - begin) + " 毫秒");


    }

    @Test
    public void createUser() throws IOException {
        List<User> users = new ArrayList<>();
        for(int i = 0; i < 1000; ++i){
            User user = new User();
            user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
            user.setPhone(18315400000L + i + "");
            users.add(user);
        }

        //将用户插入到数据库中
        userService.saveBatch(users);
        //发送登录请求，从而将用户保存到了redis中，并生成cookie的值，然后保存到压测的文件中
        String codeUrlString = "http://localhost:8081/user/code";
        String loginUrlString = "http://localhost:8081/user/login";
        File file = new File("F:\\JMeter\\redis压力测试用户code.txt");
        File file1 = new File("F:\\JMeter\\redis压力测试用户token.txt");
        if(file.exists()) {
            file.delete();
        }
        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        file.createNewFile();
        raf.seek(0);
        if(file1.exists()) {
            file1.delete();
        }
        RandomAccessFile raf1 = new RandomAccessFile(file1, "rw");
        file1.createNewFile();
        raf1.seek(0);
        for(User user : users) {
            //获取验证码
            String params = "phone=" + user.getPhone();
            Result result = doRequest(codeUrlString, params);
            String code = (String)result.getData();
            //将每一行以 用户的id,cookie的id 的形式(2个值以逗号分隔)写入到要进行压测的code文件中
            String row = user.getId()+","+code;
            raf.seek(raf.length());
            raf.write(row.getBytes());
            raf.write("\r\n".getBytes());
            //将执行登录操作
            params += "&code=" + code;
            result = doRequest(loginUrlString, params);
            String token = (String)result.getData();
            //将生成的token保存到对应的压测的token文件中
            row = user.getId()+"," + token;
            raf1.seek(raf1.length());
            raf1.write(row.getBytes());
            raf1.write("\r\n".getBytes());
        }
        raf.close();
        raf1.close();
        System.out.println("over");
    }


    public Result doRequest(String urlString, String params) throws IOException {
        //发送请求，方法是POST
        URL url = new URL(urlString);
        HttpURLConnection co = (HttpURLConnection)url.openConnection();
        co.setRequestMethod("POST");
        co.setDoOutput(true);
        OutputStream out = co.getOutputStream();
        //设置发送的请求参数
        out.write(params.getBytes());
        out.flush();
        //读取服务端发送的响应
        InputStream inputStream = co.getInputStream();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        byte buff[] = new byte[1024];
        int len = 0;
        while((len = inputStream.read(buff)) >= 0) {
            bout.write(buff, 0 ,len);
        }
        inputStream.close();
        bout.close();
        String response = new String(bout.toByteArray());
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(response, Result.class);
    }

    /**
     * 将所有商铺的地理位置信息添加到redis中，其中因为
     * 商铺的信息很多，所以最后保存到redis中的是这个商铺的id以及地理位置，
     * 当我们查询某一个类别的商铺的时候，就会从redis中获取对应的商铺id，然后
     * 再查询数据库。
     */
    @Test
    public void loadShopService(){
        //1、获取所有的商铺
        List<Shop> shops = shopService.list();
        //2、根据商铺的类别id,从而进行分组
        Map<Long, List<Shop>> shopMaps = shops.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //3、遍历不同分组的店铺，然后写入到redis中
        for(Map.Entry<Long, List<Shop>> entry : shopMaps.entrySet()){
            //3.1 获取店铺类别id
            Long typeId = entry.getKey();
            String key = RedisConstants.SHOP_GEO_KEY + typeId;
            //3.2 获取属于这个类别的所有店铺id
            List<Shop> value = entry.getValue();
            //3.3 将所有的店铺写入到redis中，通过命令GEOADD key x y member，其中
            //x,y就是店铺的位置，而member就是这个店铺的id
            /*
            这种方式需要遍历不同类别的店铺，并且是一条一条添加的，效率可能会有些低，
            所以采用的是批量插入到redis的方式
            value.forEach(shop -> {
                stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
            });*/
            List<RedisGeoCommands.GeoLocation<String>> iteration = new ArrayList<>(value.size());
            value.forEach(shop -> {
                iteration.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(), new Point(shop.getX(), shop.getY())));
            });
            stringRedisTemplate.opsForGeo().add(key, iteration);
        }

    }

    /**
     * 测试HyperLogLog：
     * HyperLogLog数据结构是基于String操作的，占用的内存很小，不超过16KB，但是
     * 他是一个具有概率性的结果，也即是说插入1000000条数据，但是最后的key中
     * 存在的元素个数可能没有1000000条，但是它的概率不超过0.81%,这对于UV统计
     * 来说，完全可以忽略的。常见的命令有:
     * 1、PFADD key element1 element2 -> 对应java客户端的方法是opsForHyperLogLog().add(key, element...)
     * 2、PFCOUNT key: 统计key中的元素个数， -> opsForHyperLogLog().size()
     * 3、PMERGE destKey sourceKey1 sourceKey2 : 将多个sourceKey合并到destKey中
     * 其中PFADD 命令条件元素到key的时候，如果新添加的元素已经存在了，那么不会进行添加操作
     */
    @Test
    public void testHyperLogLog(){
        //未添加的时候，redis中的内存为1476440,添加之后是1490800
        //所以添加的数据大小为14kb，小于16kb
        String[] users = new String[1000];
        int j = 0;
        String key = "hll2";
        for(int i = 0; i < 1000000; ++i){
            j = i % 1000;
            users[j] = "user_" + i;
            if(j == 999){
                //每一千条，就将数组插入到redis中
                stringRedisTemplate.opsForHyperLogLog().add(key,users);
            }
        }
        //打印key中有多少个元素，因为HyperLogLog是一个具有概率性的结果，所以最后
        //的结果可能不一定有1000000条，但是概率小于0.81%
        Long count = stringRedisTemplate.opsForHyperLogLog().size(key);
        System.out.println(count);
        //stringRedisTemplate.opsForHyperLogLog().delete(key);
    }

}
