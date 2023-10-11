package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;

public class UserHolder {

  /**
   * 提供线程内的局部变量--本地线程变量
   */
  private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();
  private static final ThreadLocal<User> t2 = new ThreadLocal<>();

  /**
   * 保存
   *
   * @param user
   */
  public static void saveUser(UserDTO user) {
    tl.set(user);
  }

  public static void save(User user) {
    t2.set(user);
  }

  /**
   * 获取
   *
   * @return
   */
  public static UserDTO getUser() {
    return tl.get();
  }

  /**
   * 移除
   */
  public static void removeUser() {
    tl.remove();
  }

  public static void remove() {
    t2.remove();
  }
}
