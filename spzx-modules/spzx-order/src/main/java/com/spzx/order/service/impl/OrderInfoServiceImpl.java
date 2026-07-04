package com.spzx.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.PageHelper;
import com.spzx.cart.api.RemoteCartService;
import com.spzx.cart.api.domain.CartInfo;
import com.spzx.common.core.constant.SecurityConstants;
import com.spzx.common.core.context.SecurityContextHolder;
import com.spzx.common.core.domain.R;
import com.spzx.common.core.exception.ServiceException;
import com.spzx.common.rabbit.constant.MqConst;
import com.spzx.common.rabbit.service.RabbitService;
import com.spzx.order.api.domain.OrderInfo;
import com.spzx.order.api.domain.OrderItem;
import com.spzx.order.domain.OrderLog;
import com.spzx.order.domain.vo.OrderForm;
import com.spzx.order.domain.vo.TradeVo;
import com.spzx.order.mapper.OrderInfoMapper;
import com.spzx.order.mapper.OrderItemMapper;
import com.spzx.order.mapper.OrderLogMapper;
import com.spzx.order.service.IOrderInfoService;
import com.spzx.order.service.IOrderItemService;
import com.spzx.product.api.RemoteProductService;
import com.spzx.product.api.domain.ProductSku;
import com.spzx.product.api.domain.vo.SkuLockVo;
import com.spzx.user.api.RemoteUserAddressService;
import com.spzx.user.domain.UserAddress;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Transactional
@Service
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements IOrderInfoService {
    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private OrderItemMapper orderItemMapper ;

    @Autowired
    private IOrderItemService orderItemService;

    @Autowired
    private OrderLogMapper orderLogMapper;

    @Autowired
    private RemoteCartService remoteCartService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RemoteProductService remoteProductService;

    @Autowired
    private RemoteUserAddressService remoteUserAddressService;

    @Autowired
    RabbitService rabbitService; //来自于公共模块：spzx-common-rabbit

    /**
     * 查询订单列表
     *
     * @param orderInfo 订单
     * @return 订单
     */
    @Override
    public List<OrderInfo> selectOrderInfoList(OrderInfo orderInfo) {
        return orderInfoMapper.selectOrderInfoList(orderInfo);
    }

    /**
     * 查询订单
     * @param id 订单主键
     * @return 订单
     */
    @Override
    public OrderInfo selectOrderInfoById(Long id) {
        OrderInfo orderInfo = orderInfoMapper.selectById(id);
        List<OrderItem> orderItemList = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));
        orderInfo.setOrderItemList(orderItemList);
        return orderInfo;
    }

    /**
     * 去结算
     *
     * @return
     */
    @Override
    public TradeVo trade() {
        //创建TradeVO对象
        TradeVo tradeVo = new TradeVo();
        //远程调用购物车微服务获取选中的购物项
        R<List<CartInfo>> checkedCartInfoR = remoteCartService.getCheckedCartInfo(SecurityConstants.INNER);
        if (R.FAIL == checkedCartInfoR.getCode()) {
            throw new ServiceException(checkedCartInfoR.getMsg());
        }
        //获取选中的购物项
        List<CartInfo> cartInfoList = checkedCartInfoR.getData();

        List<OrderItem> orderItemList = new ArrayList<>();

        //如果不为空
        if (!CollectionUtils.isEmpty(cartInfoList)) {
            //将List<CatInfo>转换为List<OrderItem>
            orderItemList = cartInfoList.stream().map(cartInfo -> {
                //创建OrderItem对象
                OrderItem orderItem = new OrderItem();
                //使用BeanUtils工具类复制属性值
                BeanUtils.copyProperties(cartInfo, orderItem);
                return orderItem;
            }).collect(Collectors.toList());
        }
        //设置总金额
        BigDecimal totalAmount = new BigDecimal(0);
        //遍历所有的订单项
        for (OrderItem orderItem : orderItemList) {
            //获取当前订单项的价格
            BigDecimal skuPrice = orderItem.getSkuPrice();
            //获取当前订单项购买的数量
            Integer skuNum = orderItem.getSkuNum();
            //计算金额小计
            BigDecimal amount = skuPrice.multiply(new BigDecimal(skuNum));
            //将金额小计添加奥总金额中
            totalAmount = totalAmount.add(amount);
        }
        //设置订单项
        tradeVo.setOrderItemList(orderItemList);
        //设置总金额
        tradeVo.setTotalAmount(totalAmount);
        String tradeNo = getTradeNo();
        //设置交易号
        tradeVo.setTradeNo(tradeNo);
        return tradeVo;
    }

    //获取交易号的方法
    private String getTradeNo() {
        //UUID随机生成字符串 - > 防止重复提交
        String tradeNo = UUID.randomUUID().toString().replaceAll("-", "");
        //将交易号保存到Redis中，设置五分钟有效
        redisTemplate.opsForValue().set("user:trade:" + tradeNo, tradeNo, 5, TimeUnit.MINUTES);
        return tradeNo;
    }

    @Override
    public TradeVo buy(Long skuId) {
        //根据skuId远程调用获取商品sku信息
        R<ProductSku> productSkuR = remoteProductService.getProductSku(skuId, SecurityConstants.INNER);
        if (R.FAIL == productSkuR.getCode()) {
            throw new ServiceException(productSkuR.getMsg());
        }
        //获取商品sku信息
        ProductSku productSku = productSkuR.getData();
        //OrderItem对象
        OrderItem orderItem = new OrderItem();
        //复制属性值
        BeanUtils.copyProperties(productSku, orderItem);
        //设置商品skuId
        orderItem.setSkuId(skuId);
        //设置订单的id为null-------保证数据库在插入记录时，能正确使用自增主键，防止主键冲突或数据覆盖。
        orderItem.setId(null);
        //设置商品的数量以及价格
        orderItem.setSkuPrice(productSku.getSalePrice());
        orderItem.setSkuNum(1);
        //创建一个List<OrderItem>
        List<OrderItem> orderItemList = Arrays.asList(orderItem);
        //创建TradeVO对象
        TradeVo tradeVo = new TradeVo();
        //获取交易号
        String tradeNo = getTradeNo();
        //设置属性值
        tradeVo.setTradeNo(tradeNo);
        tradeVo.setOrderItemList(orderItemList);
        tradeVo.setTotalAmount(productSku.getSalePrice());
        return tradeVo;
    }

    @Override
    public Long submitOrder(OrderForm orderForm) {
        //1.防止重复提交表单
        //获取交易号
        String tradeNo = orderForm.getTradeNo();
        //获取Redis中存储的交易号
        String redisTradeNo = (String) redisTemplate.opsForValue().get("user:trade:" + tradeNo);
        //判断
        if (!tradeNo.equals(redisTradeNo)) {
            // 下单超时或重复提交表单
            throw new ServiceException("下单超时或重复提交表单");
        } else {
            //删除Redis中的交易号
            redisTemplate.delete("user:trade:" + tradeNo);
        }
        //获取用户提交的数据
        //获取收货地址id
        Long userAddressId = orderForm.getUserAddressId();
        //获取运费
        BigDecimal feightFee = orderForm.getFeightFee();
        //获取备注
        String remark = orderForm.getRemark();

        //1.插入订单
        //创建OrderInfo对象
        OrderInfo orderInfo = new OrderInfo();
        //设置表单提交的数据
        orderInfo.setFeightFee(feightFee);
        orderInfo.setRemark(remark);
        //设置订单状态
        orderInfo.setOrderStatus(0);
        //获取userId和用户名
        Long userId = SecurityContextHolder.getUserId();
        String userName = SecurityContextHolder.getUserName();
        //设置userId
        orderInfo.setUserId(userId);
        orderInfo.setNickName(userName);
        //设置创建者
        orderInfo.setCreateBy(userName);
        //使用UUID随机生成一个字符串作为订单号
        String orderNo = UUID.randomUUID().toString().replaceAll("-", "");
        //设置交易号
        orderInfo.setOrderNo(orderNo);
        //设置订单的总金额
        BigDecimal totalAmount = new BigDecimal(0);
        //获取所有的订单项
        List<OrderItem> orderItemList = orderForm.getOrderItemList();
        //创建StringBufer对象
        StringBuffer sb = new StringBuffer();
        //遍历
        for (OrderItem orderItem : orderItemList) {
            //获取当前订单项的价格
            BigDecimal skuPrice = orderItem.getSkuPrice();
            //获取当前订单项购买的数量
            Integer skuNum = orderItem.getSkuNum();
            //查询实时价格
            //获取商品skuId
            Long skuId = orderItem.getSkuId();
            //获取实时价格
            R<ProductSku> productSkuR = remoteProductService.getProductSku(skuId, SecurityConstants.INNER);
            if(R.FAIL == productSkuR.getCode()){
                throw new ServiceException(productSkuR.getMsg());
            }
            //校验库存并且减库存
            R<String> stringR = remoteProductService.checkStockAndSubStock(skuId, skuNum, SecurityConstants.INNER);
            if(R.FAIL == stringR.getCode()){
                throw new ServiceException(stringR.getMsg());
            }
            // 获取响应的消息
            String msg = stringR.getData();
            if(StringUtils.hasLength(msg)){
                //库存不足
                throw new ServiceException(msg);
            }
            //获取商品Sku
            ProductSku productSku = productSkuR.getData();
            //获取商品的售价
            BigDecimal salePrice = productSku.getSalePrice();
            //使用实时价格减去订单项中的商品价格
            BigDecimal diff = salePrice.subtract(skuPrice);
            if(diff.intValue() > 0){
                //商品涨价
                sb.append(orderItem.getSkuName()+"涨价"+diff.doubleValue()+"元,请去结算页重新下单");
            }
            if(diff.intValue() < 0){
                //商品降价
                sb.append(orderItem.getSkuName()+"降价"+diff.doubleValue()+"元,请去结算页重新下单");
            }
            //计算金额小计
            BigDecimal amount = skuPrice.multiply(new BigDecimal(skuNum));
            //添加到总金额中
            totalAmount = totalAmount.add(amount);
        }

        //判断sb是否为空
        if(StringUtils.hasLength(sb.toString())){
            //抛出异常
            throw new ServiceException(sb.toString());
        }

        //设置总金额
        orderInfo.setTotalAmount(totalAmount);
        //设置原始金额
        orderInfo.setOriginalTotalAmount(totalAmount);

        //根据用户收货地址id远程调用用户微服务获取用户收货地址
        R<UserAddress> userAddressR = remoteUserAddressService.getUserAddress(userAddressId, SecurityConstants.INNER);
        if(R.FAIL == userAddressR.getCode()){
            throw new ServiceException(userAddressR.getMsg());
        }
        //获取用户收货地址
        UserAddress userAddress = userAddressR.getData();
        //设置用户收货地址信息
        orderInfo.setReceiverName(userAddress.getName());
        orderInfo.setReceiverPhone(userAddress.getPhone());
        orderInfo.setReceiverTagName(userAddress.getTagName());
        orderInfo.setReceiverProvince(userAddress.getProvinceCode());
        orderInfo.setReceiverCity(userAddress.getCityCode());
        orderInfo.setReceiverDistrict(userAddress.getDistrictCode());
        orderInfo.setReceiverAddress(userAddress.getFullAddress());
        //向数据库中插入订单
        baseMapper.insert(orderInfo);

        //获取订单id
        Long orderId = orderInfo.getId();
        //遍历所有的订单项，给订单项设置订单id
        orderItemList.forEach(orderItem -> {
            //设置订单id
            orderItem.setOrderId(orderId);
            orderItem.setRemark(remark);
        });
        //2.批量插入订单项
        orderItemService.saveBatch(orderItemList);
        //3.插入订单日志
        //创建OrderLog对象
        OrderLog orderLog = new OrderLog();
        //设置属性值
        orderLog.setOrderId(orderId);
        orderLog.setRemark(remark);
        orderLog.setOperateUser("用户");
        orderLog.setCreateBy(userName);
        orderLog.setNote("提交订单");
        orderLog.setProcessStatus(0);
        //插入订单日志
        orderLogMapper.insert(orderLog);

        //清空购物车中选中的购物项
        remoteCartService.clearCheckedCartInfo(SecurityConstants.INNER);

        //T发送延迟消息，30min订单未支付自动取消订单(测试阶段1min进行测试)
        rabbitService.sendDelayedMessage(MqConst.EXCHANGE_CANCEL_ORDER,MqConst.ROUTING_CANCEL_ORDER,orderId);
        return orderId;
    }

    @Override
    public List<OrderInfo> getMyOrderInfoList(String orderStatus) {
        //获取用户id
        Long userId = SecurityContextHolder.getUserId();
        //LambdaQueryWrapper
        LambdaQueryWrapper<OrderInfo> orderInfoLambdaQueryWrapper = new LambdaQueryWrapper<>();
        //封装查询条件
        orderInfoLambdaQueryWrapper.eq(OrderInfo::getUserId, userId).eq(StringUtils.hasLength(orderStatus), OrderInfo::getOrderStatus , orderStatus);
        //获取满足条件的我的订单
        List<OrderInfo> orderInfoList = baseMapper.selectList(orderInfoLambdaQueryWrapper);
        //如果为空，直接跳过查子表，不用执行任何 SQL
        if (CollectionUtils.isEmpty(orderInfoList)) {
            return new ArrayList<>();
        }
        //优化N+1次漏洞
        //1.拿到所有的订单id
        List<Long> orderIds  = orderInfoList.stream().map(OrderInfo::getId).collect(Collectors.toList());
        //2.根据所有的订单id查询所有的订单项
        List<OrderItem> allOrderItems = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds));
        //3.得到一个Map集合
        Map<Long, List<OrderItem>> orderItemMap  = allOrderItems.stream().collect(Collectors.groupingBy(OrderItem::getOrderId));
        //设置属性值
        orderInfoList.forEach(orderInfo -> {
            orderInfo.setOrderItemList(orderItemMap.getOrDefault(orderInfo.getId(), new ArrayList<>()));
        });
        //原有代码:
        //遍历所有的订单，给订单设置订单项
//        orderInfoList.forEach(orderInfo -> {
//           //给订单id查询所有的订单项
//            List<OrderItem> orderItemsList = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderInfo.getId()));
//            orderInfo.setOrderItemList(orderItemsList);
//        });
        return orderInfoList;
    }

    @Override
    public void cancelOrder(Long orderId,Integer flag) {
        //创建OrderInfo对象
        OrderInfo orderInfo = new OrderInfo();
        //设置订单状态
        orderInfo.setOrderStatus(-1);
        orderInfo.setCancelTime(new Date());
        String cancelReason = flag==1?"用户取消订单":"系统取消订单";
        orderInfo.setCancelReason(cancelReason);
        //更新订单，eq OrderStatus很重要，只更新未支付的订单-----防止用户频繁点击更新多次
        int count = baseMapper.update(orderInfo,new LambdaUpdateWrapper<OrderInfo>().eq(OrderInfo::getId, orderId).eq(OrderInfo::getOrderStatus, 0));
        if(count == 1){
            //保存订单日志
            OrderLog orderLog = new OrderLog();
            orderLog.setOrderId(orderId);
            orderLog.setNote(cancelReason);
            orderLog.setOperateUser(flag==1?"用户":"系统");
            orderLog.setProcessStatus(-1);
            orderLogMapper.insert(orderLog);
        }
        //还原库存和销量
        //根据订单id获取所有的订单项
        List<OrderItem> orderItems = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        //OrderItem是订单模块的实体，而“还原库存”是属于商品模块的业务，所以需要将OrderItem转换为SkuLockVo
        List<SkuLockVo> skuLockVoList = orderItems.stream().map(orderItem -> {
            //创建SkuLockVo对象
            SkuLockVo skuLockVo = new SkuLockVo();
            skuLockVo.setSkuId(orderItem.getSkuId());
            skuLockVo.setSkuNum(orderItem.getSkuNum());
            return skuLockVo;
        }).collect(Collectors.toList());
        //远程调用商品微服务加库存和减销量
        R<String> stringR = remoteProductService.addStockAndSubSaleNum(skuLockVoList, SecurityConstants.INNER);
        if(R.FAIL == stringR.getCode()){
            throw new ServiceException(stringR.getMsg());
        }
        String data = stringR.getData();
        if(StringUtils.hasLength(data)){
            throw new ServiceException(data);
        }
    }

    @Override
    public OrderInfo getOrderInfoByOrderNo(String orderNo) {
        OrderInfo orderInfo = baseMapper.selectOne(new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getOrderNo, orderNo));
        List<OrderItem> orderItems = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderInfo.getId()));
        orderInfo.setOrderItemList(orderItems);
        return orderInfo;
    }


}