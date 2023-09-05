package com.atguigu.gmall.cart.listener;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.feign.GmallPmsClient;
import com.atguigu.gmall.cart.mapper.CartMapper;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.rabbitmq.client.Channel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class CartListener {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CartMapper cartMapper;

    private static final String PRICE_PREFIX ="cart:price:";
    private static final  String KEY_PREFIX = "cart:info:";

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue("CART.PRICE.QUEUE"),
            exchange = @Exchange(value = "PMS.SPU.EXCHANGE",type = ExchangeTypes.TOPIC, ignoreDeclarationExceptions = "true"),
            key = {"item.update"}
    ))
    public void syncPrice(Long spuId, Channel channel, Message message)throws IOException{
        if (spuId == null){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
            return;
        }

        //根据spuId查询sku
        ResponseVo<List<SkuEntity>> skuResponseVo = this.pmsClient.querySkusBySpuId(spuId);
        List<SkuEntity> skuEntities = skuResponseVo.getData();
        if (CollectionUtils.isEmpty(skuEntities)){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
            return;
        }

        //遍历sku集合，同步价格
        skuEntities.forEach(sku ->{
            this.redisTemplate.opsForValue().setIfPresent(PRICE_PREFIX+sku.getId(),sku.getPrice().toString());
        });
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }


    @RabbitListener(bindings = @QueueBinding(
            value = @Queue("CART.DELETE.QUEUE"),
            exchange = @Exchange(value = "ORDER.EXCHANGE",type = ExchangeTypes.TOPIC, ignoreDeclarationExceptions = "true"),
            key = {"cart.delete"}
    ))
    public void deleteCart(Map<Object, Object> msg, Channel channel, Message message)throws IOException{
        if (CollectionUtils.isEmpty(msg)){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
            return;
        }

        //获取消息中的内容
        String userId = msg.get("userId").toString();
        String json = msg.get("skuIds").toString();
        if (userId == null || StringUtils.isBlank(json)){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        }
        //把json反序列化skuIds
        List<String> skuIds = JSON.parseArray(json, String.class);

        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        hashOps.delete(skuIds.toArray());
        this.cartMapper.delete(new UpdateWrapper<Cart>().eq("user_id",userId).in("sku_id",skuIds));



        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }
}
