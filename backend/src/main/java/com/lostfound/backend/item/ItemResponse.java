package com.lostfound.backend.item;

import com.lostfound.backend.photo.PhotoResponse;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * An item as sent to clients. Serializable so it can be stored in the Redis cache.
 * {@code distanceKm} is only set when the request searched "near" a point.
 */
public record ItemResponse(
        Long id,
        ItemType type,
        String title,
        String description,
        Long categoryId,
        String categoryName,
        String locationText,
        Double lat,
        Double lng,
        LocalDate eventDate,
        ItemStatus status,
        String verificationQuestion,
        Long ownerId,
        String ownerDisplayName,
        Instant createdAt,
        Instant updatedAt,
        Double distanceKm,
        List<PhotoResponse> photos,
        /* Only the author and admins can ever see a hidden post. */
        boolean hidden,
        String hiddenReason
) implements Serializable {

    public static ItemResponse from(Item item) {
        return from(item, null);
    }

    /** Same as {@link #from(Item)}, plus the distance from the searched point when there is one. */
    public static ItemResponse from(Item item, ItemSearchCriteria criteria) {
        Double distanceKm = null;
        if (criteria != null && criteria.hasLocation() && item.getLat() != null && item.getLng() != null) {
            double km = GeoDistance.haversineKm(criteria.lat(), criteria.lng(), item.getLat(), item.getLng());
            distanceKm = Math.round(km * 100) / 100.0;
        }
        return new ItemResponse(
                item.getId(),
                item.getType(),
                item.getTitle(),
                item.getDescription(),
                item.getCategory().getId(),
                item.getCategory().getName(),
                item.getLocationText(),
                item.getLat(),
                item.getLng(),
                item.getEventDate(),
                item.getStatus(),
                item.getVerificationQuestion(),
                item.getOwner().getId(),
                item.getOwner().getDisplayName(),
                item.getCreatedAt(),
                item.getUpdatedAt(),
                distanceKm,
                new ArrayList<>(item.getPhotos().stream().map(PhotoResponse::from).toList()),
                item.isHidden(),
                item.getHiddenReason()
        );
    }
}
