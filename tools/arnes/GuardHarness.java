package com.chipoodle.devilrpg.debug;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.goal.VillagerGuardGoal;
import com.chipoodle.devilrpg.survival.ObjectiveTargets;
import com.chipoodle.devilrpg.world.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Locale;
import java.util.Random;

/**
 * ARNES TEMPORAL DE DIAGNOSTICO (no se queda en el mod).
 * <p>
 * Arranca un servidor headless con la partida del jugador copiada en {@code run/world}, fuerza los chunks de la aldea
 * 2, mete un jugador de pega en la plaza (el latido del pueblo necesita jugador cerca) y deja correr el latido de
 * verdad ({@code VillageManager.manageNearby}: reparte oficios, alista a la guardia y le pone sus goals), volcando en
 * el log lo que hace cada guardia.
 */
@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.GAME)
public class GuardHarness {

    private static final BlockPos CENTRO = new BlockPos(1414, 120, 1414);
    private static final int INDICE = 2;
    private static boolean listo = false;
    private static int ticks = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();
        FakePlayer pega;
        if (!listo) {
            listo = true;
            pega = preparar(level, event.getServer());
        } else {
            pega = FakePlayerFactory.getMinecraft(level);
        }
        ticks++;
        // El latido de la aldea, tal cual lo llama el tick del jugador (con el ancla del objetivo 2).
        if (pega != null) {
            pega.moveTo(CENTRO.getX() + 0.5D, CENTRO.getY(), CENTRO.getZ() + 0.5D);
            VillageManager.manageNearby(level, pega, ancla(), INDICE);
        }
        if (ticks % 20 == 0) {
            volcar(level);
        }
    }

    private static FakePlayer preparar(ServerLevel level, net.minecraft.server.MinecraftServer server) {
        int cx = CENTRO.getX() >> 4;
        int cz = CENTRO.getZ() >> 4;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                level.setChunkForced(cx + dx, cz + dz, true);
            }
        }
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        // Sin bichos: la ronda se mide sola (el combate va antes que la ronda y los guardias se morian peleando).
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setDayTime(6000L);
        for (net.minecraft.world.entity.Mob m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                new AABB(CENTRO).inflate(160))) {
            if (m instanceof net.minecraft.world.entity.monster.Monster) {
                m.discard();
            }
        }
        FakePlayer pega = FakePlayerFactory.getMinecraft(level);
        pega.moveTo(CENTRO.getX() + 0.5D, CENTRO.getY(), CENTRO.getZ() + 0.5D);
        DevilRpg.LOGGER.info("[Arnes] ALDEA 2 centro={} aldeanos={} gameTime={} ancla={}", CENTRO,
                level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(96)).size(), level.getGameTime(),
                ancla());
        return pega;
    }

    /** El ancla del jugador que hace que el objetivo 2 caiga en el centro de la aldea (misma cuenta que el mod). */
    private static Vec3 ancla() {
        Random rnd = new Random(0x5DEECE66DL + INDICE);
        double distancia = 800 + INDICE * 600 + rnd.nextDouble() * 400;
        double rad = Math.toRadians(45.0D);
        return new Vec3(CENTRO.getX() - Math.cos(rad) * distancia, CENTRO.getY(),
                CENTRO.getZ() - Math.sin(rad) * distancia);
    }

    private static void volcar(ServerLevel level) {
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(96))) {
            if (!VillagerGuardGoal.esGuardia(v)) {
                continue;
            }
            WalkTarget wt = v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            DevilRpg.LOGGER.info("[Arnes] t={} {} pos=({},{},{}) yRot={} destino={} oficio={} trabajo={} etiqueta={}",
                    level.getGameTime(), v.getUUID().toString().substring(0, 8), fmt(v.getX()), fmt(v.getY()),
                    fmt(v.getZ()), fmt(v.getYRot()),
                    wt == null ? "SIN DESTINO" : wt.getTarget().currentBlockPosition().toShortString(),
                    v.getVillagerData().getProfession(), v.getBrain().isActive(Activity.WORK),
                    v.getCustomName() == null ? "-" : v.getCustomName().getString().replace("\n", " | "));
        }
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.2f", d);
    }
}
