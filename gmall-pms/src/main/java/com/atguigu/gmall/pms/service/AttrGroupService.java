package com.atguigu.gmall.pms.service;

import com.atguigu.gmall.pms.entity.vo.GroupVo;
import com.atguigu.gmall.pms.vo.ItemGroupVo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.pms.entity.AttrGroupEntity;

import java.util.List;
import java.util.Map;

/**
 * 属性分组
 *
 * @author lyc
 * @email lyc@atguigu.com
 * @date 2023-08-09 21:26:56
 */
public interface AttrGroupService extends IService<AttrGroupEntity> {

    PageResultVo queryPage(PageParamVo paramVo);

    List<GroupVo> queryByCid(Long cid);

    List<ItemGroupVo> queryGroupWithAttrValueByCidAndSpuIdAndSkuId(Long cid, Long spuId, Long skuId);
}

