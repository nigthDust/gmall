package com.atguigu.gmall.index.config;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.feign.GmallPmsClient;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Configuration
public class BloomFilterConfig {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private GmallPmsClient pmsClient;

    private static final String KEY_PREFIX = "index:cates:";

    @Bean
    public RBloomFilter bloomFilter(){
        RBloomFilter<Object> bloomFilter = this.redissonClient.getBloomFilter("index:bf");
        bloomFilter.tryInit(2000L, 0.03);
        // 三级分类：查询所有的一级分类
        ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryCategory(0L);
        List<CategoryEntity> categoryEntities = listResponseVo.getData();
        if (!CollectionUtils.isEmpty(categoryEntities)){
            categoryEntities.forEach(categoryEntity -> {
                bloomFilter.add(KEY_PREFIX + categoryEntity.getId());
            });
        }
        // TODO：广告数据：查询

        return bloomFilter;
    }
}