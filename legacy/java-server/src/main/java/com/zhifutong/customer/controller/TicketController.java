package com.zhifutong.customer.controller;

import com.zhifutong.customer.application.TicketApplicationService;
import com.zhifutong.customer.auth.AuthContext;
import com.zhifutong.customer.domain.TicketStatus;
import com.zhifutong.customer.dto.CreateTicketRequest;
import com.zhifutong.customer.dto.UpdateTicketStatusRequest;
import com.zhifutong.customer.vo.ApiResponse;
import com.zhifutong.customer.vo.PageResult;
import com.zhifutong.customer.vo.TicketResponse;
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
public class TicketController {
    private final TicketApplicationService ticketService;

    public TicketController(TicketApplicationService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/api/v1/tickets")
    public ApiResponse<TicketResponse> create(@Valid @RequestBody CreateTicketRequest request) {
        return ApiResponse.ok(ticketService.create(AuthContext.require(), request.conversationId(), request.description(), request.category(), request.contact()));
    }

    @GetMapping("/api/v1/tickets")
    public ApiResponse<PageResult<TicketResponse>> listMine(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(required = false) TicketStatus status) {
        return ApiResponse.ok(ticketService.listMine(AuthContext.require(), page, size, status));
    }

    @GetMapping("/api/v1/tickets/{id}")
    public ApiResponse<TicketResponse> getMine(@PathVariable Long id) {
        return ApiResponse.ok(ticketService.getMine(AuthContext.require(), id));
    }

    @GetMapping("/api/v1/admin/tickets")
    public ApiResponse<PageResult<TicketResponse>> list(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(required = false) TicketStatus status) {
        return ApiResponse.ok(ticketService.list(page, size, status));
    }

    @GetMapping("/api/v1/admin/tickets/{id}")
    public ApiResponse<TicketResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(ticketService.get(id));
    }

    @PatchMapping("/api/v1/admin/tickets/{id}/status")
    public ApiResponse<TicketResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateTicketStatusRequest request) {
        return ApiResponse.ok(ticketService.updateStatus(AuthContext.require(), id, request.status(), request.handlingNote(), request.resolution(), request.lockVersion()));
    }
}
