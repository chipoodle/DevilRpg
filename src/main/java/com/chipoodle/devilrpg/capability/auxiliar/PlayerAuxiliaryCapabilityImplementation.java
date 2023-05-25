package com.chipoodle.devilrpg.capability.auxiliar;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerAuxiliarClientServerHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

public class PlayerAuxiliaryCapabilityImplementation implements PlayerAuxiliaryCapabilityInterface {

    protected boolean werewolfAttack = false;
    protected boolean werewolfTransformation = false;
    protected boolean swingingMainHand = false;

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
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putBoolean("werewolfAttack", werewolfAttack);
        nbt.putBoolean("werewolfTransformation", werewolfTransformation);
        nbt.putBoolean("swingingMainHand", swingingMainHand);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        werewolfAttack = nbt.getBoolean("werewolfAttack");
        werewolfTransformation = nbt.getBoolean("werewolfTransformation");
        swingingMainHand = nbt.getBoolean("swingingMainHand");
    }

    private void sendAuxiliaryChangesToServer() {
        ModNetwork.CHANNEL.sendToServer(new PlayerAuxiliarClientServerHandler(serializeNBT()));
    }

    private void sendAuxiliaryChangesToClient(ServerPlayer pe) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> pe), new PlayerAuxiliarClientServerHandler(serializeNBT()));
    }

    @Override
    public InteractionHand swingHands(Player player) {
        InteractionHand interactionHand = isSwingingMainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        player.swing(interactionHand);
        setSwingingMainHand(!isSwingingMainHand(), player);
        return interactionHand;
    }

}
