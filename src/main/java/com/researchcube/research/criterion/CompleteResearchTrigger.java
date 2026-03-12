package com.researchcube.research.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.researchcube.ResearchCubeMod;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Criterion trigger that fires when a player completes a research.
 * Used in advancement JSON files to track research progress.
 */
public class CompleteResearchTrigger extends SimpleCriterionTrigger<CompleteResearchTrigger.TriggerInstance> {

    public static final CompleteResearchTrigger INSTANCE = new CompleteResearchTrigger();

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    /**
     * Trigger this criterion for a player completing a specific research.
     */
    public void trigger(ServerPlayer player, ResourceLocation researchId) {
        this.trigger(player, instance -> instance.matches(researchId));
    }

    /**
     * Trigger instance representing the conditions for this advancement criterion.
     */
    public record TriggerInstance(
            Optional<ContextAwarePredicate> player,
            Optional<String> researchId
    ) implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                        Codec.STRING.optionalFieldOf("research_id").forGetter(TriggerInstance::researchId)
                ).apply(instance, TriggerInstance::new)
        );

        /**
         * Check if this trigger matches the completed research.
         * If researchId is not specified, matches any research completion.
         */
        public boolean matches(ResourceLocation completedResearch) {
            if (researchId.isEmpty()) {
                return true; // Match any research completion
            }
            return researchId.get().equals(completedResearch.toString());
        }

        @Override
        public Optional<ContextAwarePredicate> player() {
            return player;
        }
    }

    /**
     * Create a criterion for completing any research.
     */
    public static Criterion<TriggerInstance> completedAny() {
        return INSTANCE.createCriterion(new TriggerInstance(Optional.empty(), Optional.empty()));
    }

    /**
     * Create a criterion for completing a specific research.
     */
    public static Criterion<TriggerInstance> completed(String researchId) {
        return INSTANCE.createCriterion(new TriggerInstance(Optional.empty(), Optional.of(researchId)));
    }

    /**
     * Create a criterion for completing a specific research with resource location.
     */
    public static Criterion<TriggerInstance> completed(ResourceLocation researchId) {
        return completed(researchId.toString());
    }
}
