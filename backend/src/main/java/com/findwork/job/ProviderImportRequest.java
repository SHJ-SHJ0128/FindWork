package com.findwork.job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProviderImportRequest(@NotBlank @Size(max = 200) String board) {
}
