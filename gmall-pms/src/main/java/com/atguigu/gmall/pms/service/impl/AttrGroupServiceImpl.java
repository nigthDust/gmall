package com.atguigu.gmall.pms.service.impl;

import com.atguigu.gmall.pms.entity.AttrEntity;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SpuAttrValueEntity;
import com.atguigu.gmall.pms.entity.vo.GroupVo;
import com.atguigu.gmall.pms.mapper.AttrMapper;
import com.atguigu.gmall.pms.mapper.SkuAttrValueMapper;
import com.atguigu.gmall.pms.mapper.SpuAttrValueMapper;
import com.atguigu.gmall.pms.vo.AttrValueVo;
import com.atguigu.gmall.pms.vo.ItemGroupVo;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.AttrGroupMapper;
import com.atguigu.gmall.pms.entity.AttrGroupEntity;
import com.atguigu.gmall.pms.service.AttrGroupService;


@Service("attrGroupService")
public class AttrGroupServiceImpl extends ServiceImpl<AttrGroupMapper, AttrGroupEntity> implements AttrGroupService {
    @Autowired
    private AttrMapper attrMapper;

    @Autowired
    private SpuAttrValueMapper spuAttrValueMapper;

    @Autowired
    private SkuAttrValueMapper skuAttrValueMapper;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<AttrGroupEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<AttrGroupEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public List<GroupVo> queryByCid(Long cid) {
        // 查询所有的分组
        List<AttrGroupEntity> attrGroupEntities = this.list(new QueryWrapper<AttrGroupEntity>().eq("category_id", cid));

        // 查询出每组下的规格参数
        return attrGroupEntities.stream().map(attrGroupEntity -> {
            GroupVo groupVo = new GroupVo();
            BeanUtils.copyProperties(attrGroupEntity, groupVo);
            // 查询规格参数，只需查询出每个分组下的通用属性就可以了（不需要销售属性）
            List<AttrEntity> attrEntities = this.attrMapper.selectList(new QueryWrapper<AttrEntity>().eq("group_id", attrGroupEntity.getId()).eq("type", 1));
            groupVo.setAttrEntities(attrEntities);
            return groupVo;
        }).collect(Collectors.toList());
    }

    @Override
    public List<ItemGroupVo> queryGroupWithAttrValueByCidAndSpuIdAndSkuId(Long cid, Long spuId, Long skuId) {
        // 根据分类id查询分组
        List<AttrGroupEntity> groupEntities = this.list(new QueryWrapper<AttrGroupEntity>().eq("category_id", cid));
        if (CollectionUtils.isEmpty(groupEntities)){
            return null;
        }
        // 把分组的entity集合 转行成vo集合
        return groupEntities.stream().map(attrGroupEntity -> {
            ItemGroupVo groupVo  = new ItemGroupVo();
            //设置分组的id和name
            groupVo.setId(attrGroupEntity.getId());
            groupVo.setName(attrGroupEntity.getName());
            //根据分组的id查询组的规格参数
            List<AttrEntity> attrEntities = this.attrMapper.selectList(new QueryWrapper<AttrEntity>().eq("group_id", attrGroupEntity.getId()));
            if (CollectionUtils.isEmpty(attrEntities)){
                return groupVo;
            }
            //把规格参数集合 转化成AttrValueVo集合
            groupVo.setAttrValues(attrEntities.stream().map(attrEntity -> {
                AttrValueVo attrValueVo = new AttrValueVo();
                //判断规格参数的类型
                if (attrEntity.getType() == 1){
                    SpuAttrValueEntity spuAttrValueEntity = this.spuAttrValueMapper.selectOne(new QueryWrapper<SpuAttrValueEntity>().eq("spu_id", spuId).eq("attr_id", attrEntity.getId()));
                    if (spuAttrValueEntity != null){
                        BeanUtils.copyProperties(spuAttrValueEntity,attrValueVo);
                    }
                }else {
                    SkuAttrValueEntity skuAttrValueEntity = this.skuAttrValueMapper.selectOne(new QueryWrapper<SkuAttrValueEntity>().eq("sku_id", skuId).eq("attr_id", attrEntity.getId()));
                    if (skuAttrValueEntity!=null){
                        BeanUtils.copyProperties(skuAttrValueEntity,attrValueVo);
                    }
                }
                return attrValueVo;
            }).collect(Collectors.toList()));
            return groupVo;

        }).collect(Collectors.toList());
    }
}