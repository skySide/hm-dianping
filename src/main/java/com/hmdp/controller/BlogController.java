package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.exception.BizException;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;

    /**
     * 发布博客
     * @param blog
     * @return
     */
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    /**
     * 实现博客的点赞:
     * @param id
     * @return
     */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    @GetMapping("/likes/{id}")
    public Result likesBlogTop5(@PathVariable("id")Long blogId){
        return blogService.likesBlogTop5(blogId);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryMyBlog(current);
    }

    /**
     * 热帖排行榜：没有添加缓存之前，qps: 14点多
     * 添加redis缓存之后，qps: 281
     * @param current
     * @return
     */
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        List<Blog> blogList = blogService.queryHotBlog(current);
        return Result.ok(blogList);
    }
    @GetMapping("/{id}")
    public Result queryById(@PathVariable("id")Long id) throws BizException {
        return blogService.queryById(id);
    }

    /**
     * 当进入其他用户的主页的时候，需要获取其他用户的博客
     * @param userId
     * @param current
     * @return
     */
    @GetMapping("/of/user")
    public Result queryByUser(@RequestParam("id") Long userId, @RequestParam(value = "current", defaultValue = "1") Long current){
        return blogService.queryByUser(userId, current);
    }

    /**
     * 来到个人界面之后，需要查看关注的人发布的笔记
     * 因为是关注者已发布笔记，那么这个笔记就会推送到当前用户的收件箱。
     * 而这个收件箱需要实现排序，所以可以采用Redis中的list或者zset。
     * 但是由于采用Feeds流来实现推送的时候，分页并不是传统的分页，下标是
     * 会发生变化的，所以这时候我们并不是根据下标来实现分页查询，而是
     * 根据上一次查询中的最小记录后一条开始，所以lastId就是上一次查询
     * 的记录的值，而offset就是上一次查询的记录的数目。
     * 所以执行的redis命令就是zrevrangebyscorewithscore key max min limit offset count
     * 其中zrevrangebyscore是根据score降序排序，而withscore返回的数据中同时包括了score属性
     * 这样就可以得到在[min, max]范围的score对应的元素了。
     * max一开始位当前时间戳，否则就是上一次查询是最小记录的时间戳。
     * offset一开始是0，否则就是上一次查询的最小记录的数量。
     * @param offset
     * @param lastId
     * @return
     */
    @GetMapping("/of/follow")
    public Result queryOfFollow(@RequestParam(value = "offset", defaultValue = "0") Long offset, @RequestParam(value = "lastId") Long lastId){
         return blogService.queryOfFollow(offset, lastId);
    }
}
