package com.findwork.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SalaryInfoTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesCurrencyRangeAndPeriodFromDescription() {
        SalaryInfo salary = SalaryInfo.fromText("Base salary: $120,000 - $180,000 USD per year");

        assertThat(salary.min()).isEqualByComparingTo(new BigDecimal("120000"));
        assertThat(salary.max()).isEqualByComparingTo(new BigDecimal("180000"));
        assertThat(salary.currency()).isEqualTo("USD");
        assertThat(salary.period()).isEqualTo("year");
        assertThat(salary.source()).isEqualTo("description");
    }

    @Test
    void prefersStructuredGreenhouseSalaryFields() throws Exception {
        var source = mapper.readTree("{\"pay_input_ranges\":[{\"min\":6000,\"max\":8000,\"currency\":\"SGD\",\"period\":\"month\"}]}");

        SalaryInfo salary = SalaryInfo.from(source, "Salary details are available on request");

        assertThat(salary.min()).isEqualByComparingTo(new BigDecimal("6000"));
        assertThat(salary.max()).isEqualByComparingTo(new BigDecimal("8000"));
        assertThat(salary.currency()).isEqualTo("SGD");
        assertThat(salary.period()).isEqualTo("month");
        assertThat(salary.source()).isEqualTo("structured");
    }

    @Test
    void supportsFlatStructuredSalaryFields() throws Exception {
        var source = mapper.readTree("{\"min_salary\":120000,\"max_salary\":180000,\"currency\":\"USD\",\"period\":\"year\"}");

        SalaryInfo salary = SalaryInfo.from(source, "");

        assertThat(salary.min()).isEqualByComparingTo(new BigDecimal("120000"));
        assertThat(salary.max()).isEqualByComparingTo(new BigDecimal("180000"));
        assertThat(salary.currency()).isEqualTo("USD");
        assertThat(salary.period()).isEqualTo("year");
    }

    @Test
    void ignoresUnrelatedNumbersWithoutSalaryContext() {
        assertThat(SalaryInfo.fromText("Posted 2026-09-17. Requires 3 years of experience.").present()).isFalse();
    }
}
