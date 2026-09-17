package com.findwork.job;

public record GmailImportResult(
        String provider,
        String query,
        int messagesScanned,
        int imported,
        int failed
) {
}
