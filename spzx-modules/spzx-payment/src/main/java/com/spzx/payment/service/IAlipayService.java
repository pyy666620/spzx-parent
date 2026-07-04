package com.spzx.payment.service;

import java.util.Map;

public interface IAlipayService {

    /**
     * 调用支付宝获取支付表单
     * @param orderNo
     * @return
     */
    String submitAlipay(String orderNo);

    /**
     * 更新支付状态
     * @param paramMap
     */
    void updatePaymentStatus(Map<String, String> paramMap);
}
