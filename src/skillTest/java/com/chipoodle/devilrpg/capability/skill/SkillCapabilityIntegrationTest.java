package com.chipoodle.devilrpg.capability.skill;

import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.ResourceType;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.SkillElement;
import com.chipoodle.devilrpg.util.BytesUtil;
import com.chipoodle.devilrpg.util.JsonUtil;
import com.chipoodle.devilrpg.util.PowerEnum;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Skills · <b>primer gate automático del subsistema</b> (24-nov-2026).
 * <p>
 * Comprueba, con {@code Bootstrap} headless y <b>sin cliente ni servidor</b>:
 * <ol>
 *   <li>el árbol JSON del mod tiene un nodo por cada {@link SkillEnum} real (33), con coste y display;</li>
 *   <li>la capability se construye sin {@code Minecraft} (antes dependía de una API de cliente);</li>
 *   <li>los mapas por defecto (puntos, máximos, costes, tipos, iconos y poderes) están completos;</li>
 *   <li>una partida vieja que no tiene una skill nueva recibe la entrada que falta <b>sin pisar</b> los
 *       valores que el jugador ya tenía;</li>
 *   <li>el registro real de objetos resuelve iconos vanilla y devuelve aire para ids desconocidos.</li>
 * </ol>
 * Programa {@code main}: exit 1 si falla. Imprime el número de aserciones al final.
 */
public final class SkillCapabilityIntegrationTest {

    private static int total;
    private static int fallos;

    private static final List<SkillEnum> SKILLS = SkillEnum.getSkillsWithoutEmpty();

    public static void main(String[] args) {
        net.neoforged.fml.loading.LoadingModList.of(
                List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        try {
            catalogoYArbol();
            capabilityHeadless();
            persistenciaYCompatibilidad();
            registroDeObjetos();
            werewolfMana();
            directPayload();
            regenPolicy();
        } catch (Throwable t) {
            fallos++;
            System.out.println("  EXCEPCION NO CONTROLADA: " + t);
            t.printStackTrace(System.out);
        }

        if (fallos > 0) {
            System.out.println("SKILL_CAPABILITY_INTEGRATION_TEST: FAIL (" + fallos + " de " + total + ")");
            System.exit(1);
        }
        System.out.println("SKILL_CAPABILITY_INTEGRATION_TEST: OK (" + total + " assertions)");
    }

    // ---------------------------------------------------------------------------------------------
    // 1 · catálogo, árbol JSON y ejecutores
    // ---------------------------------------------------------------------------------------------

    private static void catalogoYArbol() {
        System.out.println("[1] Catalogo y arbol JSON");
        check(SKILLS.size() == 33, "catalogo: 33 skills reales (sin EMPTY), hay " + SKILLS.size());
        check(SkillEnum.getActiveSkills().size() == 18, "catalogo: 18 activas");
        check(SkillEnum.getPassiveSkills().size() == 16, "catalogo: 15 pasivas reales + EMPTY");

        PlayerSkillCapabilityImplementation capability = new PlayerSkillCapabilityImplementation();
        var builder = capability.getClientSkillBuilder();
        check(builder.getAll().size() == 36, "arbol: 33 skills + 3 raices, hay " + builder.getAll().size());
        int roots = 0;
        for (SkillElement ignored : builder.getRoots()) {
            roots++;
        }
        check(roots == 3, "arbol: 3 raices (summoning/shapeshifting/nature), hay " + roots);

        for (SkillEnum skill : SKILLS) {
            SkillElement element = builder.getSkillElementByEnum(skill);
            check(element != null, "arbol: nodo para " + skill);
            if (element == null) {
                continue;
            }
            check(element.getSkillCapability() == skill, "arbol: capability correcta de " + skill);
            check(element.getDisplay() != null, "arbol: display de " + skill);
            check(element.getSkillManaCost() != null, "arbol: coste de " + skill);
            if (element.getSkillManaCost() != null) {
                check(element.getSkillManaCost().getMaxSkillLevel() != null
                                && element.getSkillManaCost().getMaxSkillLevel() > 0,
                        "arbol: nivel maximo > 0 de " + skill);
                check(element.getSkillManaCost().getManaCost() != null
                                && element.getSkillManaCost().getManaCost() >= 0,
                        "arbol: coste no negativo de " + skill);
                check(element.getSkillManaCost().getResourceType() != null,
                        "arbol: tipo de recurso de " + skill);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 2 · capability headless: mapas por defecto y pasivos
    // ---------------------------------------------------------------------------------------------

    private static void capabilityHeadless() {
        System.out.println("[2] Capability sin cliente");
        PlayerSkillCapabilityImplementation capability = new PlayerSkillCapabilityImplementation();

        Map<SkillEnum, Integer> points = capability.getSkillsPoints();
        check(points != null && points.size() == SKILLS.size(), "capability: puntos de las 33 skills");
        for (SkillEnum skill : SKILLS) {
            check(Integer.valueOf(0).equals(points.get(skill)), "capability: " + skill + " empieza a 0");
        }

        Map<SkillEnum, Integer> max = capability.getMaxSkillsPoints();
        check(max != null && max.size() == SKILLS.size(), "capability: maximos de las 33 skills");
        Map<SkillEnum, Integer> costs = capability.getResourceCostPoints();
        check(costs != null && costs.size() == SKILLS.size(), "capability: costes de las 33 skills");
        Map<SkillEnum, ResourceType> types = capability.getResourceType();
        check(types != null && types.size() == SKILLS.size(), "capability: tipos de las 33 skills");
        Map<SkillEnum, ResourceLocation> images = capability.getImagesOfSkills();
        check(images != null && images.size() == SKILLS.size(), "capability: iconos de las 33 skills");

        Map<PowerEnum, SkillEnum> powers = capability.getSkillsNameOfPowers();
        check(powers != null && powers.size() == PowerEnum.values().length,
                "capability: un poder por PowerEnum (" + PowerEnum.values().length + ")");
        check(capability.getSkillSetCount() == 3, "capability: 3 loadouts");
        check(capability.getActiveSkillSetIndex() == 0, "capability: loadout activo 0 por defecto");

        for (SkillEnum active : SkillEnum.getActiveSkills()) {
            check(capability.getPassivesFromActiveSkill(active) != null,
                    "capability: pasivos de " + active + " sin NPE");
        }
        for (SkillEnum skill : SKILLS) {
            check(capability.getSkillElementByEnum(skill) != null, "capability: elemento de " + skill);
            check(capability.getSkillsPoints(skill) == 0, "capability: puntos de " + skill + " = 0");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 3 · NBT: roundtrip y compatibilidad con partidas viejas (skills nuevas)
    // ---------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static void persistenciaYCompatibilidad() throws Exception {
        System.out.println("[3] NBT: roundtrip y skills nuevas en partidas viejas");
        PlayerSkillCapabilityImplementation capability = new PlayerSkillCapabilityImplementation();
        CompoundTag original = capability.serializeNBT(RegistryAccess.EMPTY).copy();
        check(original.contains("Skills") && original.contains("MaxSkills")
                        && original.contains("ManaCost") && original.contains("ResourceType")
                        && original.contains("ImagesOfSkills") && original.contains("Powers"),
                "nbt: escribe todas las secciones");

        PlayerSkillCapabilityImplementation reloaded = new PlayerSkillCapabilityImplementation();
        reloaded.deserializeNBT(RegistryAccess.EMPTY, original.copy());
        check(reloaded.getSkillsPoints().size() == SKILLS.size(), "nbt: puntos sobreviven al roundtrip");
        check(reloaded.getImagesOfSkills().size() == SKILLS.size(), "nbt: iconos sobreviven al roundtrip");

        // Partida guardada por una version anterior: falta SOULVINE en los cinco mapas y CHARGE vale 7.
        CompoundTag old = original.copy();
        HashMap<SkillEnum, Integer> skills = (HashMap<SkillEnum, Integer>) BytesUtil.toObject(
                old.getByteArray("Skills"));
        skills.remove(SkillEnum.SOULVINE);
        skills.put(SkillEnum.CHARGE, 7);
        old.putByteArray("Skills", BytesUtil.toByteArray(skills));

        HashMap<SkillEnum, Integer> max = (HashMap<SkillEnum, Integer>) BytesUtil.toObject(
                old.getByteArray("MaxSkills"));
        max.remove(SkillEnum.SOULVINE);
        old.putByteArray("MaxSkills", BytesUtil.toByteArray(max));

        HashMap<SkillEnum, Integer> costs = (HashMap<SkillEnum, Integer>) BytesUtil.toObject(
                old.getByteArray("ManaCost"));
        costs.remove(SkillEnum.SOULVINE);
        old.putByteArray("ManaCost", BytesUtil.toByteArray(costs));

        HashMap<SkillEnum, ResourceType> types = (HashMap<SkillEnum, ResourceType>) BytesUtil.toObject(
                old.getByteArray("ResourceType"));
        types.remove(SkillEnum.SOULVINE);
        old.putByteArray("ResourceType", BytesUtil.toByteArray(types));

        HashMap<SkillEnum, String> images = (HashMap<SkillEnum, String>) BytesUtil.toObject(
                old.getByteArray("ImagesOfSkills"));
        images.remove(SkillEnum.SOULVINE);
        old.putByteArray("ImagesOfSkills", BytesUtil.toByteArray(images));

        PlayerSkillCapabilityImplementation migrated = new PlayerSkillCapabilityImplementation();
        migrated.deserializeNBT(RegistryAccess.EMPTY, old);

        check(Integer.valueOf(0).equals(migrated.getSkillsPoints().get(SkillEnum.SOULVINE)),
                "compatibilidad: SOULVINE ausente se agrega a 0");
        check(Integer.valueOf(7).equals(migrated.getSkillsPoints().get(SkillEnum.CHARGE)),
                "compatibilidad: CHARGE=7 del jugador NO se pisa");
        check(migrated.getMaxSkillsPoints().get(SkillEnum.SOULVINE) != null,
                "compatibilidad: se agrega el maximo de SOULVINE");
        check(migrated.getResourceCostPoints().get(SkillEnum.SOULVINE) != null,
                "compatibilidad: se agrega el coste de SOULVINE");
        check(migrated.getResourceType().get(SkillEnum.SOULVINE) != null,
                "compatibilidad: se agrega el tipo de recurso de SOULVINE");
        check(migrated.getImagesOfSkills().get(SkillEnum.SOULVINE) != null,
                "compatibilidad: se agrega el icono de SOULVINE");
        check(migrated.getSkillsPoints().size() == SKILLS.size(),
                "compatibilidad: el mapa migrado vuelve a tener 33 skills");
    }

    // ---------------------------------------------------------------------------------------------
    // 4 · registro real de objetos (iconos) sin depender de DeferredHolder enlazado
    // ---------------------------------------------------------------------------------------------

    private static void registroDeObjetos() {
        System.out.println("[4] Registro real de objetos");
        check(JsonUtil.convertToItem(icon("minecraft:stick"), "item") == Items.STICK,
                "items: resuelve un objeto vanilla del registro real");
        check(JsonUtil.convertToItem(icon("devilrpg:no_existe"), "item") == Items.AIR,
                "items: id desconocido devuelve aire, no revienta");
        check(JsonUtil.convertToItem(icon("minecraft:air"), "item") == Items.AIR,
                "items: minecraft:air resuelve a AIR");
        check(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("minecraft:stick")),
                "items: el registro real esta bootstrapeado");
        boolean threw = false;
        try {
            JsonUtil.convertToItem(new JsonObject(), "item");
        } catch (com.google.gson.JsonSyntaxException expected) {
            threw = true;
        }
        check(threw, "items: un icono sin 'item' falla con JsonSyntaxException");
    }

    private static void werewolfMana() {
        check(com.chipoodle.devilrpg.skillsystem.WerewolfManaPolicy.debePedirReversion(true, 0.0F, 20),
                "maná: transformado, 0 maná y tick 20 pide reversión");
        check(!com.chipoodle.devilrpg.skillsystem.WerewolfManaPolicy.debePedirReversion(true, 0.0F, 10),
                "maná: no pide reversión fuera del throttle de 20 ticks");
        check(!com.chipoodle.devilrpg.skillsystem.WerewolfManaPolicy.debePedirReversion(false, 0.0F, 20),
                "maná: un humano con 0 maná NO se transforma (bug de alternancia)");
        check(!com.chipoodle.devilrpg.skillsystem.WerewolfManaPolicy.debePedirReversion(true, 1.0F, 20),
                "maná: con maná no pide reversión");
    }

    private static void directPayload() {
        check(com.chipoodle.devilrpg.skillsystem.DirectSkillExecutionPolicy.puedeEjecutar(
                        com.chipoodle.devilrpg.util.SkillEnum.TRANSFORM_WEREWOLF, 1),
                "payload directo: transformar con nivel 1 se permite");
        check(!com.chipoodle.devilrpg.skillsystem.DirectSkillExecutionPolicy.puedeEjecutar(
                        com.chipoodle.devilrpg.util.SkillEnum.TRANSFORM_WEREWOLF, 0),
                "payload directo: transformar sin nivel se rechaza");
        check(!com.chipoodle.devilrpg.skillsystem.DirectSkillExecutionPolicy.puedeEjecutar(
                        com.chipoodle.devilrpg.util.SkillEnum.SUMMON_SOUL_WOLF, 5),
                "payload directo: invocar lobo por la puerta trasera se rechaza");
        check(!com.chipoodle.devilrpg.skillsystem.DirectSkillExecutionPolicy.puedeEjecutar(null, 5),
                "payload directo: skill nula se rechaza");
    }

    private static void regenPolicy() {
        check(Math.abs(com.chipoodle.devilrpg.util.PlayerResourceRegenPolicy.manaDegeneration(true, 0) - 0.7F) < 1e-6,
                "regen: lobo sin puntos degenera 0.7");
        check(Math.abs(com.chipoodle.devilrpg.util.PlayerResourceRegenPolicy.manaDegeneration(true, 20) - 0.5F) < 1e-6,
                "regen: lobo con 20 puntos degenera 0.5");
        check(Math.abs(com.chipoodle.devilrpg.util.PlayerResourceRegenPolicy.manaDegeneration(false, 20)) < 1e-6,
                "regen: humano no degenera");
    }

    private static JsonObject icon(String id) {
        JsonObject object = new JsonObject();
        object.addProperty("item", id);
        return object;
    }

    private static void check(boolean ok, String name) {
        total++;
        if (!ok) {
            fallos++;
            System.out.println("  FALLO: " + name);
        }
    }
}
