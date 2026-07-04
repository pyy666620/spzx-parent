package com.spzx.channel.service.impl;

import com.alibaba.fastjson2.JSON;
import com.spzx.channel.domain.ItemVo;
import com.spzx.channel.service.IItemService;
import com.spzx.common.core.constant.SecurityConstants;
import com.spzx.common.core.domain.R;
import com.spzx.common.core.exception.ServiceException;
import com.spzx.product.api.RemoteProductService;
import com.spzx.product.api.domain.Product;
import com.spzx.product.api.domain.ProductDetails;
import com.spzx.product.api.domain.ProductSku;
import com.spzx.product.api.domain.vo.SkuPrice;
import com.spzx.product.api.domain.vo.SkuStockVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadPoolExecutor;

@Service
@Slf4j
public class ItemServiceImpl implements IItemService {

    @Autowired
    private RemoteProductService remoteProductService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    @Override
    public ItemVo item(Long skuId) throws Exception {

        //判断该商品的Sku是否存在（skuId在Redis的位图中是否存在）
        Boolean flag = redisTemplate.opsForValue().getBit("product:productSku:data",skuId);
        if(!flag){
            //不存在
            throw new ServiceException("您访问的id" + skuId + "商品不存在");
        }
        //使用try-catch的原因：
        // CompletableFuture 规范中，要你在异步任务里（比如 supplyAsync 或 thenApplyAsync 的 Lambda 表达式里）抛出了异常
        // 这些异常并不会直接传给主线程，而是会被统一包装成一个 java.util.concurrent.CompletionException 扔出来。
        try {
            // 提前把请求头提取出来（预防后续线程上下文丢失）
            String innerSource = SecurityConstants.INNER;

            // ================== 第一层：独立任务并行发起 ==================
            CompletableFuture<ProductSku> task1 = CompletableFuture.supplyAsync(() -> {
                R<ProductSku> result = remoteProductService.getProductSku(skuId, innerSource);
                if (R.FAIL == result.getCode()) throw new ServiceException(result.getMsg());
                return result.getData();
            }, threadPoolExecutor);

            //获取商品最新价格
            CompletableFuture<SkuPrice> task3 = CompletableFuture.supplyAsync(() -> {
                R<SkuPrice> result = remoteProductService.getSkuPrice(skuId, innerSource);
                if (R.FAIL == result.getCode()) throw new ServiceException(result.getMsg());
                return result.getData();
            }, threadPoolExecutor);

            //获取商品库存信息
            CompletableFuture<SkuStockVo> task6 = CompletableFuture.supplyAsync(() -> {
                R<SkuStockVo> result = remoteProductService.getSkuStock(skuId, innerSource);
                if (R.FAIL == result.getCode()) throw new ServiceException(result.getMsg());
                return result.getData();
            }, threadPoolExecutor);

            // ================== 第二层：依赖 task1 的后续串行任务 ==================
            //获取商品信息
            //如果只写thenApply，则在 task1 原本用的那个线程上继续执行。如果 task2 的远程调用耗时很长，它会一直霸占那个线程，容易导致线程池耗尽。
            //thenApplyAsync:把 task2 重新扔进你配置的线程池里，让池子里下一个空闲的线程去执行
            CompletableFuture<Product> task2 = task1.thenApplyAsync(productSku -> {
                R<Product> result = remoteProductService.getProduct(productSku.getProductId(), innerSource);
                if (R.FAIL == result.getCode()) throw new ServiceException(result.getMsg());
                return result.getData();
            }, threadPoolExecutor);

            //获取商品详情
            CompletableFuture<ProductDetails> task4 = task1.thenApplyAsync(productSku -> {
                R<ProductDetails> result = remoteProductService.getProductDetails(productSku.getProductId(), innerSource);
                if (R.FAIL == result.getCode()) throw new ServiceException(result.getMsg());
                return result.getData();
            }, threadPoolExecutor);

            //获取商品规格信息
            CompletableFuture<Map<String, Long>> task5 = task1.thenApplyAsync(productSku -> {
                R<Map<String, Long>> result = remoteProductService.getSkuSpecValue(productSku.getProductId(), innerSource);
                if (R.FAIL == result.getCode()) throw new ServiceException(result.getMsg());
                return result.getData();
            }, threadPoolExecutor);

            // ================== 第三层：等待所有任务完成 ==================
            CompletableFuture.allOf(task1, task2, task3, task4, task5, task6).join();

            ProductSku productSku = task1.join();
            Product product = task2.join();
            SkuPrice skuPrice = task3.join();
            ProductDetails productDetails = task4.join();
            Map<String, Long> skuSpecValueMap = task5.join();
            SkuStockVo skuStockVo = task6.join();

            // ================== 组装返回数据 ==================
            ItemVo itemVo = new ItemVo();
            itemVo.setProductSku(productSku);
            itemVo.setProduct(product);
            itemVo.setSliderUrlList(Arrays.asList(product.getSliderUrls().split(",")));
            itemVo.setSpecValueList(JSON.parseArray(product.getSpecValue()));
            itemVo.setSkuPrice(skuPrice);
            itemVo.setDetailsImageUrlList(Arrays.asList(productDetails.getImageUrls().split(",")));
            itemVo.setSkuSpecValueMap(skuSpecValueMap);
            itemVo.setSkuStockVo(skuStockVo);
            productSku.setStockNum(skuStockVo.getAvailableNum());

            return itemVo;
        } catch (CompletionException e) {
            // 1. 解包装：用 e.getCause() 拿到被包裹起来的真正异常
            Throwable cause = e.getCause();
            // 2. 还原业务错误：如果是我们项目中定义的 ServiceException，就原样抛出去
            if (cause instanceof ServiceException) {
                throw (ServiceException) cause;
            }
            // 3. 全局兜底：如果是其他错误（比如数据库断连、JSON解析错误、空指针），统一报一个友好的信息
            throw new ServiceException("查询商品详情失败：" + cause.getMessage());        }
    }
}