package com.hmdp.utils.Interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;


/**
 * @author dingchuan
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {

  private StringRedisTemplate stringRedisTemplate;

  public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
    this.stringRedisTemplate = stringRedisTemplate;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    //前端将token携带在请求头中
    String token = request.getHeader("authorization");
    //先判断token是否为空，为空直接拦截返回错误
    if (StrUtil.isBlank(token)) {
      return true;
    }
    //根据拿到的token，然后拼成完整的key
    String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
    //根据key获取用户
    Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);
    //如果userMap为空，为空直接拦截返回错误
    if (userMap.isEmpty()) {
      return true;
    }
    //userMap不为空，将userMap转换为userDTO对象并报错在ThreadLocal里。

    UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
    UserHolder.saveUser(userDTO);
    //测试
    System.out.println("-----------------刷新token");
    //最后刷新token
    stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
    return true;
  }
}
