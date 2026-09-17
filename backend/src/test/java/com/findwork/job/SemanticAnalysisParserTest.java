package com.findwork.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticAnalysisParserTest {
    @Test
    void marksEvidenceThatCannotBeFoundInsteadOfDiscardingTheWholeAnalysis() {
        JobSemanticAnalysis result = SemanticAnalysisParser.parse("""
                {"jobCategory":"software_engineering","requiredSkills":["Java"],"evidence":{"requiredSkills":"Python"}}
                """, "Java Spring Boot experience required", new ObjectMapper());

        assertThat(result.requiredSkills()).containsExactly("Java");
        assertThat(result.evidence()).containsEntry("requiredSkills", "待人工核对");
    }
}
