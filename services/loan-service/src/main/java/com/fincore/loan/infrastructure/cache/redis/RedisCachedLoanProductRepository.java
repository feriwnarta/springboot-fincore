package com.fincore.loan.infrastructure.cache.redis;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fincore.loan.domain.model.LoanProduct;
import com.fincore.loan.domain.port.LoanProductRepository;
import com.fincore.loan.infrastructure.persistence.jdbc.JdbcLoanProductRepository;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Primary
@RequiredArgsConstructor
public class RedisCachedLoanProductRepository implements LoanProductRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisCachedLoanProductRepository.class);
    private static final String KEY_PREFIX = "fincore:loan:product:";
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    private final JdbcLoanProductRepository delegate;
    private final StringRedisTemplate redisTemplate;
    private static final ConcurrentHashMap<String, CompletableFuture<Optional<LoanProduct>>> IN_FLIGHT = new ConcurrentHashMap<>();

    @Value("${fincore.cache.product.ttl:5m}")
    private Duration ttl;

    @Value("${fincore.cache.product.ttl-jitter:30s}")
    private Duration ttlJitter;

    @Value("${fincore.cache.product.redis-timeout:250ms}")
    private Duration redisTimeout;

    @Value("${fincore.cache.product.database-timeout:2s}")
    private Duration databaseTimeout;

    @Override
    public List<LoanProduct> findActiveAfter(String codeCursor, int limit) {
        List<LoanProduct> products = delegate.findActiveAfter(codeCursor, limit);
        products.forEach(this::putBestEffort);
        return products;
    }

    @Override
    public Optional<LoanProduct> findActiveByCode(String code) {
        CompletableFuture<Optional<LoanProduct>> future = IN_FLIGHT.computeIfAbsent(code,
                ignored -> CompletableFuture.supplyAsync(() -> loadWithBoundedFallback(code))
                        .whenComplete((result, error) -> IN_FLIGHT.remove(code)));
        try {
            return future.get(databaseTimeout.plus(redisTimeout).toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            LOGGER.warn("loan_product_cache_load_failed key={} errorType={}", key(code),
                    exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public Optional<LoanProduct> findByCode(String code) {
        return delegate.findByCode(code);
    }

    @Override
    public void save(LoanProduct product) {
        delegate.save(product);
        evict(product.code());
    }

    public void evict(String code) {
        try {
            redisTemplate.delete(key(code));
        } catch (RuntimeException exception) {
            LOGGER.warn("loan_product_cache_evict_failed key={} errorType={}",
                    key(code), exception.getClass().getSimpleName());
        }
    }

    private void putBestEffort(LoanProduct product) {
        try {
            long jitterMillis = ttlJitter.isZero() ? 0
                    : ThreadLocalRandom.current().nextLong(ttlJitter.toMillis() + 1);
            redisTemplate.opsForValue().set(key(product.code()), OBJECT_MAPPER.writeValueAsString(product),
                    ttl.plusMillis(jitterMillis));
        } catch (JsonProcessingException | RuntimeException exception) {
            LOGGER.warn("loan_product_cache_write_failed key={} errorType={}",
                    key(product.code()), exception.getClass().getSimpleName());
        }
    }

    private Optional<LoanProduct> loadWithBoundedFallback(String code) {
        String cacheKey = key(code);
        try {
            String cached = CompletableFuture.supplyAsync(() -> redisTemplate.opsForValue().get(cacheKey))
                    .get(redisTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (cached != null) {
                LOGGER.debug("loan_product_cache_hit key={}", cacheKey);
                return Optional.of(OBJECT_MAPPER.readValue(cached, LoanProduct.class));
            }
            LOGGER.debug("loan_product_cache_miss key={}", cacheKey);
        } catch (Exception exception) {
            LOGGER.warn("loan_product_cache_fallback key={} errorType={}", cacheKey,
                    exception.getClass().getSimpleName());
        }

        try {
            Optional<LoanProduct> product = CompletableFuture.supplyAsync(() -> delegate.findActiveByCode(code))
                    .get(databaseTimeout.toMillis(), TimeUnit.MILLISECONDS);
            product.ifPresent(this::putBestEffort);
            return product;
        } catch (Exception exception) {
            LOGGER.warn("loan_product_database_timeout key={} timeout={}", cacheKey, databaseTimeout);
            return Optional.empty();
        }
    }

    private static String key(String code) {
        return KEY_PREFIX + code + ":v1";
    }
}
