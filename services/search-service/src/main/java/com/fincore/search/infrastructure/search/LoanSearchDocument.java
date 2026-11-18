package com.fincore.search.infrastructure.search;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

@Document(indexName = "fincore-loans-v1", createIndex = false)
@Setting(shards = 1, replicas = 0, refreshInterval = "1s")
public class LoanSearchDocument {

    public static final String LEGACY_UNKNOWN_CUSTOMER_ID = "unknown";

    @Id
    private String loanId;

    @Field(type = FieldType.Keyword)
    private String customerId;

    @Field(type = FieldType.Keyword)
    private String productCode;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Double)
    private BigDecimal principalAmount;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Integer)
    private int tenorMonths;

    @Field(type = FieldType.Keyword)
    private String businessReference;

    @Field(type = FieldType.Date)
    private Instant createdAt;

    @Field(type = FieldType.Long)
    private long aggregateVersion;

    @Field(type = FieldType.Date)
    private Instant occurredAt;

    protected LoanSearchDocument() {
    }

    public LoanSearchDocument(
            String loanId, String customerId, String productCode, String status,
            BigDecimal principalAmount, String description, long aggregateVersion, Instant occurredAt) {
        this(loanId, customerId, productCode, status, principalAmount, description, 0, null, occurredAt,
                aggregateVersion, occurredAt);
    }

    public LoanSearchDocument(
            String loanId, String customerId, String productCode, String status,
            BigDecimal principalAmount, String description, int tenorMonths, String businessReference,
            Instant createdAt, long aggregateVersion, Instant occurredAt) {
        this.loanId = loanId;
        this.customerId = customerId;
        this.productCode = productCode;
        this.status = status;
        this.principalAmount = principalAmount;
        this.description = description;
        this.tenorMonths = tenorMonths;
        this.businessReference = businessReference;
        this.createdAt = createdAt;
        this.aggregateVersion = aggregateVersion;
        this.occurredAt = occurredAt;
    }

    public String getLoanId() { return loanId; }
    public String getCustomerId() { return customerId; }
    public String getProductCode() { return productCode; }
    public String getStatus() { return status; }
    public BigDecimal getPrincipalAmount() { return principalAmount; }
    public String getDescription() { return description; }
    public int getTenorMonths() { return tenorMonths; }
    public String getBusinessReference() { return businessReference; }
    public Instant getCreatedAt() { return createdAt; }
    public long getAggregateVersion() { return aggregateVersion; }
    public Instant getOccurredAt() { return occurredAt; }
}
