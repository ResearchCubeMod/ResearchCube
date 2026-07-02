package com.researchcube.research;

import com.researchcube.research.prerequisite.Prerequisite;
import com.researchcube.research.prerequisite.NonePrerequisite;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Immutable definition of a single research entry, loaded from datapack JSON.
 *
 * JSON structure (see docs/datapack-guide.md for the full schema):
 * {
 *   "tier": "ADVANCED",
 *   "duration": 6000,
 *   "category": "circuits",
 *   "prerequisites": { "type": "AND", "values": [...] },
 *   "item_costs": [ { "item": "minecraft:redstone", "count": 16 } ],
 *   "fluid_cost": { "fluid": "researchcube:pondering_fluid", "amount": 1000 },
 *   "recipe_pool": [ "researchcube:processor_recipe_1", {"id": "researchcube:processor_recipe_2", "weight": 3} ]
 * }
 *
 * The "id" is derived from the datapack file path (e.g., data/researchcube/research/advanced_processor.json
 * → "researchcube:advanced_processor").
 *
 * Definitions are loaded server-side by {@link ResearchManager} and synced to clients
 * via SyncResearchDefinitionsPacket, so all lookups through {@link ResearchRegistry}
 * work on both sides.
 */
public class ResearchDefinition {

    private final ResourceLocation id;
    private final ResearchTier tier;
    private final int duration; // in ticks
    private final Prerequisite prerequisites;
    private final List<ItemCost> itemCosts;
    private final List<ResourceLocation> recipePool;
    private final List<WeightedRecipe> weightedRecipePool;
    @Nullable
    private final String name;        // human-readable display name (optional)
    @Nullable
    private final String description;  // short description (optional)
    @Nullable
    private final String flavorText;   // optional story/lore text for the detail pane
    @Nullable
    private final String category;     // optional grouping category (e.g., "circuits", "energy")
    @Nullable
    private final FluidCost fluidCost; // optional fluid cost for this research
    private final Optional<ItemStack> ideaChip; // optional idea chip required to start this research

    private ResearchDefinition(Builder builder) {
        this.id = builder.id;
        this.tier = builder.tier;
        this.duration = builder.duration;
        this.prerequisites = builder.prerequisites != null ? builder.prerequisites : NonePrerequisite.INSTANCE;
        this.itemCosts = List.copyOf(builder.itemCosts);
        this.weightedRecipePool = List.copyOf(builder.weightedRecipePool);
        this.recipePool = this.weightedRecipePool.stream().map(WeightedRecipe::id).toList();
        this.name = builder.name;
        this.description = builder.description;
        this.flavorText = builder.flavorText;
        this.category = builder.category;
        this.fluidCost = builder.fluidCost;
        this.ideaChip = builder.ideaChip;
    }

    public static Builder builder(ResourceLocation id, ResearchTier tier, int duration) {
        return new Builder(id, tier, duration);
    }

    /**
     * Builder for research definitions. Required fields are id, tier and duration;
     * everything else is optional. Kept deliberately open so addons can construct
     * definitions in code as well.
     */
    public static class Builder {
        private final ResourceLocation id;
        private final ResearchTier tier;
        private final int duration;
        @Nullable
        private Prerequisite prerequisites;
        private List<ItemCost> itemCosts = List.of();
        private List<WeightedRecipe> weightedRecipePool = List.of();
        @Nullable
        private String name;
        @Nullable
        private String description;
        @Nullable
        private String flavorText;
        @Nullable
        private String category;
        @Nullable
        private FluidCost fluidCost;
        private Optional<ItemStack> ideaChip = Optional.empty();

        private Builder(ResourceLocation id, ResearchTier tier, int duration) {
            if (id == null) throw new IllegalArgumentException("Research id must not be null");
            if (tier == null) throw new IllegalArgumentException("Research tier must not be null");
            if (duration <= 0) throw new IllegalArgumentException("Research duration must be positive, got: " + duration);
            this.id = id;
            this.tier = tier;
            this.duration = duration;
        }

        public Builder prerequisites(@Nullable Prerequisite prerequisites) {
            this.prerequisites = prerequisites;
            return this;
        }

        public Builder itemCosts(List<ItemCost> itemCosts) {
            this.itemCosts = itemCosts != null ? itemCosts : List.of();
            return this;
        }

        public Builder recipePool(List<WeightedRecipe> weightedRecipePool) {
            this.weightedRecipePool = weightedRecipePool != null ? weightedRecipePool : List.of();
            return this;
        }

        public Builder name(@Nullable String name) {
            this.name = name;
            return this;
        }

        public Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        public Builder flavorText(@Nullable String flavorText) {
            this.flavorText = flavorText;
            return this;
        }

        public Builder category(@Nullable String category) {
            this.category = category;
            return this;
        }

        public Builder fluidCost(@Nullable FluidCost fluidCost) {
            this.fluidCost = fluidCost;
            return this;
        }

        public Builder ideaChip(Optional<ItemStack> ideaChip) {
            this.ideaChip = ideaChip != null ? ideaChip : Optional.empty();
            return this;
        }

        public ResearchDefinition build() {
            return new ResearchDefinition(this);
        }
    }

    // ── Network Sync ──

    /**
     * Full definition sync codec (server → client). Everything the client UI needs
     * must be encoded here: screens, JEI/EMI and tooltips all read from the synced registry.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchDefinition> STREAM_CODEC =
            StreamCodec.of(ResearchDefinition::toNetwork, ResearchDefinition::fromNetwork);

    private static void toNetwork(RegistryFriendlyByteBuf buf, ResearchDefinition def) {
        buf.writeResourceLocation(def.id);
        buf.writeEnum(def.tier);
        buf.writeVarInt(def.duration);
        Prerequisite.STREAM_CODEC.encode(buf, def.prerequisites);

        buf.writeVarInt(def.itemCosts.size());
        for (ItemCost cost : def.itemCosts) {
            buf.writeResourceLocation(cost.itemId());
            buf.writeVarInt(cost.count());
        }

        buf.writeVarInt(def.weightedRecipePool.size());
        for (WeightedRecipe wr : def.weightedRecipePool) {
            buf.writeResourceLocation(wr.id());
            buf.writeVarInt(wr.weight());
        }

        buf.writeNullable(def.name, FriendlyByteBuf::writeUtf);
        buf.writeNullable(def.description, FriendlyByteBuf::writeUtf);
        buf.writeNullable(def.flavorText, FriendlyByteBuf::writeUtf);
        buf.writeNullable(def.category, FriendlyByteBuf::writeUtf);

        buf.writeBoolean(def.fluidCost != null);
        if (def.fluidCost != null) {
            buf.writeResourceLocation(def.fluidCost.fluidId());
            buf.writeVarInt(def.fluidCost.amount());
        }

        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, def.ideaChip.orElse(ItemStack.EMPTY));
    }

    private static ResearchDefinition fromNetwork(RegistryFriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        ResearchTier tier = buf.readEnum(ResearchTier.class);
        int duration = buf.readVarInt();
        Prerequisite prerequisites = Prerequisite.STREAM_CODEC.decode(buf);

        int costCount = buf.readVarInt();
        List<ItemCost> itemCosts = new ArrayList<>(costCount);
        for (int i = 0; i < costCount; i++) {
            itemCosts.add(new ItemCost(buf.readResourceLocation(), buf.readVarInt()));
        }

        int poolCount = buf.readVarInt();
        List<WeightedRecipe> pool = new ArrayList<>(poolCount);
        for (int i = 0; i < poolCount; i++) {
            pool.add(new WeightedRecipe(buf.readResourceLocation(), buf.readVarInt()));
        }

        String name = buf.readNullable(FriendlyByteBuf::readUtf);
        String description = buf.readNullable(FriendlyByteBuf::readUtf);
        String flavorText = buf.readNullable(FriendlyByteBuf::readUtf);
        String category = buf.readNullable(FriendlyByteBuf::readUtf);

        FluidCost fluidCost = null;
        if (buf.readBoolean()) {
            fluidCost = new FluidCost(buf.readResourceLocation(), buf.readVarInt());
        }

        ItemStack ideaChip = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);

        return builder(id, tier, duration)
                .prerequisites(prerequisites)
                .itemCosts(itemCosts)
                .recipePool(pool)
                .name(name)
                .description(description)
                .flavorText(flavorText)
                .category(category)
                .fluidCost(fluidCost)
                .ideaChip(ideaChip.isEmpty() ? Optional.empty() : Optional.of(ideaChip))
                .build();
    }

    // ── Accessors ──

    public ResourceLocation getId() {
        return id;
    }

    public String getIdString() {
        return id.toString();
    }

    public ResearchTier getTier() {
        return tier;
    }

    /**
     * Duration in game ticks.
     */
    public int getDuration() {
        return duration;
    }

    public Prerequisite getPrerequisites() {
        return prerequisites;
    }

    public List<ItemCost> getItemCosts() {
        return itemCosts;
    }

    /**
     * Pool of recipe ResourceLocations. On completion, one is chosen via weighted random.
     */
    public List<ResourceLocation> getRecipePool() {
        return recipePool;
    }

    /**
     * Pool of weighted recipe entries for weighted random selection.
     */
    public List<WeightedRecipe> getWeightedRecipePool() {
        return weightedRecipePool;
    }

    /**
     * Select a recipe from the pool using weighted random selection.
     * Returns null if the pool is empty.
     */
    @Nullable
    public ResourceLocation pickWeightedRecipe(RandomSource random) {
        if (weightedRecipePool.isEmpty()) return null;
        int totalWeight = 0;
        for (WeightedRecipe wr : weightedRecipePool) {
            totalWeight += wr.weight();
        }
        int roll = random.nextInt(totalWeight);
        for (WeightedRecipe wr : weightedRecipePool) {
            roll -= wr.weight();
            if (roll < 0) {
                return wr.id();
            }
        }
        // Fallback (should never happen)
        return weightedRecipePool.getLast().id();
    }

    /**
     * Optional human-readable name. Falls back to the path of the ID.
     */
    @Nullable
    public String getName() {
        return name;
    }

    /**
     * Returns the display name: the name field if set, otherwise the ID path.
     */
    public String getDisplayName() {
        return name != null ? name : id.getPath();
    }

    /**
     * Optional short description.
     */
    @Nullable
    public String getDescription() {
        return description;
    }

    /**
     * Optional story/lore flavor text for the detail pane.
     */
    @Nullable
    public String getFlavorText() {
        return flavorText;
    }

    /**
     * Optional category for grouping in the UI (e.g., "circuits", "energy").
     */
    @Nullable
    public String getCategory() {
        return category;
    }

    /**
     * Optional fluid cost for this research (e.g., 1000 mB of Thinking Fluid).
     */
    @Nullable
    public FluidCost getFluidCost() {
        return fluidCost;
    }

    /**
     * Optional idea chip required to start this research.
     * If present, the player must place a matching item in the idea chip slot.
     */
    public Optional<ItemStack> getIdeaChip() {
        return ideaChip;
    }

    /**
     * Returns duration as human-readable seconds.
     */
    public float getDurationSeconds() {
        return duration / 20.0f;
    }

    /**
     * Returns true if this definition has a non-empty recipe pool.
     */
    public boolean hasRecipePool() {
        return !recipePool.isEmpty();
    }

    @Override
    public String toString() {
        return "ResearchDefinition{" + id + ", tier=" + tier + ", duration=" + duration +
                ", category=" + category + ", costs=" + itemCosts.size() + ", recipes=" + recipePool.size() + "}";
    }
}
