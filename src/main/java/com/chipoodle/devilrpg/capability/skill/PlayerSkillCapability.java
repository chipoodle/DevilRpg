package com.chipoodle.devilrpg.capability.skill;

import static com.chipoodle.devilrpg.DevilRpg.LOGGER;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.mana.IBaseManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityProvider;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerManaClientServerHandler;
import com.chipoodle.devilrpg.network.handler.PlayerSkillClientServerHandler;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.BytesUtil;
import com.chipoodle.devilrpg.util.PowerEnum;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.network.PacketDistributor;

public class PlayerSkillCapability implements IBaseSkillCapability {
	public final static String POWERS_KEY = "Powers";
	public final static String SKILLS_KEY = "Skills";
	public final static String MAX_SKILLS_KEY = "MaxSkills";
	
	private CompoundNBT nbt = new CompoundNBT();
	private ServerSkillTranslator serverSkillTranslator;

	public PlayerSkillCapability() {
		if (nbt.isEmpty()) {
			HashMap<PowerEnum, SkillEnum> powers = new HashMap<>();
			HashMap<SkillEnum, Integer> skills = new HashMap<>();
			HashMap<SkillEnum, Integer> maxSkills = new HashMap<>();

			for (PowerEnum p : Arrays.asList(PowerEnum.values())) {
				powers.put(p, null);
			}
			for (SkillEnum s : Arrays.asList(SkillEnum.values())) {
				skills.put(s, 0);
				maxSkills.put(s, 20);
			}

			try {
				nbt.putByteArray(POWERS_KEY, BytesUtil.toByteArray(powers));
				nbt.putByteArray(SKILLS_KEY, BytesUtil.toByteArray(skills));
				nbt.putByteArray(MAX_SKILLS_KEY, BytesUtil.toByteArray(maxSkills));
			} catch (IOException e) {
				DevilRpg.LOGGER.error("Error en constructor PlayerSkillCapability", e);
			}
		}
		serverSkillTranslator = new ServerSkillTranslator(this);
	}

	@SuppressWarnings("unchecked")
	@Override
	public HashMap<PowerEnum, SkillEnum> getSkillsNameOfPowers() {
		try {
			return (HashMap<PowerEnum, SkillEnum>) BytesUtil.toObject(nbt.getByteArray(POWERS_KEY));
		} catch (ClassNotFoundException | IOException e) {
			DevilRpg.LOGGER.error("Error en getSkillsNameOfPowers", e);
			return null;
		}
	}

	@Override
	public void setSkillsNameOfPowers(HashMap<PowerEnum, SkillEnum> names) {
		try {
			nbt.putByteArray(POWERS_KEY, BytesUtil.toByteArray(names));
			 //DevilRpg.LOGGER.info("--->setSkillsNameOfPowers "+ nbt+" is Client?: " +
			 //Minecraft.getInstance().world.isRemote);
			sendSkillChangesToServer();
		} catch (IOException e) {
			DevilRpg.LOGGER.error("Error en setSkillsNameOfPowers", e);
		}

	}

	@SuppressWarnings("unchecked")
	@Override
	public HashMap<SkillEnum, Integer> getSkillsPoints() {
		try {
			return (HashMap<SkillEnum, Integer>) BytesUtil.toObject(nbt.getByteArray(SKILLS_KEY));
		} catch (ClassNotFoundException | IOException e) {
			DevilRpg.LOGGER.error("Error en getSkillsNameOfPowers", e);
			return null;
		}
	}

	@Override
	public void setSkillsPoints(HashMap<SkillEnum, Integer> points) {
		try {
			nbt.putByteArray(SKILLS_KEY, BytesUtil.toByteArray(points));
			 //DevilRpg.LOGGER.info("--->setSkillsPoints "+ nbt+" is Client?: " +
			 //Minecraft.getInstance().world.isRemote);
			sendSkillChangesToServer();
		} catch (IOException e) {
			DevilRpg.LOGGER.error("Error en setSkillsNameOfPowers", e);
		}

	}

	@SuppressWarnings("unchecked")
	@Override
	public HashMap<SkillEnum, Integer> getMaxSkillsPoints() {
		try {
			return (HashMap<SkillEnum, Integer>) BytesUtil.toObject(nbt.getByteArray(MAX_SKILLS_KEY));
		} catch (ClassNotFoundException | IOException e) {
			DevilRpg.LOGGER.error("Error en getMaxSkillsNameOfPowers", e);
			return null;
		}
	}

	@Override
	public void setMaxSkillsPoints(HashMap<SkillEnum, Integer> points) {
		try {
			nbt.putByteArray(MAX_SKILLS_KEY, BytesUtil.toByteArray(points));
			 //DevilRpg.LOGGER.info("--->setSkillsPoints "+ nbt+" is Client?: " +
			 //Minecraft.getInstance().world.isRemote);
			sendSkillChangesToServer();
		} catch (IOException e) {
			DevilRpg.LOGGER.error("Error en setMaxSkillsNameOfPowers", e);
		}

	}

	@Override
	public void triggerAction(ServerPlayerEntity playerIn, PowerEnum triggeredPower) {
        LOGGER.info("----------------->Trigger Action. Is Client? "+playerIn.world.isRemote);
        if (!playerIn.world.isRemote) {
            if (serverSkillTranslator.getSkillLevelFromUserCapability(playerIn, triggeredPower) != 0) {
            	ISkillContainer poder = serverSkillTranslator.getSkill(triggeredPower);
                if (consumeMana(playerIn, poder)) {
                    poder.execute(playerIn.world, playerIn);
                }
                else {
                	String message = "Not enought mana.";
                	playerIn.sendMessage(new StringTextComponent(message));
                }
            }
        }
    }
	
	private boolean consumeMana(ServerPlayerEntity playerIn, ISkillContainer poder) {
        float consumedMana = serverSkillTranslator.getManaCost(poder.getSkillEnum(), playerIn);
        LazyOptional<IBaseManaCapability> mana = playerIn.getCapability(PlayerManaCapabilityProvider.MANA_CAP, null);
        if (mana.map(x -> x.getMana()).orElse(null) - consumedMana >= 0) {
            mana.ifPresent(m -> m.setMana(mana.map(x -> x.getMana()).orElse(null) - consumedMana));
            LOGGER.info("=====Sending to player mana consumed " + mana.map(x -> x.getMana()).orElse(null) + " Cost: " + consumedMana + " player: " + playerIn.getName());
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> playerIn), new PlayerManaClientServerHandler(mana.map(x -> x.getNBTData()).orElse(null)));
            return true;
        }
        return false;
    }
	
	@Override
	public CompoundNBT getNBTData() {
		return nbt;
	}

	@Override
	public void setNBTData(CompoundNBT nbt) {
		this.nbt = nbt;
	}

	private void sendSkillChangesToServer() {
		ModNetwork.CHANNEL.sendToServer(new PlayerSkillClientServerHandler(getNBTData()));
	}

}
