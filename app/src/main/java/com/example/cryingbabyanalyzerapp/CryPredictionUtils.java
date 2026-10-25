package com.example.cryingbabyanalyzerapp;

import java.util.Map;

public final class CryPredictionUtils {

    public static final float LOW_CONFIDENCE_THRESHOLD = 0.60f;

    private CryPredictionUtils() {
    }

    public static String getSecondBestLabel(
            Map<String, Float> scores,
            String primaryLabel
    ) {
        if (scores == null || scores.isEmpty()) return null;

        String secondLabel = null;
        float secondScore = -1f;

        for (Map.Entry<String, Float> entry : scores.entrySet()) {
            String label = entry.getKey();
            Float score = entry.getValue();

            if (label == null || score == null) continue;
            if (primaryLabel != null && primaryLabel.equalsIgnoreCase(label)) continue;

            if (score > secondScore) {
                secondScore = score;
                secondLabel = label;
            }
        }

        return secondLabel;
    }

    public static Float getScore(Map<String, Float> scores, String label) {
        if (scores == null || label == null) return null;

        for (Map.Entry<String, Float> entry : scores.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(label)) {
                return entry.getValue();
            }
        }

        return null;
    }
}
