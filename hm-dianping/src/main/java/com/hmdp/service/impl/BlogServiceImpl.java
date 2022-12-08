package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import com.sun.deploy.util.StringUtils;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
@Slf4j
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IFollowService followService;

    /**
     * 发布blog
     * @param blog
     * @return
     */
    @Override
    public Result saveBlog(Blog blog) {
        // 1、获取登录用户
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        blog.setUserId(userId);
        // 2、保存探店博文
        Boolean isSuccess = save(blog);
        if(BooleanUtil.isFalse(isSuccess)){
            return Result.fail("发布blog失败");
        }
        // 3、将blog推送给粉丝
        // 3. 1 查询数据库，从而获取当前用户的所有粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", userId).list();
        // 3. 2 获取所有的follow中的userId，就是粉丝的id,然后分别推送到粉丝id中的收件箱中
        //并且是根据发布的时间降序排序的
        for(Follow follow : follows){
            stringRedisTemplate.opsForZSet().add(RedisConstants.FEED_KEY + follow.getUserId(), blog.getId().toString(), System.currentTimeMillis());
        }
        // 4、返回id
        return Result.ok(blog.getId());
    }

    /**
     * 查看热点博客
     * @param current
     * @return
     */
    @Override
    public Result queryHotBlog(Integer current) {

        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            this.queryBlogUser(blog);
            this.isLikeByCurrentUser(blog);
        });
        return Result.ok(records);
    }

    /**
     * 根据id来查看博客
     * 1、传递的id是blog的id，然后判断能够找到这个博客
     * 1.1 不能找到，那么直接返回错误信息
     * 2、能够找到，那么同时需要查找当前博客的作者，来设置user
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        Blog blog = getById(id);
        if(blog == null){
            return Result.fail("博客不存在");
        }
        //获取当前博客的作者
        queryBlogUser(blog);
        isLikeByCurrentUser(blog);
        return Result.ok(blog);
    }

    /**
     * 判断当前的博客是否已经被当前的用户点赞了
     * @param blog
     */
    public void isLikeByCurrentUser(Blog blog) {
        UserDTO userDTO = UserHolder.getUser();
        if(userDTO == null){
            //用户没有登录,那么默认这个博客没有被当前访客点赞
            return;
        }
        Long userId = userDTO.getId();
        //判断当前的用户是否已经点赞过这个博客
        Double score = stringRedisTemplate.opsForZSet().score(RedisConstants.BLOG_LIKED_KEY + blog.getId(), userId.toString());
        blog.setIsLike(score != null);
    }

    public void queryBlogUser(Blog blog){
        User user = userService.getById(blog.getUserId());
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    /**
     * 实现博客的点赞:
     * 1、判断用户是否已经登录
     * 1.1 用户没有登录，那么返回错误提示信息 ---> 已经设置了拦截器
     * 2、用户已经登录，那么为了保证每一个用户只能点赞一次，那么利用redis
     * 中的set集合。但是为了点赞过程中获取获取前5名点赞用户，所以需要根据
     * 点赞的时间顺序进行排序。所以需要利用zset。
     * 而在zset中没有isMember方法的判断，所以只能通过score来获取用户的score值
     * 如果为null，说明这个用户没有点赞过，否则已经点赞过了
     * 2.1 用户之前已经点赞过了,再次点击的时候，那么需要将当前的用户
     * 从set中移除，同时将数据库中的这一篇博客的点赞数减1
     * 2.2 用户没有点赞过，那么第一次点击的时候，需要将当前的用户添加到
     * set中，同时将数据库的博客点赞数+1
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String blog_key = RedisConstants.BLOG_LIKED_KEY + id;
        //1、利用zset中的score方法，判断用户是否已经登录
        Double score = stringRedisTemplate.opsForZSet().score(blog_key, userId.toString());
        if(score == null){
            //1.1 用户没有点赞过，那么更新数据库的点赞数+1，同时将用户添加到redis中
            Boolean isSuccess = update(new UpdateWrapper<Blog>().setSql("liked = liked + 1").eq("id", id));
            if(BooleanUtil.isTrue(isSuccess)){
                //数据库操作成功，那么就将用户保存到redis中，score的值是点赞的时间戳
                stringRedisTemplate.opsForZSet().add(blog_key, userId.toString(), System.currentTimeMillis());
            }
        }else{
            //1.2 已经点赞过了，那么更新数据库的点赞数-1，同时将用户从redis中移除
            Boolean isSuccess = update(new UpdateWrapper<Blog>().setSql("liked = liked - 1").eq("id", id));
            if(BooleanUtil.isTrue(isSuccess)){
                stringRedisTemplate.opsForZSet().remove(blog_key, userId.toString());
            }
        }
        return Result.ok();
    }
    /*
    @Override
    public Result likeBlog(Long id) {
        String userId = UserHolder.getUser().getId().toString();
        String blog_key = RedisConstants.BLOG_LIKED_KEY + id;
        //注意将userId转成String类型，因为使用的是StringRedisTemplate
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(blog_key, userId);
        System.out.println(isMember);
        if(Boolean.TRUE.equals(isMember)){
            //2.1 如果已经点赞过了,那么再次点击的时候，需要将点赞数减1，并将当前的用户从set中移除
            Boolean isSuccess = update(new UpdateWrapper<Blog>().setSql("liked = liked - 1").eq("id", id));
            if(BooleanUtil.isTrue(isSuccess)){
                //更新redis中的set，将当前用户从set中删除
                stringRedisTemplate.opsForSet().remove(blog_key, userId);
            }
        }else{
            //2.2 没有点赞过，那么将当前的用户添加到set中，并且更新数据库的点赞数
            boolean isSuccess = update(new UpdateWrapper<Blog>().setSql("liked = liked + 1").eq("id", id));
            if(BooleanUtil.isTrue(isSuccess)){
                //数据库操作成功之后，才可以更新redis
                stringRedisTemplate.opsForSet().add(blog_key, userId);
            }
        }
        return Result.ok();
    }*/

    /**
     * 获取点赞前5个人(时间戳先后顺序，获取最先点赞的前5个人)
     * 1、利用zrange的方法，获取下标为[0,4]之间的用户id
     * 1.1可能没有人点赞过博客，所以这时候就直接返回一个空集合
     * 1.2根据id，来获取用户
     * 2、将查询到的用户返回给前端
     * @param id
     * @return
     */
    @Override
    public Result likesBlogTop5(Long id) {
        String blog_key = RedisConstants.BLOG_LIKED_KEY + id;
        List<Long> userIds = stringRedisTemplate.opsForZSet().range(blog_key, 0, 4).stream()
                                                               .map(Long::valueOf) //将zset中的string类型的值转成Long类型
                                                               .collect(Collectors.toList());
        if(userIds == null || userIds.isEmpty()){
            //1.1 没有用户点赞过这个博客
            return Result.ok(Collections.emptyList());
        }
        //根据userIds，来查询用户,但是这时候listByIds是根据in子句查询的
        //所以在mysql中根据in子句查询的时候，得到的users对象并不是根据
        //上面的userIds排序的,也即导致users不是根据时间戳先后顺序排序
        //所以需要自定义排序顺序，使得是根据userIds排序的
        String idStr = StrUtil.join( ",",userIds);
        List<User> users = userService.query().in("id", userIds)
                //自定义排序顺序，使得id是根据idStr进行排序的,而idStr就是userIds中元素顺序
                .last("ORDER BY Field(id," + idStr + ")")
                .list()
                .stream()
                .collect(Collectors.toList());
        //2、由于User对象涉及到一些隐私信息，所以需要转成UserDTO
        List<UserDTO> userDTOs = users.stream()
                .map(user -> {
            return BeanUtil.copyProperties(user, UserDTO.class);
        })
                .collect(Collectors.toList());
        return Result.ok(userDTOs);
    }

    /**
     * 获取userId中的第current页的笔记
     * @param userId
     * @param current
     * @return
     */
    @Override
    public Result queryByUser(Long userId, Long current) {
        Page<Blog> page = query().eq("user_id", userId) //获取当前用户的博客
                //根据点赞数降序排序
                .orderByDesc("liked")
                //获取第current页的记录，并且每一页有MAX_PAGE_SIZE条
                .page(new Page<Blog>(current, SystemConstants.MAX_PAGE_SIZE));
        //将page页的博客通过getRecords方法获取出来
        List<Blog> records = page.getRecords();
        records.forEach(blog -> {
            this.isLikeByCurrentUser(blog);
            this.queryBlogUser(blog);
        });
        return Result.ok(records);
    }

    /**
     * 获取当前用户的第current页的博客
     * @param current
     * @return
     */
    @Override
    public Result queryMyBlog(Integer current) {
        // 获取登录用户
        UserDTO userDTO = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = query()
                .eq("user_id", userDTO.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        records.forEach(blog -> {
            this.isLikeByCurrentUser(blog);
            this.queryBlogUser(blog);
        });
        return Result.ok(records);
    }

    /**
     * Feeds推送，并且实现滚动分页查询,从而获取关注者发布的blog
     * @param offset
     * @param lastId
     * @return
     */
    @Override
    public Result queryOfFollow(Long offset, Long lastId) {

        //1、获取当前的登陆用户
        Long userId = UserHolder.getUser().getId();
        //2、根据Feed流，进行滚动查询，其中是根据上一次查询到的记录后面的offset开始
        //lastId就是上一次查询记录的时间戳
        String key = RedisConstants.FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key,  0,lastId, offset, 5L);
        if(typedTuples == null || typedTuples.isEmpty()){
            //2.1 数据为空
            return Result.ok();
        }
        //3、解析数据，获取关注着的blogId,以及发布时间，然后统计记录中的最小时间戳以及出现的次数
        //为下一次请求的时候，发送offset以及lastId
        List<Long> blogIds = new ArrayList<>();
        long minTime = 0L, time;
        int count = 0;
        for(ZSetOperations.TypedTuple<String> typedTuple : typedTuples){
            //获取blogId
            long blogId = Long.parseLong(typedTuple.getValue());
            blogIds.add(blogId);
            //获取时间戳，由于zset已经实现了降序，所以可以直接遍历，那么最后一个必然就是这一次查询
            //中的最小时间戳
            time = typedTuple.getScore().longValue();
            if(time != minTime){
                minTime = time;
                count = 1;
            }else{
                ++count;
            }
        }
        //获取查询到的blog，但是需要根据Order BY FIELD (id, xxx, xxx)方式排序查询
        //因为如果直接是listByIds,那么数据库根据in子句进行查询，此时查询到的数据不一定
        //是和上面分页记录中的blogIds一致
        String idStr = StrUtil.join(",", blogIds);
        System.out.println("idStr = " + idStr);
        List<Blog> blogs = query().in("id", blogIds)
                .last("ORDER BY Field(id, " + idStr + " )").list();
        //4、对每一个blog，都需要获取blog的作者，避免点击blog的时候，发现作者发生报错
        //同时需要判断blog是否被当前的用户点赞了
        blogs.forEach(blog -> {
                isLikeByCurrentUser(blog);
                queryBlogUser(blog);
        });
        //5、封装blogs，count以及minTime
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset(count);
        return Result.ok(scrollResult);
    }
}
