package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;
//用于通过逻辑过期时间解决缓存击穿问题
@Data
public class RedisData {
    //逻辑过期时间
    private LocalDateTime expireTime;
    //存储类对象实例
    private Object data;
}
