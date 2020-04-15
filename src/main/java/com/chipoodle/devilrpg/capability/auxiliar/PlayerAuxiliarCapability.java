package com.chipoodle.devilrpg.capability.auxiliar;

import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerAuxiliarClientServerHandler;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
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
		if (!player.world.isRemote) {
			//player.sendMessage(new StringTextComponent("Sending to client attaking werewolf: " + active));
			sendAuxiliarChangesToClient((ServerPlayerEntity) player);
		} else {
			//player.sendMessage(new StringTextComponent("Sending to server attaking werewolf: " + active));
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
		if (!player.world.isRemote)
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
		if (!player.world.isRemote)
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

}
