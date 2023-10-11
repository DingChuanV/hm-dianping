package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@SpringBootTest
class HmDianPingApplicationTests {

  @Resource
  ShopServiceImpl shopService;
  @Resource
  RedisIdWorker redisIdWorker;

  @Test
  public void testSaveShopToRedis() {
    shopService.saveShop2Redis(1L, 100L);
  }

  @Test
  public void testRedisWorker() {
    LocalDateTime now = LocalDateTime.of(2022, 8, 4, 14, 0, 0);
    long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
    System.out.println(nowSecond);
  }
}
