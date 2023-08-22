package com.atguigu.gmall.search.listener;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.search.feign.GmallPmsClient;
import com.atguigu.gmall.search.feign.GmallWmsClient;
import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchAttrValue;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SearchListener {
    @Autowired
    private GmallPmsClient pmsClient;
    @Autowired
    private GmallWmsClient wmsClient;
    @Autowired
    private ElasticsearchRestTemplate restTemplate;



    @RabbitListener(bindings = @QueueBinding(
            value = @Queue("SEARH.INSERT.QUEUE"),
            exchange = @Exchange(value = "PMS.SPU.EXCHANGE",ignoreDeclarationExceptions = "true",type = ExchangeTypes.TOPIC),
            key = {"item.insert"}
    ))
    public  void syncData(Long spuId, Channel channel, Message message) throws IOException {
    if (spuId == null){
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }
        // 根据spuId查询spu
        ResponseVo<SpuEntity> spuEntityResponseVo = this.pmsClient.querySpuById(spuId);
        SpuEntity spu = spuEntityResponseVo.getData();
        if (spu==null){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
            return;
        }
        // 2.根据spuId查询sku skus
        ResponseVo<List<SkuEntity>> skuResponseVo = this.pmsClient.querySkusBySpuId(spuId);
        List<SkuEntity> skus = skuResponseVo.getData();
        if (CollectionUtils.isEmpty(skus)){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
            return;
        }

        // 4.根据品牌id查询品牌
        ResponseVo<BrandEntity> brandEntityResponseVo = this.pmsClient.queryBrandById(spu.getBrandId());
        BrandEntity brandEntity = brandEntityResponseVo.getData();

        // 5.根据分类id查询分类
        ResponseVo<CategoryEntity> categoryEntityResponseVo = this.pmsClient.queryCategoryById(spu.getCategoryId());
        CategoryEntity categoryEntity = categoryEntityResponseVo.getData();

        // 6.查询基本类型的检索属性和值
        ResponseVo<List<SpuAttrValueEntity>> baseSearchAttrValueResponseVo = this.pmsClient.querySaleAttrValuesByCidAndSpuId(spu.getCategoryId(), spu.getId());
        List<SpuAttrValueEntity> baseSearchAttrValues = baseSearchAttrValueResponseVo.getData();

        List<Goods> goodsList = skus.stream().map(sku -> {
            Goods goods = new Goods();
            goods.setSkuId(sku.getId());
            goods.setTitle(sku.getTitle());
            goods.setSubtitle(sku.getSubtitle());
            goods.setDefaultImage(sku.getDefaultImage());
            goods.setPrice(sku.getPrice().doubleValue());
            goods.setCreateTime(spu.getCreateTime());

            // 3.根据skuId查询库存
            ResponseVo<List<WareSkuEntity>> wareSkuResponseVo = this.wmsClient.queryWareSkuBySkuId(sku.getId());
            List<WareSkuEntity> wareSkuEntities = wareSkuResponseVo.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntities)){
                goods.setSales(wareSkuEntities.stream().map(WareSkuEntity::getSales).reduce((a, b) -> a + b).get());
                goods.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
            }

            // 设置品牌和分类参数
            if (brandEntity != null) {
                goods.setBrandId(brandEntity.getId());
                goods.setBrandName(brandEntity.getName());
                goods.setLogo(brandEntity.getLogo());
            }
            if (categoryEntity != null) {
                goods.setCategoryId(categoryEntity.getId());
                goods.setCategoryName(categoryEntity.getName());
            }

            // 7.查询销售类型的检索属性和值
            ResponseVo<List<SkuAttrValueEntity>> saleSearchAttrValueResponseVo = this.pmsClient.querySaleAttrValuesByCidAndSkuId(sku.getCategoryId(), sku.getId());
            List<SkuAttrValueEntity> saleSearchAttrValues = saleSearchAttrValueResponseVo.getData();
            // 初始化一个SearchAttrValue集合
            List<SearchAttrValue> searchAttrs = new ArrayList<>();
            // 把saleSearchAttrValues 和 baseSearchAttrValues 转化成 List<SearchAttrValue>类型集合
            if (!CollectionUtils.isEmpty(saleSearchAttrValues)){
                searchAttrs.addAll(saleSearchAttrValues.stream().map(skuAttrValueEntity -> {
                    SearchAttrValue searchAttrValue = new SearchAttrValue();
                    BeanUtils.copyProperties(skuAttrValueEntity, searchAttrValue);
                    return searchAttrValue;
                }).collect(Collectors.toList()));
            }
            if (!CollectionUtils.isEmpty(baseSearchAttrValues)){
                searchAttrs.addAll(baseSearchAttrValues.stream().map(spuAttrValueEntity -> {
                    SearchAttrValue searchAttrValue = new SearchAttrValue();
                    BeanUtils.copyProperties(spuAttrValueEntity, searchAttrValue);
                    return searchAttrValue;
                }).collect(Collectors.toList()));
            }
            goods.setSearchAttrs(searchAttrs);

            return goods;
        }).collect(Collectors.toList());
        this.restTemplate.save(goodsList);

    }
}
