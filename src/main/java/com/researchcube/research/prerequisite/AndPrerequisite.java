package com.researchcube.research.prerequisite;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AND prerequisite: ALL child prerequisites must be satisfied.
 */
public class AndPrerequisite implements Prerequisite {

    private final List<Prerequisite> children;

    public AndPrerequisite(List<Prerequisite> children) {
        this.children = children;
    }

    public List<Prerequisite> getChildren() {
        return children;
    }

    @Override
    public boolean isSatisfied(Set<String> completedResearch) {
        return children.stream().allMatch(p -> p.isSatisfied(completedResearch));
    }

    @Override
    public String describe() {
        return "ALL of: [" + children.stream()
                .map(Prerequisite::describe)
                .collect(Collectors.joining(", ")) + "]";
    }
}
