package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    //线程池用户缓存重建
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    //根据id查询商户信息
    @Override
    public Result queryById(Long id) {
        //解决缓存穿透
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, id2 -> getById(id), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //逻辑过期解决缓存击穿
//        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, id2 -> getById(id), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        //互斥锁解决缓存击穿
        Shop shop = cacheClient.queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, id2 -> getById(id), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    //查询接口，互斥锁解决缓存击穿，同时保留解决缓存穿透的逻辑
//    public Shop queryWithMutex(Long id) {
//        String key = CACHE_SHOP_KEY + id;
//        //从redis查询缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //判断是否存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            //存在则直接返回
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);//json->class
//            return shop;
//        }
//        //如果是空串，返回错误信息
//        if (shopJson == "") {
//            return null;
//        }
//        ////缓存重建
//        //获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        Shop shop = null;
//        try {
//            //循环判断是否获取成功
//            boolean isLock = tryLock(lockKey);
//            if (!isLock) {
//                //失败则短暂休眠后重试
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//            //doubleCheck缓存是否存在数据(可能其他线程获取锁后完成了缓存操作)
//            shopJson = stringRedisTemplate.opsForValue().get(key);
//            //判断是否存在
//            if (StrUtil.isNotBlank(shopJson)) {
//                //存在则直接返回
//                shop = JSONUtil.toBean(shopJson, Shop.class);//json->class
//                return shop;
//            }
//            //根据id在数据库中查询
//            shop = getById(id);
////            //模拟重建延时
////            Thread.sleep(200);
//            //数据库中也不存在返回错误
//            if (shop == null) {
//                //写入空值
//                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            //存在则写入redis进行缓存，lass->json，设置有效时长为30min
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            //释放互斥锁
//            unLock(lockKey);
//        }
//        return shop;
//    }

    //查询接口，逻辑过期解决缓存击穿,无需考虑缓存穿透问题
//    public Shop queryWithLogicalExpire(Long id) {
//        String key = CACHE_SHOP_KEY + id;
//        //从redis查询缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //判断是否存在
//        if (StrUtil.isBlank(shopJson)) {
//            //不存在则直接返回null
//            return null;
//        }
//        //redis中存在则将json反序列化
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        JSONObject data = (JSONObject) redisData.getData();//不确定data类型，因此获取的是JSONObject对象
//        Shop shop = JSONUtil.toBean(data, Shop.class);
//        //判断是否逻辑过期
//        LocalDateTime expireTime = redisData.getExpireTime();
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            //未过期则直接返回店铺信息
//            return shop;
//        }
//        //过期进行缓存重建
//        ////获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockKey);
//        ////获取成功则开启新线程重建缓存
//        if (isLock) {
//            //重建缓存之前再次进行检查，避免重复别的线程可能完成的重建操作
//            shopJson = stringRedisTemplate.opsForValue().get(key);
//            if (StrUtil.isNotBlank(shopJson)) {
//                //命中缓存则直接将json转为shop对象返回
//                shop = JSONUtil.toBean(shopJson, Shop.class);
//            }
//            //未命中则进行缓存重建
//            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                try {
//                    saveShopToRedis(id, 20L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    //释放互斥锁
//                    unLock(lockKey);
//                }
//            });
//        }
//        //直接返回过期的商品信息
//        return shop;
//    }

    //查询接口，解决缓存穿透
//    public Shop queryWithPassThrough(Long id) {
//        String key = CACHE_SHOP_KEY + id;
//        //从redis查询缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //判断是否存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            //存在则直接返回
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);//json->class
//            return shop;
//        }
//        //如果是空串，返回错误信息
//        if (shopJson == "") {
//            return null;
//        }
//        //否则根据id在数据库中查询
//        Shop shop = getById(id);
//        //数据库中也不存在返回错误
//        if (shop == null) {
//            //写入空值
//            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//        //存在则写入redis进行缓存
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        //class->json，设置有效时长为30min
//        return shop;
//    }

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

    //逻辑过期处理缓存击穿，保存shop实例，设置逻辑过期时间
//    public void saveShopToRedis(Long id, Long expireSeconds) throws InterruptedException {
//        //查询店铺数据
//        Shop shop = getById(id);
////        //模拟重建延时
////        Thread.sleep(200);
//        //封装逻辑过期时间
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        //写入redis
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
//    }

    //更新商户信息
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("id不能为空");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
