package com.freshchain.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.freshchain.inventory.domain.InventoryLot;
import com.freshchain.inventory.repository.InventoryLotRepository;
import com.freshchain.inventory.substitution.SubstitutionAdvisor;
import com.freshchain.inventory.substitution.SubstitutionCandidate;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Vector search against a real pgvector store, with the guard in front of it.
 *
 * <p>The assertions that matter are the negative ones. A ranking can be merely
 * unhelpful; a suggestion that introduces an allergen is a safety failure, and
 * that outcome has to be impossible rather than unlikely.
 */
class SubstitutionAdvisorIT extends AbstractIntegrationTest {

    private static final String BURGER_BUNS = "BRD-BRGR-96CT";  // wheat, sesame, soy
    private static final String SUB_ROLLS = "BRD-SUB-72CT";     // wheat, soy
    private static final String CHICKEN_BREAST = "CHK-BRST-5LB";
    private static final String CHICKEN_THIGH = "CHK-THGH-5LB";
    private static final String SALMON = "SLM-FLLT-4LB";

    @Autowired
    private SubstitutionAdvisor advisor;

    @Autowired
    private InventoryLotRepository lots;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    @DisplayName("a bun carrying sesame is never offered against a roll that does not")
    void neverSuggestsAProductThatIntroducesAnAllergen() {
        stock(BURGER_BUNS, 200);

        List<SubstitutionCandidate> advice = advisor.adviseFor(SUB_ROLLS, TestFixtures.WAREHOUSE, 40);

        assertThat(advice).extracting(SubstitutionCandidate::suggestedSku)
                .as("burger buns add sesame, which the ordered rolls do not carry")
                .doesNotContain(BURGER_BUNS);
    }

    @Test
    @DisplayName("dropping an allergen is fine, so rolls may stand in for buns")
    void suggestsAProductWithFewerAllergens() {
        stock(SUB_ROLLS, 200);

        List<SubstitutionCandidate> advice = advisor.adviseFor(BURGER_BUNS, TestFixtures.WAREHOUSE, 40);

        assertThat(advice).extracting(SubstitutionCandidate::suggestedSku).contains(SUB_ROLLS);
        assertThat(advice).allSatisfy(candidate -> {
            assertThat(candidate.availableQty()).isPositive();
            // 200 cases against a 40-unit shortfall genuinely covers it.
            assertThat(candidate.rationale()).contains("200 cases available, covers the 40-unit shortfall");
        });
    }

    @Test
    @DisplayName("advice says so plainly when an alternate only partly covers the gap")
    void rationaleDoesNotOverclaimCoverage() {
        stock(SUB_ROLLS, 10);

        List<SubstitutionCandidate> advice = advisor.adviseFor(BURGER_BUNS, TestFixtures.WAREHOUSE, 500);

        assertThat(advice).isNotEmpty();
        assertThat(advice.get(0).rationale()).contains("covers 10 of the 500-unit shortfall");
    }

    @Test
    @DisplayName("similar products rank above unrelated ones")
    void ranksTheNearestProductFirst() {
        stock(CHICKEN_THIGH, 100);
        stock(SALMON, 100);

        List<SubstitutionCandidate> advice = advisor.adviseFor(CHICKEN_BREAST, TestFixtures.WAREHOUSE, 20);

        assertThat(advice).isNotEmpty();
        assertThat(advice.get(0).suggestedSku())
                .as("another cut of chicken beats a fish")
                .isEqualTo(CHICKEN_THIGH);
    }

    @Test
    @DisplayName("an alternate that is also out of stock is not worth suggesting")
    void skipsCandidatesWithNoAvailableStock() {
        // Nothing received at all, so every candidate is unavailable.
        assertThat(advisor.adviseFor(CHICKEN_BREAST, TestFixtures.WAREHOUSE, 20)).isEmpty();
    }

    @Test
    @DisplayName("stock that has already expired does not count as an alternate")
    void ignoresExpiredStockWhenSuggesting() {
        stock(CHICKEN_THIGH, 100);
        // Age the lot past its date behind the receiving guard's back, which is
        // the one thing the API deliberately will not let you do.
        jdbc.update("UPDATE inventory_lot SET expiry_date = current_date - 1 WHERE sku = ?", CHICKEN_THIGH);

        assertThat(advisor.adviseFor(CHICKEN_BREAST, TestFixtures.WAREHOUSE, 20))
                .extracting(SubstitutionCandidate::suggestedSku)
                .doesNotContain(CHICKEN_THIGH);
    }

    private void stock(String sku, int qty) {
        lots.saveAndFlush(InventoryLot.receive(
                sku, TestFixtures.WAREHOUSE, qty, LocalDate.now().plusDays(20)));
    }
}
