package com.zhifutong.customer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhifutong.customer.domain.ShipmentStatus;
import java.time.LocalDateTime;

@TableName("shipment_event")
public class ShipmentEvent {
    private Long id;
    private Long orderId;
    private String carrier;
    private String trackingNo;
    private ShipmentStatus status;
    private String location;
    private String eventNote;
    private LocalDateTime eventTime;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }
    public String getTrackingNo() { return trackingNo; }
    public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
    public ShipmentStatus getStatus() { return status; }
    public void setStatus(ShipmentStatus status) { this.status = status; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getEventNote() { return eventNote; }
    public void setEventNote(String eventNote) { this.eventNote = eventNote; }
    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
