package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private ShopTypeMapper shopTypeMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public List<ShopType> queryAll() {
        return shopTypeMapper.queryAll();
    }

    @Override
    public Result queryTypeList() throws IOException {
        //1、查询redis中的数据
        String jsonString = stringRedisTemplate.opsForValue().get(RedisConstants.SHOP_TYPE_LIST);
        if(StringUtils.isNotBlank(jsonString)){
            //2、redis中存在，直接将其返回
            return Result.ok(objectMapper.readValue(jsonString, List.class));
        }
        //3、数据不存在，查询数据库
        List<ShopType> shopTypes = queryAll();
        //4、将查询到的数据保存到redis中
        stringRedisTemplate.opsForValue().set(RedisConstants.SHOP_TYPE_LIST, objectMapper.writeValueAsString(shopTypes), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //5、将数据返回
        return Result.ok(shopTypes);
    }

    @Override
    @Cacheable(value = "findIdList", key = "findIdList")
    public List<Long> findIdList() {
        LambdaQueryWrapper<ShopType> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(ShopType::getId);
        List<ShopType> shopTypeList = list(queryWrapper);
        List<Long> idList = shopTypeList.stream().map(t -> t.getId()).filter(Objects::nonNull).collect(Collectors.toList());
        return idList;
    }
}
