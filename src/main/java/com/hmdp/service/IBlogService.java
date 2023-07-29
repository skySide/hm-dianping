package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.exception.BizException;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    Result queryById(Long id) throws BizException;

    List<Blog> queryHotBlog(Integer current);

    Result likeBlog(Long id);

    Result likesBlogTop5(Long blogId);

    Result queryByUser(Long userId, Long current);

    Result queryMyBlog(Integer current);

    Result queryOfFollow(Long offset, Long lastId);

    Result saveBlog(Blog blog);
}
