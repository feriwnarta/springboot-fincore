package com.fincore.search.infrastructure.search;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

@Document(indexName = "fincore-savings-accounts-v1", createIndex = false)
@Setting(shards = 1, replicas = 0, refreshInterval = "1s")
public class SavingsAccountSearchDocument {

    @Id
    private String accountId;
    @Field(type = FieldType.Keyword)
    private String accountNumber;
    @Field(type = FieldType.Keyword)
    private String customerId;
    @Field(type = FieldType.Keyword)
    private String currency;
    @Field(type = FieldType.Keyword)
    private String status;
    @Field(type = FieldType.Double)
    private BigDecimal currentBalance;
    @Field(type = FieldType.Long)
    private long version;
    @Field(type = FieldType.Date)
    private Instant createdAt;
    @Field(type = FieldType.Date)
    private Instant updatedAt;

    protected SavingsAccountSearchDocument() {
    }

    public SavingsAccountSearchDocument(String accountId, String accountNumber, String customerId, String currency,
            String status, BigDecimal currentBalance, long version, Instant createdAt, Instant updatedAt) {
        this.accountId = accountId;
        this.accountNumber = accountNumber;
        this.customerId = customerId;
        this.currency = currency;
        this.status = status;
        this.currentBalance = currentBalance;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getAccountId() { return accountId; }
    public String getAccountNumber() { return accountNumber; }
    public String getCustomerId() { return customerId; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
