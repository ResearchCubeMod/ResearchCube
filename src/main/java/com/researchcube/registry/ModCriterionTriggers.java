package com.researchcube.registry;

import com.researchcube.ResearchCubeMod;
import com.researchcube.research.criterion.CompleteResearchTrigger;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry for custom criterion triggers (used in advancements).
 */
public class ModCriterionTriggers {

    public static final DeferredRegister<CriterionTrigger<?>> CRITERION_TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, ResearchCubeMod.MOD_ID);

    public static final DeferredHolder<CriterionTrigger<?>, CompleteResearchTrigger> COMPLETE_RESEARCH =
            CRITERION_TRIGGERS.register("complete_research", () -> CompleteResearchTrigger.INSTANCE);
}
