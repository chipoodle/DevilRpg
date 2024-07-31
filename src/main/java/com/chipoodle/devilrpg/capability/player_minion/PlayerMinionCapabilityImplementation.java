package com.chipoodle.devilrpg.capability.player_minion;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.ITamableEntity;
import com.chipoodle.devilrpg.entity.SoulBear;
import com.chipoodle.devilrpg.entity.SoulWisp;
import com.chipoodle.devilrpg.entity.SoulWolf;
import com.chipoodle.devilrpg.init.ModDamageTypes;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerMinionClientServerHandler;
import com.chipoodle.devilrpg.util.BytesUtil;
import com.chipoodle.devilrpg.util.TargetUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;


public class PlayerMinionCapabilityImplementation implements PlayerMinionCapabilityInterface {
    public final static String SOULWOLF_MINION_KEY = "Wolf_Minions";
    public final static String SOULBEAR_MINION_KEY = "Bear_Minions";
    public final static String WISP_MINIONS_KEY = "Wisp_Minions";
    public final static String SOULBEAR_INVENTORY_KEY = "Soulbear_Inventory";
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
            if (!player.level.isClientSide) {
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
            if (!player.level.isClientSide) {
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
            return null;
        }
    }

    @Override
    public void setWispMinions(ConcurrentLinkedQueue<UUID> wispMinions, Player player) {
        try {
            nbt.putByteArray(WISP_MINIONS_KEY, BytesUtil.toByteArray(wispMinions));
            if (!player.level.isClientSide) {
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
        if (!player.level.isClientSide) {
            sendSkillChangesToClient((ServerPlayer) player);
        } else {
            sendSkillChangesToServer();
        }
    }

    @Override
    public CompoundTag getSoulBearInventory() {
        CompoundTag tag =  nbt.getCompound(SOULBEAR_INVENTORY_KEY);
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
                    s.setItem(j, ItemStack.of(compoundtag));
                }
            }
            return s;
        } catch (Exception e) {
            DevilRpg.LOGGER.error("Error ", e);
            return null;
        }
    }

    @Override
    public ITamableEntity getTameableByUUID(UUID id, Level world) {
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
                    entity.level
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
                    entity.level
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
            ITamableEntity entity = getTameableByUUID(id, owner.level);
            removeWisp(owner, (SoulWisp) entity);
        });
        wisp.clear();
        setWispMinions(wisp, owner);
    }

    @Override
    public void removeAllSoulWolf(Player owner) {
        ConcurrentLinkedQueue<UUID> soulwolf = getSoulWolfMinions();
        soulwolf.forEach(id -> {
            ITamableEntity entity = getTameableByUUID(id, owner.level);
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
                    entity.level
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
            ITamableEntity entity = getTameableByUUID(uuid, owner.level);
            removeSoulBear(owner, (SoulBear) entity);
        }
        soulbear.clear();
        setSoulBearMinions(soulbear, owner);
    }

    @Override
    public CompoundTag serializeNBT() {
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.nbt = nbt;
    }

    private void sendSkillChangesToServer() {
        ModNetwork.CHANNEL.sendToServer(new PlayerMinionClientServerHandler(serializeNBT()));
    }

    private void sendSkillChangesToClient(ServerPlayer pe) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> pe),
                new PlayerMinionClientServerHandler(serializeNBT()));
    }

}
