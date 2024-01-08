package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import io.swagger.annotations.Api;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;


/**
 * @author dingchuan
 */
@RestController
@RequestMapping("/blog")
@Api(tags = "博客控制器")
public class BlogController {

  @Resource
  private IBlogService blogService;
  @Resource
  private IUserService userService;

  @PostMapping

  public Result saveBlog(@RequestBody Blog blog) {
    // 获取登录用户
    UserDTO user = UserHolder.getUser();
    blog.setUserId(user.getId());
    // 保存探店博文
    blogService.save(blog);
    // 返回id
    return Result.ok(blog.getId());
  }

  // 处理点赞操作
  @PutMapping("/like/{id}")
  public Result likeBlog(@PathVariable("id") Long id) {
    // 修改点赞数量
    return blogService.likeBlog(id);
  }

  @GetMapping("/of/me")
  public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
    // 获取登录用户
    UserDTO user = UserHolder.getUser();
    // 根据用户查询
    Page<Blog> page = blogService.query()
        .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
    // 获取当前页数据
    List<Blog> records = page.getRecords();
    return Result.ok(records);
  }

  @GetMapping("/hot")
  public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
    return blogService.queryHotBlog(current);
  }

  /**
   * 根据ID查询Blog表
   *
   * @param id
   * @return
   */
  @GetMapping("/{id}")
  public Result queryBlog(@PathVariable Long id) {
    return blogService.queryBlogbyId(id);
  }

  //点赞排行接口
  @GetMapping("/likes/{id}")
  public Result queryBlogLikes(@PathVariable("id") Long id) {

    return blogService.queryBlogLikes(id);
  }
}
