package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *
 * </p>
 *
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    SeckillVoucherServiceImpl seckillVoucherService;
    @Resource
    StringRedisTemplate stringRedisTemplate;
    //生成全局唯一ID，参数name
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    RedissonClient redissonClient;
    //线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    //创建阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    //使用lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    IVoucherOrderService proxy;

    //创建任务:从阻塞队列中拿取订单信息创建订单，在抢购之前，也就是在类服务启动后就执行。
    private class VoucherOderHandler implements Runnable {
        @Override
        public void run() {
//            while (true) {
//                try {
//                    /**
//                     * 从redis队列中拿去消息
//                     **/
//                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream()
//                            .read(Consumer.from("g1", "c1"),
//                                    StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
//                                    StreamOffset.create("stream.orders", ReadOffset.lastConsumed()));
//                    if (list == null || list.isEmpty()) {//如果没拿到，继续循环
//                        continue;
//                    }
//                    //解析数据
//                    MapRecord<String, Object, Object> entries = list.get(0);
//                    Map<Object, Object> value = entries.getValue();
//                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
//                    handleVoucherOrder(voucherOrder);
//                    //确认消息
//                    stringRedisTemplate.opsForStream().acknowledge("s1", "g1", entries.getId());
//                } catch (Exception e) {
//                    e.printStackTrace();
//                    log.error("订单处理异常");
//                    handlePendinglistOrder();
//
//                }
//            }
        }

        //处理pendingList数据
        private void handlePendinglistOrder() {
            while (true) {
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create("stream.orders", ReadOffset.from("0"))
                );
                if (list == null || list.isEmpty()) {
                    break;
                }
                MapRecord<String, Object, Object> entries = list.get(0);
                Map<Object, Object> value = entries.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                handleVoucherOrder(voucherOrder);
                stringRedisTemplate.opsForStream().acknowledge("s1", "g1", entries.getId());
            }
        }

        private void handleVoucherOrder(VoucherOrder voucherOrder) {
            Long userId = voucherOrder.getUserId();
            //获取锁
            RLock redisLock = redissonClient.getLock("lock:order:" + userId);//事实上这里不需要锁，不会发生线程安全问题，在这里没改，属于一个兜底方案
            boolean isLock = redisLock.tryLock();
            //判断获取锁是否成功
            if (!isLock) {
                log.error("不允许重复下单");
                return;
            }
            //synchronized (userId.toString().intern()){//我将锁加在了这个事物方法外面-
            try {
                proxy.createVoucherOrder(voucherOrder);
                //return proxy.createVoucherOrder(voucherId);//必须获取代理对象去调用
            } finally {
                redisLock.unlock();
            }
        }
    }

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOderHandler());
    }

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("stream.orders.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        //根据lua脚本判断用户是否有购买资格
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString(), String.valueOf(orderId));
        //根据结果如果没有返回结果
        int r = result.intValue();
        if (r != 0) {
            return Result.fail("没有购买资格");
        }
     /*   //如果有创建订单，并添加到阻塞队列中
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(oderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        //添加到阻塞队列中
        orderTasks.add(voucherOrder);*/
        //异步请求中需要，在这里创建。
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);
    }

    /*  @Override
      public Result seckillVoucher(Long voucherId) {
          Long userId = UserHolder.getUser().getId();
          long oderId = redisIdWorker.nextId("order");


          //根据lua脚本判断用户是否有购买资格
          Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());

          //根据结果如果没有返回结果

          int r = result.intValue();
          if (r!=0){
              return Result.fail("没有购买资格");
          }


          //如果有创建订单，并添加到阻塞队列中
          VoucherOrder voucherOrder = new VoucherOrder();
          long orderId = redisIdWorker.nextId("order");
          voucherOrder.setId(oderId);
          voucherOrder.setUserId(userId);
          voucherOrder.setVoucherId(voucherId);

          //添加到阻塞队列中
          orderTasks.add(voucherOrder);

          //异步请求中需要，在这里创建。
          proxy=(IVoucherOrderService)AopContext.currentProxy();
          return Result.ok(orderId);
      }
  */
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //5.实现一人一单限制
        //5.1判断用户是否下过单，查询订单表，条件voucherId，userId
        Long userId = voucherOrder.getUserId();
        //判断·根据用户id，优惠卷id查询订单表是否有订单？
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
            log.error("用户已经购买过一次");//不太可能出现，因为redis已经做了判断
            return;
        }
        //扣减库存 ：CAS解决超卖问题
        boolean success = seckillVoucherService.update()
                .setSql("stock= stock -1")
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0).update();
        if (!success) {
            log.error("库存不足");//不太可能出现，因为redis已经做了判断
            return;
        }
        //创建订单
        save(voucherOrder);
    }
}
