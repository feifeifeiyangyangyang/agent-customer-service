package com.zhifutong.customer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhifutong.customer.domain.ProductSaleStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("product_catalog")
public class ProductCatalog {
    private Long id;
    private String productCode;
    private String productName;
    private String category;
    private ProductSaleStatus saleStatus;
    private BigDecimal price;
    private Integer stockQuantity;
    private String dispatchRule;
    private String afterSaleRule;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public ProductSaleStatus getSaleStatus() { return saleStatus; }
    public void setSaleStatus(ProductSaleStatus saleStatus) { this.saleStatus = saleStatus; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(Integer stockQuantity) { this.stockQuantity = stockQuantity; }
    public String getDispatchRule() { return dispatchRule; }
    public void setDispatchRule(String dispatchRule) { this.dispatchRule = dispatchRule; }
    public String getAfterSaleRule() { return afterSaleRule; }
    public void setAfterSaleRule(String afterSaleRule) { this.afterSaleRule = afterSaleRule; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
