package com.spzx.cart.service;

import com.spzx.cart.api.domain.CartInfo;

import java.util.List;

public interface ICartService {

    //添加购物车
    void add2Cart(Long skuId, Integer num);

    //展示购物车
    List<CartInfo> cartList();

    //更新购物项的选中状态
    void updateCartIntoStatus(Long skuId, Integer status);

    //购物车中的全选和全不选
    void allCheckCart(Integer status);

    //删除购物项
    void deleteCart(Long skuId);

    //清空购物车
    void clearCart();

    //获取选中的购物项的方法
    List<CartInfo> getCheckedCartInfo();

    //清除已选中的购物项的方法
    void clearCheckedCartInfo();
}
