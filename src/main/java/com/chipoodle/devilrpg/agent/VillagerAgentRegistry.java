package com.chipoodle.devilrpg.agent;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import com.chipoodle.devilrpg.entity.goal.VillagerLumberjackGoal;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transient registry of one VillagerAgent per villager UUID. No entity references are
 * retained; the server resolves the entity each tick. No NBT is written in A7-01.
 */
@EventBusSubscriber(modid = DevilRpg.MODID)
public final class VillagerAgentRegistry {

    private static final Map<UUID, VillagerAgent> AGENTS = new ConcurrentHashMap<>();
    private static final boolean DIAG = Boolean.getBoolean("devilrpg.agentdiag");

    private VillagerAgentRegistry() {
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Villager villager)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        UUID id = villager.getUUID();
        VillagerAgent agent = AGENTS.computeIfAbsent(id,
                key -> new VillagerAgent(key, level.dimension(),
                        villager.getVillagerData().getProfession().toString()));
        agent.observe(villager);
        if (DIAG) {
            DevilRpg.LOGGER.info("[AGENT] join uuid={} dim={} profession={} agents={}",
                    id, level.dimension().location(), agent.profession(), AGENTS.size());
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }
        VillagerAgent removed = AGENTS.remove(villager.getUUID());
        if (removed != null) {
            removed.invalidate();
            if (DIAG) {
                DevilRpg.LOGGER.info("[AGENT] leave uuid={} agents={}", villager.getUUID(), AGENTS.size());
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }
        VillagerAgent removed = AGENTS.remove(villager.getUUID());
        if (removed != null) {
            removed.invalidate();
            if (DIAG) {
                DevilRpg.LOGGER.info("[AGENT] death uuid={} agents={}", villager.getUUID(), AGENTS.size());
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (Map.Entry<UUID, VillagerAgent> entry : AGENTS.entrySet()) {
            ServerLevel level = server.getLevel(entry.getValue().dimension());
            if (level == null) {
                continue;
            }
            Entity entity = level.getEntity(entry.getKey());
            if (entity instanceof Villager villager) {
                entry.getValue().observe(villager);
                for (var wrapped : villager.goalSelector.getAvailableGoals()) {
                    if (wrapped.getGoal() instanceof VillagerLumberjackGoal lumber) {
                        entry.getValue().observeForestry(lumber.snapshot());
                        break;
                    }
                }
                entry.getValue().tick(level.getGameTime());
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        AGENTS.values().forEach(VillagerAgent::invalidate);
        AGENTS.clear();
    }

    public static Optional<VillagerAgent> agent(UUID villagerId) {
        return Optional.ofNullable(AGENTS.get(villagerId));
    }

    public static int count() {
        return AGENTS.size();
    }

    public static List<VillagerAgent> snapshot() {
        return List.copyOf(new ArrayList<>(AGENTS.values()));
    }
}
