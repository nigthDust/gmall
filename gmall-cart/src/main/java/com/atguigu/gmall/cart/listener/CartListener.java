package com.atguigu.gmall.cart.listener;

import com.atguigu.gmall.cart.feign.GmallPmsClient;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.List;

@Component
public class CartListener {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String PRICE_PREFIX ="cart:price:";


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
}
