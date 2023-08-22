package com.atguigu.gmall.index.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.feign.GmallPmsClient;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class IndexService {


    @Autowired
    private GmallPmsClient pmsClient;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private  static final String KEY_PREFIX="index:cates";

    public List<CategoryEntity> queryLvl1Cateogries() {
        ResponseVo<List<CategoryEntity>> responseVo = this.pmsClient.queryCategory(0L);
        return responseVo.getData();
    }


    public List<CategoryEntity> queryLvl23CategoriesByPid(Long pid) {
        //先查询缓存，如果缓存命中则直接返回
        String json = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
        if (StringUtils.isBlank(json)){
            return JSON.parseArray(json,CategoryEntity.class);
        }


        ResponseVo<List<CategoryEntity>> responseVo = this.pmsClient.queryLevel23CategoriesByPid(pid);
        List<CategoryEntity> categoryEntities = responseVo.getData();
        if (CollectionUtils.isEmpty(categoryEntities)){
            // 为了防止缓存穿透，数据即使为null也缓存，缓存时间5min
            this.redisTemplate.opsForValue().set(KEY_PREFIX+pid,JSON.toJSONString(categoryEntities),5,TimeUnit.MINUTES);
        }else {
            //为了防止缓存雪崩，给缓存时间添加随机值
            this.redisTemplate.opsForValue().set(KEY_PREFIX+pid,JSON.toJSONString(categoryEntities),90+new Random().nextInt(10),TimeUnit.DAYS);
        }

        return categoryEntities;
    }


    public synchronized void testLock() {
        //加锁
        String uuid = UUID.randomUUID().toString();
        Boolean lock = this.redisTemplate.opsForValue().setIfAbsent("lock", uuid,3,TimeUnit.SECONDS);
        if (!lock){
            // 为了防止栈内存溢出，或者降低资源争抢，可以先睡一会在去重试
            try {
                Thread.sleep(40);
                this.testLock();

            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }else {
            String number = this.redisTemplate.opsForValue().get("number");
            if (StringUtils.isBlank(number)){
                this.redisTemplate.opsForValue().set("number","1");
                return;
            }
            int num = Integer.parseInt(number);
            this.redisTemplate.opsForValue().set("number",String.valueOf(++num));

            //解锁:先判断是不是自己的锁，如果是才能释放锁
            String script = "if redis.call('get',KEYS[1]) == ARGV[1]" +
                    "then" +
                    " return redis.call('del',KEYS[1] ) " +
                    "else " +
                    " return 0 " +
                    "end";
            this.redisTemplate.execute(new DefaultRedisScript<>(script,Boolean.class), Arrays.asList("lock"),uuid);





//            if (StringUtils.equals(uuid,this.redisTemplate.opsForValue().get("lock"))){
//                this.redisTemplate.delete("lock");
//            }

        }
    }
}