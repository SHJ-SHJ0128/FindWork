package com.findwork.candidate;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateProfileTest {
    @Test
    void storesTheConfirmedChinaAndSingaporePreferencesAsPlainData() {
        var profile = new CandidateProfile(
                List.of("Backend", "AI Application", "Solutions Engineering"),
                List.of("重庆", "成都", "广州", "深圳", "杭州"),
                List.of("China", "Singapore"),
                true,
                "internship-and-entry-level",
                List.of("Java", "Spring Boot")
        );

        assertThat(profile.allowedCities()).containsExactly("重庆", "成都", "广州", "深圳", "杭州");
        assertThat(profile.remoteAllowed()).isTrue();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(profile)).isEmpty();
        }
    }
}
