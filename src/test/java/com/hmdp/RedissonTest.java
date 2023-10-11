package com.hmdp;

import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import javax.annotation.Resource;

public class RedissonTest {

  @Resource
  RedissonClient redissonClient;

  @Test
  void voidTest() {
    RLock anyLock = redissonClient.getLock("anyLock");
    anyLock.tryLock();
  }
}
