package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author Dreamer
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //格式错误返回错误信息
            return Result.fail("手机号格式错误");
        }
        //生成验证码
        String code= RandomUtil.randomNumbers(6);
        //将验证码保存在redis中，设置有效期为2分钟
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY +phone,code,LOGIN_CODE_TTL , TimeUnit.MINUTES);
        //发送验证码
        log.debug("验证码发送成功，为："+code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        String phone=loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            //格式错误返回错误信息
            return Result.fail("手机号格式错误");
        }
        //校验验证码
        String cacheCode=stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        String code=loginForm.getCode();
        if(cacheCode==null||!cacheCode.equals(code)){
            //验证码不一致
            return Result.fail("验证码错误");
        }
        //一致则进行查询
        User user=query().eq("phone",phone).one();
        //判断结果是否为空
        if(user==null){
            //为空创建新用户
            user=createUserWithPhone(phone);
        }
        //将用户信息保存在redis中
        ///随机生成token作为登录令牌
        String token= UUID.randomUUID().toString();
        ///将user对象转为hashmap存储
        UserDTO userDTO=BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap=BeanUtil.beanToMap(userDTO,new HashMap<>(),CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((filedName,fieldValue)-> fieldValue.toString()));
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,userMap);
        //设置有效期为2小时
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL,TimeUnit.HOURS);
        ///返回token
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        //创建用户
        User user=new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        //将新用户添加到数据库中
        save(user);
        return null;
    }
}
