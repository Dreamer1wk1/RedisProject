package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {
    private String name; // 业务前缀
    private StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX= UUID.randomUUID().toString()+"-";

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        String threadId = ID_PREFIX+Thread.currentThread().getId();
        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId + " ", timeoutSec, TimeUnit.SECONDS);
        //避免空指针异常
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        //获取标识
        String id=ID_PREFIX+Thread.currentThread().getId();
        String redisId=stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        if(id.equals(redisId)){
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }
}
