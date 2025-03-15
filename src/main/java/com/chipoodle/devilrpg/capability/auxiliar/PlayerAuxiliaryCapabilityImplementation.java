package com.chipoodle.devilrpg.capability.auxiliar;

import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerAuxiliarClientServerHandler;
import com.chipoodle.devilrpg.util.TargetUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

public class PlayerAuxiliaryCapabilityImplementation implements PlayerAuxiliaryCapabilityInterface {

    protected boolean werewolfAttack = false;
    protected boolean werewolfTransformation = false;
    protected boolean swingingMainHand = false;

    protected Vec3 spawnPoint = null;

    @Override
    public boolean isWerewolfAttack() {
        return werewolfAttack;
    }

    @Override
    public void setWerewolfAttack(boolean active, Player player) {
        werewolfAttack = active;
        //DevilRpg.LOGGER.info("------Client sending to server attaking werewolf: {} isClientSide {}, main hand? {}",active, player.level.isClientSide,swingingMainHand);
        if (!player.level.isClientSide) {
            //player.sendMessage(new StringTextComponent("Sending to client attaking werewolf: " + active),player.getUUID());
            sendAuxiliaryChangesToClient((ServerPlayer) player);
        } else {
            //player.sendMessage(new StringTextComponent("Sending to server attaking werewolf: " + active),player.getUUID());
            sendAuxiliaryChangesToServer();

        }
    }

    @Override
    public boolean isWerewolfTransformation() {
        return werewolfTransformation;
    }

    @Override
    public void setWerewolfTransformation(boolean active, Player player) {
        werewolfTransformation = active;
        if (!player.level.isClientSide) sendAuxiliaryChangesToClient((ServerPlayer) player);
        else sendAuxiliaryChangesToServer();
    }

    @Override
    public boolean isSwingingMainHand() {
        return swingingMainHand;
    }

    @Override
    public void setSwingingMainHand(boolean active, Player player) {
        swingingMainHand = active;
        if (!player.level.isClientSide) sendAuxiliaryChangesToClient((ServerPlayer) player);
        else sendAuxiliaryChangesToServer();
    }

    @Override
    public InteractionHand swingHands(Player player) {
        InteractionHand interactionHand = isSwingingMainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        player.swing(interactionHand);
        setSwingingMainHand(!isSwingingMainHand(), player);
        return interactionHand;
    }

    @Override
    public Vec3 getSpawnPoint() {
        return spawnPoint;
    }

    @Override
    public void setSpawnPoint(Vec3 spawnPoint, Player player) {
        this.spawnPoint = spawnPoint;
        if (!player.level.isClientSide) sendAuxiliaryChangesToClient((ServerPlayer) player);
        else sendAuxiliaryChangesToServer();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putBoolean("werewolfAttack", werewolfAttack);
        nbt.putBoolean("werewolfTransformation", werewolfTransformation);
        //no es necesario persistir que mano está moviendose
        //nbt.putBoolean("swingingMainHand", swingingMainHand);
        nbt.putString("spawnPoint", spawnPoint.toString());
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        werewolfAttack = nbt.getBoolean("werewolfAttack");
        werewolfTransformation = nbt.getBoolean("werewolfTransformation");
        //no es necesario leer que mano está moviendose
        //swingingMainHand = nbt.getBoolean("swingingMainHand");
        // Verificar si existe el campo "spawnPoint" antes de deserializar
        if (nbt.contains("spawnPoint")) {
            spawnPoint = TargetUtils.stringToVec3(nbt.getString("spawnPoint"));
        }
    }

    private void sendAuxiliaryChangesToServer() {
        ModNetwork.CHANNEL.sendToServer(new PlayerAuxiliarClientServerHandler(serializeNBT()));
    }

    private void sendAuxiliaryChangesToClient(ServerPlayer pe) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> pe), new PlayerAuxiliarClientServerHandler(serializeNBT()));
    }
}
