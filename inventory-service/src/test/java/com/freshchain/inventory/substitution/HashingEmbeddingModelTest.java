package com.freshchain.inventory.substitution;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The offline embedding model does not need to be clever, but it does need to be
 * deterministic, unit-length, and ordered sensibly on a product catalogue.
 */
class HashingEmbeddingModelTest {

    private static final Logger log = LoggerFactory.getLogger(HashingEmbeddingModelTest.class);

    private static final String CHICKEN_BREAST =
            "Chicken Breast Boneless 5lb. Category: POULTRY. Fresh boneless skinless chicken breast, food service pack";
    private static final String CHICKEN_THIGH =
            "Chicken Thigh Boneless 5lb. Category: POULTRY. Fresh boneless chicken thigh, food service pack";
    private static final String SALMON =
            "Atlantic Salmon Fillet 4lb. Category: SEAFOOD. Fresh Atlantic salmon fillet skin on";

    private final HashingEmbeddingModel model = new HashingEmbeddingModel();

    @Test
    void producesTheSameVectorEveryTime() {
        assertThat(model.embed(CHICKEN_BREAST)).containsExactly(model.embed(CHICKEN_BREAST));
    }

    @Test
    void matchesTheOpenAiDimensionSoTheColumnNeverHasToChange() {
        assertThat(model.dimensions()).isEqualTo(1536);
        assertThat(model.embed(CHICKEN_BREAST)).hasSize(1536);
    }

    @Test
    void producesUnitVectors() {
        double magnitude = 0.0;
        for (float value : model.embed(CHICKEN_BREAST)) {
            magnitude += (double) value * value;
        }
        assertThat(Math.sqrt(magnitude)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
    }

    @Test
    @DisplayName("two cuts of chicken sit closer together than chicken and salmon")
    void ranksTheCatalogueSensibly() {
        double chickenToChicken = cosine(model.embed(CHICKEN_BREAST), model.embed(CHICKEN_THIGH));
        double chickenToSalmon = cosine(model.embed(CHICKEN_BREAST), model.embed(SALMON));

        log.info("cosine similarity — chicken/chicken {}, chicken/salmon {}",
                String.format("%.3f", chickenToChicken), String.format("%.3f", chickenToSalmon));

        assertThat(chickenToChicken).isGreaterThan(chickenToSalmon);
        assertThat(chickenToChicken).isGreaterThan(0.5);
    }

    @Test
    void handlesEmptyTextWithoutBlowingUp() {
        assertThat(model.embed("")).hasSize(1536).containsOnly(0.0f);
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
        }
        return dot;
    }
}
