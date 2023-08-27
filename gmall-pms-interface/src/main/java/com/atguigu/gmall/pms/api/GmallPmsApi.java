package com.atguigu.gmall.pms.api;

import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.vo.ItemGroupVo;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import java.util.List;

public interface GmallPmsApi {

    @PostMapping("pms/spu/json")
    ResponseVo<List<SpuEntity>> querySpuByPageJson(@RequestBody PageParamVo paramVo);

//    @PostMapping("pms/spu/page")
//    public ResponseVo<List<SpuEntity>> querySpusByPage(@RequestBody PageParamVo pageParamVo);
    @GetMapping("pms/spu/{id}")
    ResponseVo<SpuEntity> querySpuById(@PathVariable("id") Long id);

    @GetMapping("pms/sku/spu/{spuId}")
    ResponseVo<List<SkuEntity>> querySkusBySpuId(@PathVariable("spuId")Long spuId);
    @GetMapping("pms/sku/{id}")
   ResponseVo<SkuEntity> querySkuById(@PathVariable("id") Long id);

    @GetMapping("pms/category/{id}")
    ResponseVo<CategoryEntity> queryCategoryById(@PathVariable("id") Long id);
    @GetMapping("pms/category/level/321/{cid3}}")
    ResponseVo<List<CategoryEntity>> queryLvl123CategoriesByCid3(@PathVariable("cid3")Long cid3);

    @GetMapping("pms/brand/{id}")
    ResponseVo<BrandEntity> queryBrandById(@PathVariable("id") Long id);

//    @GetMapping("pms/spuattrvalue/spu/{spuId}")
//    public ResponseVo<List<SpuAttrValueEntity>> querySearchAttrValueBySpuId(@PathVariable("spuId")Long spuId);
//
//    @GetMapping("pms/skuattrvalue/sku/{skuId}")
//    public ResponseVo<List<SkuAttrValueEntity>> querySearchAttrValueBySkuId(@PathVariable("skuId")Long skuId);

    @GetMapping("pms/skuattrvalue/category/{cid}")
    ResponseVo<List<SkuAttrValueEntity>> querySaleAttrValuesByCidAndSkuId(
            @PathVariable("cid")Long cid,
            @RequestParam("skuId")Long skuId
    );


    @GetMapping("pms/spuattrvalue/category/{cid}")
    public ResponseVo<List<SpuAttrValueEntity>> querySaleAttrValuesByCidAndSpuId(
            @PathVariable("cid")Long cid,
            @RequestParam("spuId")Long spuId
    );

    @GetMapping("pms/category/parent/{parentId}")
    ResponseVo<List<CategoryEntity>> queryCategory(@PathVariable("parentId")Long parentId);

    @GetMapping("pms/category/level23/{pid}")
    ResponseVo<List<CategoryEntity>> queryLevel23CategoriesByPid(@PathVariable("pid")Long pid);
    @GetMapping("pms/skuimages/sku/{skuId}")
    ResponseVo<List<SkuImagesEntity>> queryImagesBySkuId(@PathVariable("skuId")Long skuId);

    @GetMapping("pms/skuattrvalue/spu/{spuId}")
    ResponseVo<List<SaleAttrValueVo>> querySaleAttrValueBySpuId(@PathVariable("spuId")Long spuId);

    @GetMapping("pms/skuattrvalue/sku/{skuId}")
    ResponseVo<List<SkuAttrValueEntity>> querySaleAttrValueBySkuId(@PathVariable("skuId")Long skuId);

    @GetMapping("pms/skuattrvalue/mapping/{spuId}")
    ResponseVo<String> queryMappingBySpuId(@PathVariable("spuId")Long spuId);

    @GetMapping("pms/spudesc/{spuId}")
    ResponseVo<SpuDescEntity> querySpuDescById(@PathVariable("spuId") Long spuId);

    @GetMapping("pms/attrgroup/with/attr/value/{cid}")
    ResponseVo<List<ItemGroupVo>> queryGroupWithAttrValueByCidAndSpuIdAndSkuId(
            @PathVariable("cid")Long cid,
            @RequestParam("spuId")Long spuId,
            @RequestParam("skuId")Long skuId
    );

}
