package com.freshchain.inventory.config;

import com.freshchain.inventory.substitution.HashingEmbeddingModel;
import com.freshchain.inventory.substitution.SubstitutionAdvisor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    /**
     * Default embedding model. Active only while {@code spring.ai.model.embedding}
     * is {@code none}, which is how this project ships: no API key, no model
     * download, reproducible vectors in CI. Set the property to {@code openai}
     * and supply a key, and Spring AI's own auto-configuration takes over — the
     * dimension is the same either way, so no migration is needed.
     */
    @Bean
    @ConditionalOnProperty(name = "spring.ai.model.embedding", havingValue = "none")
    public EmbeddingModel offlineEmbeddingModel() {
        return new HashingEmbeddingModel();
    }

    @Bean
    @ConditionalOnProperty(name = "freshchain.substitution.enabled", havingValue = "false")
    public SubstitutionAdvisor disabledSubstitutionAdvisor() {
        return new SubstitutionAdvisor.Disabled();
    }
}
