package com.atguigu.gmall.pms.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.pms.entity.AttrEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.pms.mapper.AttrMapper;
import com.atguigu.gmall.pms.mapper.SkuMapper;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.SkuAttrValueMapper;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.service.SkuAttrValueService;


@Service("skuAttrValueService")
public class SkuAttrValueServiceImpl extends ServiceImpl<SkuAttrValueMapper, SkuAttrValueEntity> implements SkuAttrValueService {
    @Autowired
    private  SkuAttrValueMapper skuAttrValueMapper;

    @Autowired
    private AttrMapper attrMapper;

    @Autowired
    private SkuMapper skuMapper;







    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<SkuAttrValueEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<SkuAttrValueEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public List<SkuAttrValueEntity> querySearchAttrValueBySkuId(Long skuId) {
        return this.skuAttrValueMapper.querySearchAttrValueBySkuId(skuId);
    }


    @Override
    public List<SkuAttrValueEntity> querySaleAttrValuesByCidAndSkuId(Long cid, Long skuId) {
        // 1.根据分类id查询检索属性
        List<AttrEntity> attrEntities = this.attrMapper.selectList(new QueryWrapper<AttrEntity>().eq("category_id", cid).eq("search_type", 1));
        if (CollectionUtils.isEmpty(attrEntities)){
            return null;
        }
        // 获取规格参数id集合
        List<Long> attrIds = attrEntities.stream().map(AttrEntity::getId).collect(Collectors.toList());

        // 2.根据skuId和attrIds查询销售类型的检索属性和值
        return this.list(new QueryWrapper<SkuAttrValueEntity>().eq("sku_id", skuId).in("attr_id", attrIds));
    }

    @Override
    public List<SaleAttrValueVo> querySaleAttrValueBySpuId(Long spuId) {
        // 1.根据spuId查询sku
        List<SkuEntity> skuEntities = this.skuMapper.selectList(new QueryWrapper<SkuEntity>().eq("spu_id", spuId));
        if (CollectionUtils.isEmpty(skuEntities)){
            return null;
        }
        // 把 sku实体类集合转化成skuIds集合
        List<Long> skuIds = skuEntities.stream().map(SkuEntity::getId).collect(Collectors.toList());

        // 2. 根据skuIds查询销量属性及值
        List<SkuAttrValueEntity> skuAttrValueEntities = this.list(new QueryWrapper<SkuAttrValueEntity>().in("sku_id", skuIds));
        if (CollectionUtils.isEmpty(skuAttrValueEntities)){
            return null;
        }
        Map<Long, List<SkuAttrValueEntity>> map = skuAttrValueEntities.stream()
                .collect(Collectors.groupingBy(SkuAttrValueEntity::getAttrId));
        //把map结构中每一个kv转化成一个SaleAttrValueVo
        List<SaleAttrValueVo> saleAttrValueVos = new ArrayList<>();
        map.forEach((attrId,attrValueEntities)->{
            SaleAttrValueVo saleAttrValueVo = new SaleAttrValueVo();
            saleAttrValueVo.setAttrId(attrId);
            saleAttrValueVo.setAttrName(attrValueEntities.get(0).getAttrName());
            saleAttrValueVo.setAttrValues(attrValueEntities.stream().map(SkuAttrValueEntity::getAttrValue).collect(Collectors.toList()));
            saleAttrValueVos.add(saleAttrValueVo);
        });
        return saleAttrValueVos;
    }

    public String queryMappingBySpuId(Long spuId) {
        // 1.根据spuId查询sku
        List<SkuEntity> skuEntities = this.skuMapper.selectList(new QueryWrapper<SkuEntity>().eq("spu_id", spuId));
        if (CollectionUtils.isEmpty(skuEntities)){
            return null;
        }
        // 把sku实体类集合转化成skuids集合
        List<Long> skuIds = skuEntities.stream().map(SkuEntity::getId).collect(Collectors.toList());

        // 2.查询映射关系 {'白天白,8G,256G': 100, '白天白,12G,512G': 101}
        List<Map<String, Object>> maps = this.skuAttrValueMapper.queryMappingBySkuIds(skuIds);
        if (CollectionUtils.isEmpty(maps)){
            return null;
        }
        // 把[{sku_id=19, attr_values=白色,8G,256G}, {sku_id=20, attr_values=白色,8G,512G}, {sku_id=21, attr_values=白色,12G,256G}, {sku_id=22, attr_values=白色,12G,512G}]
        // 转化成一个map
        Map<String, Long> mappingMap = maps.stream().collect(Collectors.toMap(
                map -> map.get("attr_values").toString(),
                map -> (Long) map.get("sku_id")));

        return JSON.toJSONString(mappingMap);
    }


}