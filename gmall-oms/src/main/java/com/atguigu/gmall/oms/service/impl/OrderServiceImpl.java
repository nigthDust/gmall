package com.atguigu.gmall.oms.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.oms.entity.OrderItemEntity;
import com.atguigu.gmall.oms.feign.GmallPmsClient;
import com.atguigu.gmall.oms.feign.GmallSmsClient;
import com.atguigu.gmall.oms.mapper.OrderItemMapper;
import com.atguigu.gmall.oms.vo.OrderItemVo;
import com.atguigu.gmall.oms.vo.OrderSubmitVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.ums.entity.UserAddressEntity;
import exception.OrderException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.oms.mapper.OrderMapper;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.oms.service.OrderService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;


@Service("orderService")
public class OrderServiceImpl extends ServiceImpl<OrderMapper, OrderEntity> implements OrderService {

    @Autowired
    private OrderItemMapper itemMapper;

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallSmsClient smsClient;



    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<OrderEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<OrderEntity>()
        );

        return new PageResultVo(page);
    }



    @Transactional
    @Override
    public void saveOrder(OrderSubmitVo submitVo, Long userId) {
        List<OrderItemVo> items = submitVo.getItems();
        if (CollectionUtils.isEmpty(items)){
            throw  new OrderException("请选择要购买的商品！");
        }
        //1.保存订单表
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setUserId(userId);
        orderEntity.setOrderSn(submitVo.getOrderToken());
        orderEntity.setCreateTime(new Date());
        orderEntity.setTotalAmount(submitVo.getTotalPrice());
        orderEntity.setPayAmount(submitVo.getTotalPrice());
        orderEntity.setIntegrationAmount(new BigDecimal(submitVo.getBounds()/100));
        orderEntity.setPayType(submitVo.getPayType());
        orderEntity.setSourceType(0);
        orderEntity.setStatus(0);
        orderEntity.setDeliveryCompany(submitVo.getDeliveryCompany());
        // TODO:调用sms接口查询每个商品的积分优惠信息，计算出该订单完成之后应得的积分
        UserAddressEntity address = submitVo.getAddress();
        if (address != null){
            orderEntity.setReceiverName(address.getName());
            orderEntity.setReceiverPhone(address.getPhone());
            orderEntity.setReceiverPostCode(address.getPostCode());
            orderEntity.setReceiverProvince(address.getProvince());
            orderEntity.setReceiverCity(address.getCity());
            orderEntity.setReceiverRegion(address.getRegion());
            orderEntity.setReceiverAddress(address.getAddress());
        }
        orderEntity.setConfirmStatus(0);
        orderEntity.setDeleteStatus(0);
        orderEntity.setUseIntegration(submitVo.getBounds());
        this.save(orderEntity);
        Long orderId = orderEntity.getId();


        // 2.保存订单详情表
        items.forEach(item ->{
            OrderItemEntity itemEntity = new OrderItemEntity();
            itemEntity.setOrderId(orderId);
            itemEntity.setOrderSn(submitVo.getOrderToken());
            //根据送货清单中的skuid查询sku
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(item.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity != null){
                itemEntity.setSkuId(skuEntity.getId());
                itemEntity.setSkuName(skuEntity.getName());
                itemEntity.setSkuPic(skuEntity.getDefaultImage());
                itemEntity.setSkuPrice(skuEntity.getPrice());
                itemEntity.setCategoryId(skuEntity.getCategoryId());
                itemEntity.setRealAmount(skuEntity.getPrice());
            }
            itemEntity.setSkuQuantity(item.getCount().intValue());
            //查询销售属性
            ResponseVo<List<SkuAttrValueEntity>> saleAttrsResponseVo = this.pmsClient.querySaleAttrValueBySkuId(item.getSkuId());
            List<SkuAttrValueEntity> skuAttrValueEntities = saleAttrsResponseVo.getData();
            itemEntity.setSkuAttrsVals(JSON.toJSONString(skuAttrValueEntities));
            //查询品牌
            ResponseVo<BrandEntity> brandEntityResponseVo = this.pmsClient.queryBrandById(skuEntity.getBrandId());
            BrandEntity brandEntity = brandEntityResponseVo.getData();
            itemEntity.setSpuBrand(JSON.toJSONString(brandEntity));
            //查询spu
            ResponseVo<SpuEntity> spuEntityResponseVo = this.pmsClient.querySpuById(skuEntity.getSpuId());
            SpuEntity spuEntity = spuEntityResponseVo.getData();
            if (spuEntity != null){
                itemEntity.setSpuId(spuEntity.getId());
                itemEntity.setSpuName(spuEntity.getName());
            }
            // 描述信息
            ResponseVo<SpuDescEntity> spuDescEntityResponseVo = this.pmsClient.querySpuDescById(skuEntity.getSpuId());
            SpuDescEntity descEntity = spuDescEntityResponseVo.getData();
            if (descEntity != null){
                itemEntity.setSpuPic(descEntity.getDecript());
            }
            this.itemMapper.insert(itemEntity);
        });
       //   try {
         //   TimeUnit.SECONDS.sleep(3);
       // }catch (InterruptedException e){
         //   throw new RuntimeException(e);
       // }


        //int i = 1/0;
    }

}