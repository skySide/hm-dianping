package com.hmdp.mapper;

import com.hmdp.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.dao.EmptyResultDataAccessException;

/**
 * @author MACHENIKE
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
      //查询tb_user，来获取登录时用户的信息
      User queryByPhone(String phone) throws EmptyResultDataAccessException;
}
