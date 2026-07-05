# spzx-mall - 分布式微服务电商平台

## 📖 项目简介
基于 Spring Cloud Alibaba 架构的 B2C 电商系统，实现了商品、购物车、订单、支付、会员等核心业务模块。项目采用前后端分离（UniApp H5）模式，后端基于 Spring Boot 3 + JDK 17 构建，并集成了消息队列、分布式缓存、容器化部署等主流技术。

> 本项目为个人学习与实战项目，旨在深入理解微服务架构与高并发场景下的解决方案。

## 🛠 技术栈
- **基础框架**：Spring Boot 3、Spring Cloud Alibaba 2022、JDK 17
- **注册/配置中心**：Nacos
- **网关**：Spring Cloud Gateway (集成 Sentinel 限流熔断)
- **持久层**：MyBatis-Plus + MySQL 8.0
- **缓存**：Redis (分布式锁、热点数据缓存)
- **消息队列**：RabbitMQ (延迟消息、可靠投递)
- **容器化**：Docker + Docker Compose
- **文件存储**：MinIO / 本地文件
- **前端**：UniApp (H5)

## 🏗 核心架构 (微服务拆分)
- `spzx-gateway`：网关服务，统一鉴权、限流、路由转发。
- `spzx-auth`：认证中心，基于 JWT + Redis 实现 OAuth2 登录。
- `spzx-product`：商品服务，负责商品、SKU、库存、分类管理。
- `spzx-order`：订单服务，负责订单创建、状态流转、取消订单。
- `spzx-cart`：购物车服务，基于 Redis Hash 存储购物车数据。
- `spzx-payment`：支付服务，对接支付宝支付，处理支付回调。
- `spzx-user`：会员服务，用户注册、地址管理等。
- `spzx-file`：文件服务，支持 MinIO 文件上传。

## 🚀 核心功能亮点
1. **分布式锁与缓存一致性**：商品详情接口采用 Redis 分布式锁防止缓存击穿（`setIfAbsent` + Lua 脚本），使用延迟双删策略保证缓存与数据库双写一致。
2. **异步解耦与消息可靠性**：订单提交后，通过 RabbitMQ 延迟消息队列实现 30 分钟未支付订单自动取消。生产者开启 Confirm 确认机制，消费者手动 ACK，保证消息不丢失。
3. **高并发性能优化**：商品详情页采用 `CompletableFuture` 异步编排并发调用多个微服务接口（商品、SKU、库存、规格），将接口响应时间从 800ms 优化至 200ms 左右。
4. **库存并发扣减**：底层数据库行锁 `for update` + 乐观锁机制解决高并发下单时库存超卖问题。

## 🐳 快速启动 (Local)
```bash
# 1. 拉取代码
git clone https://github.com/[你的用户名]/spzx-mall.git

# 2. 启动基础环境 (需先安装 Docker)
docker-compose -f docker/docker-compose.yml up -d

# 3. 依次启动微服务模块
# 顺序: spzx-auth -> spzx-gateway -> spzx-product -> spzx-order ...