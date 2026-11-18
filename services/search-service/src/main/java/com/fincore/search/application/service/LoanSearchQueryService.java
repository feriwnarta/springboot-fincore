package com.fincore.search.application.service;

import static com.fincore.search.infrastructure.search.LoanSearchDocument.LEGACY_UNKNOWN_CUSTOMER_ID;

import java.util.List;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.fincore.platform.security.CurrentIdentity;
import com.fincore.platform.api.PageData;
import com.fincore.platform.api.PageMeta;
import com.fincore.search.api.dto.response.LoanActivityResponse;
import com.fincore.search.api.dto.response.LoanApplicationSearchResponse;
import com.fincore.search.api.dto.response.LoanProductSearchResponse;
import com.fincore.search.api.dto.response.LoanSearchResponse;
import com.fincore.search.infrastructure.search.LoanActivityDocument;
import com.fincore.search.infrastructure.search.LoanActivityDocumentRepository;
import com.fincore.search.infrastructure.search.LoanSearchDocument;
import com.fincore.search.infrastructure.search.LoanSearchDocumentRepository;
import com.fincore.search.infrastructure.search.LoanProductSearchDocument;
import com.fincore.search.infrastructure.search.LoanProductSearchDocumentRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LoanSearchQueryService {

    private final LoanSearchDocumentRepository searchRepository;
    private final LoanActivityDocumentRepository activityRepository;
    private final CurrentIdentity currentIdentity;
    private final LoanProductSearchDocumentRepository productRepository;

    @Autowired
    public LoanSearchQueryService(LoanSearchDocumentRepository searchRepository,
            LoanActivityDocumentRepository activityRepository, CurrentIdentity currentIdentity,
            LoanProductSearchDocumentRepository productRepository) {
        this.searchRepository = searchRepository;
        this.activityRepository = activityRepository;
        this.currentIdentity = currentIdentity;
        this.productRepository = productRepository;
    }

    public LoanSearchQueryService(LoanSearchDocumentRepository searchRepository,
            LoanActivityDocumentRepository activityRepository, CurrentIdentity currentIdentity) {
        this(searchRepository, activityRepository, currentIdentity, null);
    }

    public PageData<LoanApplicationSearchResponse> listApplications(
            String status, String customerId, String productCode, int limit, String cursor) {
        int boundedLimit = boundedLimit(limit);
        Optional<String> scope = customerScope();
        Optional<String> requestedOwner = scope.isPresent() ? scope : optionalText(customerId);
        if (scope.isPresent() && customerId != null && !scope.get().equals(customerId)) {
            throw new AccessDeniedException("Customer access denied");
        }
        int page = decodeCursor(cursor);
        List<LoanSearchDocument> documents = searchRepository.findMatching(
                requestedOwner, null, status, productCode, PageRequest.of(page, boundedLimit + 1));
        boolean hasNext = documents.size() > boundedLimit;
        var items = documents.stream().limit(boundedLimit).map(LoanSearchQueryService::toApplicationResponse).toList();
        return new PageData<>(items, new PageMeta(hasNext ? encodeCursor(page + 1) : null, hasNext, boundedLimit));
    }

    public PageData<LoanProductSearchResponse> listProducts(int limit, String cursor) {
        if (productRepository == null) {
            throw new IllegalStateException("Loan product search repository is not configured");
        }
        int boundedLimit = boundedLimit(limit);
        int page = decodeCursor(cursor);
        List<LoanProductSearchDocument> documents = productRepository.findByActiveTrueOrderByCode(
                PageRequest.of(page, boundedLimit + 1));
        boolean hasNext = documents.size() > boundedLimit;
        var items = documents.stream().limit(boundedLimit).map(LoanSearchQueryService::toProductResponse).toList();
        return new PageData<>(items, new PageMeta(hasNext ? encodeCursor(page + 1) : null, hasNext, boundedLimit));
    }

    public List<LoanSearchResponse> search(String query, String status, String productCode, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        var page = PageRequest.of(0, boundedLimit);
        Optional<String> customerId = customerScope();
        return searchRepository.findMatching(customerId, query, status, productCode, page).stream()
                .filter(document -> customerId.isEmpty() || matchesCustomer(customerId.get(), document.getCustomerId()))
                .map(LoanSearchQueryService::toResponse)
                .toList();
    }

    public Optional<LoanActivityResponse> activity(String loanId) {
        String customerId = customerScope().orElse(null);
        Optional<LoanActivityDocument> document = customerId == null
                ? activityRepository.findById(loanId)
                : activityRepository.findByLoanIdAndCustomerId(loanId, customerId)
                        .filter(activity -> matchesCustomer(customerId, activity.getCustomerId()));
        return document.map(activity -> new LoanActivityResponse(
                activity.getLoanId(), activity.getCurrentStatus(), activity.getActivities().stream()
                        .map(entry -> new LoanActivityResponse.Activity(entry.eventId(), entry.type(), entry.at()))
                        .toList()));
    }

    private static boolean matchesCustomer(String customerId, String storedCustomerId) {
        return StringUtils.hasText(storedCustomerId)
                && !LEGACY_UNKNOWN_CUSTOMER_ID.equals(storedCustomerId)
                && customerId.equals(storedCustomerId);
    }

    private Optional<String> customerScope() {
        if (currentIdentity.hasRole("LOAN_OPERATOR") || currentIdentity.hasRole("LOAN_APPROVER")) {
            return Optional.empty();
        }
        if (currentIdentity.hasRole("CUSTOMER")) {
            Optional<String> customerId = currentIdentity.customerId();
            if (customerId.isPresent()) {
                return customerId;
            }
        }
        throw new AccessDeniedException("Access denied");
    }

    private static LoanSearchResponse toResponse(LoanSearchDocument document) {
        return new LoanSearchResponse(document.getLoanId(), document.getCustomerId(), document.getProductCode(),
                document.getStatus(), document.getPrincipalAmount(), document.getDescription(),
                document.getAggregateVersion(), document.getOccurredAt());
    }

    private static LoanApplicationSearchResponse toApplicationResponse(LoanSearchDocument document) {
        return new LoanApplicationSearchResponse(document.getLoanId(), document.getCustomerId(),
                document.getProductCode(), document.getPrincipalAmount().toPlainString(), document.getTenorMonths(),
                document.getStatus(), document.getAggregateVersion(), document.getBusinessReference(),
                document.getCreatedAt());
    }

    private static LoanProductSearchResponse toProductResponse(LoanProductSearchDocument document) {
        return new LoanProductSearchResponse(document.getProductId(), document.getCode(), document.getInterestType(),
                document.getAnnualRate().toPlainString(), document.getMinAmount().toPlainString(),
                document.getMaxAmount().toPlainString(), document.isActive(),
                document.getVersion(), document.getUpdatedAt());
    }

    private static int boundedLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        return limit;
    }

    private static Optional<String> optionalText(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
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
