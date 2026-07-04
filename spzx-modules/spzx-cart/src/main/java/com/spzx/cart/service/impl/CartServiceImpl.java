package com.spzx.cart.service.impl;

import com.spzx.cart.api.domain.CartInfo;
import com.spzx.cart.service.ICartService;
import com.spzx.common.core.constant.SecurityConstants;
import com.spzx.common.core.context.SecurityContextHolder;
import com.spzx.common.core.domain.R;
import com.spzx.common.core.exception.ServiceException;
import com.spzx.product.api.RemoteProductService;
import com.spzx.product.api.domain.ProductSku;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CartServiceImpl implements ICartService {

    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    private RemoteProductService remoteProductService;


    //获取Redis中的购物车的Key的方法
    public String getCartKey() {
        Long userId = SecurityContextHolder.getUserId();
        return "user:cart:" + userId;
    }

    /**
     * 添加购物车
     *
     * @param skuId
     * @param num
     */
    @Override
    //注意：购物车里有许多商品，所以使用Hash结构到Redis中，Hash的key是购物车的key，field是商品的skuId，value是购物项对象
    public void add2Cart(Long skuId, Integer num) {
        //获取购物车的Key
        String cartKey = getCartKey();
        //从给购物车中获取当前商品对应的购物项：如果有说明之前加到过购物车里面可以直接把数量相加即可
        CartInfo cartInfo = (CartInfo) redisTemplate.opsForHash().get(cartKey, String.valueOf(skuId));
        //如果不为null：之前已经添加过，只需更新数量
        if (cartInfo != null) {
            //限制单个商品购物车最大数量为 99 件
            cartInfo.setSkuNum(Math.min((cartInfo.getSkuNum() + num), 99));
        } else { // 第一次添加购物车
            cartInfo = new CartInfo();
            //设置商品信息
            cartInfo.setSkuNum(1);
            //设置商品skuId
            cartInfo.setSkuId(skuId);
            //添加时间
            cartInfo.setCreateTime(new Date());
            //添加购物车的用户的名字
            cartInfo.setCreateBy(SecurityContextHolder.getUserName());
            // 远程调用商品微服务
            R<ProductSku> productSku = remoteProductService.getProductSku(skuId, SecurityConstants.INNER);
            //如果有异常
            if (R.FAIL == productSku.getCode()) {
                throw new ServiceException(productSku.getMsg());
            }
            //获取商品Sku信息
            ProductSku productSku1 = productSku.getData();
            //给购物项设置商品Sku信息
            cartInfo.setSkuName(productSku1.getSkuName());
                cartInfo.setThumbImg(productSku1.getThumbImg());
            //设置放入购物车时的价格
            cartInfo.setCartPrice(productSku1.getSalePrice());
            //设置商品Sku的实时价格
            cartInfo.setSkuPrice(productSku1.getSalePrice());
        }
        //将购物车放到Redis当中
        redisTemplate.opsForHash().put(cartKey, String.valueOf(skuId), cartInfo);

    }

    @Override
    public List<CartInfo> cartList() {
        String cartKey = getCartKey();
        //获取购物车中的所有购物项
        List<CartInfo> cartInfoList = redisTemplate.opsForHash().values(cartKey);
        // 遍历所有的购物项
        cartInfoList.forEach(cartInfo -> {
            // 根据SkuId远程调用商品微服务获取最新的商品Sku售价
            R<ProductSku> productSkuR = remoteProductService.getProductSku(cartInfo.getSkuId(), SecurityConstants.INNER); // 这里被截断了，推测是TOKEN等常量

            if (R.FAIL == productSkuR.getCode()) {
                throw new ServiceException(productSkuR.getMsg());
            }
            //获取商品Sku
            ProductSku productSku = productSkuR.getData();
            //获取最新的售价
            BigDecimal salePrice = productSku.getSalePrice();
            //更新CartInfo中的实时价格
            cartInfo.setSkuPrice(salePrice);
            //将更新过实时价格的购物项放入Redis中
            redisTemplate.opsForHash().put(cartKey, String.valueOf(cartInfo.getSkuId()), cartInfo);
        });
        return cartInfoList;
    }


    @Override
    public void updateCartIntoStatus(Long skuId, Integer status) {
        // 获取购物车的key
        String cartKey = getCartKey();
        // 获取购物车中的当前的购物项
        CartInfo cartInfo = (CartInfo) redisTemplate.opsForHash().get(cartKey, String.valueOf(skuId));
        // 设置购物项的选中状态
        cartInfo.setIsChecked(status);
        // 重新放回Redis中
        redisTemplate.opsForHash().put(cartKey, String.valueOf(skuId), cartInfo);
    }

    @Override
    public void allCheckCart(Integer status) {
        // 获取购物车的key
        String cartKey = getCartKey();
        //获取所有的购物项
        List<CartInfo> cartInfoList = redisTemplate.opsForHash().values(cartKey);
        //重置每一个status
        cartInfoList.forEach(cartInfo -> {
            cartInfo.setIsChecked(status);
            //重新放入Redis
            redisTemplate.opsForHash().put(cartKey, String.valueOf(cartInfo.getSkuId()), cartInfo);
        });

    }

    @Override
    public void deleteCart(Long skuId) {
        //获取Key
        String cartKey = getCartKey();
        //删除购物项
        redisTemplate.opsForHash().delete(cartKey, String.valueOf(skuId));
    }

    @Override
    public void clearCart() {
        //清空购物车
        redisTemplate.delete(getCartKey());
    }


    @Override
    public List<CartInfo> getCheckedCartInfo() {
        // 获取购物车的key
        String cartKey = getCartKey();
        // 获取所有的购物项
        List<CartInfo> cartInfoList = redisTemplate.opsForHash().values(cartKey);
        // 获取选中的购物项---filter: 过滤出选中的购物项
        List<CartInfo> checkedCartInfoList = cartInfoList.stream().filter(cartInfo -> cartInfo.getIsChecked() == 1).collect(Collectors.toList());
        return checkedCartInfoList;
    }

    @Override
    public void clearCheckedCartInfo() {
        // 获取购物车的key
        String cartKey = getCartKey();
        // 获取所有选中的购物项
        List<CartInfo> checkedCartInfo = getCheckedCartInfo();
        //从redis中删除选中的购物项
        checkedCartInfo.forEach(cartInfo -> {
            redisTemplate.opsForHash().delete(cartKey, String.valueOf(cartInfo.getSkuId()));
        });
    }


}