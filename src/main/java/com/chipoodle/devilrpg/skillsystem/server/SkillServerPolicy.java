package com.chipoodle.devilrpg.skillsystem.server;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Server-authoritative rules for exceptional direct skill actions.
 *
 * <p>Rules are loaded from the mod's skill JSON, never from client payload data or client GUI
 * classes. A direct action is denied unless the skill has an explicit server rule in that JSON.
 */
public final class SkillServerPolicy {
    private static final String SKILL_TREE_RESOURCE = "/assets/devilrpg/skills/root_complete.json";
    private static final Map<SkillEnum, DirectExecutionRule> DIRECT_EXECUTION_RULES = loadRules();

    private SkillServerPolicy() {
    }

    /**
     * Evaluates the configured direct action using state read by the server.
     * Unsupported triggers/resources fail closed.
     */
    public static boolean canExecuteDirectSkill(SkillEnum skill, int level,
                                                boolean transformed, float currentMana) {
        if (skill == null || skill == SkillEnum.EMPTY || level <= 0 || !Float.isFinite(currentMana)) {
            return false;
        }
        DirectExecutionRule rule = DIRECT_EXECUTION_RULES.get(skill);
        if (rule == null || !"resource_depleted".equals(rule.trigger())) {
            return false;
        }
        if (!"mana".equals(rule.resource())) {
            return false;
        }
        if (rule.requiresTransformation() && !transformed) {
            return false;
        }
        return currentMana <= rule.threshold();
    }

    /** Compatibility/diagnostic query; this only means a rule exists, not that execution is authorized. */
    public static boolean hasDirectExecutionRule(SkillEnum skill) {
        return skill != null && DIRECT_EXECUTION_RULES.containsKey(skill);
    }

    private static Map<SkillEnum, DirectExecutionRule> loadRules() {
        Map<SkillEnum, DirectExecutionRule> rules = new HashMap<>();
        try (InputStream stream = SkillServerPolicy.class.getResourceAsStream(SKILL_TREE_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("No se encontró " + SKILL_TREE_RESOURCE);
            }
            String json = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .lines().reduce("", (left, right) -> left + right + "\n");
            JsonArray entries = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement entry : entries) {
                if (!entry.isJsonObject()) continue;
                JsonObject definition = entry.getAsJsonObject();
                if (!definition.has("capability") || !definition.has("directexecution")) continue;
                SkillEnum skill = SkillEnum.getByJsonName(definition.get("capability").getAsString());
                if (skill == null || skill == SkillEnum.EMPTY) continue;
                JsonObject rule = definition.getAsJsonObject("directexecution");
                String trigger = rule.has("trigger") ? rule.get("trigger").getAsString() : "";
                String resource = rule.has("resource") ? rule.get("resource").getAsString() : "";
                float threshold = rule.has("threshold") ? rule.get("threshold").getAsFloat() : Float.NaN;
                boolean requiresTransformation = !rule.has("requires_transformation")
                        || rule.get("requires_transformation").getAsBoolean();
                if (Float.isFinite(threshold) && threshold >= 0.0F) {
                    rules.put(skill, new DirectExecutionRule(trigger, resource, threshold, requiresTransformation));
                } else {
                    DevilRpg.LOGGER.error("[SkillPolicy] umbral inválido para {}", skill);
                }
            }
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("No se pudieron cargar las reglas server-side de skills", exception);
        }
        DevilRpg.LOGGER.info("[SkillPolicy] reglas de ejecución directa cargadas: {}", rules.size());
        return Map.copyOf(rules);
    }

    private record DirectExecutionRule(String trigger, String resource, float threshold,
                                       boolean requiresTransformation) {
    }
}
