package com.atguigu.gmall.index.aspect;


import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.index.annotation.GmallCache;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class GmallCacheAspect {
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RBloomFilter bloomFilter;




    @Pointcut("@annotation(com.atguigu.gmall.index.annotation.GmallCache)")
    public void gmallCachePoint(){}

    @Before("gmallCachePoint()")
    public void before(){
        System.out.println("=====================before");
    }
    @Around("gmallCachePoint()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable{
        // 方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        // 获取方法上的GmallCache注解对象
        GmallCache gmallCache = method.getAnnotation(GmallCache.class);
        //缓存前缀
        String prefix = gmallCache.prefix();
        //获取目标的形参
        Object[] args = joinPoint.getArgs();
        String parems = StringUtils.join(args, ",");
        //缓存的key = prefix + args
        String key = prefix + parems;
        // 1. 先查询缓存，如果缓存命中则直接放回
        String json = this.redisTemplate.opsForValue().get(key);
        if (StringUtils.isNotBlank(json)){
            return JSON.parseObject(json,method.getReturnType());
        }

        //为了防止缓存穿透，添加布隆过滤器
        if (!this.bloomFilter.contains(key)){
            return null;
        }

        //2.为了防止缓存击穿,添加分布式锁
        RLock fairLock = this.redissonClient.getFairLock(gmallCache.lock() + parems);
        fairLock.lock();

        try {
            //3.再次确认缓存中有没有，如果有则直接返回（获取锁的过程中可能有其他请求已经把数据放入缓存）
            String json2 = this.redisTemplate.opsForValue().get(key);
            if (StringUtils.isNotBlank(json)){
                return JSON.parseObject(json,method.getReturnType());
            }
            //4.执行目标方法，查询数据库
            Object result = joinPoint.proceed(args);

            //5.放入缓存释放分布式锁
            if (result != null){
                // 为了防止缓存雪崩，给缓存时间添加了随机值
                int timeout = gmallCache.timeout() + new Random().nextInt(gmallCache.random());
                this.redisTemplate.opsForValue().set(key,JSON.toJSONString(result),timeout, TimeUnit.MINUTES);
            }

            return result;
        } finally {
            fairLock.unlock();
        }
    }
}
