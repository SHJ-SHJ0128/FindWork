package com.findwork.job;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record SalaryInfo(
        BigDecimal min,
        BigDecimal max,
        String currency,
        String period,
        String text,
        String source
) {
    private static final Pattern AMOUNT = Pattern.compile(
            "(?<![\\p{L}\\d])(?:(USD|SGD|CNY|RMB|HKD|GBP|EUR|S\\$|HK\\$|US\\$|CA\\$|人民币|[$¥￥£€])\\s*)?" +
                    "((?:\\d{1,3}(?:,\\d{3})+|\\d+(?:\\.\\d+)?))(K|M|B|万)?" +
                    "(?:\\s*[-–—~至到]\\s*(?:(USD|SGD|CNY|RMB|HKD|GBP|EUR|S\\$|HK\\$|US\\$|CA\\$|人民币|[$¥￥£€])\\s*)?" +
                    "((?:\\d{1,3}(?:,\\d{3})+|\\d+(?:\\.\\d+)?))(K|M|B|万)?)?" +
                    "(?:\\s*(USD|SGD|CNY|RMB|HKD|GBP|EUR|S\\$|HK\\$|US\\$|CA\\$|人民币))?" +
                    "(?:\\s*(?:(?:/|per)\\s*)?(hour|hr|hourly|day|daily|month|monthly|mo|year|yr|yearly|小时|天|月|个月|年))?(?![\\p{L}])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FOLLOWING_PAY = Pattern.compile("(?i)\\b(?:salary|pay|compensation|base\\s*(?:salary|pay)|薪资|薪酬|月薪|年薪|hourly\\s+rate)\\b");

    static SalaryInfo from(JsonNode source, String description) {
        SalaryInfo structured = fromStructured(source);
        return structured.present() ? structured : fromText(description, "description");
    }

    static SalaryInfo fromText(String text) {
        return fromText(text, "description");
    }

    private static SalaryInfo fromText(String text, String source) {
        if (text == null || text.isBlank()) return empty();
        Matcher matcher = AMOUNT.matcher(text);
        while (matcher.find()) {
            String firstCurrency = matcher.group(1);
            String firstNumber = matcher.group(2);
            String firstSuffix = matcher.group(3);
            String secondCurrency = matcher.group(4);
            String secondNumber = matcher.group(5);
            String secondSuffix = matcher.group(6);
            String suffixCurrency = matcher.group(7);
            String period = normalizePeriod(matcher.group(8));
            boolean hasRange = secondNumber != null;
            String raw = text.substring(matcher.start(), matcher.end()).trim();
            Matcher extra = Pattern.compile("(?i)\\s*[·•]\\s*\\d+\\s*薪").matcher(text.substring(matcher.end()));
            if (extra.find() && extra.start() == 0) raw += extra.group();
            String currency = normalizeCurrency(firstCurrency != null ? firstCurrency
                    : secondCurrency != null ? secondCurrency : suffixCurrency);
            boolean explicitCurrency = currency != null;
            boolean salaryContext = hasSalaryContext(text, matcher.start(), matcher.end());
            if (!explicitCurrency && !salaryContext && period == null) continue;
            BigDecimal min = amount(firstNumber, firstSuffix == null ? secondSuffix : firstSuffix);
            BigDecimal max = hasRange ? amount(secondNumber, secondSuffix == null ? firstSuffix : secondSuffix) : null;
            if (min == null) continue;
            if (hasRange && max == null) continue;
            return new SalaryInfo(min, max, currency, period, raw, source);
        }
        return empty();
    }

    private static SalaryInfo fromStructured(JsonNode source) {
        if (source == null || source.isMissingNode()) return empty();
        Map<String, JsonNode> fields = new HashMap<>();
        collectStructured(source, fields, false);
        BigDecimal min = number(first(fields, "salarymin", "minsalary", "minimumsalary", "minpay", "minimum", "min"));
        BigDecimal max = number(first(fields, "salarymax", "maxsalary", "maximumsalary", "maxpay", "maximum", "max"));
        String currency = normalizeCurrency(text(first(fields, "salarycurrency", "currency", "paycurrency")));
        String period = normalizePeriod(text(first(fields, "salaryperiod", "period", "payperiod")));
        String raw = text(first(fields, "salarytext", "salaryrange", "payrange", "compensation", "salary", "pay", "薪资", "薪酬", "月薪", "年薪"));
        SalaryInfo parsed = raw == null ? empty() : fromText(raw, "structured");
        if (min == null) min = parsed.min();
        if (max == null) max = parsed.max();
        if (currency == null) currency = parsed.currency();
        if (period == null) period = parsed.period();
        if (raw == null) raw = parsed.text();
        if (min == null && max == null && raw == null) return empty();
        return new SalaryInfo(min, max, currency, period, raw, "structured");
    }

    private static void collectStructured(JsonNode node, Map<String, JsonNode> fields, boolean salaryContext) {
        if (node == null) return;
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> field = iterator.next();
                String key = normalizeKey(field.getKey());
                JsonNode value = field.getValue();
                boolean currentSalaryContext = salaryContext || isSalaryKey(key) || key.equals("currency") || key.equals("period");
                if (currentSalaryContext && (isSalaryKey(key) || isSalaryLeaf(key))) fields.putIfAbsent(key, value);
                if ("name".equals(key) && value.isTextual() && isSalaryKey(normalizeKey(value.asText()))) {
                    JsonNode sibling = node.get("value");
                    if (sibling != null) fields.putIfAbsent(normalizeKey(value.asText()), sibling);
                }
                collectStructured(value, fields, currentSalaryContext);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) collectStructured(child, fields, salaryContext);
        }
    }

    private static boolean isSalaryKey(String key) {
        return key.contains("salary") || key.contains("pay") || key.contains("compensation") || key.contains("range") || key.contains("rate")
                || key.contains("薪资") || key.contains("薪酬") || key.contains("月薪") || key.contains("年薪");
    }

    private static boolean isSalaryLeaf(String key) {
        return key.equals("min") || key.equals("max") || key.equals("minimum") || key.equals("maximum")
                || key.equals("currency") || key.equals("period") || key.equals("text") || key.equals("value");
    }

    private static JsonNode first(Map<String, JsonNode> fields, String... names) {
        for (String name : names) {
            JsonNode value = fields.get(name);
            if (value != null) return value;
        }
        return null;
    }

    private static BigDecimal number(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isNumber()) return node.decimalValue();
        return amount(node.asText(""), null);
    }

    private static BigDecimal amount(String value, String suffix) {
        if (value == null || value.isBlank()) return null;
        try {
            BigDecimal result = new BigDecimal(value.replace(",", "").trim());
            if (suffix == null) return result;
            return switch (suffix.toUpperCase(Locale.ROOT)) {
                case "K" -> result.multiply(BigDecimal.valueOf(1_000));
                case "M" -> result.multiply(BigDecimal.valueOf(1_000_000));
                case "B" -> result.multiply(BigDecimal.valueOf(1_000_000_000));
                case "万" -> result.multiply(BigDecimal.valueOf(10_000));
                default -> result;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean hasSalaryContext(String text, int start, int end) {
        int from = Math.max(0, start - 40);
        int to = Math.min(text.length(), end + 40);
        String context = text.substring(from, to).toLowerCase(Locale.ROOT);
        return FOLLOWING_PAY.matcher(context).find()
                || context.contains("薪资") || context.contains("薪酬") || context.contains("月薪") || context.contains("年薪");
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isValueNode()) {
            String value = node.asText("").trim();
            return value.isBlank() ? null : value;
        }
        return null;
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static String normalizeCurrency(String value) {
        if (value == null || value.isBlank()) return null;
        String currency = value.trim().toUpperCase(Locale.ROOT);
        return switch (currency) {
            case "S$", "SGD" -> "SGD";
            case "HK$", "HKD" -> "HKD";
            case "US$", "CA$", "$", "USD" -> "USD";
            case "¥", "￥", "CNY", "RMB", "人民币" -> "CNY";
            case "£", "GBP" -> "GBP";
            case "€", "EUR" -> "EUR";
            default -> currency.length() == 3 ? currency : null;
        };
    }

    private static String normalizePeriod(String value) {
        if (value == null || value.isBlank()) return null;
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "hour", "hr", "hourly", "小时" -> "hour";
            case "day", "daily", "天" -> "day";
            case "month", "monthly", "mo", "月", "个月" -> "month";
            case "year", "yr", "yearly", "年" -> "year";
            default -> null;
        };
    }

    private static SalaryInfo empty() {
        return new SalaryInfo(null, null, null, null, null, null);
    }

    boolean present() {
        return min != null || max != null || (text != null && !text.isBlank());
    }
}
