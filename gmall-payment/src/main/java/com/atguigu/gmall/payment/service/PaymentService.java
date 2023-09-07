package com.atguigu.gmall.payment.service;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.payment.feign.GmallOmsClient;
import com.atguigu.gmall.payment.mapper.PaymentInfoMapper;
import com.atguigu.gmall.payment.pojo.PayVo;
import com.atguigu.gmall.payment.pojo.PaymentInfoEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;

@Service
public class PaymentService {

    @Autowired
    private GmallOmsClient omsClient;

    @Autowired
    private PaymentInfoMapper paymentInfoMapper;

    public OrderEntity queryOrderByToken(String orderToken) {
        ResponseVo<OrderEntity> orderEntityResponseVo = this.omsClient.queryOrderByToken(orderToken);
        return orderEntityResponseVo.getData();
    }

    public Long savePaymentInfo(PayVo payVo) {
        PaymentInfoEntity paymentInfoEntity = new PaymentInfoEntity();
        paymentInfoEntity.setOutTradeNo(payVo.getOut_trade_no());
        paymentInfoEntity.setPaymentType(1);
        paymentInfoEntity.setTotalAmount(new BigDecimal(payVo.getTotal_amount()));
        paymentInfoEntity.setSubject(payVo.getSubject());
        paymentInfoEntity.setPaymentStatus(0);
        paymentInfoEntity.setCreateTime(new Date());
        this.paymentInfoMapper.insert(paymentInfoEntity);
        return paymentInfoEntity.getId();
    }

    public PaymentInfoEntity queryPaymentInfo(String payId) {
        return this.paymentInfoMapper.selectById(payId);
    }

    public int updatePaymentInfo(PaymentInfoEntity paymentInfoEntity) {
        return  this.paymentInfoMapper.updateById(paymentInfoEntity);
    }
}
