package com.freshchain.inventory.substitution;

import com.freshchain.inventory.domain.Product;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The model proposes; this disposes.
 *
 * <p>A similarity search does not know what an allergen is. It will happily rank
 * a brioche bun next to a gluten-free one, because they read almost identically.
 * Suggesting the first to a customer who ordered the second is a food-safety
 * incident, not a bad recommendation. So model output never reaches a customer
 * without passing through this: plain, deterministic, hard-coded set logic with
 * no inference anywhere in it.
 *
 * <p>The rule is one-directional. A substitute may drop allergens the original
 * had; it may never introduce one the original did not.
 */
@Component
@Slf4j
public class AllergenGuard {

    /**
     * @param original   the product that could not be filled
     * @param candidates suggestions from the similarity search
     * @param catalogue  sku -> product, for reading candidate allergens
     * @return only those candidates that introduce no new allergen
     */
    public List<SubstitutionCandidate> filter(Product original,
                                              List<SubstitutionCandidate> candidates,
                                              Map<String, Product> catalogue) {
        Set<String> permitted = original.allergenSet();

        return candidates.stream()
                .filter(candidate -> {
                    Product product = catalogue.get(candidate.suggestedSku());
                    if (product == null) {
                        // Unknown product: refuse rather than assume it is safe.
                        log.warn("blocked substitution {} for {}: not in catalogue",
                                candidate.suggestedSku(), original.getSku());
                        return false;
                    }
                    Set<String> introduced = new LinkedHashSet<>(product.allergenSet());
                    introduced.removeAll(permitted);
                    if (!introduced.isEmpty()) {
                        log.info("blocked substitution {} for {}: would introduce {}",
                                candidate.suggestedSku(), original.getSku(), introduced);
                        return false;
                    }
                    return true;
                })
                .toList();
    }
}
