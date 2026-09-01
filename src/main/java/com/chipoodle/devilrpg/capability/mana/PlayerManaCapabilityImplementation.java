package com.chipoodle.devilrpg.capability.mana;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.neoforged.neoforge.network.PacketDistributor;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.payload.PlayerManaPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;


public class PlayerManaCapabilityImplementation implements PlayerManaCapabilityInterface {

    private float mana = 0f;
    private float maxMana = 30f;
    private float regeneration = 0.2f;

    @Override
    public float getMana() {
        return mana;
    }

    @Override
    public void setMana(float mana, Player player) {
        this.mana = mana;
        if (!player.level().isClientSide)
            sendManaChangesToClient((ServerPlayer) player);
        else
            sendManaChangesToServer();
    }

    @Override
    public float getMaxMana() {
        return maxMana;
    }

    @Override
    public void setMaxMana(float maxMana, Player player) {
        this.maxMana = maxMana;
        if (!player.level().isClientSide)
            sendManaChangesToClient((ServerPlayer) player);
        else
            sendManaChangesToServer();
    }

    @Override
    public void addMana(float manaToAdd, Player player) {
        // Sumar el mana actual con la cantidad a agregar
        this.mana += manaToAdd;

        // Asegurarse de que el mana no exceda el máximo permitido
        if (this.mana > this.maxMana) {
            this.mana = this.maxMana;
        }

        // Asegurarse de que el mana no sea menor a 0
        if (this.mana < 0) {
            this.mana = 0;
        }

        // Sincronizar los cambios con el cliente o el servidor según el lado
        if (!player.level().isClientSide) {
            sendManaChangesToClient((ServerPlayer) player);
        } else {
            sendManaChangesToServer();
        }
    }

    @Override
    public float getRegeneration() {
        return regeneration;
    }

    @Override
    public void setRegeneration(float regeneration, Player player) {
        this.regeneration = regeneration;
        if (!player.level().isClientSide)
            sendManaChangesToClient((ServerPlayer) player);
        else
            sendManaChangesToServer();
    }

    @Override
    public void onPlayerTickEventRegeneration(Player player, final float degeneration) {
            mana += regeneration-degeneration;
            mana = Math.max(Math.min(mana, maxMana),0.0f);
            setMana(mana, player);

    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag nbt = new CompoundTag();
        nbt.putFloat("mana", mana);
        nbt.putFloat("maxMana", maxMana);
        nbt.putFloat("regeneration", regeneration);
        return nbt;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag compound) {
        mana = compound.getFloat("mana");
        maxMana = compound.getFloat("maxMana");
        regeneration = compound.getFloat("regeneration");
    }

    private void sendManaChangesToServer() {
        PacketDistributor.sendToServer(new PlayerManaPayload(serializeNBT(RegistryAccess.EMPTY)));
    }

    private void sendManaChangesToClient(ServerPlayer pe) {
        PacketDistributor.sendToPlayer(pe,
                new PlayerManaPayload(serializeNBT(RegistryAccess.EMPTY)));
    }
}
