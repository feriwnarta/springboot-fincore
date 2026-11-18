package com.fincore.search.application.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import com.fincore.platform.api.PageData;
import com.fincore.platform.api.PageMeta;
import com.fincore.platform.security.CurrentIdentity;
import com.fincore.search.api.dto.response.SavingsAccountSearchResponse;
import com.fincore.search.infrastructure.search.SavingsAccountSearchDocument;
import com.fincore.search.infrastructure.search.SavingsAccountSearchDocumentRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;

@Service
@RequiredArgsConstructor
public class SavingsAccountSearchQueryService {

    private static final String[] STAFF_ROLES = {"SAVINGS_OPERATOR", "LOAN_OPERATOR", "LOAN_APPROVER"};

    private final SavingsAccountSearchDocumentRepository repository;
    private final CurrentIdentity currentIdentity;

    public PageData<SavingsAccountSearchResponse> list(int limit, String cursor, String customerId, String status) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        Optional<String> scope = customerScope();
        if (scope.isPresent() && customerId != null && !scope.get().equals(customerId)) {
            throw new AccessDeniedException("Customer access denied");
        }
        String owner = scope.orElse(customerId);
        int page = decodeCursor(cursor);
        var pageable = PageRequest.of(page, limit + 1);
        List<SavingsAccountSearchDocument> documents;
        if (owner == null) {
            documents = status == null || status.isBlank()
                    ? repository.findAllByOrderByAccountNumber(pageable)
                    : repository.findByStatusOrderByAccountNumber(status, pageable);
        } else {
            documents = status == null || status.isBlank()
                    ? repository.findByCustomerIdOrderByAccountNumber(owner, pageable)
                    : repository.findByCustomerIdAndStatusOrderByAccountNumber(owner, status, pageable);
        }
        boolean hasNext = documents.size() > limit;
        var items = documents.stream().limit(limit).map(SavingsAccountSearchQueryService::toResponse).toList();
        return new PageData<>(items, new PageMeta(hasNext ? encodeCursor(page + 1) : null, hasNext, limit));
    }

    private Optional<String> customerScope() {
        for (String role : STAFF_ROLES) {
            if (currentIdentity.hasRole(role)) {
                return Optional.empty();
            }
        }
        if (currentIdentity.hasRole("CUSTOMER")) {
            return currentIdentity.customerId();
        }
        throw new AccessDeniedException("Access denied");
    }

    private static SavingsAccountSearchResponse toResponse(SavingsAccountSearchDocument document) {
        return new SavingsAccountSearchResponse(document.getAccountId(), document.getAccountNumber(),
                document.getCustomerId(), document.getCurrency(), document.getStatus(),
                document.getCurrentBalance().toPlainString(),
                document.getVersion(), document.getCreatedAt(), document.getUpdatedAt());
    }

    private static int decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            int page = Integer.parseInt(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
            if (page < 0) {
                throw new IllegalArgumentException("cursor must be non-negative");
            }
            return page;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("cursor must be a valid opaque cursor", exception);
        }
    }

    private static String encodeCursor(int page) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Integer.toString(page).getBytes(StandardCharsets.UTF_8));
    }
}
