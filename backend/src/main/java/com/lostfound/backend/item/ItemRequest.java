package com.lostfound.backend.item;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.ZoneOffset;

public record ItemRequest(
        @NotNull ItemType type,
        @NotBlank @Size(max = 100) String title,
        @Size(max = 2000) String description,
        @NotNull Long categoryId,
        @NotBlank @Size(max = 200) String locationText,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double lng,
        @NotNull LocalDate eventDate,
        @Size(max = 200) String verificationQuestion
) {

    /** UTC+14 (Kiribati) is the first place on Earth where a new day begins. */
    static final ZoneOffset EARLIEST_TIMEZONE = ZoneOffset.ofHours(14);

    /**
     * The date can't be in the future. "Today" depends on where the user is: at 6 a.m. in Taipei it is
     * still yesterday in UTC, so comparing with the server's own date would wrongly reject today's posts.
     * A date only counts as future if it hasn't started anywhere in the world yet.
     */
    @JsonIgnore
    @AssertTrue(message = "must not be in the future")
    public boolean isEventDateNotInFuture() {
        return eventDate == null || !eventDate.isAfter(LocalDate.now(EARLIEST_TIMEZONE));
    }
}
