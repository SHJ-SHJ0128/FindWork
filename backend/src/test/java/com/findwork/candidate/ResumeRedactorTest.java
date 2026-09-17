package com.findwork.candidate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeRedactorTest {
    @Test
    void redactsContactDetailsBeforeExternalAiCall() {
        String result = new ResumeRedactor().redact("姓名：Alice Email alice@example.com, phone +65 8123 4567");

        assertThat(result).doesNotContain("Alice", "alice@example.com", "+65 8123 4567")
                .contains("[redacted-name]", "[redacted-email]", "[redacted-phone]");
    }
}
