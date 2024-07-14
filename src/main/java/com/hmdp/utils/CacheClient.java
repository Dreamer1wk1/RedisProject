package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //存入redis并设置过期时间
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    //存入redis并设置逻辑过期时间
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    //缓存空值解决缓存穿透
    //Function<ID, R> dbFallBack代表一个从ID类型的输入到R类型的输出的函数,调用时该参数为lambda表达式
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        //从redis查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isNotBlank(json)) {
            //存在则直接返回
            return JSONUtil.toBean(json, type);
        }
        //如果是空串，返回错误信息
        if (json == "") {
            return null;
        }
        //否则根据id在数据库中查询
        R r = dbFallBack.apply(id);
        //数据库中也不存在返回错误
        if (r == null) {
            //写入空值
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //存在则写入redis进行缓存
        this.set(key, r, time, unit);
        return r;
    }
    //逻辑过期解决缓存击穿
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        //从redis查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isBlank(json)) {
            //不存在则直接返回null
            return null;
        }
        //redis中存在则将json反序列化
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();//不确定data类型，因此获取的是JSONObject对象
        R r = JSONUtil.toBean(data, type);
        //判断是否逻辑过期
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期则直接返回店铺信息
            return r;
        }
        //过期进行缓存重建
        ////获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        ////获取成功则开启新线程重建缓存
        if (isLock) {
            //重建缓存之前再次进行检查，避免重复别的线程可能完成的重建操作
            json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(json)) {
                //命中缓存则直接将json转为shop对象返回
                r = JSONUtil.toBean(json, type);
            }
            //未命中则进行缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //查询数据库
                    R rr = dbFallBack.apply(id);
                    //保存到redis
                    setWithLogicalExpire(key, rr, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放互斥锁
                    unLock(lockKey);
                }
            });
        }
        //直接返回过期的商品信息
        return r;
    }
    //互斥锁解决缓存击穿
    public <R, ID> R queryWithMutex(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        //从redis查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isNotBlank(json)) {
            //存在则直接返回
            R r = JSONUtil.toBean(json, type);
            return r;
        }
        //如果是空串，返回错误信息
        if (json == "") {
            return null;
        }
        ////缓存重建
        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        R r= null;
        try {
            //循环判断是否获取成功
            boolean isLock = tryLock(lockKey);
            if (!isLock) {
                //失败则短暂休眠后重试
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallBack, time, unit);
            }
            //doubleCheck缓存是否存在数据(可能其他线程获取锁后完成了缓存操作)
            json = stringRedisTemplate.opsForValue().get(key);
            //判断是否存在
            if (StrUtil.isNotBlank(json)) {
                //存在则直接返回
                r = JSONUtil.toBean(json, type);//json->class
                return r;
            }
            //根据id在数据库中查询
            r = dbFallBack.apply(id);
//            //模拟重建延时
//            Thread.sleep(200);
            //数据库中也不存在返回错误
            if (r == null) {
                //写入空值
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //存在则写入redis进行缓存，class->json，设置有效时长为30min
            set(key,r,time,unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放互斥锁
            unLock(lockKey);
        }
        return r;
    }

    //获取锁，返回是否成功
    private boolean tryLock(String key) {
        //设置ttl避免释放出现异常
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        //Boolean是boolean的包装类，可能为null。所以使用isTrue方法，只有flag为True才返回True
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

}
