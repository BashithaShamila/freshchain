package com.freshchain.inventory.substitution;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * A deterministic, offline embedding model: hashed bag-of-words plus character
 * trigrams, L2-normalised.
 *
 * <p>It exists so the whole stack runs in Docker with no API key, no model
 * download and no network, and so CI produces the same vectors every run. On a
 * catalogue of product names it is genuinely useful — "Chicken Breast Boneless"
 * and "Chicken Thigh Boneless" share most of their tokens and land close
 * together, while "Atlantic Salmon Fillet" does not.
 *
 * <p>It is not a language model and makes no semantic claims: it will not know
 * that "scallion" and "spring onion" are the same thing. Set
 * {@code spring.ai.model.embedding=openai} with an API key to swap in a real
 * one. The output dimension deliberately matches OpenAI's 1536, so switching
 * needs no schema migration.
 */
public class HashingEmbeddingModel implements EmbeddingModel {

    private static final int DIMENSIONS = 1536;
    private static final int TRIGRAM_WEIGHT_DIVISOR = 3;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> inputs = request.getInstructions();
        for (int i = 0; i < inputs.size(); i++) {
            embeddings.add(new Embedding(vectorise(inputs.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return vectorise(document.getText());
    }

    @Override
    public float[] embed(String text) {
        return vectorise(text);
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    private float[] vectorise(String text) {
        float[] vector = new float[DIMENSIONS];
        if (text == null || text.isBlank()) {
            return vector;
        }
        String normalised = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ");

        for (String token : normalised.split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            // Whole tokens carry full weight: an exact word match is the strongest signal.
            vector[bucket(token)] += 1.0f;
            // Trigrams carry partial weight so near-misses and plurals still attract.
            for (int i = 0; i + 3 <= token.length(); i++) {
                vector[bucket(token.substring(i, i + 3))] += 1.0f / TRIGRAM_WEIGHT_DIVISOR;
            }
        }
        return l2Normalise(vector);
    }

    private static int bucket(String term) {
        return Math.floorMod(term.hashCode(), DIMENSIONS);
    }

    /** Cosine distance in pgvector expects unit vectors to behave sensibly. */
    private static float[] l2Normalise(float[] vector) {
        double sumOfSquares = 0.0;
        for (float value : vector) {
            sumOfSquares += (double) value * value;
        }
        if (sumOfSquares == 0.0) {
            return vector;
        }
        float magnitude = (float) Math.sqrt(sumOfSquares);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= magnitude;
        }
        return vector;
    }
}
