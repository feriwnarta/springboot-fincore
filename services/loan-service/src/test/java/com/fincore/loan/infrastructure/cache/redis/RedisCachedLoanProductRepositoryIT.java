package com.fincore.loan.infrastructure.cache.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.fincore.loan.domain.enums.InterestType;
import com.fincore.loan.domain.model.LoanProduct;
import com.fincore.loan.infrastructure.persistence.jdbc.JdbcLoanProductRepository;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RedisCachedLoanProductRepositoryIT {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    @Test
    void twoRepositoryInstancesShareProductCache() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();

        JdbcLoanProductRepository delegate = mock(JdbcLoanProductRepository.class);
        LoanProduct product = new LoanProduct(UUID.randomUUID(), "PERSONAL_FLAT", InterestType.FLAT,
                new BigDecimal("0.1200"), new BigDecimal("1000"), new BigDecimal("100000"), true, 1,
                Instant.parse("2026-01-01T00:00:00Z"));
        when(delegate.findActiveByCode(product.code())).thenReturn(Optional.of(product));

        RedisCachedLoanProductRepository first = repository(delegate, template);
        RedisCachedLoanProductRepository second = repository(delegate, template);
        assertThat(first.findActiveByCode(product.code())).contains(product);
        assertThat(second.findActiveByCode(product.code())).contains(product);
        verify(delegate).findActiveByCode(product.code());

        template.delete("fincore:loan:product:" + product.code() + ":v1");
        connectionFactory.destroy();
    }

    private static RedisCachedLoanProductRepository repository(
            JdbcLoanProductRepository delegate, StringRedisTemplate template) {
        RedisCachedLoanProductRepository repository = new RedisCachedLoanProductRepository(delegate, template);
        ReflectionTestUtils.setField(repository, "ttl", Duration.ofMinutes(5));
        ReflectionTestUtils.setField(repository, "ttlJitter", Duration.ZERO);
        ReflectionTestUtils.setField(repository, "redisTimeout", Duration.ofSeconds(1));
        ReflectionTestUtils.setField(repository, "databaseTimeout", Duration.ofSeconds(2));
        return repository;
    }
}
