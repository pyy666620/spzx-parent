package com.spzx.cart.api.factory;

import com.spzx.cart.api.RemoteCartService;
import com.spzx.cart.api.domain.CartInfo;
import com.spzx.common.core.constant.ServiceNameConstants;
import com.spzx.common.core.domain.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 防止一个微服务挂了，把整个系统拖垮（避免雪崩效应）
 */
@Component
public class RemoteCartFallbackFactory implements FallbackFactory<RemoteCartService> {

    private Logger log = LoggerFactory.getLogger(RemoteCartFallbackFactory.class);

    @Override
    public RemoteCartService create(Throwable throwable) {

        log.error("远程调用服务【{}】出现降级", ServiceNameConstants.CART_SERVICE);

        return new RemoteCartService() {

            @Override
            public R<List<CartInfo>> getCheckedCartInfo(String source) {
                return R.fail("购物车远程调用获取选中的购物项失败，失败的原因为：" + throwable.getMessage());
            }

            @Override
            public R<Void> clearCheckedCartInfo(String source) {
                return R.fail("购物车远程调用清除购物项失败，失败的原因为：" + throwable.getMessage());
            }
        };
    }
}
