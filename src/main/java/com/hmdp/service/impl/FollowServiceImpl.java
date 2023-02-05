package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
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
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;

    @Autowired
    private IBlogService blogService;
    /**
     * 当前用户关注/取关followerUser
     * @param followerUserId
     * @param isFollowed
     * @return
     */
    @Override
    public Result follow(Long followerUserId, Boolean isFollowed) {
        //1、获取当前用户的id
        Long userId = UserHolder.getUser().getId();
        //2、判断isFollowed的值，从而判断是关注还是取关
        String user_key = RedisConstants.FOLLOW_USER_KEY + userId;
        if(isFollowed){
            //2.1 关注followerUser，那么将这一条数据插入到数据库tb_follower中
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followerUserId);
            boolean isSuccess = save(follow);
            if(isSuccess){
                //将followerUserId添加到当前的用户的set中，来统计当前用户关注的人
                stringRedisTemplate.opsForSet().add(user_key, followerUserId.toString());
            }
        }else{
            //2.2 取关followerUser，那么将这一条数据删除
            boolean isSuccess = remove(new LambdaQueryWrapper<Follow>().eq(Follow::getUserId, userId).eq(Follow::getFollowUserId, followerUserId));
            if(isSuccess){
                //将followerUserId从当前用户关注的set中删除
                stringRedisTemplate.opsForSet().remove(user_key, followerUserId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 判断当前的用户是否已经关注了followerUserId用户
     * @param followUserId
     * @return
     */
    @Override
    public Result followOrNot(Long followUserId) {
        UserDTO user = UserHolder.getUser();
        if(user == null){
            //1、用户没有登录，那么默认是没有关注
            return Result.ok(false);
        }
        Long userId = user.getId();
        //获取当前用户关注的set的key
        String user_key = RedisConstants.FOLLOW_USER_KEY + userId;
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(user_key, followUserId.toString());
        return Result.ok(BooleanUtil.isTrue(isMember));
    }

    /**
     * 获取当前用户和otherUserId的共同关注对象
     * @param otherUserId
     * @return
     */
    @Override
    public Result queryCommonFollow(Long otherUserId) {
        Long userId = UserHolder.getUser().getId();
        String current_user_key = RedisConstants.FOLLOW_USER_KEY + userId;
        String other_user_key = RedisConstants.FOLLOW_USER_KEY + otherUserId;
        //获取当前用户和otherUserId共同关注的用户id
        List<Long> userIds = stringRedisTemplate.opsForSet().intersect(current_user_key, other_user_key)
                                                   .stream()
                                                   .map(Long::valueOf)
                                                   .collect(Collectors.toList());
        if(userIds == null || userIds.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<UserDTO> userDTO = userService.listByIds(userIds)
                .stream()
                .map(user -> {
                    return BeanUtil.copyProperties(user, UserDTO.class, "false");
                })
                .collect(Collectors.toList());
        return Result.ok(userDTO);
    }
}
