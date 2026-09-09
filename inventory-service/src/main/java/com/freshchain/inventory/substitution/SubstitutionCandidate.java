package com.freshchain.inventory.substitution;

/** A proposed alternate SKU, before and after the guard has had its say. */
public record SubstitutionCandidate(
        String forSku,
        String suggestedSku,
        String suggestedName,
        String category,
        int availableQty,
        double similarity,
        String rationale) {

    public SubstitutionCandidate withRationale(String newRationale) {
        return new SubstitutionCandidate(
                forSku, suggestedSku, suggestedName, category, availableQty, similarity, newRationale);
    }
}
