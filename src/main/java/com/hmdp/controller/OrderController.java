package com.hmdp.controller;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Order;
import com.hmdp.service.IOrderService;
import com.hmdp.service.impl.OrderCancelProducerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@Slf4j
@RequestMapping("/order")
public class OrderController {
    @Resource
    private IOrderService orderService;
    @Resource
    private OrderCancelProducerService orderCancelProducerService;

    @PostMapping
    public Result createOrder(@RequestBody Order order) {
        orderService.save(order);
        orderCancelProducerService.scheduled(order.getOrderId());
        log.info("创建订单："+order.getOrderId());
        return Result.ok();
    }

    /**
     *
     * @param id
     * @param status 订单状态 0待支付 1已支付 2已取消
     * @return
     */
    @PostMapping("/pay/{id}/{status}")
    public Result payOrder(@PathVariable ("id") Long id ,@PathVariable ("status") Integer status ) {
        orderService.updateOrder(id,status);
        return Result.ok();
    }
}
