package com.hmdp.utils.Interceptor;


import com.hmdp.entity.User;
import com.hmdp.utils.UserHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;


/**
 * 实现登陆校验拦截器
 *
 * @author dingchuan
 */
public class LoginInterceptor implements HandlerInterceptor {

  // StringRedisTemplate stringRedisTemplat;

  /**
   * public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
   * this.stringRedisTemplate = stringRedisTemplate;
   * }
   **/
  /**
   * 在拦截之前做用户登陆校验
   *
   * @param request  current HTTP request
   * @param response current HTTP response
   * @param handler  chosen handler to execute, for type and/or instance evaluation
   * @return
   * @throws Exception
   */
  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {

    /**
     * 我们要用redis替代session，所以这里使用redis，但是我们不能在这里直接注入，
     * 因为该拦截器，是我们在MvcConfig类里手动new的，该对象不由spring管，所以我们构造注入
     */
//        UserDTO user = UserHolder.getUser();
//        if (user == null) {
//            response.setStatus(401);
//            return false;
//        }
//        return true;
    // 1.从HttpServletRequest获取session
    HttpSession session = request.getSession();
    // 2.获取session中的用户
    User user = (User) session.getAttribute("user");
    // 3.判断用户是否存在，如果存在就将用户的信息放到ThreadLocal，如果不存在，就拦截
    if (user == null) {
      response.setStatus(401);
      return false;
    }
    // 将用户的信息存到本地线程变量中，每一个Controller去拿ThreadLocal保存用户变量信息的副本
    UserHolder.save(user);
    // 4.放行
    return true;
  }

  /**
   * 在拦截之后将用户的信息传递给每个Controller
   *
   * @param request  current HTTP request
   * @param response current HTTP response
   * @param handler  the handler (or {@link HandlerMethod}) that started asynchronous execution, for
   *                 type and/or instance examination
   * @param ex       any exception thrown on handler execution, if any; this does not include
   *                 exceptions that have been handled through an exception resolver
   * @throws Exception
   */
  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
      Object handler, Exception ex) throws Exception {
    HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    // 在登陆校验之后，考虑到内存，将用户信息移除
    UserHolder.remove();
  }
}
