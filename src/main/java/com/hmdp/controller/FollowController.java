package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Autowired
    private IFollowService followService;

    /**
     *
     * @param followerUserId 需要关注或者取关的用户id
     * @param isFollowed  判断是否关注还是取关，如果为true，则是关注，否则取关
     * @return
     */
    @PutMapping("/{followerUserId}/{isFollowed}")
    public Result follow(@PathVariable("followerUserId")Long followerUserId, @PathVariable("isFollowed")Boolean isFollowed){
         return followService.follow(followerUserId, isFollowed);
    }

    /**
     * 判断当前的用户是否关注了followUserId这个用户
     * @param followUserId
     * @return
     */
    @GetMapping("/or/not/{followUserId}")
    public Result followOrNot(@PathVariable("followUserId")Long followUserId){
        return followService.followOrNot(followUserId);
    }

    /**
     * 获取当前的用户和otherUserId的共同关注的人
     * @param otherUserId
     * @return
     */
    @GetMapping("/common/{otherUserId}")
    public Result queryCommonFollow(@PathVariable("otherUserId")Long otherUserId){
        return followService.queryCommonFollow(otherUserId);
    }

}
