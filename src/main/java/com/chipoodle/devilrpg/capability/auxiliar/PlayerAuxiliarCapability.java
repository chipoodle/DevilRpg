package com.chipoodle.devilrpg.capability.auxiliar;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerAuxiliarClientServerHandler;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Hand;
import net.minecraftforge.fml.network.PacketDistributor;

public class PlayerAuxiliarCapability implements IBaseAuxiliarCapability {

	protected boolean werewolfAttack = false;
	protected boolean werewolfTransformation = false;
	protected boolean swingingMainHand = false;

	@Override
	public boolean isWerewolfAttack() {
		return werewolfAttack;
	}

	@Override
	public void setWerewolfAttack(boolean active, PlayerEntity player) {
		werewolfAttack = active;
		DevilRpg.LOGGER.info("------client sending to server attaking werewolf: " + active);
		if (!player.level.isClientSide) {
			//player.sendMessage(new StringTextComponent("Sending to client attaking werewolf: " + active),player.getUUID());
			sendAuxiliarChangesToClient((ServerPlayerEntity) player);
		} else {
			//player.sendMessage(new StringTextComponent("Sending to server attaking werewolf: " + active),player.getUUID());
			sendAuxiliarChangesToServer();
			
		}
	}

	@Override
	public boolean isWerewolfTransformation() {
		return werewolfTransformation;
	}

	@Override
	public void setWerewolfTransformation(boolean active, PlayerEntity player) {
		werewolfTransformation = active;
		if (!player.level.isClientSide)
			sendAuxiliarChangesToClient((ServerPlayerEntity) player);
		else
			sendAuxiliarChangesToServer();
	}
	@Override
	public boolean isSwingingMainHand() {
		return swingingMainHand;
	}
	
	@Override
	public void setSwingingMainHand(boolean active, PlayerEntity player) {
		swingingMainHand = active;
		if (!player.level.isClientSide)
			sendAuxiliarChangesToClient((ServerPlayerEntity) player);
		else
			sendAuxiliarChangesToServer();
	}

	@Override
	public CompoundNBT getNBTData() {
		CompoundNBT nbt = new CompoundNBT();
		nbt.putBoolean("werewolfAttack", werewolfAttack);
		nbt.putBoolean("werewolfTransformation", werewolfTransformation);
		nbt.putBoolean("swingingMainHand", swingingMainHand);
		return nbt;
	}

	@Override
	public void setNBTData(CompoundNBT compound) {
		werewolfAttack = compound.getBoolean("werewolfAttack");
		werewolfTransformation = compound.getBoolean("werewolfTransformation");
		swingingMainHand = compound.getBoolean("swingingMainHand");
	}

	private void sendAuxiliarChangesToServer() {
		ModNetwork.CHANNEL.sendToServer(new PlayerAuxiliarClientServerHandler(getNBTData()));
	}

	private void sendAuxiliarChangesToClient(ServerPlayerEntity pe) {
		ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> pe),
				new PlayerAuxiliarClientServerHandler(getNBTData()));
	}

	@Override
	public Hand swingHands(PlayerEntity player) {
		Hand hand = isSwingingMainHand() ? Hand.MAIN_HAND : Hand.OFF_HAND;
		player.swing(hand);
		setSwingingMainHand(!isSwingingMainHand(), player);
		return hand;
	}

}
