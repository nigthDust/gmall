package com.atguigu.gmall.pms.vo;

import lombok.Data;

import java.util.List;

@Data
public class SaleAttrValueVo {

    private Long attrId;
    private String attrName;
    private List<String> attrValues;

}
