package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.events.Event;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static cn.hutool.poi.excel.sax.AttributeName.r;
import static com.hmdp.utils.RedisConstants.*;

/**
 * @author chenghao
 * @purpose：
 * @备注：redis工具类 * 方法1：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间 *
 * 方法2：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问 *
 * 方法3：根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题 * 方法4：根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题
 * @data 2022年08月03日 15:59
 */
@Component
public class CacheClient {

  StringRedisTemplate stringRedisTemplate;
  private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

  public CacheClient(StringRedisTemplate stringRedisTemplate) {
    this.stringRedisTemplate = stringRedisTemplate;
  }

  public void set(String key, Object value, Long time, TimeUnit unit) {
    stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
  }

  //Long time,TimeUnit unit,spring里的
  public void setWithLogicExpire(String key, Object value, Long time, TimeUnit unit) {
    RedisData redisData = new RedisData();
    redisData.setData(value);
    redisData.getExpireTime().plusSeconds(time);
    stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
  }

  //：根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
  public <R, ID> R queryWithPassThrough(String keyPrefx, ID id, Class<R> type,
      Function<ID, R> dbFallback, Long time, TimeUnit unit) {
    //组装key
    String key = keyPrefx + id;
    //根据key查询redis
    String json = stringRedisTemplate.opsForValue().get(key);
    //redis中存在，直接返回
    if (StrUtil.isNotBlank(json)) {
      return JSONUtil.toBean(json, type);
    }
    //判断是否为空
    if (json != null) {
      return null;
    }
    //查询数据库
    R r = dbFallback.apply(id);

    //判断从数据库拿到的r是否为空

    if (r == null) {
      stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
    }
    //不为空
    //写缓存
    set(key, r, time, unit);
    return r;
  }

  public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type,
      Function<ID, R> dbFallback, Long time, TimeUnit unit) {
    //根据ID查询缓存
    //组装key
    String key = keyPrefix + id;
    //从Redis中查询
    String json = stringRedisTemplate.opsForValue().get(key);
    //判断是否为空，为空直接返回
    if (StrUtil.isBlank(json)) {
      return null;
    }
    //存在，判段是否过期
    RedisData redisData = JSONUtil.toBean(json, RedisData.class);
    LocalDateTime expireTime = redisData.getExpireTime();
    R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);

    //如果没有过期
    if (expireTime.isAfter(LocalDateTime.now())) {
      return r;
    }
    //如果过期：我们需要开启一个新的线程去，本线程继续去返回旧值。
    Boolean isFlag = tryLock(id);

    if (isFlag) {
      CACHE_REBUILD_EXECUTOR.submit(() -> {
        try {
          // 查询数据库
          R newR = dbFallback.apply(id);
          // 重建缓
          this.setWithLogicalExpire(key, newR, time, unit);
        } catch (Exception e) {
          throw new RuntimeException(e);
        } finally {
          // 释放锁
          unLock(id);
        }
      });
    }
    return r;
  }

  //基于互斥锁解决缓存击穿问题
  public <R, ID> R queryWithMutex(
      String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time,
      TimeUnit unit) {
    String key = keyPrefix + id;
    // 1.从redis查询商铺缓存
    String shopJson = stringRedisTemplate.opsForValue().get(key);
    // 2.判断是否存在
    if (StrUtil.isNotBlank(shopJson)) {
      // 3.存在，直接返回
      return JSONUtil.toBean(shopJson, type);
    }
    // 判断命中的是否是空值
    if (shopJson != null) {
      // 返回一个错误信息
      return null;
    }

    // 4.实现缓存重建
    // 4.1.获取互斥锁
    String lockKey = LOCK_SHOP_KEY + id;
    R r = null;
    try {
      boolean isLock = tryLock(lockKey);
      // 4.2.判断是否获取成功
      if (!isLock) {
        // 4.3.获取锁失败，休眠并重试
        Thread.sleep(50);
        return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
      }
      // 4.4.获取锁成功，根据id查询数据库
      r = dbFallback.apply(id);
      // 5.不存在，返回错误
      if (r == null) {
        // 将空值写入redis
        stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
        // 返回错误信息
        return null;
      }
      // 6.存在，写入redis
      this.set(key, r, time, unit);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } finally {
      // 7.释放锁
      unLock(id);
    }
    // 8.返回
    return r;
  }


  //设置逻辑过期时间，并将对象和过期时间封装在RedisData类中
  //加锁
  public <ID> Boolean tryLock(ID id) {
    //组装key
    String key = LOCK_SHOP_KEY + id;
    //给key加锁
    Boolean isFlag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
    //这里不可以直接返回isFlag，会自动拆箱？
    //return isFlag;
    return BooleanUtil.isTrue(isFlag);
  }

  public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
    // 设置逻辑过期
    RedisData redisData = new RedisData();
    redisData.setData(value);
    redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
    // 写入Redis
    stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
  }

  //删除锁
  public <ID> void unLock(ID id) {
    //组装key
    String key = LOCK_SHOP_KEY + id;
    stringRedisTemplate.delete(key);
  }


}
