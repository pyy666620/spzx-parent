package com.spzx.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.spzx.order.api.domain.OrderInfo;
import com.spzx.order.domain.vo.OrderForm;
import com.spzx.order.domain.vo.TradeVo;

import java.util.List;

public interface IOrderInfoService extends IService<OrderInfo> {
    /**
     * 查询订单列表
     *
     * @param orderInfo 订单
     * @return 订单集合
     */
    public List<OrderInfo> selectOrderInfoList(OrderInfo orderInfo);

    /**
     * 查询订单
     *
     * @param id 订单主键
     * @return 订单
     */
    public OrderInfo selectOrderInfoById(Long id);

    /**
     * 去结算的方法
     * @return
     */
    TradeVo trade();

    /**
     * 立即购买
     * @param skuId
     * @return
     */
    TradeVo buy(Long skuId);

    /**
     提交订单
     * @param orderForm
     * @return
     */
    Long submitOrder(OrderForm orderForm);

    /**
     * 分页获取我的订单
     * @param orderStatus
     * @return
     */
    List<OrderInfo> getMyOrderInfoList(String orderStatus);

    /**
     * 取消订单的方法
     * @param orderId
     * @param flag  取消订单的标识 1：用户 2：系统
     */
    void cancelOrder(Long orderId,Integer flag);

    /**
     * 根据订单号查询订单详情
     * @param orderNo
     * @return
     */
    OrderInfo getOrderInfoByOrderNo(String orderNo);
}