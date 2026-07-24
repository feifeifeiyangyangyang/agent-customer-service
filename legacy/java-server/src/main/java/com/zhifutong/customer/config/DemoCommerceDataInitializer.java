package com.zhifutong.customer.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhifutong.customer.domain.OrderStatus;
import com.zhifutong.customer.domain.ProductSaleStatus;
import com.zhifutong.customer.domain.ShipmentStatus;
import com.zhifutong.customer.domain.UserRole;
import com.zhifutong.customer.entity.CustomerOrder;
import com.zhifutong.customer.entity.ProductCatalog;
import com.zhifutong.customer.entity.ShipmentEvent;
import com.zhifutong.customer.entity.UserAccount;
import com.zhifutong.customer.mapper.CustomerOrderMapper;
import com.zhifutong.customer.mapper.ProductCatalogMapper;
import com.zhifutong.customer.mapper.ShipmentEventMapper;
import com.zhifutong.customer.mapper.UserAccountMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class DemoCommerceDataInitializer implements ApplicationRunner {
    private final ProductCatalogMapper productMapper;
    private final CustomerOrderMapper orderMapper;
    private final ShipmentEventMapper shipmentMapper;
    private final UserAccountMapper userMapper;

    public DemoCommerceDataInitializer(ProductCatalogMapper productMapper, CustomerOrderMapper orderMapper,
                                       ShipmentEventMapper shipmentMapper, UserAccountMapper userMapper) {
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.shipmentMapper = shipmentMapper;
        this.userMapper = userMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (productMapper.selectCount(null) == 0) {
            createProducts();
        }
        UserAccount customer = userMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getRole, UserRole.CUSTOMER)
                .orderByAsc(UserAccount::getId)
                .last("LIMIT 1"));
        if (customer != null) {
            createOrders(customer.getId());
        }
    }

    private void createProducts() {
        LocalDateTime now = LocalDateTime.now();
        insertProduct("H100", "暖风杯 H100", "小家电", new BigDecimal("199.00"), 48,
                "现货订单通常在付款后 48 小时内发货，预售或活动高峰可能顺延。",
                "未影响二次销售可申请退货；质量问题可提供照片或视频凭证申请换货。", now);
        insertProduct("C20", "轻氧洗面巾 C20", "个护耗材", new BigDecimal("39.90"), 320,
                "工作日 18 点前付款通常当天出库，偏远地区以物流时效为准。",
                "个护耗材拆封后通常不支持无理由退货；质量问题可转人工核实。", now);
        insertProduct("P9", "云感靠枕 P9", "居家纺织品", new BigDecimal("129.00"), 86,
                "现货订单通常 24 小时内发货，定制颜色以页面预计时间为准。",
                "未清洗、未明显使用且包装完整时可提交退货申请。", now);
    }

    private void insertProduct(String code, String name, String category, BigDecimal price, int stock,
                               String dispatchRule, String afterSaleRule, LocalDateTime now) {
        ProductCatalog product = new ProductCatalog();
        product.setProductCode(code);
        product.setProductName(name);
        product.setCategory(category);
        product.setSaleStatus(ProductSaleStatus.ON_SALE);
        product.setPrice(price);
        product.setStockQuantity(stock);
        product.setDispatchRule(dispatchRule);
        product.setAfterSaleRule(afterSaleRule);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        productMapper.insert(product);
    }

    private void createOrders(Long userId) {
        ProductCatalog h100 = productByCode("H100");
        ProductCatalog c20 = productByCode("C20");
        ProductCatalog p9 = productByCode("P9");
        LocalDateTime now = LocalDateTime.now();
        CustomerOrder waiting = insertOrder("ORD202607170001", userId, h100.getId(), 1, h100.getPrice(),
                OrderStatus.WAITING_SHIPMENT, now.minusHours(6), now.plusHours(30), null, null,
                "张同学", "13800000001", "上海市浦东新区演示路 100 号", "等待仓库出库", now);
        if (waiting != null) {
            addShipment(waiting.getId(), null, null, ShipmentStatus.CREATED, "上海仓",
                    "订单已支付，等待仓库拣货", now.minusHours(6));
        }

        CustomerOrder transit = insertOrder("ORD202607160002", userId, c20.getId(), 2, c20.getPrice().multiply(BigDecimal.valueOf(2)),
                OrderStatus.IN_TRANSIT, now.minusDays(1), now.minusHours(8), now.minusHours(5), null,
                "张同学", "13800000001", "杭州市西湖区演示路 66 号", "包裹运输中", now.minusDays(1));
        if (transit != null) {
            addShipment(transit.getId(), "顺丰速运", "SF1000002002", ShipmentStatus.PICKED_UP, "杭州转运中心",
                    "快递员已揽收", now.minusHours(5));
            addShipment(transit.getId(), "顺丰速运", "SF1000002002", ShipmentStatus.IN_TRANSIT, "嘉兴中转场",
                    "包裹已到达嘉兴中转场，准备发往目的城市", now.minusHours(2));
        }

        CustomerOrder signed = insertOrder("ORD202607140003", userId, p9.getId(), 1, p9.getPrice(),
                OrderStatus.SIGNED, now.minusDays(3), now.minusDays(2), now.minusDays(2), now.minusDays(1),
                "张同学", "13800000001", "苏州市工业园区演示路 8 号", "已签收订单", now.minusDays(3));
        if (signed != null) {
            addShipment(signed.getId(), "京东物流", "JD1000003003", ShipmentStatus.DELIVERED, "苏州配送站",
                    "订单已签收，感谢您的购买", now.minusDays(1));
        }

        CustomerOrder shipped = insertOrder("ORD202607180004", userId, h100.getId(), 1, h100.getPrice(),
                OrderStatus.SHIPPED, now.minusHours(18), now.minusHours(2), now.minusHours(1), null,
                "张同学", "13800000001", "南京市玄武区演示路 18 号", "刚交给快递", now.minusHours(18));
        if (shipped != null) {
            addShipment(shipped.getId(), "中通快递", "ZT1000004004", ShipmentStatus.PICKED_UP, "上海仓",
                    "包裹已交给中通快递，等待发往南京转运中心", now.minusHours(1));
        }

        CustomerOrder refunding = insertOrder("ORD202607130005", userId, c20.getId(), 3, c20.getPrice().multiply(BigDecimal.valueOf(3)),
                OrderStatus.REFUNDING, now.minusDays(5), now.minusDays(4), now.minusDays(4), now.minusDays(2),
                "张同学", "13800000001", "广州市天河区演示路 28 号", "用户反馈包装破损，售后审核中", now.minusDays(5));
        if (refunding != null) {
            addShipment(refunding.getId(), "圆通速递", "YT1000005005", ShipmentStatus.DELIVERED, "广州天河网点",
                    "订单已签收，用户已提交售后申请", now.minusDays(2));
        }

        CustomerOrder cancelled = insertOrder("ORD202607120006", userId, p9.getId(), 1, p9.getPrice(),
                OrderStatus.CANCELLED, now.minusDays(6), now.minusDays(5), null, null,
                "张同学", "13800000001", "成都市高新区演示路 6 号", "用户取消未发货订单", now.minusDays(6));
        if (cancelled != null) {
            addShipment(cancelled.getId(), null, null, ShipmentStatus.CREATED, "系统",
                    "订单已取消，未进入仓库发货流程", now.minusDays(5));
        }
    }

    private CustomerOrder insertOrder(String orderNo, Long userId, Long productId, int quantity, BigDecimal amount,
                                      OrderStatus status, LocalDateTime paidAt, LocalDateTime expectedShipAt,
                                      LocalDateTime shippedAt, LocalDateTime signedAt, String receiverName,
                                      String receiverPhone, String receiverAddress, String remark, LocalDateTime createdAt) {
        if (orderMapper.selectCount(new LambdaQueryWrapper<CustomerOrder>().eq(CustomerOrder::getOrderNo, orderNo)) > 0) {
            return null;
        }
        CustomerOrder order = new CustomerOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setProductId(productId);
        order.setQuantity(quantity);
        order.setAmount(amount);
        order.setStatus(status);
        order.setPaidAt(paidAt);
        order.setExpectedShipAt(expectedShipAt);
        order.setShippedAt(shippedAt);
        order.setSignedAt(signedAt);
        order.setReceiverName(receiverName);
        order.setReceiverPhone(receiverPhone);
        order.setReceiverAddress(receiverAddress);
        order.setRemark(remark);
        order.setCreatedAt(createdAt);
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.insert(order);
        return order;
    }

    private void addShipment(Long orderId, String carrier, String trackingNo, ShipmentStatus status, String location,
                             String eventNote, LocalDateTime eventTime) {
        ShipmentEvent event = new ShipmentEvent();
        event.setOrderId(orderId);
        event.setCarrier(carrier);
        event.setTrackingNo(trackingNo);
        event.setStatus(status);
        event.setLocation(location);
        event.setEventNote(eventNote);
        event.setEventTime(eventTime);
        event.setCreatedAt(LocalDateTime.now());
        shipmentMapper.insert(event);
    }

    private ProductCatalog productByCode(String code) {
        return productMapper.selectOne(new LambdaQueryWrapper<ProductCatalog>()
                .eq(ProductCatalog::getProductCode, code)
                .last("LIMIT 1"));
    }
}
