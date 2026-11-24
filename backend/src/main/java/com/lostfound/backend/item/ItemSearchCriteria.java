package com.lostfound.backend.item;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Optional filters for browsing items. Any field may be null, meaning "don't filter on this".
 * Location filtering needs all three of lat, lng and radiusKm.
 */
public record ItemSearchCriteria(
        String q,
        ItemType type,
        Long categoryId,
        ItemStatus status,
        LocalDate from,
        LocalDate to,
        Double lat,
        Double lng,
        Double radiusKm
) implements Serializable {

    public boolean hasLocation() {
        return lat != null && lng != null && radiusKm != null;
    }

    /** Trimmed keyword, or null when blank. */
    public String keyword() {
        if (q == null) return null;
        String trimmed = q.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
