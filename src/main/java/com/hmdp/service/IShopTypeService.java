package com.hmdp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

import java.io.IOException;
import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopTypeService extends IService<ShopType> {
     List<ShopType> queryAll();

    Result queryTypeList() throws IOException;

    /**
     * 查找shopType中的所有的id
     * @return
     */
    List<Long> findIdList();
}
