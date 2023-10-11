package com.hmdp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.User;
import com.hmdp.utils.SystemConstants;

import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

  Result queryBlogbyId(Long id);

  Result queryHotBlog(Integer current);

  Result likeBlog(Long id);

  Result queryBlogLikes(Long id);

  Result queryBlogOfFollow(Long max, Integer offset);
}
