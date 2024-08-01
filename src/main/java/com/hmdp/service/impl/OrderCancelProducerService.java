package com.hmdp.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import static com.hmdp.utils.MQConstants.*;

@Slf4j
@Service
public class OrderCancelProducerService {
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    public void scheduled(Long id) {
        Message<Long> rocketMessage = MessageBuilder.withPayload(id).build();
        SendResult sendResult = rocketMQTemplate.syncSend(ORDER_CANCEL_TOPIC, rocketMessage, 1000, ORDER_CANCEL_LEVEL);
        log.info("延时级别：" + ORDER_CANCEL_LEVEL + "，订单：" + id+"，发送：" + sendResult.getSendStatus().toString());
    }

}
