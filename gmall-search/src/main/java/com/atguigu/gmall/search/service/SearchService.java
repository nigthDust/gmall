package com.atguigu.gmall.search.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.pms.entity.BrandEntity;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchParamVo;
import com.atguigu.gmall.search.pojo.SearchResponseAttrVo;
import com.atguigu.gmall.search.pojo.SearchResponseVo;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SearchService {
    @Autowired
    private RestHighLevelClient restHighLevelClient;
    public SearchResponseVo search(SearchParamVo searchParamVo) {
        try {
            SearchRequest request = new SearchRequest(new String[]{"goods"}, this.buildDsl(searchParamVo));
            SearchResponse response = this.restHighLevelClient.search(request, RequestOptions.DEFAULT);

            //解析结果集

            SearchResponseVo responseVo = this.parsedResult(response);
            responseVo.setPageNum(searchParamVo.getPageNum());
            responseVo.setPageSize(searchParamVo.getPageSize());
            return  responseVo;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 构建DSL语句
     *
     */
    private SearchSourceBuilder buildDsl(SearchParamVo searchParamVo){
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        String keyword = searchParamVo.getKeyword();
        if (StringUtils.isBlank(keyword)){
            throw new RuntimeException("请输入搜索关键字！");
        }

        //1.构建查询过滤条件
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);
        //1.1 构建匹配查询
        boolQueryBuilder.must(QueryBuilders.matchQuery("title",keyword).operator(Operator.AND));
        //1.2 构建过滤条件
        //1.2.1  构建品牌的过滤
        List<Long> brandId = searchParamVo.getBrandId();
        if (!CollectionUtils.isEmpty(brandId)){
            boolQueryBuilder.filter(QueryBuilders.termsQuery("brandId",brandId));
        }
        //1.2.2 构建分类过滤
        List<Long> categoryId = searchParamVo.getCategoryId();
        if (!CollectionUtils.isEmpty(categoryId)){
            boolQueryBuilder.filter(QueryBuilders.termsQuery("categoryId",categoryId));
        }
        //1.2.3 价格区间过滤
        Double priceFrom = searchParamVo.getPriceFrom();
        Double priceTo = searchParamVo.getPriceTo();
        if (priceFrom != null || priceTo != null){
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("price");
            boolQueryBuilder.filter(rangeQuery);
            if (priceFrom != null){
                rangeQuery.gte(priceFrom);
            }
            if (priceTo != null){
                rangeQuery.lte(priceTo);
            }
        }
         // 1.2.4构建是否有货的过滤条件
         Boolean store = searchParamVo.getStore();
         if (store != null){
             //实际开发中写true就行,只能过滤出有货，无法过滤出无货
             boolQueryBuilder.filter(QueryBuilders.termQuery("store",store));
         }
         //1.2.5构建规格二参数的嵌套过滤
        List<String> props = searchParamVo.getProps();
         if (!CollectionUtils.isEmpty(props)){
             props.forEach(prop ->{
                 // 对prop进行切割
                 String[] attrs = StringUtils.split(prop, ":");
                 if (attrs != null && attrs.length == 2 && StringUtils.isNumeric(attrs[0])){
                     // 嵌套过滤真正的查询时bool查询
                     BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                     //每一个prop对应一个嵌套过滤
                     boolQueryBuilder.filter(QueryBuilders.nestedQuery("searchAttrs",boolQuery, ScoreMode.None));
                     boolQuery.must(QueryBuilders.termQuery("searchAttrs.attrId",attrs[0]));
                     boolQuery.must(QueryBuilders.termsQuery("searchAttrs.attrValue",StringUtils.split(attrs[1],"-")));
                 }
             });
         }

        //2.构建排序条件 // 排序字段：0-默认，得分降序；1-按价格升序；2-按价格降序；3-按创建时间降序；4-按销量降序
        Integer sort = searchParamVo.getSort();
         switch (sort){
             case 1: sourceBuilder.sort("price", SortOrder.DESC);break;
             case 2: sourceBuilder.sort("price", SortOrder.ASC);break;
             case 3: sourceBuilder.sort("sales", SortOrder.DESC);break;
             case 4: sourceBuilder.sort("createTime", SortOrder.DESC);break;
             default: sourceBuilder.sort("_score",SortOrder.DESC);
         }

        //3.构建分页条件
        Integer pageNum = searchParamVo.getPageNum();
        Integer pageSize = searchParamVo.getPageSize();
        sourceBuilder.from((pageNum-1)*pageSize);
        sourceBuilder.size(pageSize);

        //4.构建高亮
        sourceBuilder.highlighter(new HighlightBuilder()
                .field("title")
                .postTags("<font style='color:red;'>")
                 .preTags("</font>"));
        //5.构建聚合条件
        //5.1 构建品牌聚合
        sourceBuilder.aggregation(AggregationBuilders.terms("brandIdAgg").field("brandId")
                .subAggregation(AggregationBuilders.terms("brandNameAgg").field("brandName"))
                .subAggregation(AggregationBuilders.terms("logoAgg").field("logo")));
        //5.2 构建分类聚合
        sourceBuilder.aggregation(AggregationBuilders.terms("categoryIdAgg").field("categoryId")
                .subAggregation(AggregationBuilders.terms("categoryNameAgg").field("categoryName")));
        //5.3 构建规格参数 的嵌套聚合
        sourceBuilder.aggregation(AggregationBuilders.nested("attrAgg","searchAttrs")
                .subAggregation(AggregationBuilders.terms("attrIdAgg").field("searchAttrs.attrId")
                        .subAggregation(AggregationBuilders.terms("attrNameAgg").field("searchAttrs.attrName"))
                        .subAggregation(AggregationBuilders.terms("attrValueAgg").field("searchAttrs.attrValue"))));
        //6.结果集过滤
        sourceBuilder.fetchSource(new String[]{"skuId","title","subtitle","price","defaultImage"},null);

        System.out.println(sourceBuilder);
        return sourceBuilder;
    }


    private SearchResponseVo parsedResult(SearchResponse response) {
        SearchResponseVo responseVo = new SearchResponseVo();
        //1.解析普通的搜索结果集
        SearchHits hits = response.getHits();
        //总记录数
        responseVo.setTotal(hits.getTotalHits().value);
        SearchHit[] hitsHits = hits.getHits();
        if (hitsHits != null && hitsHits.length > 0) {
            //把hitsHits数组 转化城GoodsList集合
            responseVo.setGoodsList(Arrays.stream(hitsHits).map(hit -> {
                //goods的json字符串
                String json = hit.getSourceAsString();
                Goods goods = JSON.parseObject(json, Goods.class);
                //获取高亮标题
                Map<String, HighlightField> highlightFields = hit.getHighlightFields();
                if (CollectionUtils.isEmpty(highlightFields)) {
                    return goods;
                }
                HighlightField highlightField = highlightFields.get("title");
                if (highlightField == null) {
                    return goods;
                }
                Text[] fragments = highlightField.fragments();
                if (fragments != null && fragments.length > 0 && fragments[0] != null) {
                    goods.setTitle(fragments[0].string());
                }
                return  goods;
            }).collect(Collectors.toList()));
        }
        //2.解析聚合结果集
        Aggregations aggregations = response.getAggregations();
        //2.1解析品牌聚合结果集获取品牌列表
        ParsedLongTerms brandIdAgg = aggregations.get("brandIdAgg");
        List<? extends Terms.Bucket> brandIdAggBuckets = brandIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(brandIdAggBuckets)) {
            //把品牌同集合转换城品牌集合
            responseVo.setBrands(brandIdAggBuckets.stream().map(bucket -> {
                BrandEntity brandEntity = new BrandEntity();
                //当前桶中的key就是品牌id
                brandEntity.setId(bucket.getKeyAsNumber().longValue());
                //获取子聚合
                Aggregations subAggs = bucket.getAggregations();
                //获取品牌名称的子聚合
                ParsedStringTerms brandNameAgg = subAggs.get("brandNameAgg");
                //品牌名称的桶集合
                List<? extends Terms.Bucket> brandNameAggBuckets = brandNameAgg.getBuckets();
                if (!CollectionUtils.isEmpty(brandNameAggBuckets)) {
                    brandEntity.setName(brandNameAggBuckets.get(0).getKeyAsString());
                }
                //获取品牌logo的子聚合
                ParsedStringTerms logoAgg = subAggs.get("logoAgg");
                List<? extends Terms.Bucket> logoAggBuckets = logoAgg.getBuckets();
                if (!CollectionUtils.isEmpty(logoAggBuckets)) {
                    brandEntity.setLogo(logoAggBuckets.get(0).getKeyAsString());
                }
                return brandEntity;
            }).collect(Collectors.toList()));
        }
        // 2.2 解析分类聚合结果集获取分类列表
        ParsedLongTerms categoryIdAgg = aggregations.get("categoryIdAgg");
        List<? extends Terms.Bucket> categoryIdAggBuckets = categoryIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(categoryIdAggBuckets)) {
            responseVo.setCategories(categoryIdAggBuckets.stream().map(bucket -> {
                CategoryEntity categoryEntity = new CategoryEntity();
                categoryEntity.setId(bucket.getKeyAsNumber().longValue());
                //分类名称的子聚合
                ParsedStringTerms categoryNameAgg = bucket.getAggregations().get("categoryNameAgg");
                List<? extends Terms.Bucket> buckets = categoryNameAgg.getBuckets();
                if (!CollectionUtils.isEmpty(buckets)) {
                    categoryEntity.setName(buckets.get(0).getKeyAsString());
                }
                return categoryEntity;
            }).collect(Collectors.toList()));
        }

        //2.3 解析规格参数的聚合结果集获取规格参数列表
        ParsedNested attrAgg = aggregations.get("attrAgg");
        //获取规格参数id的子聚合
        ParsedLongTerms attrIdAgg = attrAgg.getAggregations().get("attrIdAgg");
        //获取规格参数id子聚合中的桶
        List<? extends Terms.Bucket> buckets = attrIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(buckets)) {
            List<SearchResponseAttrVo> filters = buckets.stream().map(bucket -> {
                SearchResponseAttrVo attrVo = new SearchResponseAttrVo();
                attrVo.setAttrId(bucket.getKeyAsNumber().longValue());
                //获取子聚合
                Aggregations subAggs = bucket.getAggregations();
                //获取规格参数名称的子聚合
                ParsedStringTerms attrNameAgg = subAggs.get("attrNameAgg");
                List<? extends Terms.Bucket> attrNameAggBuckets = attrNameAgg.getBuckets();
                if (!CollectionUtils.isEmpty(attrNameAggBuckets)) {
                    attrVo.setAttrName(attrNameAggBuckets.get(0).getKeyAsString());
                }
                //获取规格参数值的子聚合
                ParsedStringTerms attrValueAgg = subAggs.get("attrValueAgg");
                List<? extends Terms.Bucket> attrValueAggBuckets = attrValueAgg.getBuckets();
                if (!CollectionUtils.isEmpty(attrValueAggBuckets)) {
                    attrVo.setAttrValues(attrValueAggBuckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList()));
                }
                return attrVo;
            }).collect(Collectors.toList());
            responseVo.setFilters(filters);
        }
        return responseVo;
    }
}
