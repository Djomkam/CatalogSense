package com.djomkam.catalogsense.health;

/**
 * A catalog's Data Health score: the share of tracked SKUs without any open issue, from 0 to 100.
 * A catalog with nothing tracked scores 100 (nothing is wrong yet).
 */
public record HealthScore(int value) {

    public static HealthScore of(long trackedSkus, long skusWithIssues) {
        if (trackedSkus == 0) {
            return new HealthScore(100);
        }
        long cleanSkus = trackedSkus - skusWithIssues;
        return new HealthScore((int) Math.round(100.0 * cleanSkus / trackedSkus));
    }
}
