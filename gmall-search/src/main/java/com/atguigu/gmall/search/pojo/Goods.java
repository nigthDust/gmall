package com.atguigu.gmall.search.pojo;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Date;
import java.util.List;

@Data
@JsonIgnoreProperties("_class")
@Document(indexName = "goods",shards = 3,replicas = 2)
@NoArgsConstructor
@AllArgsConstructor
public class Goods {

    //商品列表所需字段
    @Id
    private Long skuId;
    @Field(type = FieldType.Text,analyzer = "ik_max_word")
    private String title;
    @Field(type = FieldType.Keyword,index = false)
    private String subtitle;
    @Field(type = FieldType.Keyword, index = false)
    private String defaultImage;
    @Field(type = FieldType.Double)
    private Double price;

    //排序及过滤
    // 排序及过滤
    @Field(type = FieldType.Long)
    private Long sales;
    @Field(type = FieldType.Date,format = DateFormat.date)
    private Date createTime;
    @Field(type = FieldType.Boolean)
    private Boolean store; // 是否有货

    //品牌聚合字段
    @Field(type = FieldType.Long)
    private  Long brandId;
    @Field(type = FieldType.Keyword)
    private String brandName;
    @Field(type = FieldType.Keyword)
    private String logo;

    //分类聚合字段
    @Field(type = FieldType.Long)
    private Long categoryId;
    @Field(type = FieldType.Keyword)
    private String categoryName;


    //检索类型的规格参数聚合所需字段
    @Field(type = FieldType.Nested)
    private List<SearchAttrValue> searchAttrs;
}
