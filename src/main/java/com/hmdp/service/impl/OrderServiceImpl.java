package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Order;
import com.hmdp.mapper.OrderMapper;
import com.hmdp.service.IOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {
    public Result updateOrder(Long id,Integer status){
        update().set("order_status",status)
                .eq("order_id",id).update();
        log.info("订单："+id+" 状态已更新为："+status);
        return  Result.ok();
    }
}
