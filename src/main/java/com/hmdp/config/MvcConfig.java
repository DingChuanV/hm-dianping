package com.hmdp.config;

import com.hmdp.utils.Interceptor.LoginInterceptor;
import com.hmdp.utils.Interceptor.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * 为了使登陆校验拦截器生效，将其注册到WebMvcConfigurer
 *
 * @author dingchuan
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

  @Resource
  private StringRedisTemplate stringRedisTemplate;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    // 注册登陆验证拦截器
    registry.addInterceptor(new LoginInterceptor())
        // 排除一下这些路径
        .excludePathPatterns(
            "/shop/**",
            "/voucher/**",
            "/shop-type/**",
            "/upload/**",
            "/blog/hot",
            "/user/code",
            "/user/login"
        ).order(1);
    // 注册刷新token令牌拦截器
    registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**")
        .order(0);
  }
}
