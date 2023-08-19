package com.atguigu.gmall.search;

import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.api.GmallPmsApi;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.search.feign.GmallPmsClient;
import com.atguigu.gmall.search.feign.GmallWmsClient;
import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchAttrValue;
import com.atguigu.gmall.search.repository.GoodsRepository;
import com.atguigu.gmall.wms.api.GmallWmsApi;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
class GmallSearchApplicationTests {
    @Autowired
    ElasticsearchRestTemplate restTemplate;
//    @Autowired
//    GmallPmsApi pmsClient;
//    @Autowired
//    GmallWmsApi wmsClient;
//    @Autowired
//    GoodsRepository goodsRepository;
    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallWmsClient wmsClient;
    @Test
    void contextLoads() {
        IndexOperations indexOps = this.restTemplate.indexOps(Goods.class);
        if (!indexOps.exists()){
            indexOps.create();
            indexOps.putMapping(indexOps.createMapping());
        }

        Integer pageNum = 1;
        Integer pageSize = 100;
        do {
            // 1.分页查询spu spus
            ResponseVo<List<SpuEntity>> spuResponseVo = this.pmsClient.querySpuByPageJson(new PageParamVo(pageNum, pageSize, null));
            List<SpuEntity> spuEntities = spuResponseVo.getData();
            spuEntities.forEach(spu -> {

                // 2.根据spuId查询sku skus
                ResponseVo<List<SkuEntity>> skuResponseVo = this.pmsClient.querySkusBySpuId(spu.getId());
                List<SkuEntity> skus = skuResponseVo.getData();
                if (CollectionUtils.isEmpty(skus)){
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
            });

            pageSize = spuEntities.size();
            pageNum++;
        } while (pageSize == 100);
    }

//    public static void main(String[] args) {
//        List<Integer> arr = Arrays.asList(1,2,3,4,5,6,7,8,9);
//        List<User> users = Arrays.asList(
//                new User("柳岩", 20),
//                new User("马蓉", 21),
//                new User("柳岩1", 22),
//                new User("柳岩2", 23),
//                new User("柳岩3", 24)
//        );
//        System.out.println(arr.stream().reduce((a, b) -> a + b).get());
//        System.out.println(users.stream().map(User::getAge).reduce((a, b) -> a + b).get());
//    }
//
//    @AllArgsConstructor
//    @NoArgsConstructor
//    @Data
//    public static class User{
//        String name;
//        Integer age;
//    }
}




//    @Test
//    void  importData(){
//        IndexOperations indexOps = this.restTemplate.indexOps(Goods.class);
//        if (!indexOps.exists()){
//            indexOps.create();
//            indexOps.createMapping();
//        }
//
//        //数据导入
//        Integer pageNum = 1;
//        Integer pageSize = 100;
//
//        do {
//            //分批查询spu
//            PageParamVo pageParamVo = new PageParamVo();
//            pageParamVo.setPageNum(pageNum);
//            pageParamVo.setPageSize(pageSize);
//            ResponseVo<List<SpuEntity>> responseVo = this.pmsClient.querySpusByPage(pageParamVo);
//            //获取当前也得spuEntity集合
//            List<SpuEntity> spuEntities = responseVo.getData();
//            if (CollectionUtils.isEmpty(spuEntities)){
//                return;
//            }
//            //遍历spu获取spu下的sku集合
//            spuEntities.forEach(spuEntity -> {
//                ResponseVo<List<SkuEntity>> skuResponseVo
//                        = this.pmsClient.querySkusBySpuId(spuEntity.getId());
//                List<SkuEntity> skuEntities = skuResponseVo.getData();
//                if (!CollectionUtils.isEmpty(skuEntities)){
//                    //如果spu下的sku不为空，查询品牌
//
//                    ResponseVo<BrandEntity> brandEntityResponseVo = this.pmsClient.queryBrandById(spuEntity.getBrandId());
//                    BrandEntity brandEntity = brandEntityResponseVo.getData();
//                    //如果spu下的sku不为空,查询分类
//                    ResponseVo<CategoryEntity> categoryEntityResponseVo = this.pmsClient.queryCategoryById(spuEntity.getCategoryId());
//                    CategoryEntity categoryEntity = categoryEntityResponseVo.getData();
//                    //如果spu下的sku不为空，查询基本类型的检索属性和值
//                    ResponseVo<List<SpuAttrValueEntity>> baseAttrResponseVo = this.pmsClient.querySearchAttrValueBySpuId(spuEntity.getId());
//                    List<SpuAttrValueEntity> spuAttrValueEntities = baseAttrResponseVo.getData();
//
//                    // 把sku集合转化成goods集合
//                        List<Goods> goodsList =   skuEntities.stream().map(skuEntity -> {
//                        Goods goods = new Goods();
//                        //设置sku相关参数
//                        goods.setSkuId(skuEntity.getId());
//                        goods.setTitle(skuEntity.getTitle());
//                        goods.setSubtitle(skuEntity.getSubtitle());
//                        goods.setPrice(skuEntity.getPrice().doubleValue());
//                        goods.setDefaultImage(skuEntity.getDefaultImage());
//                        //设置创建时间
//                        goods.setCreateTime(new Date());
//                        //根据skuId查询库存
//
//                        ResponseVo<List<WareSkuEntity>> wareResponseVo = this.wmsClient.queryWareSkuBySkuId(skuEntity.getId());
//                        List<WareSkuEntity> wareSkuEntities = wareResponseVo.getData();
//
//                        if (!CollectionUtils.isEmpty(wareSkuEntities)){
//                            //任何IG仓库有货，都认为是有货
//                            goods.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
//                            //对所有仓库求销量之和
//                            goods.setSales(wareSkuEntities.stream().map(WareSkuEntity::getSales).reduce((a,b) -> a+b).get ());
//                        }
//                        //根据品牌id查询品牌
//                        if (brandEntity != null){
//                            goods.setBrandId(brandEntity.getId());
//                            goods.setBrandName(brandEntity.getName());
//                            goods.setLogo(brandEntity.getLogo());
//                        }
//
//                        //分类
//                        if (categoryEntity!=null){
//                            goods.setCategoryId(categoryEntity.getId());
//                            goods.setCategoryName(categoryEntity.getName());
//                        }
//
//                        //检索类型的规模参数
//                        List<SearchAttrValue>  searchAttrValueVOs = new ArrayList<>();
//
//                        ResponseVo<List<SkuAttrValueEntity>> saleAttrResponseVo = this.pmsClient.querySearchAttrValueBySkuId(skuEntity.getId());
//                        List<SkuAttrValueEntity> skuAttrValueEntities = saleAttrResponseVo.getData();
//                        //把基本类型的检索参数转换成SearchAttrValueVo集合
//                        if (!CollectionUtils.isEmpty(spuAttrValueEntities)){
//                            searchAttrValueVOs.addAll(spuAttrValueEntities.stream().map(spuAttrValueEntity -> {
//                                SearchAttrValue searchAttrValueVO = new SearchAttrValue();
//
//                                BeanUtils.copyProperties(spuAttrValueEntity, searchAttrValueVO);
//                                return searchAttrValueVO;
//                            }).collect(Collectors.toList()));
//                        }
//                        if (!CollectionUtils.isEmpty(skuAttrValueEntities)){
//                            searchAttrValueVOs.addAll(skuAttrValueEntities.stream().map(skuAttrValueEntity -> {
//                                SearchAttrValue searchAttrValueVO = new SearchAttrValue();
//                                BeanUtils.copyProperties(skuAttrValueEntity, searchAttrValueVO);
//                                return searchAttrValueVO;
//                            }).collect(Collectors.toList()));
//                        }
//                        goods.setSearchAttrs(searchAttrValueVOs);
//
//                        return goods;
//                    }).collect(Collectors.toList());
//                    //批量导入到索引库
//                    this.goodsRepository.saveAll(goodsList);
//
//
//                }
//
//            });
//            pageSize = spuEntities.size();
//            pageNum++;
//        }while (pageSize == 100);
//    }


