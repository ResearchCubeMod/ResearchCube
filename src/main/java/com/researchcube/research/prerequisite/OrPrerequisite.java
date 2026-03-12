package com.researchcube.research.prerequisite;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * OR prerequisite: ANY child prerequisite must be satisfied.
 */
public class OrPrerequisite implements Prerequisite {

    private final List<Prerequisite> children;

    public OrPrerequisite(List<Prerequisite> children) {
        this.children = children;
    }

    public List<Prerequisite> getChildren() {
        return children;
    }

    @Override
    public boolean isSatisfied(Set<String> completedResearch) {
        return children.stream().anyMatch(p -> p.isSatisfied(completedResearch));
    }

    @Override
    public String describe() {
        return "ANY of: [" + children.stream()
                .map(Prerequisite::describe)
                .collect(Collectors.joining(", ")) + "]";
    }
}
