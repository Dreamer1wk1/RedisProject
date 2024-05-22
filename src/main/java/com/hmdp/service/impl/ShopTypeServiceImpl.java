package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryByType() {
        //先在redis中查询
        String shopTypeListJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);
        //如果不为空直接返回
        if (StrUtil.isNotBlank(shopTypeListJson)) {
            return Result.ok(JSONUtil.toList(shopTypeListJson, ShopType.class));
        }
        //如果为空从数据库中查询
        //query是该类的父类ServiceImpl中实现的接口IService中的方法
        List<ShopType> typeList = this.query().orderByAsc("sort").list();
        //数据库中也查不返回错误信息
        if (typeList == null || typeList.size() == 0) {
            return Result.fail("商品类型查询失败");
        }
        //查到就将查询结果存入redis并返回
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(typeList));
        return Result.ok(typeList);
    }
}
