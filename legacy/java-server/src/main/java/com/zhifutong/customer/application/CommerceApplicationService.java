package com.zhifutong.customer.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhifutong.customer.auth.AuthenticatedUser;
import com.zhifutong.customer.domain.OrderStatus;
import com.zhifutong.customer.domain.ProductSaleStatus;
import com.zhifutong.customer.domain.ShipmentStatus;
import com.zhifutong.customer.domain.DocumentStatus;
import com.zhifutong.customer.domain.MessageRole;
import com.zhifutong.customer.domain.TicketStatus;
import com.zhifutong.customer.entity.ChatMessage;
import com.zhifutong.customer.entity.CustomerOrder;
import com.zhifutong.customer.entity.KbDocument;
import com.zhifutong.customer.entity.ProductCatalog;
import com.zhifutong.customer.entity.ShipmentEvent;
import com.zhifutong.customer.entity.SupportTicket;
import com.zhifutong.customer.exception.BusinessException;
import com.zhifutong.customer.mapper.ChatMessageMapper;
import com.zhifutong.customer.mapper.CustomerOrderMapper;
import com.zhifutong.customer.mapper.KbDocumentMapper;
import com.zhifutong.customer.mapper.ProductCatalogMapper;
import com.zhifutong.customer.mapper.ShipmentEventMapper;
import com.zhifutong.customer.mapper.SupportTicketMapper;
import com.zhifutong.customer.vo.AdminDashboardResponse;
import com.zhifutong.customer.vo.OrderResponse;
import com.zhifutong.customer.vo.PageResult;
import com.zhifutong.customer.vo.ProductResponse;
import com.zhifutong.customer.vo.ShipmentEventResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommerceApplicationService {
    private static final Pattern ORDER_NO_PATTERN = Pattern.compile("(ORD[0-9A-Z]{8,})", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final ProductCatalogMapper productMapper;
    private final CustomerOrderMapper orderMapper;
    private final ShipmentEventMapper shipmentMapper;
    private final SupportTicketMapper ticketMapper;
    private final KbDocumentMapper documentMapper;
    private final ChatMessageMapper messageMapper;

    public CommerceApplicationService(ProductCatalogMapper productMapper, CustomerOrderMapper orderMapper,
                                      ShipmentEventMapper shipmentMapper, SupportTicketMapper ticketMapper,
                                      KbDocumentMapper documentMapper, ChatMessageMapper messageMapper) {
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.shipmentMapper = shipmentMapper;
        this.ticketMapper = ticketMapper;
        this.documentMapper = documentMapper;
        this.messageMapper = messageMapper;
    }

    public PageResult<ProductResponse> listProducts(long page, long size, String keyword) {
        Page<ProductCatalog> result = productMapper.selectPage(Page.of(page, size),
                new LambdaQueryWrapper<ProductCatalog>()
                        .and(keyword != null && !keyword.isBlank(), wrapper -> wrapper
                                .like(ProductCatalog::getProductName, keyword)
                                .or()
                                .like(ProductCatalog::getProductCode, keyword))
                        .orderByDesc(ProductCatalog::getUpdatedAt));
        return new PageResult<>(page, size, result.getTotal(), result.getRecords().stream().map(this::toProductResponse).toList());
    }

    public PageResult<OrderResponse> listMine(AuthenticatedUser user, long page, long size, OrderStatus status) {
        Page<CustomerOrder> result = orderMapper.selectPage(Page.of(page, size),
                new LambdaQueryWrapper<CustomerOrder>()
                        .eq(CustomerOrder::getUserId, user.userId())
                        .eq(status != null, CustomerOrder::getStatus, status)
                        .orderByDesc(CustomerOrder::getCreatedAt));
        return new PageResult<>(page, size, result.getTotal(), result.getRecords().stream().map(this::toOrderResponse).toList());
    }

    public PageResult<OrderResponse> listAllOrders(long page, long size, OrderStatus status, String keyword) {
        Page<CustomerOrder> result = orderMapper.selectPage(Page.of(page, size),
                new LambdaQueryWrapper<CustomerOrder>()
                        .eq(status != null, CustomerOrder::getStatus, status)
                        .like(keyword != null && !keyword.isBlank(), CustomerOrder::getOrderNo, keyword)
                        .orderByDesc(CustomerOrder::getCreatedAt));
        return new PageResult<>(page, size, result.getTotal(), result.getRecords().stream().map(this::toOrderResponse).toList());
    }

    @Transactional
    public OrderResponse createOrder(AuthenticatedUser user, Long productId, Integer quantity, String receiverName,
                                     String receiverPhone, String receiverAddress, String remark) {
        ProductCatalog product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        if (product.getSaleStatus() != ProductSaleStatus.ON_SALE) {
            throw new BusinessException("商品当前不可下单");
        }
        if (product.getStockQuantity() < quantity) {
            throw new BusinessException("商品库存不足");
        }
        LocalDateTime now = LocalDateTime.now();
        product.setStockQuantity(product.getStockQuantity() - quantity);
        product.setUpdatedAt(now);
        productMapper.updateById(product);

        CustomerOrder order = new CustomerOrder();
        order.setOrderNo("ORD" + ORDER_NO_TIME.format(now));
        order.setUserId(user.userId());
        order.setProductId(product.getId());
        order.setQuantity(quantity);
        order.setAmount(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        order.setStatus(OrderStatus.WAITING_SHIPMENT);
        order.setPaidAt(now);
        order.setExpectedShipAt(expectedShipAt(product, now));
        order.setReceiverName(receiverName);
        order.setReceiverPhone(receiverPhone);
        order.setReceiverAddress(receiverAddress);
        order.setRemark(remark);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderMapper.insert(order);

        ShipmentEvent event = new ShipmentEvent();
        event.setOrderId(order.getId());
        event.setStatus(ShipmentStatus.CREATED);
        event.setLocation("系统");
        event.setEventNote("订单已创建并支付，等待仓库处理");
        event.setEventTime(now);
        event.setCreatedAt(now);
        shipmentMapper.insert(event);
        return toOrderResponse(order);
    }

    public AdminDashboardResponse dashboard() {
        LocalDateTime today = LocalDateTime.now().toLocalDate().atStartOfDay();
        long todayConsultations = messageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getRole, MessageRole.USER)
                .ge(ChatMessage::getCreatedAt, today));
        long productCount = productMapper.selectCount(null);
        long waitingShipment = orderMapper.selectCount(new LambdaQueryWrapper<CustomerOrder>()
                .eq(CustomerOrder::getStatus, OrderStatus.WAITING_SHIPMENT));
        long shippingOrders = orderMapper.selectCount(new LambdaQueryWrapper<CustomerOrder>()
                .in(CustomerOrder::getStatus, OrderStatus.SHIPPED, OrderStatus.IN_TRANSIT));
        long signedOrders = orderMapper.selectCount(new LambdaQueryWrapper<CustomerOrder>()
                .eq(CustomerOrder::getStatus, OrderStatus.SIGNED));
        long pendingTickets = ticketMapper.selectCount(new LambdaQueryWrapper<SupportTicket>()
                .in(SupportTicket::getStatus, TicketStatus.OPEN, TicketStatus.PROCESSING));
        long readyDocuments = documentMapper.selectCount(new LambdaQueryWrapper<KbDocument>()
                .in(KbDocument::getStatus, DocumentStatus.READY, DocumentStatus.COMPLETED));
        long failedDocuments = documentMapper.selectCount(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getStatus, DocumentStatus.FAILED));
        return new AdminDashboardResponse(todayConsultations, productCount, waitingShipment, shippingOrders,
                signedOrders, pendingTickets, readyDocuments, failedDocuments);
    }

    public OrderResponse getMine(AuthenticatedUser user, Long id) {
        CustomerOrder order = requireOrder(id);
        if (!order.getUserId().equals(user.userId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "不能查看其他用户的订单");
        }
        return toOrderResponse(order);
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long id, OrderStatus status, String carrier, String trackingNo, String location, String eventNote) {
        CustomerOrder order = requireOrder(id);
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(status);
        order.setUpdatedAt(now);
        if ((status == OrderStatus.SHIPPED || status == OrderStatus.IN_TRANSIT) && order.getShippedAt() == null) {
            order.setShippedAt(now);
        }
        if (status == OrderStatus.SIGNED && order.getSignedAt() == null) {
            order.setSignedAt(now);
        }
        orderMapper.updateById(order);
        ShipmentEvent event = new ShipmentEvent();
        event.setOrderId(order.getId());
        event.setCarrier(carrier);
        event.setTrackingNo(trackingNo);
        event.setStatus(toShipmentStatus(status));
        event.setLocation(location);
        event.setEventNote((eventNote == null || eventNote.isBlank()) ? defaultShipmentNote(status) : eventNote);
        event.setEventTime(now);
        event.setCreatedAt(now);
        shipmentMapper.insert(event);
        return toOrderResponse(order);
    }

    public Optional<String> answerBusinessQuestion(AuthenticatedUser user, String question) {
        if (question == null || question.isBlank()) {
            return Optional.empty();
        }
        String clean = question.trim();
        boolean orderRelated = looksLikeOrderQuestion(clean) || looksLikeAfterSaleQuestion(clean);
        if (looksLikeOrderListQuestion(clean)) {
            return Optional.of(answerForOrderList(user, clean));
        }
        Optional<CustomerOrder> matchedOrder = findOrderByNo(user, clean);
        if (matchedOrder.isEmpty()) {
            matchedOrder = findOrderByIndex(user, clean);
        }
        if (matchedOrder.isEmpty() && orderRelated) {
            ProductCatalog product = findMentionedProduct(clean);
            if (product != null) {
                List<CustomerOrder> productOrders = findOrdersByProduct(user, product);
                if (!productOrders.isEmpty()) {
                    return Optional.of(answerForProductOrders(product, productOrders, clean));
                }
            }
        }
        if (matchedOrder.isEmpty() && orderRelated) {
            matchedOrder = latestOrder(user);
        }
        if (matchedOrder.isPresent()) {
            return Optional.of(answerForOrder(matchedOrder.get(), clean));
        }
        if (looksLikeProductQuestion(clean)) {
            ProductCatalog product = findMentionedProduct(clean);
            if (product != null) {
                return Optional.of(answerForProduct(product));
            }
        }
        return Optional.empty();
    }

    private Optional<CustomerOrder> findOrderByNo(AuthenticatedUser user, String question) {
        Matcher matcher = ORDER_NO_PATTERN.matcher(question.toUpperCase());
        if (!matcher.find()) {
            return Optional.empty();
        }
        CustomerOrder order = orderMapper.selectOne(new LambdaQueryWrapper<CustomerOrder>()
                .eq(CustomerOrder::getOrderNo, matcher.group(1))
                .last("LIMIT 1"));
        if (order == null || !order.getUserId().equals(user.userId())) {
            return Optional.empty();
        }
        return Optional.of(order);
    }

    private Optional<CustomerOrder> latestOrder(AuthenticatedUser user) {
        CustomerOrder order = userOrders(user).stream().findFirst().orElse(null);
        return Optional.ofNullable(order);
    }

    private Optional<CustomerOrder> findOrderByIndex(AuthenticatedUser user, String question) {
        int index = requestedOrderIndex(question);
        if (index < 0) {
            return Optional.empty();
        }
        List<CustomerOrder> orders = userOrders(user);
        return index < orders.size() ? Optional.of(orders.get(index)) : Optional.empty();
    }

    private boolean looksLikeOrderQuestion(String question) {
        return containsAny(question, "订单", "发货", "物流", "快递", "到哪", "签收", "配送", "什么时候到", "什么时候发",
                "第一个", "第二个", "第三个", "第四个", "最近", "刚买", "我买的", "那个");
    }

    private boolean looksLikeProductQuestion(String question) {
        return containsAny(question, "商品", "库存", "价格", "发货", "售后", "退货", "拆封", "质量", "买过", "买了");
    }

    private boolean looksLikeAfterSaleQuestion(String question) {
        return containsAny(question, "能退", "退货", "退款", "换货", "售后", "拆封", "破损", "质量", "坏了");
    }

    private boolean looksLikeOrderListQuestion(String question) {
        if (containsAny(question, "所有商品", "全部商品", "所有订单", "全部订单", "订单列表", "买的所有商品",
                "已经下单", "已下单", "下过单", "下单的商品", "买过哪些", "买了哪些", "我买了什么", "我买过什么",
                "哪些商品", "分别是哪个", "分别是什么")) {
            return true;
        }
        return requestedOrderIndex(question) >= 0 && containsAny(question, "分别", "哪些", "是什么", "是哪个");
    }

    private int requestedOrderIndex(String question) {
        List<Integer> indexes = requestedOrderIndexes(question);
        return indexes.isEmpty() ? -1 : indexes.get(0);
    }

    private List<Integer> requestedOrderIndexes(String question) {
        List<Integer> indexes = new java.util.ArrayList<>();
        if (containsAny(question, "第一个", "第一单", "1号订单", "第1个", "第1单")) {
            indexes.add(0);
        }
        if (containsAny(question, "第二个", "第二单", "2号订单", "第2个", "第2单")) {
            indexes.add(1);
        }
        if (containsAny(question, "第三个", "第三单", "3号订单", "第3个", "第3单")) {
            indexes.add(2);
        }
        if (containsAny(question, "第四个", "第四单", "4号订单", "第4个", "第4单")) {
            indexes.add(3);
        }
        if (containsAny(question, "第五个", "第五单", "5号订单", "第5个", "第5单")) {
            indexes.add(4);
        }
        return indexes;
    }

    private String orderPositionLabel(int index) {
        return switch (index) {
            case 0 -> "第一个";
            case 1 -> "第二个";
            case 2 -> "第三个";
            case 3 -> "第四个";
            case 4 -> "第五个";
            default -> "第" + (index + 1) + "个";
        };
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private ProductCatalog findMentionedProduct(String question) {
        List<ProductCatalog> products = productMapper.selectList(new LambdaQueryWrapper<ProductCatalog>()
                .orderByAsc(ProductCatalog::getId));
        for (ProductCatalog product : products) {
            if (question.contains(product.getProductName()) || question.toUpperCase().contains(product.getProductCode().toUpperCase())) {
                return product;
            }
            if (product.getProductName().contains("杯") && question.contains("杯")) {
                return product;
            }
            if (product.getProductName().contains("洗面巾") && containsAny(question, "洗面巾", "洗脸巾", "洁面巾")) {
                return product;
            }
            if (product.getProductName().contains("靠枕") && containsAny(question, "靠枕", "枕头")) {
                return product;
            }
        }
        return null;
    }

    private List<CustomerOrder> userOrders(AuthenticatedUser user) {
        return orderMapper.selectList(new LambdaQueryWrapper<CustomerOrder>()
                .eq(CustomerOrder::getUserId, user.userId())
                .orderByDesc(CustomerOrder::getCreatedAt));
    }

    private List<CustomerOrder> findOrdersByProduct(AuthenticatedUser user, ProductCatalog product) {
        return orderMapper.selectList(new LambdaQueryWrapper<CustomerOrder>()
                .eq(CustomerOrder::getUserId, user.userId())
                .eq(CustomerOrder::getProductId, product.getId())
                .orderByDesc(CustomerOrder::getCreatedAt));
    }

    private String answerForOrderList(AuthenticatedUser user, String question) {
        List<CustomerOrder> orders = userOrders(user);
        if (orders.isEmpty()) {
            return "我这边暂时没有查到您的订单。如果您刚下单，可以稍后刷新订单列表，或者提供订单号让我再查一次。";
        }
        List<Integer> indexes = requestedOrderIndexes(question);
        boolean askAll = containsAny(question, "所有商品", "全部商品", "所有订单", "全部订单", "订单列表", "买的所有商品",
                "已经下单", "已下单", "下过单", "下单的商品", "买过哪些", "买了哪些", "我买了什么", "我买过什么",
                "哪些商品");
        StringBuilder builder;
        if (!askAll && !indexes.isEmpty()) {
            builder = new StringBuilder("按页面“我的订单”从上到下看，您问的这几个位置对应：");
            for (Integer index : indexes) {
                if (index < orders.size()) {
                    appendOrderLine(builder, orderPositionLabel(index), orders.get(index));
                } else {
                    builder.append("\n").append(orderPositionLabel(index)).append("：目前没有对应订单。");
                }
            }
        } else {
            builder = new StringBuilder("我查到您当前有 ")
                    .append(orders.size())
                    .append(" 个订单，按页面“我的订单”从上到下分别是：");
            for (int i = 0; i < orders.size(); i++) {
                appendOrderLine(builder, String.valueOf(i + 1), orders.get(i));
            }
        }
        builder.append("\n您可以继续问“第几个订单物流到哪里了”，也可以直接报商品名或订单号。");
        return builder.toString();
    }

    private void appendOrderLine(StringBuilder builder, String label, CustomerOrder order) {
        ProductCatalog product = productMapper.selectById(order.getProductId());
        ShipmentEvent latest = latestShipmentEvent(order.getId());
        String productName = product == null ? "商品" : product.getProductName();
        String delimiter = label.matches("\\d+") ? ". " : "：";
        builder.append("\n").append(label).append(delimiter).append("「").append(productName).append("」")
                .append(" × ").append(order.getQuantity())
                .append("，订单号 ").append(order.getOrderNo())
                .append("，状态：").append(orderStatusLabel(order.getStatus()));
        if (order.getStatus() == OrderStatus.WAITING_SHIPMENT || order.getStatus() == OrderStatus.PAID) {
            builder.append("，预计发货：").append(format(order.getExpectedShipAt()));
        } else if (latest != null) {
            builder.append("，最新物流：").append(latest.getEventNote())
                    .append("（").append(format(latest.getEventTime())).append("）");
        }
        builder.append("。");
    }

    private String answerForProductOrders(ProductCatalog product, List<CustomerOrder> orders, String question) {
        CustomerOrder latest = orders.get(0);
        if (orders.size() == 1) {
            return answerForOrder(latest, question);
        }
        long waiting = orders.stream()
                .filter(order -> order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.WAITING_SHIPMENT)
                .count();
        long shipping = orders.stream()
                .filter(order -> order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.IN_TRANSIT)
                .count();
        return "我查到您有 " + orders.size() + " 笔「" + product.getProductName() + "」相关订单"
                + orderCountSummary(waiting, shipping)
                + "，先按最近一笔看。\n"
                + answerForOrder(latest, question)
                + "\n如果您想查更早的一笔，可以直接问“第几个订单物流到哪里了”，或者报订单号。";
    }

    private String orderCountSummary(long waiting, long shipping) {
        if (waiting == 0 && shipping == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder("，其中");
        if (waiting > 0) {
            builder.append(" ").append(waiting).append(" 笔待发货");
        }
        if (shipping > 0) {
            if (waiting > 0) {
                builder.append("、");
            } else {
                builder.append(" ");
            }
            builder.append(shipping).append(" 笔配送中");
        }
        return builder.toString();
    }

    private String answerForOrder(CustomerOrder order, String question) {
        ProductCatalog product = productMapper.selectById(order.getProductId());
        ShipmentEvent latest = latestShipmentEvent(order.getId());
        String productName = product == null ? "商品" : product.getProductName();
        String lead = orderAnswerLead(order, productName, question);
        if (looksLikeAfterSaleQuestion(question)) {
            String rule = product == null ? "具体规则需要人工客服结合商品情况核实。" : product.getAfterSaleRule();
            return lead + "当前订单状态是"
                    + orderStatusLabel(order.getStatus()) + "。售后规则：" + rule
                    + " 如果已经拆封、使用、配件缺失或商品破损，建议直接转人工并补充照片/视频凭证。";
        }
        if (order.getStatus() == OrderStatus.WAITING_SHIPMENT || order.getStatus() == OrderStatus.PAID) {
            return lead + pendingShipmentText(question, order);
        }
        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.IN_TRANSIT) {
            String latestText = latest == null ? "暂时没有新的物流节点" : latest.getEventNote() + "（" + format(latest.getEventTime()) + "）";
            return lead + "这单已发货，物流单号是 "
                    + valueOrDefault(latest == null ? null : latest.getTrackingNo(), "暂未同步") + "。最新进展：" + latestText + "。";
        }
        if (order.getStatus() == OrderStatus.SIGNED) {
            return lead + "这单已在 " + format(order.getSignedAt())
                    + " 签收。如果商品有破损、缺件或质量问题，可以继续描述情况，我帮您转到售后工单。";
        }
        if (order.getStatus() == OrderStatus.REFUNDING || order.getStatus() == OrderStatus.REFUNDED) {
            return lead + "这单当前处于" + orderStatusLabel(order.getStatus())
                    + "状态，退款/售后进度建议在工单里继续跟进，避免遗漏凭证。";
        }
        return lead + "当前状态是 " + orderStatusLabel(order.getStatus())
                + "。如果需要更具体处理，可以转人工继续核实。";
    }

    private String orderAnswerLead(CustomerOrder order, String productName, String question) {
        int index = requestedOrderIndex(question);
        if (index >= 0) {
            return "按页面“我的订单”从上到下，" + orderPositionLabel(index) + "是「" + productName
                    + "」（订单 " + order.getOrderNo() + "）。";
        }
        if (containsAny(question, "最近", "刚买", "刚下单")) {
            return "我先按最近一笔订单看：您这单是「" + productName + "」（订单 " + order.getOrderNo() + "）。";
        }
        return "我查到您的订单 " + order.getOrderNo() + " 是「" + productName + "」。";
    }

    private String pendingShipmentText(String question, CustomerOrder order) {
        if (containsAny(question, "物流", "快递", "到哪", "到哪里")) {
            return "这单还没有进入物流运输，预计发货时间是 " + format(order.getExpectedShipAt())
                    + "。超过这个时间还没更新的话，可以转人工帮您催仓库。";
        }
        if (containsAny(question, "什么时候到", "多久到", "到货")) {
            return "这单目前还未发货，所以暂时不能给到准确到达时间；预计发货时间是 "
                    + format(order.getExpectedShipAt()) + "。发出后我就能继续查物流节点。";
        }
        return "当前还未发货，预计发货时间是 " + format(order.getExpectedShipAt())
                + "。如果超过这个时间还没有物流更新，可以直接转人工帮您催一下仓库。";
    }

    private String answerForProduct(ProductCatalog product) {
        String stock = product.getSaleStatus() == ProductSaleStatus.ON_SALE
                ? "当前有库存 " + product.getStockQuantity() + " 件"
                : "当前状态为 " + product.getSaleStatus();
        return "「" + product.getProductName() + "」" + stock + "，售价 " + product.getPrice()
                + " 元。发货规则：" + product.getDispatchRule() + "。售后说明：" + product.getAfterSaleRule();
    }

    private LocalDateTime expectedShipAt(ProductCatalog product, LocalDateTime now) {
        if ("H100".equals(product.getProductCode())) {
            return now.plusHours(48);
        }
        if ("P9".equals(product.getProductCode())) {
            return now.plusHours(24);
        }
        return now.plusHours(8);
    }

    private ShipmentEvent latestShipmentEvent(Long orderId) {
        return shipmentMapper.selectOne(new LambdaQueryWrapper<ShipmentEvent>()
                .eq(ShipmentEvent::getOrderId, orderId)
                .orderByDesc(ShipmentEvent::getEventTime)
                .last("LIMIT 1"));
    }

    private CustomerOrder requireOrder(Long id) {
        CustomerOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        return order;
    }

    private OrderResponse toOrderResponse(CustomerOrder order) {
        ProductCatalog product = productMapper.selectById(order.getProductId());
        List<ShipmentEventResponse> events = shipmentMapper.selectList(new LambdaQueryWrapper<ShipmentEvent>()
                        .eq(ShipmentEvent::getOrderId, order.getId())
                        .orderByDesc(ShipmentEvent::getEventTime))
                .stream()
                .map(this::toShipmentResponse)
                .toList();
        return new OrderResponse(order.getId(), order.getOrderNo(), order.getUserId(), toProductResponse(product),
                order.getQuantity(), order.getAmount(), order.getStatus(), order.getPaidAt(), order.getExpectedShipAt(),
                order.getShippedAt(), order.getSignedAt(), order.getReceiverName(), order.getReceiverPhone(),
                order.getReceiverAddress(), order.getRemark(), events, order.getCreatedAt(), order.getUpdatedAt());
    }

    private ProductResponse toProductResponse(ProductCatalog product) {
        if (product == null) {
            return null;
        }
        return new ProductResponse(product.getId(), product.getProductCode(), product.getProductName(), product.getCategory(),
                product.getSaleStatus(), product.getPrice(), product.getStockQuantity(), product.getDispatchRule(),
                product.getAfterSaleRule(), product.getCreatedAt(), product.getUpdatedAt());
    }

    private ShipmentEventResponse toShipmentResponse(ShipmentEvent event) {
        return new ShipmentEventResponse(event.getId(), event.getCarrier(), event.getTrackingNo(), event.getStatus(),
                event.getLocation(), event.getEventNote(), event.getEventTime());
    }

    private ShipmentStatus toShipmentStatus(OrderStatus status) {
        return switch (status) {
            case SHIPPED -> ShipmentStatus.PICKED_UP;
            case IN_TRANSIT -> ShipmentStatus.IN_TRANSIT;
            case SIGNED -> ShipmentStatus.DELIVERED;
            default -> ShipmentStatus.CREATED;
        };
    }

    private String defaultShipmentNote(OrderStatus status) {
        return switch (status) {
            case SHIPPED -> "包裹已交给快递";
            case IN_TRANSIT -> "包裹运输中";
            case SIGNED -> "订单已签收";
            default -> "订单状态已更新";
        };
    }

    private String format(LocalDateTime value) {
        return value == null ? "暂未同步" : DATE_TIME.format(value);
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String orderStatusLabel(OrderStatus status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case PENDING_PAYMENT -> "待支付";
            case PAID, WAITING_SHIPMENT -> "待发货";
            case SHIPPED -> "已发货";
            case IN_TRANSIT -> "运输中";
            case SIGNED -> "已签收";
            case REFUNDING -> "退款中";
            case REFUNDED -> "已退款";
            case CANCELLED -> "已取消";
        };
    }
}
