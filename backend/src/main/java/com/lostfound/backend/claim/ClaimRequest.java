package com.lostfound.backend.claim;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClaimRequest(
        @Size(max = 500) String answer,
        @NotBlank @Size(max = 1000) String message
) {
}
