package com.spzx.payment.controller;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.spzx.common.core.web.controller.BaseController;
import com.spzx.common.core.web.domain.AjaxResult;
import com.spzx.common.security.annotation.RequiresLogin;
import com.spzx.payment.configure.AlipayConfig;
import com.spzx.payment.service.IAlipayService;
import com.spzx.payment.service.IPaymentInfoService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/alipay")
public class AlipayController extends BaseController {

    @Autowired
    private IAlipayService alipayService;

    @Operation(summary = "调用支付宝获取打开支付宝表单的方法")
    @GetMapping("/submitAlipay/{orderNo}")
    public AjaxResult submitAlipay(@PathVariable String orderNo){
        //调用IAliPayService中调用支付宝的方法
        String form = alipayService.submitAlipay(orderNo);
        return success(form);
    }

    @Operation(summary = "支付宝回调通知支付结果的方法")
    @PostMapping("/callback/notify")
    public String notify(@RequestParam Map<String,String> paramMap){
        try {
            // 验签
            boolean flag = AlipaySignature.rsaCheckV1(paramMap, AlipayConfig.alipay_public_key, AlipayConfig.charset, AlipayConfig.sign_type);
            //成功
            if (flag) {
                //验签成功，获取交易状态
                String tradeStatus = paramMap.get("trade_status");
                //判断交易状态是否是成功或完成
                if("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)){
                    //调用IAliPayService中更新支付状态的方法
                    alipayService.updatePaymentStatus(paramMap);
                    return "success";
                }
            }
        } catch (AlipayApiException e) {
            throw new RuntimeException(e);
        }
        return "fail";
    }
}