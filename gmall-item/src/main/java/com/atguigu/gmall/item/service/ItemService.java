package com.atguigu.gmall.item.service;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.item.feign.GmallPmsClient;
import com.atguigu.gmall.item.feign.GmallSmsClient;
import com.atguigu.gmall.item.feign.GmallWmsClient;
import com.atguigu.gmall.item.vo.ItemVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.vo.ItemGroupVo;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class ItemService {
    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private GmallWmsClient wmsClient;

   @Autowired
   private ExecutorService executorService;

   @Autowired
   private TemplateEngine templateEngine;


    public ItemVo loadData(Long skuId) {
        ItemVo itemVo = new ItemVo();

        //1.根据skuId查询sku
        CompletableFuture<SkuEntity> skuFuture = CompletableFuture.supplyAsync(() -> {
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(skuId);
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity == null) {
                throw new RuntimeException("你访问的商品不存在！");
            }
            itemVo.setSkuId(skuId);
            itemVo.setTitle(skuEntity.getTitle());
            itemVo.setSubtitile(skuEntity.getSubtitle());
            itemVo.setDefaultImage(skuEntity.getDefaultImage());
            itemVo.setPrice(skuEntity.getPrice());
            itemVo.setWeight(skuEntity.getWeight());
            return skuEntity;
        }, executorService);

        

        // 2.根据三级分类id查询一二三级分类
        CompletableFuture<Void> categoryFuture  = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<List<CategoryEntity>> categoryResponseVo = this.pmsClient.queryLvl123CategoriesByCid3(skuEntity.getCategoryId());
            List<CategoryEntity> categoryEntities = categoryResponseVo.getData();
            itemVo.setCategories(categoryEntities);
        }, executorService);


        // 3.根据品牌id查询品牌
        CompletableFuture<Void> brandFuture  = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<BrandEntity> brandEntityResponseVo = this.pmsClient.queryBrandById(skuEntity.getBrandId());
            BrandEntity brandEntity = brandEntityResponseVo.getData();
            if (brandEntity != null) {
                itemVo.setBrandId(brandEntity.getId());
                itemVo.setBrandName(brandEntity.getName());
            }
        }, executorService);


        //4.根据spuId查询spu
        CompletableFuture<Void> spuFuture  = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<SpuEntity> spuEntityResponseVo = this.pmsClient.querySpuById(skuEntity.getSpuId());
            SpuEntity spuEntity = spuEntityResponseVo.getData();
            if (spuEntity != null) {
                itemVo.setSpuId(spuEntity.getId());
                itemVo.setSpuName(spuEntity.getName());
            }
        }, executorService);


        //5.根据skuId查询sku图片列表
        CompletableFuture<Void> imagesFuture  = CompletableFuture.runAsync(() -> {
            ResponseVo<List<SkuImagesEntity>> imagesResponseVo = this.pmsClient.queryImagesBySkuId(skuId);
            List<SkuImagesEntity> skuImagesEntities = imagesResponseVo.getData();
            itemVo.setImages(skuImagesEntities);
        }, executorService);


        // 6.根据skuId查询营销信息
        CompletableFuture<Void> salesFuture  = CompletableFuture.runAsync(() -> {
            ResponseVo<List<ItemSaleVo>> salesResponseVo = this.smsClient.querySalesBySkuId(skuId);
            List<ItemSaleVo> itemSaleVos = salesResponseVo.getData();
            itemVo.setSales(itemSaleVos);
        }, executorService);


        //7.根据skuId查询库存信息
        CompletableFuture<Void> wareSkuFuture  = CompletableFuture.runAsync(() -> {
            ResponseVo<List<WareSkuEntity>> wareSkuResponseVo = this.wmsClient.queryWareSkuBySkuId(skuId);
            List<WareSkuEntity> wareSkuEntities = wareSkuResponseVo.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                itemVo.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
            }
        }, executorService);


        // 8.根据spuId查询spu下所有的sku的销售属性组合
        CompletableFuture<Void> saleAttrsFuture  = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<List<SaleAttrValueVo>> saleAttrsResponseVo = this.pmsClient.querySaleAttrValueBySpuId(skuEntity.getSpuId());
            List<SaleAttrValueVo> saleAttrValueVos = saleAttrsResponseVo.getData();
            itemVo.setSaleAttrs(saleAttrValueVos);
        }, executorService);


        // 9.根据skuId查询当前sku的销售属性
        CompletableFuture<Void> saleAttrFuture  = CompletableFuture.runAsync(() -> {
            ResponseVo<List<SkuAttrValueEntity>> saleAttrResponseVo = this.pmsClient.querySaleAttrValueBySkuId(skuId);
            List<SkuAttrValueEntity> skuAttrValueEntities = saleAttrResponseVo.getData();
            if (!CollectionUtils.isEmpty(skuAttrValueEntities)) {
                itemVo.setSaleAttr(skuAttrValueEntities.stream().collect(Collectors.toMap(SkuAttrValueEntity::getAttrId, SkuAttrValueEntity::getAttrValue)));
            }
        }, executorService);


        // 10.根据spuId查询spu下所有销售属性组合与skuId的映射关系
        CompletableFuture<Void> mappingFuture  = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<String> stringResponseVo = this.pmsClient.queryMappingBySpuId(skuEntity.getSpuId());
            String json = stringResponseVo.getData();
            itemVo.setSkuJsons(json);
        }, executorService);

        // 11.根据spuId查询spu的描述信息
        CompletableFuture<Void> descFuture  = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<SpuDescEntity> spuDescEntityResponseVo = this.pmsClient.querySpuDescById(skuEntity.getSpuId());
            SpuDescEntity descEntity = spuDescEntityResponseVo.getData();
            if (descEntity != null) {
                itemVo.setSpuImage(Arrays.asList(StringUtils.split(descEntity.getDecript(), ",")));
            }
        }, executorService);


        // 12.查询规格参数分组及组下的规格参数和值
        CompletableFuture<Void> groupFuture  = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<List<ItemGroupVo>> groupResponseVo = this.pmsClient.queryGroupWithAttrValueByCidAndSpuIdAndSkuId(skuEntity.getCategoryId(), skuEntity.getSpuId(), skuId);
            List<ItemGroupVo> itemGroupVos = groupResponseVo.getData();
            itemVo.setGroups(itemGroupVos);
        }, executorService);

        CompletableFuture.allOf(categoryFuture, brandFuture, spuFuture, imagesFuture, salesFuture, wareSkuFuture,
                saleAttrsFuture, saleAttrFuture, mappingFuture, descFuture, groupFuture).join();
        executorService.execute(()->{
                generateHtml(itemVo);
        });
        return itemVo;
    }

    private void generateHtml(ItemVo itemVo){
        //通过文件流吧静态文件保存到硬盘
        try (PrintWriter printWriter = new PrintWriter("E:\\atguigu\\20230309-java(sh)\\Code\\gmall\\html\\" + itemVo.getSkuId() +".html");){
            //上下文对象
            Context context = new Context();
            //通过上下文对象给模版传递数据
            context.setVariable("itemVo",itemVo);
            //执行页面静态化方法
            this.templateEngine.process("item",context,printWriter);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
