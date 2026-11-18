package com.fincore.search.infrastructure.search;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

@Document(indexName = "fincore-loan-products-v1", createIndex = false)
@Setting(shards = 1, replicas = 0, refreshInterval = "1s")
public class LoanProductSearchDocument {

    @Id
    private String productId;
    @Field(type = FieldType.Keyword)
    private String code;
    @Field(type = FieldType.Keyword)
    private String interestType;
    @Field(type = FieldType.Double)
    private BigDecimal annualRate;
    @Field(type = FieldType.Double)
    private BigDecimal minAmount;
    @Field(type = FieldType.Double)
    private BigDecimal maxAmount;
    @Field(type = FieldType.Boolean)
    private boolean active;
    @Field(type = FieldType.Long)
    private long version;
    @Field(type = FieldType.Date)
    private Instant updatedAt;

    protected LoanProductSearchDocument() {
    }

    public LoanProductSearchDocument(String productId, String code, String interestType, BigDecimal annualRate,
            BigDecimal minAmount, BigDecimal maxAmount, boolean active, long version, Instant updatedAt) {
        this.productId = productId;
        this.code = code;
        this.interestType = interestType;
        this.annualRate = annualRate;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.active = active;
        this.version = version;
        this.updatedAt = updatedAt;
    }

    public String getProductId() { return productId; }
    public String getCode() { return code; }
    public String getInterestType() { return interestType; }
    public BigDecimal getAnnualRate() { return annualRate; }
    public BigDecimal getMinAmount() { return minAmount; }
    public BigDecimal getMaxAmount() { return maxAmount; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }
}
