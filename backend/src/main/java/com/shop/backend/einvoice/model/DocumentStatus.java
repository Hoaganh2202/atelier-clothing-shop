package com.shop.backend.einvoice.model;

public enum DocumentStatus {
    DRAFT,
    PENDING_ISSUE,
    ISSUING,
    ISSUANCE_UNKNOWN,
    RECONCILING,
    ISSUED,
    ISSUE_FAILED
}
