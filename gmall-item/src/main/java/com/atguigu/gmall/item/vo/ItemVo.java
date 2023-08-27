package com.atguigu.gmall.item.vo;

import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.pms.entity.SkuImagesEntity;
import com.atguigu.gmall.pms.vo.ItemGroupVo;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ItemVo {

    // 一二三级分类
    private List<CategoryEntity> categories;
    //品牌
    private Long brandId;
    private String brandName;
    //spu信息
    private  Long spuId;;
    private  String spuName;

    //基本信息
    private Long skuId;
    private String title;
    private String subtitile;
    private String defaultImage;
    private BigDecimal price;
    private Integer weight;
    //sku图片列表
    private List<SkuImagesEntity> images;
    //营销信息
    private  List<ItemSaleVo> sales;
    //是否有货
    private Boolean store;

    // 销售属性列表（同一个spu下所有sku的）    V
    // [{attrId: 3, attrName: 机身颜色, attrValues: ['暗夜黑', '白天白']}，
    // {attrId: 4, attrName: 运行内存, attrValues: ['8G', '12G']}，
    // {attrId: 5, attrName: 机身存储, attrValues: ['256G', '512G']}]
    private List<SaleAttrValueVo> saleAttrs;

    //当前sku的销售属性
    private Map<Long,String> saleAttr;

    //销售属性组合与skuId的映射关系：
    private String skuJsons;
    //商品详情
    private List<String> spuImage;
    //规格参数分组
    private List<ItemGroupVo> groups;
}
