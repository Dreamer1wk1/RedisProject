package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.entity.Order;
import com.hmdp.service.IOrderService;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;

import static com.hmdp.utils.MQConstants.*;
import static com.hmdp.utils.OrderConstants.CANCELED;

@Service
@Slf4j
@RocketMQMessageListener(
        topic = ORDER_CANCEL_TOPIC,
        consumerGroup = "consumer_group",
        messageModel = MessageModel.CLUSTERING
)
public class OrderCancelConsumerService implements RocketMQListener<Long>{
    @Resource
    private IOrderService orderService;

    @Override
    public void onMessage(Long id) {
        log.info("OrderCancelConsumer收到订单：" + id);
        Order order= orderService.query().eq("order_id",id).one();
        if(order==(null)){
            log.info("订单信息错误");
            return;
        }
        if(order.getOrderStatus()==0){
            orderService.updateOrder(id,CANCELED);
            log.info("订单："+id+"超时取消");
        }
    }
}