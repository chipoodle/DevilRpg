package com.chipoodle.devilrpg.capability.player_minion;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.neoforged.neoforge.network.PacketDistributor;
import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.entity.ITamableEntity;
import com.chipoodle.devilrpg.entity.SoulBear;
import com.chipoodle.devilrpg.entity.SoulWisp;
import com.chipoodle.devilrpg.entity.SoulWolf;
import com.chipoodle.devilrpg.init.ModDamageTypes;
import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.payload.PlayerMinionPayload;
import com.chipoodle.devilrpg.util.BytesUtil;
import com.chipoodle.devilrpg.util.SkillEnum;
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
import net.minecraft.world.entity.LivingEntity;
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
    /**
     * Cuántas veces seguidas tiene que faltar un minion de una copia periódica para quitarlo de las listas.
     * Dos (no una) para no borrar por un fallo tonto: la copia puede ser de hasta 10 s antes y el minion pudo
     * andar una casilla mientras su trozo seguía cargado (por eso se cargan las 9 casillas de alrededor).
     */
    private static final int STORED_MISSES_BEFORE_DROP = 2;
    private CompoundTag nbt = new CompoundTag();

    /*
     * Cache de las listas de minions. Antes CADA llamada a getSoulWolfMinions()/getSoulBearMinions()/
     * getWispMinions() deserializaba el byte[] del NBT con serializacion Java (ObjectInputStream), y el HUD
     * de retratos las pedia TRES veces POR FRAME. Con la cache se deserializa una sola vez y se reutiliza la
     * misma cola (ConcurrentLinkedQueue, segura para varios hilos: el render y el hilo principal la tocan a
     * la vez). Se invalida al recibir NBT nuevo (deserializeNBT) y los setter guardan directamente la cola.
     */
    private ConcurrentLinkedQueue<UUID> soulWolfMinionsCache;
    private ConcurrentLinkedQueue<UUID> soulBearMinionsCache;
    private ConcurrentLinkedQueue<UUID> wispMinionsCache;

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

    /**
     * Deserializa una de las listas del NBT. Devuelve siempre una cola (vacia si el dato falta o esta
     * corrupto) en vez de null: asi ninguna llamada revienta con NullPointerException.
     */
    @SuppressWarnings("unchecked")
    private ConcurrentLinkedQueue<UUID> readMinionQueue(String key) {
        try {
            Object read = BytesUtil.toObject(nbt.getByteArray(key));
            if (read instanceof ConcurrentLinkedQueue<?>) {
                return (ConcurrentLinkedQueue<UUID>) read;
            }
        } catch (ClassNotFoundException | IOException | RuntimeException e) {
            DevilRpg.LOGGER.error("Error al leer los minions de {}", key, e);
        }
        return new ConcurrentLinkedQueue<>();
    }

    @Override
    public ConcurrentLinkedQueue<UUID> getSoulWolfMinions() {
        if (soulWolfMinionsCache == null) {
            soulWolfMinionsCache = readMinionQueue(SOULWOLF_MINION_KEY);
        }
        return soulWolfMinionsCache;
    }

    @Override
    public void setSoulWolfMinions(ConcurrentLinkedQueue<UUID> soulWolMinions, Player player) {
        soulWolfMinionsCache = soulWolMinions;
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

    @Override
    public ConcurrentLinkedQueue<UUID> getSoulBearMinions() {
        if (soulBearMinionsCache == null) {
            soulBearMinionsCache = readMinionQueue(SOULBEAR_MINION_KEY);
        }
        return soulBearMinionsCache;
    }

    @Override
    public ConcurrentLinkedQueue<UUID> getAllMinions() {
        // Cola NUEVA: si reutilizaramos la cache del oso, le meteriamos dentro los lobos y los wisps.
        ConcurrentLinkedQueue<UUID> all = new ConcurrentLinkedQueue<>(getSoulBearMinions());
        all.addAll(getSoulWolfMinions());
        all.addAll(getWispMinions());
        return all;
    }


    @Override
    public void setSoulBearMinions(ConcurrentLinkedQueue<UUID> soulBearMinions, Player player) {
        soulBearMinionsCache = soulBearMinions;
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

    @Override
    public ConcurrentLinkedQueue<UUID> getWispMinions() {
        if (wispMinionsCache == null) {
            wispMinionsCache = readMinionQueue(WISP_MINIONS_KEY);
        }
        return wispMinionsCache;
    }

    @Override
    public void setWispMinions(ConcurrentLinkedQueue<UUID> wispMinions, Player player) {
        wispMinionsCache = wispMinions;
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
        // NBT nuevo (sincronizacion cliente<->servidor): las colas cacheadas ya no valen.
        this.soulWolfMinionsCache = null;
        this.soulBearMinionsCache = null;
        this.wispMinionsCache = null;
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
        List<UUID> ids = allMinionIds();
        // Poda: si un minion ya no está en las listas del jugador (murió, se le soltó...), su copia guardada
        // se tira. Si no se podara, al entrar resucitaría un minion que el jugador ya no tiene.
        pruneStoredEntries(stored, ids);
        int count = 0;
        for (UUID id : ids) {
            Entity entity = findLoadedAnywhere(server, id);
            if (!(entity instanceof ITamableEntity) || !entity.isAlive()) {
                continue; // descargado o muerto: se conserva la copia anterior
            }
            removeStoredEntry(stored, id);
            stored.add(buildStoredEntry(entity, removeFromWorld));
            if (removeFromWorld) {
                // Se saca del mundo SIN morir (discard no dispara die(), así no ensucia las listas de minions).
                entity.discard();
            }
            if (removeFromWorld) {
                DevilRpg.LOGGER.info("[Minion] guardado {} {} dim={} pos={} (salud {}/{})",
                        EntityType.getKey(entity.getType()), id,
                        entity.level().dimension().location(),
                        entity.blockPosition(),
                        String.format("%.1f", entity instanceof LivingEntity living ? living.getHealth() : 0f),
                        String.format("%.1f", entity instanceof LivingEntity living ? living.getMaxHealth() : 0f));
            }
            count++;
        }
        if (removeFromWorld) {
            DevilRpg.LOGGER.info("[Minion] {} minion(es) guardados para {} (vuelven al entrar)",
                    count, player.getName().getString());
        } else {
            // Copia periódica: a nivel debug para no llenar el log cada 10 s.
            DevilRpg.LOGGER.debug("[Minion] copia periódica para {}: {} minion(es) en el mundo, {} entrada(s) guardadas",
                    player.getName().getString(), count, stored.size());
        }
        if (player instanceof ServerPlayer serverPlayer && removeFromWorld) {
            sendSkillChangesToClient(serverPlayer);
        }
    }

    /** Tira las copias guardadas de minions que ya no están en las listas del jugador. */
    private void pruneStoredEntries(ListTag stored, List<UUID> currentIds) {
        for (int i = stored.size() - 1; i >= 0; i--) {
            CompoundTag entry = stored.getCompound(i);
            if (!entry.hasUUID("Id")) {
                stored.remove(i);
                continue;
            }
            UUID id = entry.getUUID("Id");
            if (!currentIds.contains(id)) {
                DevilRpg.LOGGER.info("[Minion] olvido la copia guardada de {} (ya no es un minion tuyo)", id);
                stored.remove(i);
            }
        }
    }

    /** Construye la entrada guardada de un minion: UUID, dimensión, posición y NBT completo. */
    private CompoundTag buildStoredEntry(Entity entity, boolean stowed) {
        CompoundTag entry = new CompoundTag();
        entry.putUUID("Id", entity.getUUID());
        entry.putString("Dimension", entity.level().dimension().location().toString());
        entry.putLong("Pos", entity.blockPosition().asLong());
        // Stowed = la copia se hizo al SACAR el minion del mundo (al desconectarse), así que si al entrar no
        // aparece hay que recrearlo. En una copia periódica (Stowed false) el minion sigue en el mundo: si al
        // entrar no está, es que se murió mientras no mirabas y entonces NO se resucita (se limpia la lista).
        entry.putBoolean("Stowed", stowed);
        entry.put("Data", saveEntityData(entity));
        return entry;
    }

    /**
     * NBT completo de la entidad, listo para volver a crearla.
     * <p>
     * <b>OJO, ESTE ERA EL BUG</b>: {@code saveWithoutId} <b>no escribe el campo {@code id}</b> (por eso se llama
     * "without id"; el id solo lo pone {@code Entity.save()}, que además se niega a guardar si la entidad va
     * montada). Sin ese campo {@code EntityType.create} no sabe qué crear: escribe en el log
     * {@code Skipping Entity with id} (con el id <b>vacío</b>, ese warning es la firma de este bug) y devuelve
     * vacío. Por eso los minions nunca volvían.
     */
    private CompoundTag saveEntityData(Entity entity) {
        CompoundTag data = entity.saveWithoutId(new CompoundTag());
        data.putString("id", EntityType.getKey(entity.getType()).toString());
        return data;
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
        DevilRpg.LOGGER.info("[Minion] {} entrada(s) guardadas para {} al entrar", stored.size(), player.getName().getString());
        if (stored.isEmpty()) {
            return;
        }
        int restored = 0;
        int cleaned = 0;
        // Al revés: así se pueden quitar las entradas recuperadas sin descolocar el índice.
        for (int i = stored.size() - 1; i >= 0; i--) {
            CompoundTag entry = stored.getCompound(i);
            if (!entry.hasUUID("Id")) {
                DevilRpg.LOGGER.warn("[Minion] entrada guardada sin UUID: la tiro");
                stored.remove(i);
                continue;
            }
            UUID id = entry.getUUID("Id");
            BlockPos pos = BlockPos.of(entry.getLong("Pos"));
            String dimension = entry.getString("Dimension");
            String typeId = entry.getCompound("Data").getString("id");
            ServerLevel storedLevel = levelOf(server, dimension);
            ITamableEntity minion = storedLevel != null ? resolveMinion(storedLevel, id, pos) : null;

            if (minion != null) {
                // Sigue existiendo (cargado, o en un trozo que acabamos de cargar): se adopta, no se recrea.
                entry.remove("Misses");
                bringToPlayer(minion, player, playerLevel);
                stored.remove(i);
                restored++;
                DevilRpg.LOGGER.info("[Minion] devuelto ADOPTADO {} ({}), dim {} pos {}",
                        id, typeId.isEmpty() ? "sin tipo en la copia" : typeId, dimension, pos);
                continue;
            }

            // No aparece. ¿La copia se hizo al sacarlo del mundo (salida) o es una foto periódica?
            // Las copias antiguas (sin el campo) se tratan como "de salida", que es lo que eran.
            boolean stowed = !entry.contains("Stowed") || entry.getBoolean("Stowed");
            if (stowed) {
                minion = recreateMinion(entry, playerLevel, player);
                if (minion == null) {
                    // NO se borra: se deja la copia para el próximo intento. Antes se borraba SIEMPRE al final,
                    // así que un fallo de recreación destruía el minion para siempre.
                    DevilRpg.LOGGER.warn("[Minion] NO pude recuperar {} (tipo '{}', dim {}, pos {}); dejo la copia guardada",
                            id, typeId.isEmpty() ? "desconocido" : typeId, dimension, pos);
                    continue;
                }
                bringToPlayer(minion, player, playerLevel);
                stored.remove(i);
                restored++;
                DevilRpg.LOGGER.info("[Minion] devuelto RECREADO {} ({}), dim {} pos {}",
                        id, typeId.isEmpty() ? "sin tipo en la copia" : typeId, dimension, pos);
                continue;
            }

            // Foto periódica de un minion que ya no está: se murió mientras no mirabas. No se resucita: se
            // cuenta el fallo y, al segundo, se quita de las listas (así no se acumulan minions muertos).
            int misses = entry.getInt("Misses") + 1;
            if (misses >= STORED_MISSES_BEFORE_DROP) {
                boolean forgotten = forgetMinionId(player, id);
                stored.remove(i);
                cleaned++;
                DevilRpg.LOGGER.info("[Minion] {} ya no existe ({} fallos): {} de tus listas",
                        id, misses, forgotten ? "lo quito" : "no estaba en ninguna");
            } else {
                entry.putInt("Misses", misses);
                DevilRpg.LOGGER.info("[Minion] no encuentro a {} ({}), aviso {}/{}: si vuelve a faltar al entrar lo quito de las listas",
                        id, typeId.isEmpty() ? "sin tipo" : typeId, misses, STORED_MISSES_BEFORE_DROP);
            }
        }
        DevilRpg.LOGGER.info("[Minion] {} devuelto(s), {} limpiado(s) y {} copia(s) pendientes para {}",
                restored, cleaned, stored.size(), player.getName().getString());
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
        int brought = 0;
        for (UUID id : allMinionIds()) {
            Entity entity = findLoadedAnywhere(server, id);
            if (entity instanceof ITamableEntity minion) {
                bringToPlayer(minion, player, playerLevel);
                brought++;
                DevilRpg.LOGGER.info("[Minion] traigo junto a {} el {} {} que seguia vivo en {}",
                        player.getName().getString(), EntityType.getKey(entity.getType()), id,
                        entity.level().dimension().location());
            }
        }
        if (brought > 0) {
            DevilRpg.LOGGER.info("[Minion] {} minion(es) vivos traidos junto a {}", brought, player.getName().getString());
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
            // Se cargan también las 8 casillas de alrededor: la copia puede ser de hasta 10 s antes, así que el
            // minion pudo andar una casilla mientras su trozo seguía cargado. getEntity(UUID) busca entre TODAS
            // las entidades cargadas del nivel, así que con esas 9 casillas basta.
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.getChunk(cx + dx, cz + dz);
                }
            }
            entity = level.getEntity(id);
        }
        return entity instanceof ITamableEntity minion ? minion : null;
    }

    /** Quita un UUID de las listas de minions (lobo, oso, wisp). Se usa cuando se confirma que ya no existe. */
    private boolean forgetMinionId(Player player, UUID id) {
        boolean removed = false;
        ConcurrentLinkedQueue<UUID> wolves = getSoulWolfMinions();
        if (wolves.remove(id)) {
            setSoulWolfMinions(wolves, player);
            removed = true;
        }
        ConcurrentLinkedQueue<UUID> bears = getSoulBearMinions();
        if (bears.remove(id)) {
            setSoulBearMinions(bears, player);
            removed = true;
        }
        ConcurrentLinkedQueue<UUID> wisps = getWispMinions();
        if (wisps.remove(id)) {
            setWispMinions(wisps, player);
            removed = true;
        }
        return removed;
    }

    /** Recrea un minion desde su NBT guardado, junto al jugador (conserva su UUID: las listas siguen valiendo). */
    private ITamableEntity recreateMinion(CompoundTag entry, ServerLevel level, Player player) {
        CompoundTag data = entry.getCompound("Data");
        // Copias escritas por la version con el bug: les falta el campo "id". Se deduce de las listas del
        // jugador (el UUID sigue estando en la lista de lobos, osos o wisps), que es información fiable.
        if (!data.contains("id", Tag.TAG_STRING) || data.getString("id").isEmpty()) {
            String inferred = inferEntityId(entry.hasUUID("Id") ? entry.getUUID("Id") : null, player);
            if (inferred == null) {
                DevilRpg.LOGGER.warn("[Minion] la copia de {} no tiene campo 'id' y no puedo deducir el tipo",
                        entry.hasUUID("Id") ? entry.getUUID("Id") : "(sin uuid)");
                return null;
            }
            data.putString("id", inferred);
            entry.put("Data", data);
            DevilRpg.LOGGER.info("[Minion] copia antigua sin 'id': deduzco que {} es un {}", entry.getUUID("Id"), inferred);
        }
        return EntityType.create(data, level)
                .map(entity -> {
                    if (!(entity instanceof ITamableEntity minion)) {
                        return null;
                    }
                    // El NBT del lobo y del wisp guarda el dueño como TEXTO VACÍO (putString("Owner", "") en su
                    // addAdditionalSaveData, que machaca el UUID de TamableAnimal). Sin volver a asignarlo,
                    // getOwnerUUID() es null -> isTame() false -> addToAiStep lo mata en el primer tick.
                    minion.tame(player);
                    entity.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
                    level.addFreshEntity(entity);
                    return minion;
                })
                .orElse(null);
    }

    /**
     * Deduce el tipo de entidad de una copia guardada antigua (sin campo {@code id}) a partir de la lista de
     * minions en la que sigue su UUID. Para los wisps (que comparten lista) se elige la clase con más puntos
     * del jugador; es una deducción, no un dato, así que se avisa en el log.
     */
    private String inferEntityId(UUID id, Player player) {
        if (id == null) {
            return null;
        }
        if (getSoulWolfMinions().contains(id)) {
            return EntityType.getKey(ModEntities.SOUL_WOLF.get()).toString();
        }
        if (getSoulBearMinions().contains(id)) {
            return EntityType.getKey(ModEntities.SOUL_BEAR.get()).toString();
        }
        if (getWispMinions().contains(id)) {
            return wispTypeWithMostPoints(player);
        }
        return null;
    }

    /** De los tres wisps invocables, el que tenga más puntos en el árbol del jugador (empate -> salud). */
    private String wispTypeWithMostPoints(Player player) {
        int health = 0;
        int archer = 0;
        int ranger = 0;
        PlayerSkillCapabilityInterface skill =
                IGenericCapability.getUnwrappedPlayerCapability(player, PlayerSkillCapability.INSTANCE);
        if (skill != null && skill.getSkillsPoints() != null) {
            health = skill.getSkillsPoints().getOrDefault(SkillEnum.SUMMON_WISP_HEALTH, 0);
            archer = skill.getSkillsPoints().getOrDefault(SkillEnum.SUMMON_WISP_ARCHER, 0);
            ranger = skill.getSkillsPoints().getOrDefault(SkillEnum.SUMMON_WISP_RANGER, 0);
        }
        if (archer > health && archer >= ranger) {
            return EntityType.getKey(ModEntities.WISP_ARCHER.get()).toString();
        }
        if (ranger > health && ranger > archer) {
            return EntityType.getKey(ModEntities.WISP_RANGER.get()).toString();
        }
        return EntityType.getKey(ModEntities.WISP_HEALTH.get()).toString();
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
