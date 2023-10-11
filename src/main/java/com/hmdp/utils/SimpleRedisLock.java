package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;


/**
 * @author dingchuan
 */
public class SimpleRedisLock implements ILock {

  //可用于区分不同业务的前缀
  private String name;
  //说明这是一个锁
  private static final String KEY_PREFIX = "lock";
  //UUID
  private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

  private StringRedisTemplate stringRedisTemplate;

  private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

  static {
    UNLOCK_SCRIPT = new DefaultRedisScript<>();
    UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
  }

  public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
    this.name = name;
    this.stringRedisTemplate = stringRedisTemplate;
  }

  @Override
  public boolean tryLock(long timeoutSec) {

    //获取线程标识
    String threadId = ID_PREFIX + Thread.currentThread().getId();
    //获取锁
    Boolean success = stringRedisTemplate.opsForValue()
        .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);

    return Boolean.TRUE.equals(success);

  }

  @Override
  public void unlock() {

    stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(KEY_PREFIX + name),
        ID_PREFIX + Thread.currentThread().getId());


  }
    /*
    @Override
    public void unlock() {
        //获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //获取锁的标识
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        //判断是否一致
        if (threadId.equals(id)){
            //释放
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }

    }
*/

}
