package com.lostfound.backend.match;

import com.lostfound.backend.item.GeoDistance;
import com.lostfound.backend.item.Item;
import com.lostfound.backend.item.ItemType;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Scores how likely a LOST post and a FOUND post are about the same object.
 * The score is a weighted sum of four signals, each between 0 and 1:
 *
 * <pre>
 *   category 35%  +  time 20%  +  distance 25%  +  text 20%   =  0.0 … 1.0
 * </pre>
 *
 * Pure logic with no database access, so it is easy to unit test.
 */
public final class MatchScorer {

    static final double W_CATEGORY = 0.35;
    static final double W_TIME = 0.20;
    static final double W_DISTANCE = 0.25;
    static final double W_TEXT = 0.20;

    /** Posts further apart than this in time get no time points. */
    public static final int MAX_DAYS_APART = 30;
    /** Posts further apart than this get no distance points. */
    static final double MAX_KM_APART = 5.0;
    /** Distance points given when one post has no coordinates: we simply don't know. */
    static final double UNKNOWN_DISTANCE_SCORE = 0.3;

    public record Result(double score, List<MatchReason> reasons, Double distanceKm) {
    }

    private MatchScorer() {
    }

    public static Result score(Item a, Item b) {
        List<MatchReason> reasons = new ArrayList<>();

        // 1. Category: a wallet is never a phone.
        double category = a.getCategory().getId().equals(b.getCategory().getId()) ? 1 : 0;
        if (category == 1) reasons.add(MatchReason.SAME_CATEGORY);

        // 2. Time: closer dates score higher. Something can't be found long before it was lost.
        Item lost = a.getType() == ItemType.LOST ? a : b;
        Item found = lost == a ? b : a;
        long foundMinusLost = ChronoUnit.DAYS.between(lost.getEventDate(), found.getEventDate());
        long daysApart = Math.abs(foundMinusLost);
        double time = Math.max(0, 1 - (double) daysApart / MAX_DAYS_APART);
        if (foundMinusLost < -1) time *= 0.3; // "found" more than a day before "lost": unlikely the same object
        if (daysApart <= 3 && foundMinusLost >= -1) reasons.add(MatchReason.CLOSE_IN_TIME);

        // 3. Distance: only when both posts have coordinates.
        double distance;
        Double km = null;
        if (a.getLat() != null && a.getLng() != null && b.getLat() != null && b.getLng() != null) {
            km = GeoDistance.haversineKm(a.getLat(), a.getLng(), b.getLat(), b.getLng());
            distance = Math.max(0, 1 - km / MAX_KM_APART);
            if (km <= 1) reasons.add(MatchReason.NEARBY);
        } else {
            distance = UNKNOWN_DISTANCE_SCORE;
        }

        // 4. Text: title, description and place name.
        double text = TextSimilarity.cosine(describe(a), describe(b));
        if (text >= 0.25) reasons.add(MatchReason.SIMILAR_DESCRIPTION);

        double score = W_CATEGORY * category + W_TIME * time + W_DISTANCE * distance + W_TEXT * text;
        return new Result(score, reasons, km == null ? null : Math.round(km * 100) / 100.0);
    }

    private static String describe(Item item) {
        return String.join(" ",
                item.getTitle(),
                item.getDescription() == null ? "" : item.getDescription(),
                item.getLocationText());
    }
}
