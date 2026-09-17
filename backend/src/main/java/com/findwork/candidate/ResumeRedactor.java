package com.findwork.candidate;

import org.springframework.stereotype.Component;

@Component
public class ResumeRedactor {
    public String redact(String text) {
        return text
                .replaceAll("(?i)\\b[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b", "[redacted-email]")
                .replaceAll("(?<!\\d)(?:\\+?\\d[\\d ()-]{7,}\\d)(?!\\d)", "[redacted-phone]")
                .replaceAll("(?i)(住址|地址|address)\\s*[:：]?\\s*[^,，;；\\n]{3,}", "$1: [redacted-address]")
                .replaceAll("(?i)(姓名|full\\s+name)\\s*[:：]?\\s*[\\p{L} .'-]{2,40}(?=\\s+(?:电话|手机|邮箱|email|phone|教育|工作|项目|技能|education)|$)", "$1: [redacted-name]")
                .replaceAll("\\s+", " ").trim();
    }
}
