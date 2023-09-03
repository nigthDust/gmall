package com.atguigu.gmall.order.pojo;

import com.atguigu.gmall.ums.entity.UserAddressEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderSubmitVo {

    // 选中的收货地址
    private UserAddressEntity address;

    // 使用的购物积分
    private Integer bounds;

    // 配送方式 或者 物流公司
    private String deliveryCompany;

    // 送货清单
    private List<OrderItemVo> items;

    // 防重复提交的唯一标识
    private String orderToken;

    // 支付方式
    private Integer payType;

    // 总价格：验价
    private BigDecimal totalPrice;
}