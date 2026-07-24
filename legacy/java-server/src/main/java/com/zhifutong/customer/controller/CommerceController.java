package com.zhifutong.customer.controller;

import com.zhifutong.customer.application.CommerceApplicationService;
import com.zhifutong.customer.auth.AuthContext;
import com.zhifutong.customer.domain.OrderStatus;
import com.zhifutong.customer.dto.CreateOrderRequest;
import com.zhifutong.customer.dto.UpdateOrderStatusRequest;
import com.zhifutong.customer.vo.AdminDashboardResponse;
import com.zhifutong.customer.vo.ApiResponse;
import com.zhifutong.customer.vo.OrderResponse;
import com.zhifutong.customer.vo.PageResult;
import com.zhifutong.customer.vo.ProductResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class CommerceController {
    private final CommerceApplicationService commerceService;

    public CommerceController(CommerceApplicationService commerceService) {
        this.commerceService = commerceService;
    }

    @GetMapping("/api/v1/products")
    public ApiResponse<PageResult<ProductResponse>> products(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(commerceService.listProducts(page, size, keyword));
    }

    @GetMapping("/api/v1/orders")
    public ApiResponse<PageResult<OrderResponse>> myOrders(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(required = false) OrderStatus status) {
        return ApiResponse.ok(commerceService.listMine(AuthContext.require(), page, size, status));
    }

    @GetMapping("/api/v1/orders/{id}")
    public ApiResponse<OrderResponse> myOrder(@PathVariable Long id) {
        return ApiResponse.ok(commerceService.getMine(AuthContext.require(), id));
    }

    @PostMapping("/api/v1/orders")
    public ApiResponse<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.ok(commerceService.createOrder(AuthContext.require(), request.productId(), request.quantity(),
                request.receiverName(), request.receiverPhone(), request.receiverAddress(), request.remark()));
    }

    @GetMapping("/api/v1/admin/dashboard")
    public ApiResponse<AdminDashboardResponse> dashboard() {
        return ApiResponse.ok(commerceService.dashboard());
    }

    @GetMapping("/api/v1/admin/products")
    public ApiResponse<PageResult<ProductResponse>> adminProducts(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) long size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(commerceService.listProducts(page, size, keyword));
    }

    @GetMapping("/api/v1/admin/orders")
    public ApiResponse<PageResult<OrderResponse>> adminOrders(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(commerceService.listAllOrders(page, size, status, keyword));
    }

    @PatchMapping("/api/v1/admin/orders/{id}/status")
    public ApiResponse<OrderResponse> updateOrder(@PathVariable Long id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ApiResponse.ok(commerceService.updateOrderStatus(id, request.status(), request.carrier(),
                request.trackingNo(), request.location(), request.eventNote()));
    }
}
