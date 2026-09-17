package com.findwork.job;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public record JobSemanticAnalysis(
        String jobCategory,
        List<String> requiredSkills,
        List<String> preferredSkills,
        Integer minimumExperienceYears,
        Integer maximumExperienceYears,
        String educationLevel,
        String seniorityLevel,
        Boolean graduateFriendly,
        String employmentType,
        String workplaceType,
        List<String> responsibilities,
        Boolean workAuthorizationRequired,
        Boolean visaSponsorship,
        JsonNode salary,
        Map<String, Object> evidence
) {
}
