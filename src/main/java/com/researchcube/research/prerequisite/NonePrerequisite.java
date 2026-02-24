package com.researchcube.research.prerequisite;

import java.util.Set;

/**
 * No prerequisites required. Always satisfied.
 */
public class NonePrerequisite implements Prerequisite {

    public static final NonePrerequisite INSTANCE = new NonePrerequisite();

    private NonePrerequisite() {}

    @Override
    public boolean isSatisfied(Set<String> completedResearch) {
        return true;
    }

    @Override
    public String describe() {
        return "None";
    }
}
