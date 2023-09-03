package com.atguigu.gmall.wms.vo;

import lombok.Data;

@Data
public class SkuLockVo {

    private Long skuId;
    private Integer count;
    private Boolean lock; //锁定是否成功： true=锁定成功， false-锁定失败
    private Long wareSkuId; //锁定库存的id

}
