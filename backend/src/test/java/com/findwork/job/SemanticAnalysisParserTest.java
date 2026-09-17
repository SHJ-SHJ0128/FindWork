package com.findwork.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticAnalysisParserTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesStrictJsonAndKeepsExactEvidence() {
        JobSemanticAnalysis result = SemanticAnalysisParser.parse("""
                {"jobCategory":"software_engineering","requiredSkills":["Java"],"preferredSkills":[],
                 "minimumExperienceYears":null,"maximumExperienceYears":null,"educationLevel":"bachelor",
                 "seniorityLevel":"entry","graduateFriendly":true,"employmentType":"full-time",
                 "workplaceType":"onsite","responsibilities":["Build services"],
                 "workAuthorizationRequired":null,"visaSponsorship":null,"salary":null,
                 "evidence":{"seniority":"entry-level position"}}
                """, "This is an entry-level position. Build services with Java.", mapper);

        assertThat(result.jobCategory()).isEqualTo("software_engineering");
        assertThat(result.requiredSkills()).containsExactly("Java");
        assertThat(result.graduateFriendly()).isTrue();
        assertThat(result.evidence()).containsEntry("seniority", "entry-level position");
    }

    @Test
    void allowsJsonFenceButRejectsExtraTextAndUnknownFields() {
        assertThat(SemanticAnalysisParser.parse("```json\n{}\n```", "", mapper)).isNotNull();
        assertThatThrownBy(() -> SemanticAnalysisParser.parse("Here is the JSON: {}", "", mapper))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SemanticAnalysisParser.parse("{\"unexpected\":true}", "", mapper))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMalformedJsonAndEvidenceOutsideDescription() {
        assertThatThrownBy(() -> SemanticAnalysisParser.parse("{", "", mapper))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SemanticAnalysisParser.parse("{\"evidence\":{\"x\":\"not present\"}}", "A job description", mapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidence");
    }

    @Test
    void normalizesCommonModelStringShapes() {
        JobSemanticAnalysis result = SemanticAnalysisParser.parse("""
                {"requiredSkills":"Java, Spring Boot, SQL",
                 "responsibilities":"Build services; review code",
                 "evidence":"Java Spring Boot SQL service."}
                """, "Java Spring Boot SQL service.", mapper);

        assertThat(result.requiredSkills()).containsExactly("Java", "Spring Boot", "SQL");
        assertThat(result.responsibilities()).containsExactly("Build services", "review code");
        assertThat(result.evidence()).containsEntry("text", "Java Spring Boot SQL service.");
    }
}
