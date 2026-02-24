package com.researchcube.research.prerequisite;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.researchcube.ResearchCubeMod;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses prerequisite JSON into the polymorphic Prerequisite tree.
 *
 * JSON formats:
 *
 * No prerequisites (field absent or null):
 *   → NonePrerequisite
 *
 * Single string:
 *   "prerequisites": "researchcube:basic_circuit"
 *   → SinglePrerequisite
 *
 * Object with type + values:
 *   "prerequisites": {
 *     "type": "AND",
 *     "values": ["researchcube:basic_circuit", "researchcube:energy_handling"]
 *   }
 *   → AndPrerequisite / OrPrerequisite containing SinglePrerequisites
 *
 * Nested:
 *   "prerequisites": {
 *     "type": "AND",
 *     "values": [
 *       "researchcube:basic_circuit",
 *       { "type": "OR", "values": ["researchcube:a", "researchcube:b"] }
 *     ]
 *   }
 *   → Recursive nesting supported
 */
public final class PrerequisiteParser {

    private PrerequisiteParser() {}

    /**
     * Parse a prerequisite from the "prerequisites" field of a research JSON.
     * Returns NonePrerequisite if the element is null.
     */
    public static Prerequisite parse(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return NonePrerequisite.INSTANCE;
        }

        // Single string → SinglePrerequisite
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return new SinglePrerequisite(element.getAsString());
        }

        // Object → AND/OR with values array
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            String type = obj.has("type") ? obj.get("type").getAsString().toUpperCase() : "AND";
            JsonArray values = obj.has("values") ? obj.getAsJsonArray("values") : new JsonArray();

            List<Prerequisite> children = new ArrayList<>();
            for (JsonElement val : values) {
                children.add(parse(val)); // Recursive for nested structures
            }

            if (children.isEmpty()) {
                return NonePrerequisite.INSTANCE;
            }

            return switch (type) {
                case "OR" -> new OrPrerequisite(children);
                case "AND" -> new AndPrerequisite(children);
                default -> {
                    ResearchCubeMod.LOGGER.warn("Unknown prerequisite type '{}', defaulting to AND", type);
                    yield new AndPrerequisite(children);
                }
            };
        }

        ResearchCubeMod.LOGGER.warn("Invalid prerequisite format, returning NonePrerequisite");
        return NonePrerequisite.INSTANCE;
    }
}
