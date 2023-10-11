package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

  //创建线程池：我们在逻辑过期解决缓存击穿时，如果逻辑过期需要开启一个线程（加锁）去查询数据库，并写入缓存
  private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
  @Resource
  private CacheClient cacheClient;
  @Resource
  private StringRedisTemplate stringRedisTemplate;

  @Override
  public Result queryById(Long id) {
    // 解决缓存穿透
    Shop shop = cacheClient
        .queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL,
            TimeUnit.MINUTES);

    // 互斥锁解决缓存击穿
        /*  Shop shop = cacheClient
            .queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);*/

    // 逻辑过期解决缓存击穿
        /*   Shop shop = cacheClient
                .queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);*/
    if (shop == null) {
      return Result.fail("店铺不存在");
    }
    return Result.ok(shop);

  }


  //更新
  @Override
  @Transactional
  public Result update(Shop shop) {
    Long id = shop.getId();
    if (id == null) {
      return Result.fail("店铺ID不能为空");
    }
    //先更新数据库
    updateById(shop);
    //删除缓存
    Boolean isDelete = stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
    System.out.println(isDelete);
    return Result.ok();
  }

  //缓存置空解决穿透问题：
  public Shop queryWithPassThrough(Long id) {
    //先查询Redis缓存，如果有则返回，如果没有查询数据库，并且写入缓存库
    String key = CACHE_SHOP_KEY + id;
    String shopJson = stringRedisTemplate.opsForValue().get(key);

    if (StrUtil.isNotBlank(shopJson)) {//只有里边儿有数据才true，例如："abc"
      Shop shop = JSONUtil.toBean(shopJson, Shop.class);
      return shop;
    } else if (shopJson != null) { //不等于null，并且里边没有数据，就为空字符串
      return null;
    }
    Shop shop = getById(id);
    if (shop == null) {
      //缓存穿透：如果没有找到则设置缓存value=""；
      stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
      return null;
    }
    shopJson = JSONUtil.toJsonStr(shop);
    //添加过期时间
    stringRedisTemplate.opsForValue()
        .set(CACHE_SHOP_KEY + id, shopJson, CACHE_SHOP_TTL, TimeUnit.MINUTES);

    return shop;
  }

  //缓存穿透的基础上加互斥锁防止缓存击穿（热点key）
  public Shop queryWithMutex(Long id) {
    //先查询Redis缓存，如果有则返回，如果没有查询数据库，并且写入缓存库
    String key = CACHE_SHOP_KEY + id;
    String shopJson = stringRedisTemplate.opsForValue().get(key);

    if (StrUtil.isNotBlank(shopJson)) {//只有里边儿有数据才true，例如："abc"
      Shop shop = JSONUtil.toBean(shopJson, Shop.class);
      return shop;
    } else if (shopJson != null) { //不等于null，并且里边没有数据，就为空字符串
      return null;
    }
    String lockKey = LOCK_SHOP_KEY + id;
    Shop shop = null;
    try {
      Boolean isLock = tryLock(lockKey);
      if (!isLock) {
        Thread.sleep(50);
        return queryWithMutex(id);
      }
      //如果拿到了锁，查询数据库，如果不存在置空，返回错误
      shop = getById(id);

      if (shop == null) {
        //缓存穿透：如果没有找到则设置缓存value=""；
        stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
        return null;
      }
      //添加过期时间
      stringRedisTemplate.opsForValue()
          .set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
    } catch (InterruptedException e) {
      new RuntimeException();
    } finally {
      //释放锁
      unLock(lockKey);
    }
    return shop;
  }

  //基于逻辑过期解决缓存击穿
  @Override
  public Shop queryWithLogicalExpire(Long id) {

    //先查询Redis缓存，如果没有直接返回null
    String key = CACHE_SHOP_KEY + id;
    String shopJson = stringRedisTemplate.opsForValue().get(key);

    if (StrUtil.isBlank(shopJson)) {//只有里边儿有数据才true，例如："abc"
      return null;
    }
    //如果查询到缓存，查看是否逻辑过期
    //shopJso反序列化为shop对象
    RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
    //(JSONObject)redisData.getData()这里需要将shop对象转换为Json对象
    Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
    LocalDateTime expireTime = redisData.getExpireTime();
    if (expireTime.isAfter(LocalDateTime.now())) {
      return shop;
    }
    //如果过期
    String lockKey = LOCK_SHOP_KEY + id;
    Boolean isFlag = tryLock(lockKey);
    if (isFlag) {
      CACHE_REBUILD_EXECUTOR.submit(() -> {
        try {
          //重建缓存
          this.saveShop2Redis(id, 20L);
        } catch (Exception e) {
          throw new RuntimeException(e);
        } finally {
          unLock(lockKey);
        }
      });
    }
    return shop;
  }


  //加锁
  public Boolean tryLock(String key) {
    //给key加锁
    Boolean isFlag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
    //这里不可以直接返回isFlag，会自动拆箱？
    //return isFlag;
    return BooleanUtil.isTrue(isFlag);
  }

  //删除锁
  public void unLock(String key) {
    stringRedisTemplate.delete(key);
  }

  //根据ID查询到用户，将用户和逻辑过期时间作为成员变量封装在RedisData中，在测试类中做数据预热
  public void saveShop2Redis(Long id, Long expireSeconds) {
    Shop shop = getById(id);
    RedisData redisData = new RedisData();
    redisData.setData(shop);
    redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

    stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
  }
}

