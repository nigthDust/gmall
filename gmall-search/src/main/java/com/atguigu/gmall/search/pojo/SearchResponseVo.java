package com.atguigu.gmall.search.pojo;

import com.atguigu.gmall.pms.entity.BrandEntity;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import lombok.Data;

import java.util.List;

@Data
public class SearchResponseVo {

    //品牌列表
    private List<BrandEntity> brands;
    //分类列表
    private List<CategoryEntity> categories;
    //规格参数列表
    private List<SearchResponseAttrVo> filters;

    //分页参数
    private Long total; // 总记录数
    private Integer pageNum; //页码
    private Integer pageSize; //页大小
    private List<Goods> goodsList; //当前页记录
}
