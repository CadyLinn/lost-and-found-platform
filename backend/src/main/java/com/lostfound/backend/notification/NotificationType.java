package com.lostfound.backend.notification;

public enum NotificationType {
    /** To the poster: someone responded to your post. */
    CLAIM_RECEIVED,
    /** To the claimant: the poster accepted your claim. */
    CLAIM_APPROVED,
    /** To the claimant: the poster declined your claim. */
    CLAIM_REJECTED,
    /** To the approved claimant: the poster marked the item as returned. */
    ITEM_RETURNED
}
