package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.entity.Order;

public interface IOrderService extends IService<Order> {
    public Result updateOrder(Long id, Integer status);
}
