package com.freshchain.inventory.substitution;

import java.util.List;

/**
 * Suggests alternates when a line cannot be filled. Advisory only: a suggestion
 * never allocates anything and never changes an order.
 */
public interface SubstitutionAdvisor {

    List<SubstitutionCandidate> adviseFor(String sku, String warehouseId, int shortfall);

    /** Used when the feature is switched off, so call sites need no null checks. */
    final class Disabled implements SubstitutionAdvisor {
        @Override
        public List<SubstitutionCandidate> adviseFor(String sku, String warehouseId, int shortfall) {
            return List.of();
        }
    }
}
