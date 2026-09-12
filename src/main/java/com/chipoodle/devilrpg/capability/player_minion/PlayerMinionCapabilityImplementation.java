package com.chipoodle.devilrpg.capability.player_minion;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.neoforged.neoforge.network.PacketDistributor;
import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.ITamableEntity;
import com.chipoodle.devilrpg.entity.SoulBear;
import com.chipoodle.devilrpg.entity.SoulWisp;
import com.chipoodle.devilrpg.entity.SoulWolf;
import com.chipoodle.devilrpg.init.ModDamageTypes;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.payload.PlayerMinionPayload;
import com.chipoodle.devilrpg.util.BytesUtil;
import com.chipoodle.devilrpg.util.TargetUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;


public class PlayerMinionCapabilityImplementation implements PlayerMinionCapabilityInterface {
    public final static String SOULWOLF_MINION_KEY = "Wolf_Minions";
    public final static String SOULBEAR_MINION_KEY = "Bear_Minions";
    public final static String WISP_MINIONS_KEY = "Wisp_Minions";
    public final static String SOULBEAR_INVENTORY_KEY = "Soulbear_Inventory";
    /** Minions guardados al desconectarse el jugador (lista de {Id, Dimension, Pos, Data}). */
    public final static String STORED_MINIONS_KEY = "Stored_Minions";
    private CompoundTag nbt = new CompoundTag();

    public PlayerMinionCapabilityImplementation() {
        ConcurrentLinkedQueue<UUID> soulwolf;
        ConcurrentLinkedQueue<UUID> soulbear;
        ConcurrentLinkedQueue<UUID> wisp;
        CompoundTag soulbearInventory;
        if (nbt.isEmpty()) {
            soulwolf = new ConcurrentLinkedQueue<>();
            soulbear = new ConcurrentLinkedQueue<>();
            wisp = new ConcurrentLinkedQueue<>();
            soulbearInventory = new CompoundTag();
            try {
                nbt.putByteArray(SOULWOLF_MINION_KEY, BytesUtil.toByteArray(soulwolf));
                nbt.putByteArray(SOULBEAR_MINION_KEY, BytesUtil.toByteArray(soulbear));
                nbt.putByteArray(WISP_MINIONS_KEY, BytesUtil.toByteArray(wisp));
                nbt.put(SOULBEAR_INVENTORY_KEY, soulbearInventory);
            } catch (IOException e) {
                DevilRpg.LOGGER.error("Error en constructor PlayerMinionCapabilityImplementation", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public ConcurrentLinkedQueue<UUID> getSoulWolfMinions() {
        try {
            return (ConcurrentLinkedQueue<UUID>) BytesUtil.toObject(nbt.getByteArray(SOULWOLF_MINION_KEY));
        } catch (ClassNotFoundException | IOException e) {
            DevilRpg.LOGGER.error("Error en getSoulWolfMinions", e);
            return null;
        }
    }

    @Override
    public void setSoulWolfMinions(ConcurrentLinkedQueue<UUID> soulWolMinions, Player player) {
        try {
            nbt.putByteArray(SOULWOLF_MINION_KEY, BytesUtil.toByteArray(soulWolMinions));
            if (!player.level().isClientSide) {
                sendSkillChangesToClient((ServerPlayer) player);
            } else {
                sendSkillChangesToServer();
            }
        } catch (IOException e) {
            DevilRpg.LOGGER.error("Error en setSoulWolfMinions", e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public ConcurrentLinkedQueue<UUID> getSoulBearMinions() {
        try {
            return (ConcurrentLinkedQueue<UUID>) BytesUtil.toObject(nbt.getByteArray(SOULBEAR_MINION_KEY));
        } catch (ClassNotFoundException | IOException e) {
            DevilRpg.LOGGER.error("Error en getSoulBearMinions", e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public ConcurrentLinkedQueue<UUID> getAllMinions() {
        try {
            ConcurrentLinkedQueue<UUID> soulbears = (ConcurrentLinkedQueue<UUID>) BytesUtil.toObject(nbt.getByteArray(SOULBEAR_MINION_KEY));
            ConcurrentLinkedQueue<UUID> soulwolves = (ConcurrentLinkedQueue<UUID>) BytesUtil.toObject(nbt.getByteArray(SOULWOLF_MINION_KEY));
            ConcurrentLinkedQueue<UUID> wisps = (ConcurrentLinkedQueue<UUID>) BytesUtil.toObject(nbt.getByteArray(WISP_MINIONS_KEY));
            soulbears.addAll(soulwolves);
            soulbears.addAll(wisps);
            return soulbears;

        } catch (ClassNotFoundException | IOException e) {
            DevilRpg.LOGGER.error("Error en getAllMinions", e);
            return new ConcurrentLinkedQueue<>();
        }
    }


    @Override
    public void setSoulBearMinions(ConcurrentLinkedQueue<UUID> soulBearMinions, Player player) {
        try {
            nbt.putByteArray(SOULBEAR_MINION_KEY, BytesUtil.toByteArray(soulBearMinions));
            if (!player.level().isClientSide) {
                sendSkillChangesToClient((ServerPlayer) player);
            } else {
                sendSkillChangesToServer();
            }
        } catch (IOException e) {
            DevilRpg.LOGGER.error("Error en setSoulBearMinions", e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public ConcurrentLinkedQueue<UUID> getWispMinions() {
        try {
            return (ConcurrentLinkedQueue<UUID>) BytesUtil.toObject(nbt.getByteArray(WISP_MINIONS_KEY));
        } catch (ClassNotFoundException | IOException e) {
            DevilRpg.LOGGER.error("Error en getWispMinions", e);
            return new ConcurrentLinkedQueue<>();
        }
    }

    @Override
    public void setWispMinions(ConcurrentLinkedQueue<UUID> wispMinions, Player player) {
        try {
            nbt.putByteArray(WISP_MINIONS_KEY, BytesUtil.toByteArray(wispMinions));
            if (!player.level().isClientSide) {
                sendSkillChangesToClient((ServerPlayer) player);
            } else {
                sendSkillChangesToServer();
            }
        } catch (IOException e) {
            DevilRpg.LOGGER.error("Error en setWispMinions", e);
        }
    }

    @Override
    public void setSoulBearInventory(CompoundTag soulbearInventory, Player player) {
        nbt.put(SOULBEAR_INVENTORY_KEY, soulbearInventory);
        //DevilRpg.LOGGER.debug("setSoulBearInventory {}", soulbearInventory);
        if (!player.level().isClientSide) {
            sendSkillChangesToClient((ServerPlayer) player);
        } else {
            sendSkillChangesToServer();
        }
    }

    @Override
    public CompoundTag getSoulBearInventory() {
        CompoundTag tag = nbt.getCompound(SOULBEAR_INVENTORY_KEY);
        if (tag.isEmpty())
            return null;
        return tag;
    }

    public SimpleContainer fromTag(ListTag listtag) {
        try {
            SimpleContainer s = new SimpleContainer();
            //s.clearContent();
            for (int i = 0; i < listtag.size(); ++i) {
                CompoundTag compoundtag = listtag.getCompound(i);
                int j = compoundtag.getByte("Slot") & 255;
                if (j < s.getContainerSize()) {
                    s.setItem(j, ItemStack.parse(RegistryAccess.EMPTY, compoundtag).orElse(ItemStack.EMPTY));
                }
            }
            return s;
        } catch (Exception e) {
            DevilRpg.LOGGER.error("Error ", e);
            return null;
        }
    }

    @Override
    public ITamableEntity getTamableByUUID(UUID id, Level world) {
        Entity e;
        if (id != null) {
            if (!world.isClientSide) {
                e = TargetUtils.getEntityByUUID((ServerLevel) world, id);
            } else {
                e = TargetUtils.getEntityByUUID((ClientLevel) world, id);
            }
            if (e != null && e instanceof ITamableEntity) {
                return (ITamableEntity) e;
            }
        }
        return null;
    }

    @Override
    public void removeWisp(Player owner, SoulWisp entity) {
        ConcurrentLinkedQueue<UUID> wisp = getWispMinions();
        if (wisp != null && entity != null && wisp.contains(entity.getUUID())) {
            wisp.remove(entity.getUUID());
            setWispMinions(wisp, owner);
            DamageSource damagesource = new DamageSource(
                    entity.level()
                            .registryAccess()
                            .registryOrThrow(Registries.DAMAGE_TYPE)
                            .getHolderOrThrow(ModDamageTypes.MINION_DEATH));
            entity.hurt(damagesource, Integer.MAX_VALUE);
        }
    }

    @Override
    public void removeSoulWolf(Player owner, SoulWolf entity) {
        ConcurrentLinkedQueue<UUID> soulwolf = getSoulWolfMinions();
        if (soulwolf != null && entity != null && soulwolf.contains(entity.getUUID())) {
            soulwolf.remove(entity.getUUID());
            setSoulWolfMinions(soulwolf, owner);
            DamageSource damagesource = new DamageSource(
                    entity.level()
                            .registryAccess()
                            .registryOrThrow(Registries.DAMAGE_TYPE)
                            .getHolderOrThrow(ModDamageTypes.MINION_DEATH));
            entity.hurt(damagesource, Integer.MAX_VALUE);
        }
    }

    @Override
    public void removeAllWisp(Player owner) {
        ConcurrentLinkedQueue<UUID> wisp = getWispMinions();
        wisp.forEach(id -> {
            ITamableEntity entity = getTamableByUUID(id, owner.level());
            removeWisp(owner, (SoulWisp) entity);
        });
        wisp.clear();
        setWispMinions(wisp, owner);
    }

    @Override
    public void removeAllSoulWolf(Player owner) {
        ConcurrentLinkedQueue<UUID> soulwolf = getSoulWolfMinions();
        soulwolf.forEach(id -> {
            ITamableEntity entity = getTamableByUUID(id, owner.level());
            removeSoulWolf(owner, (SoulWolf) entity);
        });
        soulwolf.clear();
        setSoulWolfMinions(soulwolf, owner);
    }

    @Override
    public void removeSoulBear(Player owner, SoulBear entity) {
        ConcurrentLinkedQueue<UUID> soulbear = getSoulBearMinions();
        if (soulbear != null && entity != null && soulbear.contains(entity.getUUID())) {
            soulbear.remove(entity.getUUID());
            setSoulBearMinions(soulbear, owner);
            DamageSource damagesource = new DamageSource(
                    entity.level()
                            .registryAccess()
                            .registryOrThrow(Registries.DAMAGE_TYPE)
                            .getHolderOrThrow(ModDamageTypes.MINION_DEATH));
            entity.hurt(damagesource, Integer.MAX_VALUE);
        }

    }

    @Override
    public void removeAllSoulBear(Player owner) {
        ConcurrentLinkedQueue<UUID> soulbear = getSoulBearMinions();
        for (UUID uuid : soulbear) {
            ITamableEntity entity = getTamableByUUID(uuid, owner.level());
            removeSoulBear(owner, (SoulBear) entity);
        }
        soulbear.clear();
        setSoulBearMinions(soulbear, owner);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return nbt;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        this.nbt = nbt;
    }

    private void sendSkillChangesToServer() {
        PacketDistributor.sendToServer(new PlayerMinionPayload(serializeNBT(RegistryAccess.EMPTY)));
    }

    private void sendSkillChangesToClient(ServerPlayer pe) {
        PacketDistributor.sendToPlayer(pe,
                new PlayerMinionPayload(serializeNBT(RegistryAccess.EMPTY)));
    }

    @Override
    public SoulWisp existsWisp(Class<? extends SoulWisp> instance, Player player) {
        return getWispMinions().stream()
                .map(wispKey -> (SoulWisp) getTamableByUUID(wispKey, player.level()))
                .filter(Objects::nonNull)
                .filter(instance::isInstance)
                .findFirst()
                .orElse(null);
    }

    public void summonWispComplete(Level levelIn, Player player, Random rand, Supplier<SoulWisp> summonWispFunction, int maxSummons, Class<? extends SoulWisp> instance) {
        ConcurrentLinkedQueue<UUID> keys = getWispMinions();
        SoulWisp existingWisp = existsWisp(instance, player);

        if (existingWisp == null) {
            summonNewWisp(player, keys, maxSummons, summonWispFunction);
        } else {
            replaceExistingWisp(player, existingWisp, keys, summonWispFunction);
        }

        setWispMinions(keys, player);
    }

    private void summonNewWisp(Player player, ConcurrentLinkedQueue<UUID> keys, int maxSummons, Supplier<SoulWisp> summonWispFunction) {
        keys.offer(summonWispFunction.get().getUUID());
        removeOldestWispIfNecessary(keys, maxSummons, player);
    }

    private void replaceExistingWisp(Player player, SoulWisp existingWisp, ConcurrentLinkedQueue<UUID> keys, Supplier<SoulWisp> summonWispFunction) {
        keys.remove(existingWisp.getUUID());
        removeWisp(player, existingWisp);

        keys.offer(summonWispFunction.get().getUUID());
    }

    private void removeOldestWispIfNecessary(ConcurrentLinkedQueue<UUID> keys, int maxSummons, Player player) {
        if (keys.size() > maxSummons) {
            // OJO: se MIRA el más viejo, pero NO se quita de la lista hasta saber que el wisp existe de verdad.
            // Antes se hacía `poll()` primero: si ese wisp estaba en un chunk descargado (o guardado), la lista
            // lo olvidaba pero el wisp seguía vivo, así que al cargarse de nuevo tenías uno de más (duplicado).
            UUID oldestKey = keys.peek();
            SoulWisp oldestWisp = oldestKey == null ? null : (SoulWisp) getTamableByUUID(oldestKey, player.level());
            if (oldestWisp != null) {
                removeWisp(player, oldestWisp); // removeWisp ya quita el UUID de la lista
            }
        }
    }

    // --- Persistencia de minions: que te sigan al salir, volver a entrar y cambiar de dimensión -----

    /** Lista de minions guardados: cada entrada es {Id (UUID), Dimension, Pos (long), Data (NBT completo)}. */
    private ListTag storedMinionsTag() {
        if (!nbt.contains(STORED_MINIONS_KEY, Tag.TAG_LIST)) {
            nbt.put(STORED_MINIONS_KEY, new ListTag());
        }
        return nbt.getList(STORED_MINIONS_KEY, Tag.TAG_COMPOUND);
    }

    /** Todas las UUIDs de minions (de las tres listas), sin repetir y sin petar si alguna vuelve null. */
    private List<UUID> allMinionIds() {
        List<UUID> ids = new ArrayList<>();
        for (ConcurrentLinkedQueue<UUID> queue : List.of(getSoulWolfMinions(), getSoulBearMinions(), getWispMinions())) {
            if (queue != null) {
                ids.addAll(queue);
            }
        }
        return ids;
    }

    /**
     * Guarda la copia del estado de los minions vivos (NBT completo + dimensión + posición).
     * <p>
     * Se llama <b>periódicamente</b> (cada pocos segundos) y también al desconectarse con
     * {@code removeFromWorld = true}. La copia periódica es lo que hace que esto funcione: el evento de
     * desconexión salta <b>después</b> de que el servidor haya guardado al jugador, así que lo que se
     * escribiera solo ahí no llegaba al disco (era el bug por el que los minions no volvían). Con la copia
     * periódica el estado ya está en los datos del jugador, y un corte de luz tampoco lo pierde.
     * <p>
     * Las entradas se <b>reemplazan</b> por UUID (no se acumulan) y si el minion no está cargado o está
     * muerto se deja la entrada anterior como estaba.
     */
    @Override
    public void captureMinions(Player player, boolean removeFromWorld) {
        if (player == null || player.level().isClientSide) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ListTag stored = storedMinionsTag();
        int count = 0;
        for (UUID id : allMinionIds()) {
            Entity entity = findLoadedAnywhere(server, id);
            if (!(entity instanceof ITamableEntity) || !entity.isAlive()) {
                continue; // descargado o muerto: se conserva la copia anterior
            }
            removeStoredEntry(stored, id);
            stored.add(buildStoredEntry(entity));
            if (removeFromWorld) {
                // Se saca del mundo SIN morir (discard no dispara die(), así no ensucia las listas de minions).
                entity.discard();
            }
            count++;
        }
        if (count > 0 && removeFromWorld) {
            DevilRpg.LOGGER.info("[Minion] {} minion(es) guardados para {} (vuelven al entrar)",
                    count, player.getName().getString());
        }
        if (player instanceof ServerPlayer serverPlayer && removeFromWorld) {
            sendSkillChangesToClient(serverPlayer);
        }
    }

    /** Construye la entrada guardada de un minion: UUID, dimensión, posición y NBT completo. */
    private CompoundTag buildStoredEntry(Entity entity) {
        CompoundTag entry = new CompoundTag();
        entry.putUUID("Id", entity.getUUID());
        entry.putString("Dimension", entity.level().dimension().location().toString());
        entry.putLong("Pos", entity.blockPosition().asLong());
        CompoundTag data = new CompoundTag();
        entity.saveWithoutId(data);
        entry.put("Data", data);
        return entry;
    }

    /** Quita de la lista la entrada de ese minion, si la hay (para reemplazarla, no para duplicarla). */
    private void removeStoredEntry(ListTag stored, UUID id) {
        for (int i = stored.size() - 1; i >= 0; i--) {
            CompoundTag entry = stored.getCompound(i);
            if (entry.hasUUID("Id") && id.equals(entry.getUUID("Id"))) {
                stored.remove(i);
            }
        }
    }

    /**
     * Devuelve los minions guardados: <b>adopta</b> los que sigan existiendo en el mundo (el caso de un corte
     * de luz, en el que no se llegaron a guardar) y <b>recrea</b> los que ya no estén. Nunca recrea "por si
     * acaso": eso es lo que duplicaría. Después los trae junto al jugador.
     */
    @Override
    public void restoreStoredMinions(Player player) {
        if (player == null || player.level().isClientSide || !(player.level() instanceof ServerLevel playerLevel)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ListTag stored = storedMinionsTag();
        if (stored.isEmpty()) {
            return;
        }
        int restored = 0;
        for (Tag element : stored) {
            CompoundTag entry = (CompoundTag) element;
            if (!entry.hasUUID("Id")) {
                continue;
            }
            UUID id = entry.getUUID("Id");
            BlockPos pos = BlockPos.of(entry.getLong("Pos"));
            ServerLevel storedLevel = levelOf(server, entry.getString("Dimension"));
            ITamableEntity minion = null;
            if (storedLevel != null) {
                minion = resolveMinion(storedLevel, id, pos);
            }
            if (minion == null) {
                minion = recreateMinion(entry, playerLevel, player);
            }
            if (minion == null) {
                continue; // no se pudo recuperar: se descarta la entrada (mejor perderlo que duplicarlo)
            }
            bringToPlayer(minion, player, playerLevel);
            restored++;
        }
        stored.clear();
        DevilRpg.LOGGER.info("[Minion] {} minion(es) devueltos a {}", restored, player.getName().getString());
        if (player instanceof ServerPlayer serverPlayer) {
            sendSkillChangesToClient(serverPlayer);
        }
    }

    /** Lleva a los minions vivos junto al jugador, cambiándolos de dimensión si hace falta. */
    @Override
    public void bringMinionsToPlayer(Player player) {
        if (player == null || player.level().isClientSide || !(player.level() instanceof ServerLevel playerLevel)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        for (UUID id : allMinionIds()) {
            Entity entity = findLoadedAnywhere(server, id);
            if (entity instanceof ITamableEntity minion) {
                bringToPlayer(minion, player, playerLevel);
            }
        }
    }

    /** Olvida los minions guardados (al morir el jugador, que además los mata). */
    @Override
    public void clearStoredMinions(Player player) {
        nbt.put(STORED_MINIONS_KEY, new ListTag());
        if (player instanceof ServerPlayer serverPlayer) {
            sendSkillChangesToClient(serverPlayer);
        }
    }

    /** Busca una entidad por UUID en todos los niveles del servidor (solo encuentra las cargadas). */
    private Entity findLoadedAnywhere(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private ServerLevel levelOf(MinecraftServer server, String dimensionId) {
        try {
            return server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimensionId)));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * ¿Sigue existiendo el minion? Si no está cargado, se fuerza la carga de su chunk (donde estaba al
     * guardarse) y se reintenta: así se distingue "está en un chunk descargado" de "ya no existe".
     */
    private ITamableEntity resolveMinion(ServerLevel level, UUID id, BlockPos pos) {
        Entity entity = level.getEntity(id);
        if (entity == null) {
            level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            entity = level.getEntity(id);
        }
        return entity instanceof ITamableEntity minion ? minion : null;
    }

    /** Recrea un minion desde su NBT guardado, junto al jugador (conserva su UUID: las listas siguen valiendo). */
    private ITamableEntity recreateMinion(CompoundTag entry, ServerLevel level, Player player) {
        CompoundTag data = entry.getCompound("Data");
        return EntityType.create(data, level)
                .map(entity -> {
                    entity.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
                    level.addFreshEntity(entity);
                    return entity instanceof ITamableEntity minion ? minion : null;
                })
                .orElse(null);
    }

    /** Trae un minion al lado del jugador, cambiándolo de dimensión si está en otra. */
    private void bringToPlayer(ITamableEntity minion, Player player, ServerLevel playerLevel) {
        Entity entity = (Entity) minion;
        if (entity.level() != playerLevel) {
            entity = entity.changeDimension(new DimensionTransition(playerLevel, player, DimensionTransition.DO_NOTHING));
            if (entity == null) {
                return;
            }
        }
        entity.teleportTo(player.getX(), player.getY(), player.getZ());
    }
}
