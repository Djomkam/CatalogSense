package com.djomkam.catalogsense.health;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthScoreTest {

    @Test
    void scoreIsTheRoundedShareOfSkusWithoutIssues() {
        assertThat(HealthScore.of(4, 1).value()).isEqualTo(75);
        assertThat(HealthScore.of(3, 1).value()).isEqualTo(67);
    }

    @Test
    void anEmptyCatalogScores100() {
        assertThat(HealthScore.of(0, 0).value()).isEqualTo(100);
    }

    @Test
    void everySkuWithAnIssueScores0() {
        assertThat(HealthScore.of(5, 5).value()).isZero();
    }
}
