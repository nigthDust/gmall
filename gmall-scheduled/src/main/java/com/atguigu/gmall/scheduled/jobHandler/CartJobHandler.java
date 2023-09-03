package com.atguigu.gmall.scheduled.jobHandler;

import com.atguigu.gmall.scheduled.mapper.CartMapper;
import com.atguigu.gmall.scheduled.pojo.Cart;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Component
public class CartJobHandler {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private CartMapper cartMapper;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static  final String EXCEPTION_KEY="car t:exception";
    private static final  String KEY_PREFIX = "cart:info:";

    @XxlJob("cartSyncData")
    public ReturnT<String> syncData(String param){
        //读取失败的购物车信息set<userId>
        BoundSetOperations<String, String> setOps = this.redisTemplate.boundSetOps(EXCEPTION_KEY);
        String userId = setOps.pop();
        //遍历失败信息
        while (userId != null){
            // 先删除当前用户的mysql中的购物车信息
            this.cartMapper.delete(new UpdateWrapper<Cart>().eq("user_id",userId));

            //在读取当前用户的redis中的购物车信息
            BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
            List<Object> cartJsons = hashOps.values();
            // 如果redis中的购物车为空，说明同步已经完成
            if (CollectionUtils.isEmpty(cartJsons)){
                userId = setOps.pop();
                continue;
            }

            //最后新增到mysql数据库
            cartJsons.forEach(cartJson ->{

                try {
                    Cart cart = MAPPER.readValue(cartJson.toString(), Cart.class);
                    this.cartMapper.insert(cart);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }

            });
            // 获取下一个用户
            userId = setOps.pop();
        }
        return ReturnT.SUCCESS;
    }

}
