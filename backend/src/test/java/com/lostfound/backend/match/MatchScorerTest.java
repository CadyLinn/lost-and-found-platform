package com.lostfound.backend.match;

import static org.assertj.core.api.Assertions.assertThat;

import com.lostfound.backend.item.Category;
import com.lostfound.backend.item.Item;
import com.lostfound.backend.item.ItemType;
import com.lostfound.backend.user.User;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class MatchScorerTest {

    private static final Category PHONE = category(1L);
    private static final Category WALLET = category(2L);
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);
    // NTU main library, and a point about 0.3 km away.
    private static final double LAT = 25.0173, LNG = 121.5397;

    private static Category category(long id) {
        Category category = BeanUtils.instantiateClass(Category.class);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private static Item item(ItemType type, Category category, String title, LocalDate date, Double lat, Double lng) {
        Item item = new Item(new User("someone@test.com", "hash", "Someone"), type, category);
        item.applyDetails(type, title, null, category, "總圖書館", lat, lng, date, null);
        return item;
    }

    private final Item lostPhone = item(ItemType.LOST, PHONE, "黑色 iPhone 貓咪貼紙", MONDAY, LAT, LNG);

    @Test
    void nearlyIdenticalPostsScoreHighWithAllReasons() {
        Item found = item(ItemType.FOUND, PHONE, "撿到黑色 iPhone，有貓咪貼紙", MONDAY.plusDays(1), LAT + 0.003, LNG);

        MatchScorer.Result result = MatchScorer.score(lostPhone, found);

        assertThat(result.score()).isGreaterThan(0.8);
        assertThat(result.reasons()).containsExactlyInAnyOrder(
                MatchReason.SAME_CATEGORY, MatchReason.CLOSE_IN_TIME, MatchReason.NEARBY, MatchReason.SIMILAR_DESCRIPTION);
        assertThat(result.distanceKm()).isBetween(0.3, 0.4);
    }

    @Test
    void scoreIsTheSameWhicheverPostComesFirst() {
        Item found = item(ItemType.FOUND, PHONE, "黑色手機", MONDAY.plusDays(2), LAT + 0.01, LNG);

        assertThat(MatchScorer.score(lostPhone, found).score()).isEqualTo(MatchScorer.score(found, lostPhone).score());
    }

    @Test
    void differentCategoryLosesTheBiggestSignal() {
        Item samePhone = item(ItemType.FOUND, PHONE, "某個東西", MONDAY, LAT, LNG);
        Item wallet = item(ItemType.FOUND, WALLET, "某個東西", MONDAY, LAT, LNG);

        double difference = MatchScorer.score(lostPhone, samePhone).score() - MatchScorer.score(lostPhone, wallet).score();
        assertThat(difference).isCloseTo(MatchScorer.W_CATEGORY, org.assertj.core.api.Assertions.within(1e-9));
    }

    @Test
    void foundLongBeforeItWasLostIsUnlikely() {
        Item foundAfter = item(ItemType.FOUND, PHONE, "手機", MONDAY.plusDays(5), null, null);
        Item foundBefore = item(ItemType.FOUND, PHONE, "手機", MONDAY.minusDays(5), null, null);

        assertThat(MatchScorer.score(lostPhone, foundAfter).score())
                .isGreaterThan(MatchScorer.score(lostPhone, foundBefore).score());
        assertThat(MatchScorer.score(lostPhone, foundBefore).reasons()).doesNotContain(MatchReason.CLOSE_IN_TIME);
    }

    @Test
    void farAwayGetsNoDistancePointsButUnknownGetsSome() {
        Item far = item(ItemType.FOUND, PHONE, "手機", MONDAY, LAT + 0.2, LNG);    // ~22 km
        Item unknown = item(ItemType.FOUND, PHONE, "手機", MONDAY, null, null);

        MatchScorer.Result farResult = MatchScorer.score(lostPhone, far);
        MatchScorer.Result unknownResult = MatchScorer.score(lostPhone, unknown);

        assertThat(unknownResult.score()).isGreaterThan(farResult.score());
        assertThat(unknownResult.distanceKm()).isNull();
        assertThat(farResult.reasons()).doesNotContain(MatchReason.NEARBY);
    }

    @Test
    void scoreAlwaysStaysBetweenZeroAndOne() {
        Item nothingInCommon = item(ItemType.FOUND, WALLET, "雨傘", MONDAY.minusDays(60), LAT + 1, LNG + 1);

        assertThat(MatchScorer.score(lostPhone, nothingInCommon).score()).isBetween(0.0, 1.0);
        assertThat(MatchScorer.score(lostPhone, lostPhone).score()).isBetween(0.0, 1.0);
    }
}
