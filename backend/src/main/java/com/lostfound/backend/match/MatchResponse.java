package com.lostfound.backend.match;

import com.lostfound.backend.item.ItemResponse;
import java.io.Serializable;
import java.util.List;

/** One suggested post, with a 0–100 score and the reasons behind it. */
public record MatchResponse(ItemResponse item, int score, List<MatchReason> reasons, Double distanceKm)
        implements Serializable {
}
