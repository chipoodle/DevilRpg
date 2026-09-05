/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.eventsubscriber.common;

import net.neoforged.neoforge.network.PacketDistributor;
import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.capability.stamina.PlayerStaminaCapability;
import com.chipoodle.devilrpg.capability.stamina.PlayerStaminaCapabilityInterface;
import com.chipoodle.devilrpg.entity.ISoulEntity;
import com.chipoodle.devilrpg.entity.ITamableEntity;
import com.chipoodle.devilrpg.network.payload.PotionPayload;
import com.chipoodle.devilrpg.util.EventUtils;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;


/**
 * @author Christian
 */

@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.GAME)
public class CommonForgeInteractionEventSubscriber {

    public static final double XY_JUMP_FACTOR = 2;

    /**
     * Increase jump height by 1 when Werewolf form
     */
    @SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
    public static void onLivingJumpEvent(LivingJumpEvent event) {
        if (event.getEntity() instanceof Player) {
            //Salta más cuando está transformado
            BiConsumer<LivingJumpEvent, PlayerAuxiliaryCapabilityInterface> c = (eve, auxiliar) -> {
                Vec3 motion = eve.getEntity().getDeltaMovement();

                PlayerSkillCapabilityInterface skillCap = event.getEntity()
                        .getData(PlayerSkillCapability.INSTANCE);
                int points = skillCap.getSkillsPoints().get(SkillEnum.TRANSFORM_WEREWOLF);
                double yJumpFactor = (points * 0.005) + 0.03f; // max 0.13
                double xyJumpFactor = (points * 0.05) + 1; // Salta el doble de distancia hacia todas direcciones cuando points = 20 (x controla rectas, z diagonales)
                eve.getEntity().setDeltaMovement(motion.x() * xyJumpFactor, motion.y() + yJumpFactor, motion.z() * xyJumpFactor);
            };
            EventUtils.onWerewolfTransformation((Player) event.getEntity(), c, event);
        }
    }

    /**
     * Increase fall damage threshold by 1 block when in werewolf form
     */
    @SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
    public static void onLivingFallEvent(LivingFallEvent event) {
        if (event.getEntity() instanceof Player) {
            //el daño por caida es menor cuando está transformado
            BiConsumer<LivingFallEvent, PlayerAuxiliaryCapabilityInterface> c = (eve, auxiliar) -> {
                if (eve.getDistance() > 1) {
                    eve.setDistance(eve.getDistance() - 1);
                }
            };
            EventUtils.onWerewolfTransformation((Player) event.getEntity(), c, event);
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        BiConsumer<PlayerInteractEvent.RightClickItem, PlayerAuxiliaryCapabilityInterface> c = (eve, aux) -> {
            eve.getEntity().swinging = false;
            eve.setCanceled(true);

        };
        //DevilRpg.LOGGER.debug("--------RightClickItem");
        EventUtils.onWerewolfTransformation(event.getEntity(), c, event);
    }


    @SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        BiConsumer<PlayerInteractEvent.LeftClickBlock, PlayerAuxiliaryCapabilityInterface> c = (eve, aux) -> {
            eve.getEntity().swinging = false;
            eve.setCanceled(true);
        };
        EventUtils.onWerewolfTransformation(event.getEntity(), c, event);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        BiConsumer<PlayerInteractEvent.RightClickBlock, PlayerAuxiliaryCapabilityInterface> c = (eve, aux) -> {
            eve.getEntity().swinging = false;
            eve.setCanceled(true);
        };
        EventUtils.onWerewolfTransformation(event.getEntity(), c, event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        BiConsumer<PlayerInteractEvent.EntityInteractSpecific, PlayerAuxiliaryCapabilityInterface> c = (eve, aux) -> {
            eve.getEntity().swinging = false;
            eve.setCanceled(true);

        };
        EventUtils.onWerewolfTransformation(event.getEntity(), c, event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        BiConsumer<PlayerInteractEvent.EntityInteract, PlayerAuxiliaryCapabilityInterface> c = (eve, aux) -> {
            eve.getEntity().swinging = false;
            eve.setCanceled(true);
            //DevilRpg.LOGGER.debug("--------EntityInteract {}",event.getResult());
        };
        EventUtils.onWerewolfTransformation(event.getEntity(), c, event);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        BiConsumer<AttackEntityEvent, PlayerAuxiliaryCapabilityInterface> c = (eve, aux) -> {
            eve.getEntity().swinging = false;
            //eve.setCanceled(true);
            PlayerStaminaCapabilityInterface staminaCap = IGenericCapability.getUnwrappedPlayerCapability(event.getEntity(), PlayerStaminaCapability.INSTANCE);
            //Stamina generation
            staminaCap.addStamina(1.f, event.getEntity());
        };
        EventUtils.onWerewolfTransformation(event.getEntity(), c, event);
    }






	/*@SubscribeEvent
	public static void onLivingUpdateEvent(LivingUpdateEvent event) {

		 * Collection<EffectInstance> activePotionEffects =
		 * event.getEntityLiving().getActivePotionEffects();
		 * DevilRpg.LOGGER.info("---->Entity: "+event.getEntityLiving().getType() +
		 * "active potion effects: "+activePotionEffects);

	}*/

    @SubscribeEvent
    public static void onCriticalHitEvent(CriticalHitEvent event) {
        /*
         * event.getPlayer() .sendMessage(new StringTextComponent( "Critical hit on " +
         * event.getTarget().getName().getStringTruncated(10) + " by " +
         * event.getPlayer().getName().getStringTruncated(10)),
         * event.getPlayer().getUUID());
         */

    }

    /**
     * Updates potion effects on client
     */
    @SubscribeEvent
    public static void onPotionAddedEvent(MobEffectEvent.Added event) {
        handlePotionEvent(event);
    }

    @SubscribeEvent
    public static void onPotionExpiredEvent(MobEffectEvent.Expired event) {
        handlePotionEvent(event);
    }

    private static void handlePotionEvent(MobEffectEvent event) {

        if (
                event.getEntity() instanceof ISoulEntity &&
                        event.getEntity() instanceof ITamableEntity minion &&
                        event.getEffectInstance() != null
        ) {
            LivingEntity owner = minion.getOwner();
            if (owner instanceof ServerPlayer && !event.getEntity().level().isClientSide()) {
                MobEffectInstance potionEffect = event.getEffectInstance();
                CompoundTag effectInstanceNbt = (CompoundTag) potionEffect.save();
                effectInstanceNbt.putUUID(PotionPayload.ENTITY_ID_KEY, event.getEntity().getUUID());
                effectInstanceNbt.putString(PotionPayload.EFFECT_EVENT_TYPE, event.getClass().getSimpleName());
                PacketDistributor.sendToPlayer((ServerPlayer) owner, new PotionPayload(effectInstanceNbt));
            }
        }
    }

    @SubscribeEvent
    public static void onLivingHurtEvent(LivingIncomingDamageEvent event) {

        // DevilRpg.LOGGER.debug("Entity {} source {} ammount {}",event.getEntity().getClass().getName(),event.getSource(),event.getAmount());

    }

    /** Registro de entidades congeladas (UUID -> ticks restantes) para emitir particulas de hielo. */
    private static final Map<UUID, Integer> FROZEN = new ConcurrentHashMap<>();

    /** Marca una criatura como congelada durante {@code ticks}. */
    public static void markFrozen(UUID entityId, int ticks) {
        FROZEN.put(entityId, ticks);
    }

    /**
     * Mientras un enemigo este congelado (Frost Bite), emite particulas de hielo en sus patas/cuerpo
     * durante los segundos que dura el efecto, para visualizar la congelacion sin usar render layers.
     */
    @SubscribeEvent
    public static void onServerTickFrozen(ServerTickEvent.Post event) {
        if (FROZEN.isEmpty()) {
            return;
        }
        var server = event.getServer();
        // Se itera una copia de las claves para poder actualizar/eliminar con seguridad
        // (los entries de ConcurrentHashMap son inmutables y no permiten setValue).
        for (UUID id : new java.util.ArrayList<>(FROZEN.keySet())) {
            Integer remaining = FROZEN.get(id);
            if (remaining == null) {
                continue;
            }
            // Buscar la entidad en cualquier nivel del servidor.
            LivingEntity living = null;
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(id) instanceof LivingEntity l) {
                    living = l;
                    break;
                }
            }
            if (living == null || living.isRemoved()) {
                FROZEN.remove(id); // ya no existe -> limpiar
                continue;
            }
            int next = remaining - 1;
            if (next <= 0) {
                FROZEN.remove(id); // se acabo el efecto -> limpiar
                continue;
            }
            // Emitir particulas de hielo cada 3 ticks en el nivel de la entidad.
            if (living.tickCount % 3 == 0 && living.level() instanceof ServerLevel sl) {
                double x = living.getX();
                double y = living.getY();
                double z = living.getZ();
                sl.sendParticles(ParticleTypes.SNOWFLAKE, x, y + 0.3, z, 2, 0.35, 0.2, 0.35, 0.05);
                sl.sendParticles(ParticleTypes.CRIT, x, y + 0.1, z, 1, 0.2, 0.15, 0.2, 0.1);
            }
            FROZEN.put(id, next);
        }
    }

    @SubscribeEvent
    public static void onEntitySpawn(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Player player) {            PlayerAuxiliaryCapabilityInterface playerCapability = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
            Vec3 spawnPoint = playerCapability.getSpawnPoint();
            if (spawnPoint == null) {
                playerCapability.setSpawnPoint(player.position(), player);
            }
        }
        /*if (!event.getLevel().isClientSide() && event.getEntity() instanceof Zombie) {
            if (event.getLevel().getRandom().nextInt(100) < 5) { // Probabilidad del 5%
                AggressiveZombieEntity aggressiveZombie = new AggressiveZombieEntity(ModEntities.AGGRESSIVE_ZOMBIE.get(), event.getLevel());
                aggressiveZombie.moveTo(event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), event.getEntity().getYRot(), event.getEntity().getXRot());
                event.getLevel().addFreshEntity(aggressiveZombie);
                event.setCanceled(true); // Reemplazar el Zombie normal
            }
        }*/
    }

}
