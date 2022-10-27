package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;
import org.apache.ibatis.annotations.Lang;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    //秒杀优惠卷
    Result seckillVoucher(Long voucherId);
    //
    void createVoucherOrder(VoucherOrder voucherOrder);
}
