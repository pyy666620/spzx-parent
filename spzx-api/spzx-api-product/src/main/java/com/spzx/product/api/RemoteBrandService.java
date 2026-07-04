package com.spzx.product.api;

import com.spzx.common.core.constant.SecurityConstants;
import com.spzx.common.core.constant.ServiceNameConstants;
import com.spzx.common.core.domain.R;
import com.spzx.product.api.domain.Brand;
import com.spzx.product.api.factory.RemoteBrandFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(contextId = "remoteBrandService", value = ServiceNameConstants.PRODUCT_SERVICE, fallbackFactory = RemoteBrandFallbackFactory.class)
//三个参数：contextId、value、fallbackFactory，分别表示Feign客户端的名称、服务提供者的名称、降级处理的类
public interface RemoteBrandService {

    @GetMapping("/brand/getBrandAllList")
    public R<List<Brand>> getBrandAllList(@RequestHeader(SecurityConstants.FROM_SOURCE) String source);
}