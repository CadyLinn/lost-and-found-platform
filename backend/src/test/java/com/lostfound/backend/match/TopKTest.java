package com.lostfound.backend.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TopKTest {

    @Test
    void keepsTheLargestKBestFirst() {
        TopK<Integer> top = new TopK<>(3, Comparator.<Integer>naturalOrder());
        List.of(5, 1, 9, 3, 7, 2, 8).forEach(top::offer);

        assertThat(top.best()).containsExactly(9, 8, 7);
    }

    @Test
    void returnsEverythingWhenFewerThanK() {
        TopK<Integer> top = new TopK<>(5, Comparator.<Integer>naturalOrder());
        List.of(2, 1).forEach(top::offer);

        assertThat(top.best()).containsExactly(2, 1);
    }

    @Test
    void matchesSortingOnRandomInput() {
        Random random = new Random(42);
        List<Integer> numbers = IntStream.range(0, 10_000).map(i -> random.nextInt(1_000_000)).boxed().toList();

        TopK<Integer> top = new TopK<>(10, Comparator.<Integer>naturalOrder());
        numbers.forEach(top::offer);

        List<Integer> expected = numbers.stream().sorted(Comparator.reverseOrder()).limit(10).toList();
        assertThat(top.best()).isEqualTo(expected);
    }

    @Test
    void rejectsNonPositiveK() {
        assertThatThrownBy(() -> new TopK<Integer>(0, Comparator.naturalOrder()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
