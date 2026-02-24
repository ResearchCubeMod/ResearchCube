package com.researchcube.research.prerequisite;

import java.util.Set;

/**
 * A single research ID prerequisite. Satisfied if that research ID is in the completed set.
 */
public class SinglePrerequisite implements Prerequisite {

    private final String researchId;

    public SinglePrerequisite(String researchId) {
        this.researchId = researchId;
    }

    public String getResearchId() {
        return researchId;
    }

    @Override
    public boolean isSatisfied(Set<String> completedResearch) {
        return completedResearch.contains(researchId);
    }

    @Override
    public String describe() {
        return researchId;
    }
}
