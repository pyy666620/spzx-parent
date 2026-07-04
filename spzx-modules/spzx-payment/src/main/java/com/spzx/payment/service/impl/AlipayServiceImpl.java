package com.spzx.payment.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradeWapPayModel;
import com.alipay.api.domain.ExtUserInfo;
import com.alipay.api.domain.ExtendParams;
import com.alipay.api.domain.GoodsDetail;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.spzx.common.rabbit.constant.MqConst;
import com.spzx.common.rabbit.service.RabbitService;
import com.spzx.payment.configure.AlipayConfig;
import com.spzx.payment.domain.PaymentInfo;
import com.spzx.payment.service.IAlipayService;
import com.spzx.payment.service.IPaymentInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 负责调用支付宝 SDK
 */
@Service
public class AlipayServiceImpl implements IAlipayService {

    @Autowired
    AlipayClient alipayClient;

    @Autowired
    IPaymentInfoService paymentInfoService;

    @Autowired
    private RabbitService rabbitService;

    @Override
    public String submitAlipay(String orderNo) {
        //获取支付记录
        PaymentInfo paymentInfo = paymentInfoService.addOrGetPaymentInfo(orderNo);
        // 构造请求参数以调用接口
        AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
        AlipayTradeWapPayModel model = new AlipayTradeWapPayModel();

        // 设置商户订单号
        model.setOutTradeNo(orderNo);

        // 设置订单总金额
        model.setTotalAmount(paymentInfo.getAmount().toString());

        // 设置订单标题
        model.setSubject(paymentInfo.getContent());

        // 设置产品码
        model.setProductCode("QUICK_WAP_WAY");

        //设置同步调用网站支付成功页面的地址
        request.setReturnUrl(AlipayConfig.return_payment_url);
        //设置用户支付成功之后让支付宝异步调用通知支付结果的地址
        request.setNotifyUrl(AlipayConfig.notify_payment_url);

        // 设置商户的原始订单号
        model.setMerchantOrderNo("20161008001");

        request.setBizModel(model);
        // 第三方代调用模式下请设置app_auth_token
        // request.putOtherTextParam("app_auth_token", "<-- 请填写应用授权令牌 -->");

        AlipayTradeWapPayResponse response = null;
        try {
            response = alipayClient.pageExecute(request, "POST");
            // 如果需要返回GET请求，请使用
            // AlipayTradeWapPayResponse response = alipayClient.pageExecute(request, "GET");
            String formUrl = response.getBody();
            System.out.println(formUrl);
            if (response.isSuccess()) {
                System.out.println("调用成功");
                return formUrl;
            } else {
                System.out.println("调用失败");
            }
        } catch (AlipayApiException e) {
            throw new RuntimeException(e);
        }

        return "";
    }

    @Override
    public void updatePaymentStatus(Map<String, String> paramMap) {
        //获取订单号
        String orderNo = paramMap.get("out_trade_no");
        //根据订单号获取支付记录
        PaymentInfo paymentInfo = new PaymentInfo();
        //设置支付状态为已支付
        paymentInfo.setPaymentStatus("1");
        paymentInfo.setUpdateTime(new Date());
        //设置回调的时间
        paymentInfo.setCallbackTime(new Date());
        //设置回调的内容
        paymentInfo.setCallbackContent(paramMap.toString());
        //设置交易号
        paymentInfo.setTradeNo(paramMap.get("trade_no"));
        //调用带条件的更新方法
        paymentInfoService.update(paymentInfo,new LambdaUpdateWrapper<PaymentInfo>().eq(PaymentInfo::getOrderNo,orderNo));
        //更新订单的状态，①同步方式：远程调用 ②异步方式：使用消息队列
        rabbitService.sendMessage(MqConst.EXCHANGE_PAYMENT_PAY, MqConst.ROUTING_PAYMENT_PAY, orderNo);

    }
}
