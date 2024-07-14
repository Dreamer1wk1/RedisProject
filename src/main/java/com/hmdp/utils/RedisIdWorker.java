package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    /*
     * 开始时间戳
     * */
    private static final long BEGIN_TIMESTAMP = 1670803200L;
    /*
     * 序列号位数
     * */
    private static final long COUNT_BITS = 32;
    private StringRedisTemplate stringRedisTemplate;//构造函数构造，无需自动注入

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix) {
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;
        //2生成序列号
        //2.1获取当天日期作为key的一部分
        String data = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //2.2自增长
        //根据指定key进行自增
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + data);
        //拼接生成ID
        return timeStamp << COUNT_BITS | count;
    }
}
