package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

  @Resource
  IUserService userService;
  @Resource
  StringRedisTemplate stringRedisTemplate;


  @Override
  public Result queryBlogbyId(Long id) {
    //查询Blog
    Blog blog = getById(id);

    if (blog == null) {
      return Result.fail("笔记不存在");
    }
    //根据Blog查询用户
    queryBlogUser(blog);
    // isBlogLiked(blog);
    return Result.ok(blog);
  }

  @Override
  public Result queryHotBlog(Integer current) { // 根据用户查询
    Page<Blog> page = query()
        .orderByDesc("liked")
        .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
    // 获取当前页数据
    List<Blog> records = page.getRecords();// 查询用户
    records.forEach(blog -> {
      this.queryBlogUser(blog);
      //    this.isBlogLiked(blog);
    });

    return Result.ok(records);
  }

  /*
 1.此方法主要是判断用户是否点过赞，进行点赞处理，对于点赞标识isLike，在涉及查询需求中处理isLike字段
 2.为点赞排序修改set为SortedSet
   */
  @Override
  public Result likeBlog(Long id) {
    //在Redis中建立一个set集合，key为Blog的ID，值为一个存储用户的ID
    //获取当前用户
    UserDTO user = UserHolder.getUser();
    Long userID = user.getId();

    String key = BLOG_LIKED_KEY + id;
    //Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userID.toString());
    Double score = stringRedisTemplate.opsForZSet().score(key, userID.toString());

    if (score == null) {
      //如果不存在，可以点赞给数据库中的点赞数加一
      boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
      //如果点赞成功
      if (isSuccess) {
        stringRedisTemplate.opsForZSet().add(key, userID.toString(), System.currentTimeMillis());
      }
    } else {
      //如果点过赞，本次点赞将取消上次点赞
      boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
      if (isSuccess) {
        stringRedisTemplate.opsForZSet().remove(key, userID.toString());
      }
    }
    return Result.ok();

  }

  @Override
  public Result queryBlogLikes(Long id) {
    //
    String key = BLOG_LIKED_KEY + id;
    Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
    if (top5 == null || top5.isEmpty()) {
      return Result.ok(Collections.emptyList());
    }// 2.解析出其中的用户id
    List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
    String idStr = StrUtil.join(",", ids);
    // 3.根据用户id查询用户 WHERE id IN ( 5 , 1 ) ORDER BY FIELD(id, 5, 1)
    List<UserDTO> userDTOS = userService.query()
        .in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list()
        .stream()
        .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
        .collect(Collectors.toList());
    // 4.返回
    return Result.ok(userDTOS);

  }

  //修改点赞状态：对应前端👍按钮高亮与否
  private void isBlogLiked(Blog blog) {
    UserDTO user = UserHolder.getUser();
    Long userID = user.getId();
    if (user == null) {
      //用户为登陆，无需点赞
      return;
    }
    String key = BLOG_LIKED_KEY + blog.getId();
    Double score = stringRedisTemplate.opsForZSet().score(key, userID.toString());

    blog.setIsLike(score != null);

  }

  //从blog中查询出
  private void queryBlogUser(Blog blog) {
    Long userId = blog.getUserId();
    User user = userService.getById(userId);

    blog.setIcon(user.getIcon());
    blog.setName(user.getNickName());
  }

  @Override
  public Result queryBlogOfFollow(Long max, Integer offset) {
    // 1.获取当前用户
    Long userId = UserHolder.getUser().getId();
    // 2.查询收件箱 ZREVRANGEBYSCORE key Max Min LIMIT offset count
    String key = FEED_KEY + userId;
    Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
        .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
    // 3.非空判断
    if (typedTuples == null || typedTuples.isEmpty()) {
      return Result.ok();
    }
    // 4.解析数据：blogId、minTime（时间戳）、offset
    List<Long> ids = new ArrayList<>(typedTuples.size());
    long minTime = 0; // 2
    int os = 1; // 2
    for (ZSetOperations.TypedTuple<String> tuple : typedTuples) { // 5 4 4 2 2
      // 4.1.获取id
      ids.add(Long.valueOf(tuple.getValue()));
      // 4.2.获取分数(时间戳）
      long time = tuple.getScore().longValue();
      if (time == minTime) {
        os++;
      } else {
        minTime = time;
        os = 1;
      }
    }
    os = minTime == max ? os : os + offset;
    // 5.根据id查询blog
    String idStr = StrUtil.join(",", ids);
    List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

    for (Blog blog : blogs) {
      // 5.1.查询blog有关的用户
      queryBlogUser(blog);
      // 5.2.查询blog是否被点赞
      isBlogLiked(blog);
    }

    // 6.封装并返回
    ScrollResult r = new ScrollResult();
    r.setList(blogs);
    r.setOffset(os);
    r.setMinTime(minTime);

    return Result.ok(r);
  }
}
