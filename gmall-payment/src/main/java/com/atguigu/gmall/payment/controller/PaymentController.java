package com.atguigu.gmall.payment.controller;

import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.payment.Interceptor.LoginInterceptor;
import com.atguigu.gmall.payment.config.AlipayTemplate;
import com.atguigu.gmall.payment.pojo.PayAsyncVo;
import com.atguigu.gmall.payment.pojo.PayVo;
import com.atguigu.gmall.payment.pojo.PaymentInfoEntity;
import com.atguigu.gmall.payment.pojo.UserInfo;
import com.atguigu.gmall.payment.service.PaymentService;
import exception.OrderException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.util.Date;

@Controller
public class PaymentController {
    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AlipayTemplate alipayTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @GetMapping("pay.html")
    public String pay(@RequestParam("orderToken")String orderToken, Model model){
        OrderEntity orderEntity =  this.paymentService.queryOrderByToken(orderToken);

        // 1.判断定时是否有空，如果为空则抛出异常
        if (orderEntity == null){
            throw new OrderException("非法请求！");
        }

        // 2.判断订单状态是否为未支付状态，如果不是未支付状态，则抛出异常
        if (orderEntity.getStatus() != 0){
            throw new OrderException("当前订单不可支付！");
        }

        // 3.判断当前订单是否属于当前用户
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();
        Long orderEntityUserId = orderEntity.getUserId();
        if (!userId.equals(orderEntityUserId)){
            throw new OrderException("当前订单不属于您！");
        }

        model.addAttribute("orderEntity",orderEntity);
        return "pay";
    }

    @GetMapping("alipay.html")
    @ResponseBody  //以其他视图形式渲染方法的返回结果集
    public String alipay(@RequestParam("orderToken")String orderToken){

        OrderEntity orderEntity =  this.paymentService.queryOrderByToken(orderToken);

        // 1.判断定时是否有空，如果为空则抛出异常
        if (orderEntity == null){
            throw new OrderException("非法请求！");
        }

        // 2.判断订单状态是否为未支付状态，如果不是未支付状态，则抛出异常
        if (orderEntity.getStatus() != 0){
            throw new OrderException("当前订单不可支付！");
        }

        // 3.判断当前订单是否属于当前用户
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();
        Long orderEntityUserId = orderEntity.getUserId();
        if (!userId.equals(orderEntityUserId)){
            throw new OrderException("当前订单不属于您！");
        }

        //调用支付宝远程接口,打开支付页面
        try {
            PayVo payVo = new PayVo();
            payVo.setOut_trade_no(orderToken);
            //此处不要写订单中的实际应付金额，建议写0.01
            payVo.setTotal_amount("0.01");
            payVo.setSubject("谷粒商城支付平台");

            //保存对账记录，并返回对账记录的id
            Long payId = this.paymentService.savePaymentInfo(payVo);
            payVo.setPassback_params(payId.toString());

            return this.alipayTemplate.pay(payVo);
        }catch (AlipayApiException e){
            throw new OrderException("跳转到支付页面失败，请重试！");
        }
    }
    @GetMapping("pay/success")
    public String paySuccess(){
        return "paysuccess";
    }

    @PostMapping("pay/ok")
    @ResponseBody
    public String payOk(PayAsyncVo asyncVo){
        // 1.验签：确保请求时支付宝发送的 failure
        Boolean flag = this.alipayTemplate.checkSignature(asyncVo);
        if (!flag){
            return "failure";
        }

        // 2.校验业务参数app_id、out_trade_no、total_amount
        String appId = asyncVo.getApp_id();
        String outTradeNo = asyncVo.getOut_trade_no();
        String totalAmount = asyncVo.getTotal_amount();
        // 获取公共回传参数
        String payId = asyncVo.getPassback_params();
        PaymentInfoEntity paymentInfoEntity =  this.paymentService.queryPaymentInfo(payId);
        if (!StringUtils.equals(appId,alipayTemplate.getApp_id())
        || !StringUtils.equals(outTradeNo,paymentInfoEntity.getOutTradeNo())
        || new BigDecimal(totalAmount).compareTo(paymentInfoEntity.getTotalAmount()) != 0){
            return "failure";
        }

        // 3.验证支付状态：TRADE_SUCCESS
        if (!StringUtils.equals("TRADE_SUCCESS",asyncVo.getTrade_status())){
            return "failure";
        }

        // 4.修改对账记录表，更新为已支付状态
        paymentInfoEntity.setTradeNo(asyncVo.getTrade_no());
        paymentInfoEntity.setPaymentStatus(1);
        paymentInfoEntity.setCallbackTime(new Date());
        paymentInfoEntity.setCallbackContent(JSON.toJSONString(asyncVo));
        if (this.paymentService.updatePaymentInfo(paymentInfoEntity) == 1){
            // 5.发送消息给MQ，修改订单状态并减库存
            this.rabbitTemplate.convertAndSend("ORDER.EXCHANGE","order.pay",outTradeNo);
        }else {
            // TODO:退款流程
        }

        // 6.返回success
        return "success";

    }
}
