package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

public class RefreshTokenInterceptor implements HandlerInterceptor {
    //拦截器对象不是由spring创建的，因此不能自动注入，需要通过构造函数创建
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //放行一切操作，检验工作由登录拦截器完成
        //获取请求头中的token
        String token=request.getHeader("authorization");
        if(StrUtil.isBlank(token)){
            return true;
        }
        //通过token获取redis中的用户
        String key=RedisConstants.LOGIN_USER_KEY+token;
        Map<Object,Object> userMap=stringRedisTemplate.opsForHash().entries(key);
        //判断用户是否存在
        if(userMap.isEmpty()){
            return true;
        }
        //将查询到的hashmap转化为UserDTO对象
        UserDTO userDTO= BeanUtil.fillBeanWithMap(userMap,new UserDTO(),false);
        //将用户信息存储在ThreadLocal中，通过工具类简化代码
        UserHolder.saveUser(userDTO);
        //刷新redis中用户数据有效期(2hours)
        stringRedisTemplate.expire(key,LOGIN_USER_TTL,TimeUnit.HOURS);
        //放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除ThreadLocal
        UserHolder.removeUser();
    }
}
