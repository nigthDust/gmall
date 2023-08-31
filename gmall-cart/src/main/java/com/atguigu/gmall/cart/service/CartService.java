package com.atguigu.gmall.cart.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.Interceptor.LoginInterceptor;
import com.atguigu.gmall.cart.feign.GmallPmsClient;
import com.atguigu.gmall.cart.feign.GmallSmsClient;
import com.atguigu.gmall.cart.feign.GmallWmsClient;
import com.atguigu.gmall.cart.mapper.CartMapper;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.cart.pojo.UserInfo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import exception.CartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CartService {

    @Autowired
    private CartAsyncService asyncService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private CartMapper cartMapper;

    @Autowired
    private GmallPmsClient gmallPmsClient;

    @Autowired
    private GmallSmsClient gmallSmsClient;

    @Autowired
    private GmallWmsClient gmallWmsClient;

    private static final  String KEY_PREFIX = "cart:info:";


    public void saveCart(Cart cart) {
        //1.判断登录状态
        String userId = getUserId();
        //2.判断当前用户的购物车是否包含该商品
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        //通过内层的key判断是否包含该商品
        String skuId = cart.getSkuId().toString();
        BigDecimal count = cart.getCount(); // 本次新增的数量
        if (hashOps.hasKey(skuId)){
            //包含更新数量
            String json = hashOps.get(skuId).toString();
            cart = JSON.parseObject(json, Cart.class);
            cart.setCount(cart.getCount().add(count));
            //更新到redis
            this.asyncService.updateCart(userId,skuId,cart);
        }else {
            //不包含则新增记录
            cart.setUserId(userId);
            cart.setCheck(true);
            //根据skuId查询sku
            ResponseVo<SkuEntity> skuEntityResponseVo = this.gmallPmsClient.querySkuById(cart.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity != null){
                cart.setTitle(skuEntity.getTitle());
                cart.setDefaultImage(skuEntity.getDefaultImage());
                cart.setPrice(skuEntity.getPrice());
            }
            //查询库存
            ResponseVo<List<WareSkuEntity>> wareSliResponseVo = this.gmallWmsClient.queryWareSkuBySkuId(cart.getSkuId());
            List<WareSkuEntity> wareSkuEntities = wareSliResponseVo.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntities)){
                cart.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
            }
            //销售属性
            ResponseVo<List<SkuAttrValueEntity>> saleAttrResponseVo  = this.gmallPmsClient.querySaleAttrValueBySkuId(cart.getSkuId());
            List<SkuAttrValueEntity> skuAttrValueEntities = saleAttrResponseVo .getData();
            cart.setSaleAttrs(JSON.toJSONString(skuAttrValueEntities));
            //营销信息
            ResponseVo<List<ItemSaleVo>> salesResponseVo  = this.gmallSmsClient.querySalesBySkuId(cart.getSkuId());
            List<ItemSaleVo> itemSaleVos = salesResponseVo.getData();
            cart.setSales(JSON.toJSONString(itemSaleVos));
            //保存到redis和mysql
            this.asyncService.insertCart(cart);
        }
        hashOps.put(skuId,JSON.toJSONString(cart));
    }

    private static String getUserId() {
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        // 获取外层的key，如果userId不为null则取userId，如果userId为null则取userKey
        String userId = userInfo.getUserKey();
        if (userInfo.getUserId() !=null ) {
            userId = userInfo.getUserId().toString();
        }
        return userId;
    }

    public Cart queryCart(Long skuId) {
        String userId = getUserId();
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        if (!hashOps.hasKey(skuId.toString())){
            throw new CartException("您的购物车中没有该商品！");
        }
        String json = hashOps.get(skuId.toString()).toString();
        return JSON.parseObject(json,Cart.class);
    }

    public List<Cart> queryCarts() {
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        String userKey = userInfo.getUserKey();
        //1.根据userKey查询未登录的购物车 Map<skuId,cartJson>
        BoundHashOperations<String, Object, Object> unloginHashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userKey);
        List<Object> unloginCartJsons = unloginHashOps.values();
        List<Cart> unloginCarts = null;
        if (!CollectionUtils.isEmpty(unloginCartJsons)){
            unloginCarts = unloginCartJsons.stream().map(cartJson ->{
                Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
                return cart;
            }).collect(Collectors.toList());
        }

        // 2.判断是否登录，如果未登录直接返回未登录的购物车
        Long userId = userInfo.getUserId();
        if (userId == null){
            return unloginCarts;
        }
        //3.把未登录的购物车合并到已登录的购物车中去
        BoundHashOperations<String, Object, Object> loginHashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        if (!CollectionUtils.isEmpty(unloginCarts)){
            unloginCarts.forEach(cart -> { // 每一条未登录的购物车对象
                String skuId = cart.getSkuId().toString();
                BigDecimal count = cart.getCount(); //未登录购物车中商品数量
                if (loginHashOps.hasKey(skuId)){
                    //累加数量
                    String json = loginHashOps.get(skuId).toString();
                    cart = JSON.parseObject(json, Cart.class); //已登录的购物车对象
                    cart.setCount(cart.getCount().add(count));
                    //更新到数据库
                    this.asyncService.updateCart(userId.toString(),skuId,cart);
                }else {
                    //新增记录
                    cart.setId(null);
                    cart.setUserId(userId.toString());
                    this.asyncService.insertCart(cart);
                }
                loginHashOps.put(skuId,JSON.toJSONString(cart));
            });
            //4.清空未登录的购物车
            this.redisTemplate.delete(KEY_PREFIX + userKey);
            this.asyncService.deleteByUserId(userKey);
        }
        // 5.返回合并后的购物车给用户
        List<Object> loginCartJsons = loginHashOps.values();
        if (!CollectionUtils.isEmpty(loginCartJsons)){
            return loginCartJsons.stream().map(cartJson->{
                Cart cart =JSON.parseObject(cartJson.toString(),Cart.class);
                return cart;
            }).collect(Collectors.toList());
        }
        return null;

    }
}
