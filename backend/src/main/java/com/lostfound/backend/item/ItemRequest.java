package com.lostfound.backend.item;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ItemRequest(
        @NotNull ItemType type,
        @NotBlank @Size(max = 100) String title,
        @Size(max = 2000) String description,
        @NotNull Long categoryId,
        @NotBlank @Size(max = 200) String locationText,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double lng,
        @NotNull @PastOrPresent LocalDate eventDate,
        @Size(max = 200) String verificationQuestion
) {
}
