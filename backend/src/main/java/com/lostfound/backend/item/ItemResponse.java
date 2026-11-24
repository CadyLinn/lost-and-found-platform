package com.lostfound.backend.item;

import java.time.Instant;
import java.time.LocalDate;

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
        Instant updatedAt
) {

    public static ItemResponse from(Item item) {
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
                item.getUpdatedAt()
        );
    }
}
