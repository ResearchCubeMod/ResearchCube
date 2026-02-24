package com.researchcube.research.prerequisite;

import java.util.Set;

/**
 * A prerequisite condition for research. Can be AND, OR, or a single research ID.
 * Evaluated against a set of completed research IDs.
 */
public interface Prerequisite {

    /**
     * Returns true if this prerequisite is satisfied given the set of completed research IDs.
     */
    boolean isSatisfied(Set<String> completedResearch);

    /**
     * Return a human-readable description for tooltips/UI.
     */
    String describe();
}
