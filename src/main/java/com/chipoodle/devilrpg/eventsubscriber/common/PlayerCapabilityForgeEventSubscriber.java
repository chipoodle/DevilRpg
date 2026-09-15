/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the editor in the editor.
 */
package com.chipoodle.devilrpg.eventsubscriber.common;

import net.minecraft.resources.ResourceLocation;
import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapability;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityInterface;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityInterface;
import com.chipoodle.devilrpg.world.VillageManager;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.capability.stamina.PlayerStaminaCapability;
import com.chipoodle.devilrpg.capability.stamina.PlayerStaminaCapabilityInterface;
import com.chipoodle.devilrpg.capability.tamable_minion.TamableMinionCapability;
import com.chipoodle.devilrpg.capability.tamable_minion.TamableMinionCapabilityInterface;
import com.chipoodle.devilrpg.entity.ITamableEntity;
import com.chipoodle.devilrpg.init.ModEffects;
import com.chipoodle.devilrpg.network.payload.PlayerAuxiliarPayload;
import com.chipoodle.devilrpg.network.payload.PlayerExperiencePayload;
import com.chipoodle.devilrpg.network.payload.PlayerManaPayload;
import com.chipoodle.devilrpg.network.payload.PlayerMinionPayload;
import com.chipoodle.devilrpg.network.payload.PlayerPassiveSkillPayload;
import com.chipoodle.devilrpg.network.payload.PlayerSkillTreePayload;
import com.chipoodle.devilrpg.network.payload.PlayerStaminaPayload;
import com.chipoodle.devilrpg.skillsystem.AbstractSkillExecutor;
import com.chipoodle.devilrpg.survival.ObjectiveManager;
import com.chipoodle.devilrpg.util.EventUtils;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * @author Christian
 */

@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.GAME)
public class PlayerCapabilityForgeEventSubscriber {

    /** Fraccion de XP que se conserva al morir: 0.95 = se pierde un 5%. */
    private static final double XP_KEPT = 0.95;

    /**
     * Aviso de la XP perdida al morir, pendiente de mostrar al reaparecer.
     * <p>
     * Se guarda aquí y no se manda desde {@code PlayerEvent.Clone} porque en ese momento la entidad clonada
     * todavía no es el jugador vivo: se manda al recibir {@code PlayerRespawnEvent}, que sí lo es.
     */
    private static final Map<UUID, String> PENDING_DEATH_XP_MESSAGE = new HashMap<>();

    /**
     * Cada cuantos ticks se repara 1 punto de durabilidad por pieza de cuero mientras se esta
     * transformado. 120 ticks = 6s (10x mas lento que antes) para simular la durabilidad de un
     * armadura de diamante.
     */
    private static final int ARMOR_REPAIR_INTERVAL_TICKS = 120;

    /** Cada cuántos ticks se guarda una copia del estado de los minions (10 s). Ver {@code captureMinions}. */
    private static final int MINION_CAPTURE_INTERVAL_TICKS = 200;

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone e) {
        if (e.isWasDeath()) {
            // Al morir se conserva el NIVEL y solo se pierde un 5% de la experiencia del nivel.
            applyDeathXpPenalty(e);
            // Regla de diseño: al morir el jugador, sus minions se van con él (y no vuelven al entrar).
            releaseMinionsOnDeath(e.getOriginal());
            clonePlayerCapability(e, PlayerAuxiliaryCapability.INSTANCE);
            clonePlayerCapability(e, PlayerExperienceCapability.INSTANCE);
            clonePlayerCapability(e, PlayerManaCapability.INSTANCE);
            clonePlayerCapability(e, PlayerStaminaCapability.INSTANCE);
            clonePlayerCapability(e, PlayerMinionCapability.INSTANCE);
            clonePlayerCapability(e, PlayerSkillCapability.INSTANCE);
        }
    }

    /**
     * Penalización por muerte: <b>se conserva el nivel</b> y solo se pierde el 10% de la experiencia
     * acumulada dentro de ese nivel.
     * <p>
     * Vanilla resetea la XP del jugador al morir (el clon nace en nivel 0 y sin progreso), así que aquí se
     * restaura desde {@code original}: el nivel <b>tal cual estaba</b> y la barra al 90% de donde estaba. O
     * sea que no se pierden niveles —el nivel del mod (los puntos de habilidad) se deriva de él, así que
     * tampoco— pero sí hay que volver a ganar esa experiencia para seguir subiendo.
     * <p>
     * <b>Antes estaba mal</b>: hacía {@code nivel × 0.9} (morir a nivel 42 te dejaba en 37), que es justo lo
     * contrario a la intención de diseño.
     * <p>
     * Además deja preparado el aviso al jugador con el porcentaje y <b>cuánto</b> ha perdido, porque antes la
     * pérdida solo quedaba en el log del servidor y morir parecía no costar nada.
     */
    private static void applyDeathXpPenalty(PlayerEvent.Clone e) {
        Player original = e.getOriginal();
        Player clone = e.getEntity();

        // El NIVEL no se toca: se recupera el que tenía antes de morir.
        clone.experienceLevel = original.experienceLevel;
        // La experiencia del nivel sí: se conserva el 90% de la barra.
        float progressBefore = original.experienceProgress;
        clone.experienceProgress = progressBefore * (float) XP_KEPT;
        // Contador de XP acumulada (solo estadística: no decide el nivel ni la barra).
        clone.totalExperience = (int) Math.floor(original.totalExperience * XP_KEPT);

        int xpNeeded = Math.max(1, original.getXpNeededForNextLevel());
        int lostPoints = Math.max(0, Math.round((progressBefore - clone.experienceProgress) * xpNeeded));

        DevilRpg.LOGGER.info("[XP] Muerte: nivel {} CONSERVADO, barra {}% -> {}% y {} de {} puntos perdidos ({} XP total)",
                clone.experienceLevel,
                Math.round(progressBefore * 100.0F),
                Math.round(clone.experienceProgress * 100.0F),
                lostPoints, xpNeeded, clone.totalExperience);

        if (lostPoints <= 0) {
            return; // no había experiencia acumulada en el nivel: no se avisa de una pérdida de cero
        }
        int lostPercent = (int) Math.round((1.0D - XP_KEPT) * 100.0D);
        PENDING_DEATH_XP_MESSAGE.put(clone.getUUID(), "Has muerto: pierdes el " + lostPercent
                + "% de la experiencia de tu nivel (" + lostPoints + " de " + xpNeeded + " puntos). "
                + "Conservas el nivel " + clone.experienceLevel
                + ": vuelve a ganar esa experiencia para seguir subiendo.");
    }

    /**
     * Skin Armor: mientras el jugador esta transformado en hombre lobo y lleva armadura de CUERO
     * COMPLETA, la armadura se repara gradualmente (su durabilidad se mantiene alta como si fuera
     * de diamante). Al volver a humano se deja de reparar y vuelve a desgastarse con normalidad.
     */
    /**
     * <b>Sello místico de la aldea</b>: si una aldea ya venció su asedio y sigue viva, ninguna criatura <b>hostil</b>
     * puede aparecer dentro de su perímetro (vanilla o del mod). Fuera, en el campo, se spawnea con normalidad: la
     * horda sigue pudiendo llegar andando y atacar.
     */
    @SubscribeEvent
    public static void onMobSpawnPositionCheck(net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck event) {
        if (event.getEntity().getType().getCategory() != net.minecraft.world.entity.MobCategory.MONSTER) {
            return;
        }
        if (VillageManager.estaProtegida((net.minecraft.server.level.ServerLevel) event.getLevel(),
                event.getEntity().blockPosition())) {
            event.setResult(net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Objetivo de progresión: comprueba si el jugador alcanzó el objetivo y avanza al siguiente.
        ObjectiveManager.tick(player);
        // Copia de seguridad del estado de los minions cada MINION_CAPTURE_INTERVAL_TICKS (10 s). Es lo que
        // hace fiables las dos cosas raras: un corte de luz (no hay evento de salida) y que el guardado al
        // desconectarse llegue tarde (el servidor guarda al jugador ANTES de ese evento).
        if (player.tickCount % MINION_CAPTURE_INTERVAL_TICKS == 0) {
            PlayerMinionCapabilityInterface minionCap =
                    IGenericCapability.getUnwrappedPlayerCapability(player, PlayerMinionCapability.INSTANCE);
            if (minionCap != null) {
                minionCap.captureMinions(player, false);
                // REINTENTO de la recuperación de minions durante el primer minuto y medio: al entrar, los chunks
                // alrededor del jugador pueden NO estar cargados todavía, así que `getEntity(uuid)` devuelve null y
                // el minion (lobo, oso, wisp de salud con su aura) no se recuperaba. Con dos entradas así, la copia
                // se borraba y el minion se perdía para siempre: era el "a veces sí y a veces no". Ahora se
                // reintenta cada 10 s hasta que sus chunks cargan.
                if (player.tickCount < 1800) { // 90 s: los chunks tardan en cargarse tras entrar
                    minionCap.restoreStoredMinions(player);
                }
            }
            // Texto flotante sobre la cabeza de los aldeanos ("que esta haciendo cada uno"), cada segundo.
            if (player.tickCount % 20 == 0 && player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                VillageManager.refrescarEtiquetas(serverLevel, (net.minecraft.server.level.ServerPlayer) player);
            }
        }
        // Reparar muy despacio (cada ARMOR_REPAIR_INTERVAL_TICKS) para simular la durabilidad de
        // una armadura de diamante: la de cuero se desgasta mucho mas lento mientras eres lobo.
        if (player.tickCount % ARMOR_REPAIR_INTERVAL_TICKS != 0) {
            return;
        }
        PlayerAuxiliaryCapabilityInterface aux = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
        if (aux == null || !aux.isWerewolfTransformation()) {
            return;
        }
        repairLeatherArmor(player);
    }

    /** Repara 1 punto de durabilidad en cada pieza de cuero que no este al maximo. */
    private static void repairLeatherArmor(ServerPlayer player) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot slot : slots) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof ArmorItem armorItem
                    && armorItem.getMaterial().equals(ArmorMaterials.LEATHER)
                    && stack.isDamageableItem()) {
                int damage = stack.getDamageValue();
                if (damage > 0) {
                    stack.setDamageValue(damage - 1);
                }
            }
        }
    }

    private static <T extends IGenericCapability> void clonePlayerCapability(PlayerEvent.Clone e, Supplier<AttachmentType<T>> cap) {        Player originalPlayer = e.getOriginal();
        Player actualPlayer = e.getEntity();

        CompoundTag originalCompound = originalPlayer.getData(cap).serializeNBT(originalPlayer.level().registryAccess());
        actualPlayer.getData(cap).deserializeNBT(actualPlayer.level().registryAccess(), originalCompound);
    }

    /**
     * Regla de diseño: al morir el jugador sus minions desaparecen y no vuelven. Se matan los vivos (que
     * además se suicidan solos al ver al dueño muerto, esto es la red de seguridad) y se olvida lo guardado.
     */
    private static void releaseMinionsOnDeath(Player original) {
        PlayerMinionCapabilityInterface minionCap =
                IGenericCapability.getUnwrappedPlayerCapability(original, PlayerMinionCapability.INSTANCE);
        if (minionCap == null) {
            return;
        }
        minionCap.removeAllSoulWolf(original);
        minionCap.removeAllSoulBear(original);
        minionCap.removeAllWisp(original);
        minionCap.clearStoredMinions(original);
    }

    /**
     * Al desconectarse el jugador, sus minions se guardan (NBT completo + dónde estaban) y se sacan del
     * mundo: así no se quedan sueltos mientras no juegas y vuelven contigo al entrar. Si el corte es brusco
     * (luz, crash) esto no se ejecuta: el minion se queda en el mundo y al volver se <b>adopta</b> — nunca se
     * duplica.
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        PlayerMinionCapabilityInterface minionCap =
                IGenericCapability.getUnwrappedPlayerCapability(player, PlayerMinionCapability.INSTANCE);
        if (minionCap != null) {
            minionCap.captureMinions(player, true);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();

        if (player.level().isClientSide) {
            return;
        }

        // Aviso de la experiencia que se perdió al morir (la calculó restoreNinetyPercentXp en el Clone).
        String deathXpMessage = PENDING_DEATH_XP_MESSAGE.remove(player.getUUID());
        if (deathXpMessage != null) {
            player.displayClientMessage(Component.literal(deathXpMessage), false);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();

        PlayerSkillCapabilityInterface aSkillCap = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerSkillCapability.INSTANCE);
        //Es necesrio enviar nuevamente la activación de todos los pasivos del Player que no se guardan en un Attribute
        SkillEnum.getPassiveSkills().stream().filter(x -> !x.isForMinion()).forEach(skillEnum -> {
            CompoundTag compoundTag = aSkillCap.setSkillToByteArray(skillEnum);
            PacketDistributor.sendToServer(new PlayerPassiveSkillPayload(compoundTag));
        });

        // Los minions persistentes (lobo, oso, wisp) siguen al jugador de dimensión. Se hace en el siguiente
        // tick del servidor para asegurar que el jugador ya está en el nivel destino (el evento puede llegar
        // con la dimensión vieja todavía puesta).
        MinecraftServer server = player.getServer();
        if (server != null) {
            server.execute(() -> {
                PlayerMinionCapabilityInterface minionCap =
                        IGenericCapability.getUnwrappedPlayerCapability(player, PlayerMinionCapability.INSTANCE);
                if (minionCap != null) {
                    minionCap.bringMinionsToPlayer(player);
                }
            });
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevelEvent(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Player player) {

            if (player.level().isClientSide)
                return;

            BiConsumer<Player, PlayerManaCapabilityInterface> manaBiConsumer = sendManaNBTData();
            manaBiConsumer.accept(player, player.getData(PlayerManaCapability.INSTANCE));

            BiConsumer<Player, PlayerStaminaCapabilityInterface> staminaBiConsumer = sendStaminaNBTData();
            staminaBiConsumer.accept(player, player.getData(PlayerStaminaCapability.INSTANCE));

            BiConsumer<Player, PlayerSkillCapabilityInterface> skillBiConsumer = removeStoredSkillAttributes();
            skillBiConsumer.accept(player, player.getData(PlayerSkillCapability.INSTANCE));

            BiConsumer<Player, PlayerExperienceCapabilityInterface> expBiConsumer = sendExperienceNBTData();
            expBiConsumer.accept(player, player.getData(PlayerExperienceCapability.INSTANCE));

            BiConsumer<Player, PlayerAuxiliaryCapabilityInterface> auxBiConsumer = shapeshiftToNormal();
            auxBiConsumer.accept(player, player.getData(PlayerAuxiliaryCapability.INSTANCE));

            // Los minions NO se tocan aquí: este evento también salta al morir y al cambiar de dimensión, y
            // cada caso tiene su propio tratamiento (guardarlos al desconectarse, matarlos al morir y
            // llevarlos contigo al cambiar de dimensión). Antes se borraban todos aquí, que es justo lo que
            // hacía que no sobrevivieran a salir y volver a entrar.
        }
    }

    /**
     * Restore client player capabilities' values on join. Applies passive skills to entities
     *
     * @param event EntityJoinLevelEvent
     */
    @SubscribeEvent
    public static void onApplyPetPassives(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();

        if ((entity instanceof Player || entity instanceof ItemEntity)) {
            return;
        }

        if (entity instanceof ITamableEntity) {
            TamableMinionCapabilityInterface minionPassiveCap
                    = IGenericCapability.getUnwrappedMinionCapability((ITamableEntity) entity,
                    TamableMinionCapability.INSTANCE);
            minionPassiveCap.applyPassives((ITamableEntity) entity);
        }
    }

    /**
     * Restore client player capabilities' values on join. Applies passive skills to player
     *
     * @param event EntityJoinLevelEvent
     */
    @SubscribeEvent
    public static void onPlayerJoinLevelEvent(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (player.level().isClientSide)
            return;

        BiConsumer<Player, PlayerManaCapabilityInterface> manaBiConsumer = sendManaNBTData();
        EventUtils.onJoin(player, manaBiConsumer, PlayerManaCapability.INSTANCE);

        BiConsumer<Player, PlayerStaminaCapabilityInterface> staminaBiConsumer = sendStaminaNBTData();
        EventUtils.onJoin(player, staminaBiConsumer, PlayerStaminaCapability.INSTANCE);

        BiConsumer<Player, PlayerSkillCapabilityInterface> skillBiConsumer = removeStoredSkillAttributes();
        EventUtils.onJoin(player, skillBiConsumer, PlayerSkillCapability.INSTANCE);

        BiConsumer<Player, PlayerExperienceCapabilityInterface> expBiConsumer = sendExperienceNBTData();
        EventUtils.onJoin(player, expBiConsumer, PlayerExperienceCapability.INSTANCE);

        BiConsumer<Player, PlayerAuxiliaryCapabilityInterface> auxBiConsumer = shapeshiftToNormal();
        EventUtils.onJoin(player, auxBiConsumer, PlayerAuxiliaryCapability.INSTANCE);

        // MINIONS: la restauración va DIRECTA (no por EventUtils.onJoin), porque ese helper difiere la llamada
        // al hilo del CLIENTE y aquí hay que tocar entidades del servidor. Y el guardado NO se hace solo aquí:
        // PlayerLoggedOutEvent salta DESPUÉS de que el servidor guarde al jugador, así que lo escrito ahí no
        // llegaba al disco (era el bug por el que los minions no volvían). La copia buena es la periódica de
        // onPlayerTick (cada MINION_CAPTURE_INTERVAL_TICKS).
        PlayerMinionCapabilityInterface minionCap =
                IGenericCapability.getUnwrappedPlayerCapability(player, PlayerMinionCapability.INSTANCE);
        if (minionCap != null) {
            // 1) Devuelve los minions guardados (adoptando los que sigan por el mundo y recreando los que ya
            //    no estén). 2) Y trae los que sigan VIVOS en el mundo sin copia guardada: es el caso de un
            //    corte de luz y también el de partidas anteriores a este cambio.
            // ENVUELTO EN try/catch A PROPÓSITO: esto corre dentro del evento de entrada al mundo y, si algo
            // peta aquí, el servidor NO te deja entrar ("Couldn't place player in world" / "Invalid player
            // data"). Un problema con un minion nunca debe impedirte jugar. Paso el 12-sep-2026 con un NPE.
            try {
                minionCap.restoreStoredMinions(player);
                minionCap.bringMinionsToPlayer(player);
            } catch (Exception e) {
                DevilRpg.LOGGER.error("[Minion] error restaurando minions al entrar (sigo, para que puedas jugar)", e);
            }
        }
    }

    private static BiConsumer<Player, PlayerAuxiliaryCapabilityInterface> shapeshiftToNormal() {
        return (aPlayer, theAux) -> {
            if (!aPlayer.isLocalPlayer()) {
                theAux.setWerewolfAttack(false, aPlayer);
                theAux.setWerewolfTransformation(false, aPlayer);
            }
        };
    }

    private static BiConsumer<Player, PlayerExperienceCapabilityInterface> sendExperienceNBTData() {
        return (aPlayer, theExp) -> {
            if (!aPlayer.isLocalPlayer())
                PacketDistributor.sendToPlayer((ServerPlayer) aPlayer, new PlayerExperiencePayload(theExp.serializeNBT(aPlayer.level().registryAccess())));
        };
    }

    private static BiConsumer<Player, PlayerManaCapabilityInterface> sendManaNBTData() {
        return (aPlayer, theMana) -> {
            if (!aPlayer.isLocalPlayer())
                PacketDistributor.sendToPlayer((ServerPlayer) aPlayer, new PlayerManaPayload(theMana.serializeNBT(aPlayer.level().registryAccess())));
        };
    }

    private static BiConsumer<Player, PlayerStaminaCapabilityInterface> sendStaminaNBTData() {
        return (aPlayer, theStamina) -> {
            if (!aPlayer.isLocalPlayer())
                PacketDistributor.sendToPlayer((ServerPlayer) aPlayer, new PlayerStaminaPayload(theStamina.serializeNBT(aPlayer.level().registryAccess())));
        };
    }

    private static BiConsumer<Player, PlayerSkillCapabilityInterface> removeStoredSkillAttributes() {
        return (aPlayer, presentSkill) -> {
            if (!aPlayer.isLocalPlayer()) {
                HashMap<String, String> attributeModifiers = presentSkill.getAttributeModifiers();
                String hlthAttMod = attributeModifiers.get(Attributes.MAX_HEALTH.value().getDescriptionId());
                String spdAttMod = attributeModifiers.get(Attributes.MOVEMENT_SPEED.value().getDescriptionId());
                String armrAttMod = attributeModifiers.get(Attributes.ARMOR.value().getDescriptionId());
                String attDmgMod = attributeModifiers.get(Attributes.ATTACK_DAMAGE.value().getDescriptionId());
                String stepDmgMod = attributeModifiers.get(Attributes.STEP_HEIGHT.value().getDescriptionId());

                if (hlthAttMod != null) {
                    Objects.requireNonNull(aPlayer.getAttribute(Attributes.MAX_HEALTH)).removeModifier(ResourceLocation.parse(hlthAttMod));
                }
                if (spdAttMod != null) {
                    Objects.requireNonNull(aPlayer.getAttribute(Attributes.MOVEMENT_SPEED)).removeModifier(ResourceLocation.parse(spdAttMod));
                }
                if (armrAttMod != null) {
                    Objects.requireNonNull(aPlayer.getAttribute(Attributes.ARMOR)).removeModifier(ResourceLocation.parse(armrAttMod));
                }
                if (attDmgMod != null) {
                    Objects.requireNonNull(aPlayer.getAttribute(Attributes.ATTACK_DAMAGE)).removeModifier(ResourceLocation.parse(attDmgMod));
                }
                if (stepDmgMod != null) {
                    Objects.requireNonNull(aPlayer.getAttribute(Attributes.STEP_HEIGHT)).removeModifier(ResourceLocation.parse(stepDmgMod));
                }

                PacketDistributor.sendToPlayer((ServerPlayer) aPlayer, new PlayerSkillTreePayload(presentSkill.serializeNBT(aPlayer.level().registryAccess())));

                aPlayer.removeEffect(ModEffects.KNOCKBACK_RESISTANCE);
                aPlayer.removeEffect(MobEffects.ABSORPTION);
            }
        };
    }

    @SubscribeEvent
    public static void onPlayerLevelChange(PlayerXpEvent.LevelChange e) {
        Player player = e.getEntity();

        PlayerExperienceCapabilityInterface expCap = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerExperienceCapability.INSTANCE);
        expCap.setCurrentLevel(player.experienceLevel + e.getLevels(), player);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onLivingEquipmentChangeEvent(LivingEquipmentChangeEvent event) {

        if ((event.getEntity() instanceof Player player) && event.getSlot().getType().compareTo(EquipmentSlot.Type.HUMANOID_ARMOR) == 0) {
            AbstractSkillExecutor loadedSkill = getLoadedSkillForPlayer(player, SkillEnum.SKIN_ARMOR);
            loadedSkill.execute(player.level(), player, new HashMap<>());
        }
    }

    private static AbstractSkillExecutor getLoadedSkillForPlayer(Player player, SkillEnum skill) {
        PlayerSkillCapabilityInterface aSkillCap = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerSkillCapability.INSTANCE);
        AbstractSkillExecutor loadedSkill = aSkillCap.getLoadedSkillExecutor(skill);
        return loadedSkill;
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPlayerSetSpawnEvent(PlayerSetSpawnEvent event) {
        if (event.getNewSpawn() != null) {
            //DevilRpg.LOGGER.info("||||||||||||||||||||||||||| SPAWN POINT FIRST {}", event.getNewSpawn());
        } else {
            //DevilRpg.LOGGER.info("||||||||||||||||||||||||||| SPAWN POINT");
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPlayerPickupXP(PlayerXpEvent.PickupXp e) {
        //e.getOrb().value *= 0.5;
    }
}
