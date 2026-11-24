package com.lostfound.backend.claim;

import com.lostfound.backend.item.ItemStatus;
import com.lostfound.backend.item.ItemType;
import java.time.Instant;

/**
 * One claim, as seen by either the item's poster or the claimant.
 * {@code contactEmail} is the other person's email, and is only filled in once the claim is approved.
 */
public record ClaimResponse(
        Long id,
        Long itemId,
        String itemTitle,
        ItemType itemType,
        ItemStatus itemStatus,
        ClaimStatus status,
        String answer,
        String message,
        Long claimantId,
        String claimantDisplayName,
        String contactEmail,
        Instant createdAt,
        Instant decidedAt
) {

    /** For the poster: after approval they can reach the claimant. */
    public static ClaimResponse forPoster(Claim claim) {
        return of(claim, claim.getClaimant().getEmail());
    }

    /** For the claimant: after approval they can reach the poster. */
    public static ClaimResponse forClaimant(Claim claim) {
        return of(claim, claim.getItem().getOwner().getEmail());
    }

    private static ClaimResponse of(Claim claim, String counterpartEmail) {
        boolean approved = claim.getStatus() == ClaimStatus.APPROVED;
        return new ClaimResponse(
                claim.getId(),
                claim.getItem().getId(),
                claim.getItem().getTitle(),
                claim.getItem().getType(),
                claim.getItem().getStatus(),
                claim.getStatus(),
                claim.getAnswer(),
                claim.getMessage(),
                claim.getClaimant().getId(),
                claim.getClaimant().getDisplayName(),
                approved ? counterpartEmail : null,
                claim.getCreatedAt(),
                claim.getDecidedAt()
        );
    }
}
