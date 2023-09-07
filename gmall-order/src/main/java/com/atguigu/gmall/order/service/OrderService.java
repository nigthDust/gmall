package com.atguigu.gmall.order.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.order.Interceptor.LoginInterceptor;
import com.atguigu.gmall.order.feign.*;
import com.atguigu.gmall.order.pojo.OrderConfirmVo;
import com.atguigu.gmall.oms.vo.OrderItemVo;
import com.atguigu.gmall.oms.vo.OrderSubmitVo;
import com.atguigu.gmall.order.pojo.UserInfo;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.ums.entity.UserAddressEntity;
import com.atguigu.gmall.ums.entity.UserEntity;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import exception.CartException;
import exception.OrderException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class OrderService {
    @Autowired
    private GmallCartClient cartClient;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private GmallUmsClient umsClient;

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private GmallOmsClient omsClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;
    private static final  String KEY_PREFIX = "cart:info:";
    public OrderConfirmVo confirm() {
        OrderConfirmVo confirmVo = new OrderConfirmVo();
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();

        //根据userId查询收货地址
        ResponseVo<List<UserAddressEntity>> addressResponseVo = this.umsClient.queryAddressesByUserId(userId);
        List<UserAddressEntity> userAddressEntities = addressResponseVo.getData();
        confirmVo.setAddresses(userAddressEntities);

        //查询已选中的购物车记录
        ResponseVo<List<Cart>> responseVo = this.cartClient.queryCheckedCartsByUserId(userId);
        List<Cart> carts = responseVo.getData();
        if (CollectionUtils.isEmpty(carts)){
            throw new CartException("请选择要购买的商品！");
        }
        // 把购物车集合转化成送货清单集合
        confirmVo.setItems(carts.stream().map(cart -> {
            OrderItemVo orderItemVo = new OrderItemVo();
            orderItemVo.setSkuId(cart.getSkuId());
            orderItemVo.setCount(cart.getCount());
            //根据skuId查询sku
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(cart.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity != null){
                orderItemVo.setTitle(skuEntity.getTitle());
                orderItemVo.setDefaultImage(skuEntity.getDefaultImage());
                orderItemVo.setPrice(skuEntity.getPrice());
                orderItemVo.setWeight(skuEntity.getWeight());
            }
            //设置销售属性
            ResponseVo<List<SkuAttrValueEntity>> saleAttrsResponseVo = this.pmsClient.querySaleAttrValueBySkuId(cart.getSkuId());
            List<SkuAttrValueEntity> skuAttrValueEntities = saleAttrsResponseVo.getData();
            orderItemVo.setSaleAttrs(skuAttrValueEntities);

            //设置是否有货
            ResponseVo<List<WareSkuEntity>> wareSkuResponseVo = this.wmsClient.queryWareSkuBySkuId(cart.getSkuId());
            List<WareSkuEntity> wareSkuEntities = wareSkuResponseVo.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntities)){
                orderItemVo.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0 ));
            }
        //设置营销信息
            ResponseVo<List<ItemSaleVo>> salesResponseVo = this.smsClient.querySalesBySkuId(cart.getSkuId());
            List<ItemSaleVo> itemSaleVos = salesResponseVo.getData();
            orderItemVo.setSales(itemSaleVos);

            return orderItemVo;
        }).collect(Collectors.toList()));

        //根据userId查询用户
        ResponseVo<UserEntity> userEntityResponseVo = this.umsClient.queryUserById(userId);
        UserEntity userEntity = userEntityResponseVo.getData();
        if (userEntity != null){
            confirmVo.setBounds(userEntity.getIntegration());
        }

        //设置orderToken：全局唯一标识,雪花算法
        String orderToken = IdWorker.getIdStr();
        this.redisTemplate.opsForValue().set(KEY_PREFIX + orderToken,orderToken,24, TimeUnit.HOURS);
        confirmVo.setOrderToken(orderToken);
        return confirmVo;
    }

    public void submit(OrderSubmitVo submitVo) {
        //1.防止重复提交（页面的orderToken 到 redis 查询, 如果存在着说明没有重复提交放行（立马删除），否则抛出异常）
        String orderToken = submitVo.getOrderToken(); //页面上的orderToken
        if (StringUtils.isBlank(orderToken)){
            throw new OrderException("非法请求！");
        }
        //解锁:先判断是不是自己的锁，如果是才能释放锁
        String script = "if redis.call('get',KEYS[1]) == ARGV[1]" +
                "then" +
                " return redis.call('del',KEYS[1] ) " +
                "else " +
                " return 0 " +
                "end";
        Boolean flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(KEY_PREFIX + orderToken), orderToken);
        if (!flag){
            throw new OrderException("请不要重复提交！");
        }

        //2.验证总价：页面上的总价格和数据库获取单价进行实时计算总价格比较，如果不一致则抛出异常
        List<OrderItemVo> items = submitVo.getItems();  //送货清单
        if (CollectionUtils.isEmpty(items)){
            throw new OrderException("请选择要购买的商品！");
        }
        BigDecimal totalPrice = submitVo.getTotalPrice();  //页面总价格
        // 计算实时总价格
        BigDecimal currentTotalPrice  = items.stream().map(item -> {
            //查询实时单价
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(item.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity == null) {
                return new BigDecimal(0);
            }
            return skuEntity.getPrice().multiply(item.getCount()); //实时小计
        }).reduce((a, b) -> a.add(b)).get();
        if (currentTotalPrice.compareTo(totalPrice) != 0){
            throw new OrderException("页面已过期，请刷新页面后重试！");
        }
        // 3.验库存并锁库存（使用分布式锁保证原子性）
        // 把送货清单集合（items） 转化成skuLockVo集合
        List<SkuLockVo> skuLockVos = items.stream().map(item -> {
            SkuLockVo skuLockVo = new SkuLockVo();
            skuLockVo.setSkuId(item.getSkuId());
            skuLockVo.setCount(item.getCount().intValue());
            return skuLockVo;
        }).collect(Collectors.toList());
        ResponseVo<List<SkuLockVo>> responseVo = this.wmsClient.checkLock(skuLockVos, orderToken);
        List<SkuLockVo> lockVos = responseVo.getData();
        if (!CollectionUtils.isEmpty(lockVos)){
            throw new OrderException(JSON.toJSONString(lockVos));
        }

      //  int i = 1/0;

        //4.创建订单
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();
        try {
            this.omsClient.saveOrder(submitVo, userId);
            // 创建订单成功，但是我对用户未来是否支付不清楚，最坏打算：定时关单
            this.rabbitTemplate.convertAndSend("ORDER.EXCHANGE", "order.ttl", orderToken);
        } catch (Exception e) {
            // 发送消息给wms立马解锁库存，并发送消息给oms标记为无效订单
            this.rabbitTemplate.convertAndSend("ORDER.EXCHANGE", "order.fail", orderToken);
            throw new RuntimeException(e);
        }

        //5.异步删除购物车中对于的记录：1.提高性能 2.不应该影响订单的创建
        Map<Object, Object> msg = new HashMap<>();
        msg.put("userId",userId);
        List<Long> skuIds = items.stream().map(OrderItemVo::getSkuId).collect(Collectors.toList());
        msg.put("skuIds",JSON.toJSONString(skuIds));
        this.rabbitTemplate.convertAndSend("ORDER.EXCHANGE", "cart.delete", msg);
    }
}
