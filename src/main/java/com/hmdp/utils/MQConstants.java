package com.hmdp.utils;

public class MQConstants {
    public static final String DEFAULT_TOPIC="Order_Topic";
    public static final String ORDER_CANCEL_TOPIC="Order_Cancel_Topic";
    public static final String SECKILL_TOPIC="Seckill_Topic";

    //延时消息一共分为18个等级分别为：1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
    public static final Integer ORDER_CANCEL_LEVEL=4;
}
