package com.findwork.candidate;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CandidateProfile(
        @NotEmpty @Size(max = 12) List<String> targetRoleFamilies,
        @NotEmpty @Size(max = 10) List<String> allowedCities,
        @NotEmpty @Size(max = 4) List<String> preferredRegions,
        @NotNull Boolean remoteAllowed,
        @NotEmpty String experienceLevel,
        @Size(max = 100) List<String> skills
) {
}
