package com.hmdp.utils;

import com.hmdp.service.IShopTypeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * @author Administrator
 * @date 2023/7/28 7:14
 */
@Slf4j
@Component
public class StartupRunner implements CommandLineRunner {

    @Resource
    private IShopTypeService shopTypeService;

    @Resource
    private RedisBloomFilter bloomFilter;

    @Resource
    private ThreadPoolTaskExecutor threadPoolExecutor;

    @Override
    public void run(String... args) throws Exception {
        List<Long> idList = shopTypeService.findIdList();
        List<CompletableFuture<Object>> futureList = idList.stream().map(id -> CompletableFuture.supplyAsync(
                () -> {
                    importShopTypeKey(id);
                    return null;
                }, threadPoolExecutor))
                .collect(Collectors.toList());
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futureList.toArray(new CompletableFuture[]{}));
        allOf.join();
    }

    /**
     * 将shopType中的所有id添加到bloomFilter中
     * @param id
     */
    private void importShopTypeKey(Object id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        boolean isContained = bloomFilter.containsKey(key);
        if(!isContained) {
            bloomFilter.add(key);
        }
    }
}
