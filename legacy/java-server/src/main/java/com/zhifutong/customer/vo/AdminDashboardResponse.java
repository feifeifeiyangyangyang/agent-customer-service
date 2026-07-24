package com.zhifutong.customer.vo;

public record AdminDashboardResponse(
        long todayConsultations,
        long productCount,
        long waitingShipmentOrders,
        long shippingOrders,
        long signedOrders,
        long pendingTickets,
        long readyDocuments,
        long failedDocuments
) {
}
