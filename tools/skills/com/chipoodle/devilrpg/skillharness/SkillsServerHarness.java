package com.chipoodle.devilrpg.skillharness;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.skillsystem.AbstractSkillExecutor;
import com.chipoodle.devilrpg.skillsystem.SkillCastPolicy;
import com.chipoodle.devilrpg.util.PowerEnum;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * <b>Skills · servidor dedicado con fake player</b> (24-nov-2026).
 * <p>
 * Entry point único de {@code -PskillHarness=true}. Arranca un servidor NeoForge real (sin cliente),
 * crea un {@link FakePlayer}, comprueba la capability/árbol, ejecuta una skill real en el servidor y
 * verifica el gasto de maná, la reversión y la ausencia de crash con una skill activa de minion.
 * No sustituye a un cliente gráfico: la UI/HUD no se ejecutan aquí.
 */
@EventBusSubscriber(modid = DevilRpg.MODID)
public final class SkillsServerHarness {

    private static final Path OUT = Path.of(System.getProperty("skills.out", "build/skill-harness"));
    private static final List<String> FAILS = new ArrayList<>();
    private static int checks = 0;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        try {
            run(server);
        } catch (Throwable t) {
            FAILS.add("excepción no controlada: " + t);
            t.printStackTrace();
        } finally {
            try {
                Files.createDirectories(OUT);
                StringBuilder report = new StringBuilder();
                report.append("SKILL_SERVER_HARNESS ").append(FAILS.isEmpty() ? "OK" : "FAIL")
                        .append(" checks=").append(checks).append('\n');
                for (String fail : FAILS) {
                    report.append("FAIL ").append(fail).append('\n');
                }
                Files.writeString(OUT.resolve("report.txt"), report.toString(), StandardCharsets.UTF_8);
                DevilRpg.LOGGER.info("[SkillsHarness] {}", report.toString().replace('\n', ' '));
            } catch (Exception e) {
                DevilRpg.LOGGER.error("[SkillsHarness] no se pudo escribir el reporte", e);
            }
            server.halt(false);
        }
    }

    private static void run(MinecraftServer server) {
        ServerLevel level = server.overworld();
        FakePlayer player = FakePlayerFactory.get(level,
                new GameProfile(UUID.fromString("00000000-0000-0000-0000-00000000A11C"), "SkillHarness"));
        level.addFreshEntity(player);

        PlayerSkillCapabilityInterface skills = player.getData(PlayerSkillCapability.INSTANCE);
        check(skills != null, "capability de skills creada en servidor dedicado");
        if (skills == null) {
            return;
        }
        check(skills.getSkillsPoints() != null && skills.getSkillsPoints().size() == 33,
                "33 skills cargadas en la capability del servidor");
        check(skills.getClientSkillBuilder() != null && count(skills) == 36,
                "36 nodos del árbol cargados en el servidor");

        int ejecutores = 0;
        for (SkillEnum skill : SkillEnum.getActiveSkills()) {
            if (!SkillCastPolicy.esAsignableAPoder(skill)) {
                continue;
            }
            AbstractSkillExecutor executor = skills.getLoadedSkillExecutor(skill);
            check(executor != null, "ejecutor de servidor para " + skill);
            ejecutores++;
        }
        check(ejecutores >= 14, "al menos 14 skills de jugador con ejecutor (" + ejecutores + ")");

        // Poder real: transformación, nivel 1, maná 100.
        HashMap<SkillEnum, Integer> points = skills.getSkillsPoints();
        points.put(SkillEnum.TRANSFORM_WEREWOLF, 1);
        skills.setSkillsPoints(points, player);
        HashMap<PowerEnum, SkillEnum> powers = skills.getSkillsNameOfPowers();
        powers.put(PowerEnum.POWER1, SkillEnum.TRANSFORM_WEREWOLF);
        skills.setSkillsNameOfPowers(powers, player);

        PlayerManaCapabilityInterface mana = player.getData(PlayerManaCapability.INSTANCE);
        PlayerAuxiliaryCapabilityInterface aux = player.getData(PlayerAuxiliaryCapability.INSTANCE);
        check(mana != null && aux != null, "capabilities de maná/auxiliar disponibles");
        if (mana == null || aux == null) {
            return;
        }
        mana.setMana(100.0F, player);
        aux.setWerewolfTransformation(false, player);

        skills.triggerAction(player, PowerEnum.POWER1);
        check(aux.isWerewolfTransformation(), "triggerAction del servidor transforma al jugador");
        check(mana.getMana() < 100.0F, "triggerAction del servidor consume maná una vez");
        float manaTrasTransformar = mana.getMana();

        // La transformación no vuelve a consumir (bypass) y revierte.
        skills.triggerAction(player, PowerEnum.POWER1);
        check(!aux.isWerewolfTransformation(), "segundo triggerAction revierte al humano");
        check(mana.getMana() == manaTrasTransformar, "la reversión no consume maná adicional");

        // Skill activa de minion: comprable, pero no asignable/ejecutable por el jugador; no debe crashear.
        points = skills.getSkillsPoints();
        points.put(SkillEnum.WOLF_FROSTBITE, 1);
        skills.setSkillsPoints(points, player);
        powers = skills.getSkillsNameOfPowers();
        powers.put(PowerEnum.POWER2, SkillEnum.WOLF_FROSTBITE);
        skills.setSkillsNameOfPowers(powers, player);
        boolean minionCrashed = false;
        try {
            skills.triggerAction(player, PowerEnum.POWER2);
        } catch (Throwable t) {
            minionCrashed = true;
            FAILS.add("triggerAction de skill de minion lanzó " + t);
        }
        check(!minionCrashed, "skill activa de minion no crashea al no ser asignable");

        // Persistencia real (NBT de la capability) en el servidor.
        CompoundTag nbt = skills.serializeNBT(level.registryAccess());
        PlayerSkillCapabilityImplementation reloaded = new PlayerSkillCapabilityImplementation();
        reloaded.deserializeNBT(level.registryAccess(), nbt.copy());
        check(reloaded.getSkillsPoints() != null
                        && reloaded.getSkillsPoints().get(SkillEnum.TRANSFORM_WEREWOLF) == 1,
                "persistencia NBT del árbol en el servidor");

        DevilRpg.LOGGER.info("[SkillsHarness] servidor dedicado OK: {} checks, {} fallos", checks, FAILS.size());
    }

    private static int count(PlayerSkillCapabilityInterface skills) {
        int n = 0;
        for (Object ignored : skills.getClientSkillBuilder().getAll()) {
            n++;
        }
        return n;
    }

    private static void check(boolean condition, String what) {
        checks++;
        if (!condition) {
            FAILS.add(what);
            DevilRpg.LOGGER.error("[SkillsHarness] FAIL: {}", what);
        }
    }
}
