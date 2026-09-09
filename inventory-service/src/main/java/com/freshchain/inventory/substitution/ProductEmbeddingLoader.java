package com.freshchain.inventory.substitution;

import com.freshchain.inventory.domain.Product;
import com.freshchain.inventory.repository.ProductRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Loads the product catalogue into the vector store once, at startup.
 *
 * <p>Idempotent by construction: the document id is derived deterministically
 * from the SKU and PgVectorStore upserts on id, so a restart or a second replica
 * rewrites the same rows rather than accumulating duplicates.
 */
@Component
@ConditionalOnProperty(name = "freshchain.substitution.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class ProductEmbeddingLoader {

    private final ProductRepository products;
    private final VectorStore vectorStore;

    /**
     * PgVectorStore's id column is a real {@code uuid}, so a SKU cannot be used
     * directly. A name-based UUID keeps the mapping stable and reversible-by-lookup
     * without needing a second table; the SKU itself rides along in the metadata.
     */
    static String documentIdFor(String sku) {
        return java.util.UUID.nameUUIDFromBytes(sku.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadCatalogue() {
        List<Product> catalogue = products.findAll();
        if (catalogue.isEmpty()) {
            log.info("no products to embed");
            return;
        }

        List<Document> documents = catalogue.stream()
                .map(product -> Document.builder()
                        .id(documentIdFor(product.getSku()))
                        .text(product.embeddableText())
                        .metadata(Map.of(
                                "sku", product.getSku(),
                                "name", product.getName(),
                                "category", product.getCategory()))
                        .build())
                .toList();

        try {
            vectorStore.add(documents);
            log.info("embedded {} products into the vector store", documents.size());
        } catch (RuntimeException e) {
            // A failed embed must not stop the service from allocating stock.
            // Substitution advice is a nicety; taking orders is the job.
            log.warn("could not load product embeddings; substitution advice will be degraded", e);
        }
    }
}
