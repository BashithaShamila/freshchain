package com.freshchain.inventory.substitution;

import static org.assertj.core.api.Assertions.assertThat;

import com.freshchain.inventory.domain.Product;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The safety-critical half of the substitution feature, and the reason it is
 * deterministic code rather than a prompt.
 */
class AllergenGuardTest {

    private final AllergenGuard guard = new AllergenGuard();

    @Test
    @DisplayName("a substitute that would introduce a new allergen is blocked")
    void blocksASubstituteThatAddsAnAllergen() {
        Product subRolls = product("BRD-SUB-72CT", "Sub Rolls 72ct", "BAKERY", "wheat", "soy");
        Product burgerBuns = product("BRD-BRGR-96CT", "Burger Buns 96ct", "BAKERY", "wheat", "soy", "sesame");

        List<SubstitutionCandidate> survivors = guard.filter(
                subRolls, List.of(candidate(burgerBuns)), catalogue(burgerBuns));

        assertThat(survivors)
                .as("sesame is not on the original, so this suggestion is a food-safety incident")
                .isEmpty();
    }

    @Test
    @DisplayName("a substitute carrying fewer allergens is allowed")
    void allowsASubstituteThatDropsAnAllergen() {
        Product burgerBuns = product("BRD-BRGR-96CT", "Burger Buns 96ct", "BAKERY", "wheat", "soy", "sesame");
        Product subRolls = product("BRD-SUB-72CT", "Sub Rolls 72ct", "BAKERY", "wheat", "soy");

        List<SubstitutionCandidate> survivors = guard.filter(
                burgerBuns, List.of(candidate(subRolls)), catalogue(subRolls));

        assertThat(survivors).extracting(SubstitutionCandidate::suggestedSku)
                .containsExactly("BRD-SUB-72CT");
    }

    @Test
    @DisplayName("an allergen-free product may only be replaced by another allergen-free one")
    void blocksAnyAllergenAgainstAnAllergenFreeOriginal() {
        Product chicken = product("CHK-BRST-5LB", "Chicken Breast 5lb", "POULTRY");
        Product salmon = product("SLM-FLLT-4LB", "Atlantic Salmon Fillet 4lb", "SEAFOOD", "fish");
        Product turkey = product("TKY-BRST-5LB", "Turkey Breast 5lb", "POULTRY");

        List<SubstitutionCandidate> survivors = guard.filter(
                chicken, List.of(candidate(salmon), candidate(turkey)), catalogue(salmon, turkey));

        assertThat(survivors).extracting(SubstitutionCandidate::suggestedSku)
                .containsExactly("TKY-BRST-5LB");
    }

    @Test
    @DisplayName("a candidate missing from the catalogue is refused rather than assumed safe")
    void blocksAnUnknownProduct() {
        Product chicken = product("CHK-BRST-5LB", "Chicken Breast 5lb", "POULTRY");
        SubstitutionCandidate ghost = new SubstitutionCandidate(
                "CHK-BRST-5LB", "MYSTERY-SKU", "Something", "POULTRY", 10, 0.9, "");

        assertThat(guard.filter(chicken, List.of(ghost), Map.of())).isEmpty();
    }

    @Test
    @DisplayName("allergen matching ignores case and surrounding whitespace")
    void normalisesAllergenSpelling() {
        Product original = product("A", "A", "X", " Milk ");
        Product candidate = product("B", "B", "X", "MILK");

        assertThat(guard.filter(original, List.of(candidate(candidate)), catalogue(candidate)))
                .hasSize(1);
    }

    // ---------------------------------------------------------------- fixtures --

    private static Product product(String sku, String name, String category, String... allergens) {
        return new Product(sku, name, category, name + " description", "CASE", 7, allergens);
    }

    private static SubstitutionCandidate candidate(Product product) {
        return new SubstitutionCandidate("ORIGINAL", product.getSku(), product.getName(),
                product.getCategory(), 25, 0.8, "");
    }

    private static Map<String, Product> catalogue(Product... products) {
        return java.util.Arrays.stream(products).collect(Collectors.toMap(Product::getSku, Function.identity()));
    }
}
