package com.freshchain.inventory.substitution;

import com.freshchain.inventory.config.SubstitutionProperties;
import com.freshchain.inventory.domain.Product;
import com.freshchain.inventory.repository.InventoryLotRepository;
import com.freshchain.inventory.repository.ProductRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Similarity search, then three hard filters, in this order:
 *
 * <ol>
 *   <li>drop the product itself;</li>
 *   <li>drop anything without real, unexpired stock in the same warehouse —
 *       suggesting an alternate that is also out is worse than saying nothing;</li>
 *   <li>drop anything that introduces an allergen, via {@link AllergenGuard}.</li>
 * </ol>
 *
 * <p>Every one of those is deterministic code. The model only ever gets to
 * propose an ordering of candidates.
 */
@Component
@ConditionalOnProperty(name = "freshchain.substitution.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class VectorSubstitutionAdvisor implements SubstitutionAdvisor {

    private final VectorStore vectorStore;
    private final ProductRepository products;
    private final InventoryLotRepository lots;
    private final AllergenGuard allergenGuard;
    private final SubstitutionProperties properties;

    @Override
    public List<SubstitutionCandidate> adviseFor(String sku, String warehouseId, int shortfall) {
        Product original = products.findById(sku).orElse(null);
        if (original == null) {
            return List.of();
        }

        List<Document> hits;
        try {
            hits = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(original.embeddableText())
                    // Over-fetch: the stock and allergen filters below will thin
                    // this out, and topK alone would leave too few survivors.
                    .topK(properties.topK() * 4)
                    .similarityThreshold(properties.minSimilarity())
                    .build());
        } catch (RuntimeException e) {
            log.warn("similarity search failed for {}; returning no advice", sku, e);
            return List.of();
        }
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }

        LocalDate today = LocalDate.now();
        List<SubstitutionCandidate> inStock = new ArrayList<>();

        for (Document hit : hits) {
            String candidateSku = String.valueOf(hit.getMetadata().getOrDefault("sku", hit.getId()));
            if (candidateSku.equals(sku)) {
                continue;
            }
            long available = lots.availableQuantity(candidateSku, warehouseId, today);
            if (available <= 0) {
                continue;
            }
            Product candidate = products.findById(candidateSku).orElse(null);
            if (candidate == null) {
                continue;
            }
            double similarity = hit.getScore() == null ? 0.0 : hit.getScore();
            inStock.add(new SubstitutionCandidate(
                    sku,
                    candidateSku,
                    candidate.getName(),
                    candidate.getCategory(),
                    (int) Math.min(available, Integer.MAX_VALUE),
                    similarity,
                    rationale(original, candidate, available, shortfall)));
        }

        Map<String, Product> catalogue = products
                .findBySkuIn(inStock.stream().map(SubstitutionCandidate::suggestedSku).toList())
                .stream()
                .collect(Collectors.toMap(Product::getSku, Function.identity()));

        return allergenGuard.filter(original, inStock, catalogue).stream()
                .sorted((a, b) -> Double.compare(b.similarity(), a.similarity()))
                .limit(properties.topK())
                .toList();
    }

    /**
     * Deterministic wording. An LLM could phrase this more warmly, but the facts
     * in it are the ones a buyer actually acts on, and a template cannot invent
     * a stock figure that is not there.
     */
    private String rationale(Product original, Product candidate, long available, int shortfall) {
        String sameCategory = Objects.equals(original.getCategory(), candidate.getCategory())
                ? "same category"
                : "category " + candidate.getCategory();
        String allergens = candidate.allergenSet().isEmpty()
                ? "no declared allergens"
                : "allergens: " + String.join(", ", candidate.allergenSet());
        // Say whether it actually closes the gap. Claiming a 60-case alternate
        // "covers" a 500-case shortfall would be worse than saying nothing.
        String coverage = available >= shortfall
                ? "covers the %d-unit shortfall".formatted(shortfall)
                : "covers %d of the %d-unit shortfall".formatted(available, shortfall);
        return "%s, %d %s available, %s, %s"
                .formatted(sameCategory, available, unitLabel(candidate, available), coverage, allergens);
    }

    private static String unitLabel(Product product, long quantity) {
        String unit = product.getUnit().toLowerCase();
        return quantity == 1 ? unit : unit + "s";
    }
}
