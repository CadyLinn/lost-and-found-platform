package com.lostfound.backend.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class TextSimilarityTest {

    @Test
    void chineseIsSplitIntoBigrams() {
        assertThat(TextSimilarity.tokenize("咖啡色皮夾"))
                .containsOnlyKeys("咖啡", "啡色", "色皮", "皮夾");
    }

    @Test
    void englishIsSplitIntoLowercaseWordsAndStopWordsAreDropped() {
        assertThat(TextSimilarity.tokenize("Lost my Black iPhone 15!"))
                .containsOnlyKeys("black", "iphone", "15");
    }

    @Test
    void mixedTextKeepsBothKinds() {
        assertThat(TextSimilarity.tokenize("黑色iPhone手機"))
                .containsOnlyKeys("黑色", "iphone", "手機");
    }

    @Test
    void identicalTextScoresOne() {
        assertThat(TextSimilarity.cosine("黑色 iPhone 貓咪貼紙", "黑色 iPhone 貓咪貼紙")).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void unrelatedTextScoresZero() {
        assertThat(TextSimilarity.cosine("黑色手機", "藍色雨傘")).isZero();
    }

    @Test
    void similarDescriptionsScoreHigherThanDifferentOnes() {
        String lost = "黑色 iPhone 透明手機殼 貓咪貼紙";
        double similar = TextSimilarity.cosine(lost, "撿到黑色手機，背面有貓咪貼紙");
        double different = TextSimilarity.cosine(lost, "咖啡色皮夾，裡面有學生證");
        assertThat(similar).isGreaterThan(0.2).isGreaterThan(different);
    }

    @Test
    void emptyOrNullTextScoresZero() {
        assertThat(TextSimilarity.cosine(null, "手機")).isZero();
        assertThat(TextSimilarity.cosine("", "")).isZero();
        assertThat(TextSimilarity.cosine("!!!", "手機")).isZero();
    }
}
