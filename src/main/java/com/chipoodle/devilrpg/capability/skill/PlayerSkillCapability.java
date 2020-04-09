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
import com.chipoodle.devilrpg.skillsystem.SingletonSkillFactory;
import com.chipoodle.devilrpg.util.BytesUtil;
import com.chipoodle.devilrpg.util.PowerEnum;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.network.PacketDistributor;

public class PlayerSkillCapability implements IBaseSkillCapability {
	public final static String POWERS_KEY = "Powers";
	public final static String SKILLS_KEY = "Skills";
	public final static String MAX_SKILLS_KEY = "MaxSkills";
	public final static String MANA_COST_KEY = "ManaCost";
	
	private CompoundNBT nbt = new CompoundNBT();
	private HashMap<SkillEnum, Integer> fastManaCostContainer;
	private SingletonSkillFactory singletonSkillFactory;

	public PlayerSkillCapability() {
		if (nbt.isEmpty()) {
			HashMap<PowerEnum, SkillEnum> powers = new HashMap<>();
			HashMap<SkillEnum, Integer> skills = new HashMap<>();
			HashMap<SkillEnum, Integer> maxSkills = new HashMap<>();
			fastManaCostContainer = new HashMap<>();

			for (PowerEnum p : Arrays.asList(PowerEnum.values())) {
				powers.put(p, null);
			}
			for (SkillEnum s : Arrays.asList(SkillEnum.values())) {
				skills.put(s, 0);
				maxSkills.put(s, 20);
			}
			for (SkillEnum s : Arrays.asList(SkillEnum.values())) {
				fastManaCostContainer.put(s, 20);
			}

			try {
				nbt.putByteArray(POWERS_KEY, BytesUtil.toByteArray(powers));
				nbt.putByteArray(SKILLS_KEY, BytesUtil.toByteArray(skills));
				nbt.putByteArray(MAX_SKILLS_KEY, BytesUtil.toByteArray(maxSkills));
				nbt.putByteArray(MANA_COST_KEY, BytesUtil.toByteArray(fastManaCostContainer));
			} catch (IOException e) {
				DevilRpg.LOGGER.error("Error en constructor PlayerSkillCapability", e);
			}
		}
		singletonSkillFactory = new SingletonSkillFactory(this);
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
			DevilRpg.LOGGER.error("Error en getSkillsPoints", e);
			return null;
		}
	}

	@Override
	public void setSkillsPoints(HashMap<SkillEnum, Integer> points) {
		try {
			nbt.putByteArray(SKILLS_KEY, BytesUtil.toByteArray(points));
			sendSkillChangesToServer();
		} catch (IOException e) {
			DevilRpg.LOGGER.error("Error en setSkillsPoints", e);
		}

	}

	@SuppressWarnings("unchecked")
	@Override
	public HashMap<SkillEnum, Integer> getMaxSkillsPoints() {
		try {
			return (HashMap<SkillEnum, Integer>) BytesUtil.toObject(nbt.getByteArray(MAX_SKILLS_KEY));
		} catch (ClassNotFoundException | IOException e) {
			DevilRpg.LOGGER.error("Error en getMaxSkillsPoints", e);
			return null;
		}
	}

	@Override
	public void setMaxSkillsPoints(HashMap<SkillEnum, Integer> points) {
		try {
			nbt.putByteArray(MAX_SKILLS_KEY, BytesUtil.toByteArray(points));
			sendSkillChangesToServer();
		} catch (IOException e) {
			DevilRpg.LOGGER.error("Error en setMaxSkillsPoints", e);
		}

	}

	@SuppressWarnings("unchecked")
	@Override
	public HashMap<SkillEnum, Integer> getManaCostPoints() {
		try {
			return fastManaCostContainer = (HashMap<SkillEnum, Integer>) BytesUtil.toObject(nbt.getByteArray(MANA_COST_KEY));
		} catch (ClassNotFoundException | IOException e) {
			DevilRpg.LOGGER.error("Error en getManaCost", e);
			return null;
		}
	}

	@Override
	public void setManaCostPoints(HashMap<SkillEnum, Integer> points) {
		try {
			fastManaCostContainer = points;
			nbt.putByteArray(MANA_COST_KEY, BytesUtil.toByteArray(points));
			sendSkillChangesToServer();
		} catch (IOException e) {
			DevilRpg.LOGGER.error("Error en setManaCost", e);
		}

	}
	
	@Override
	public void triggerAction(ServerPlayerEntity playerIn, PowerEnum triggeredPower) {
        LOGGER.info("----------------->Trigger Action. Is Client? "+playerIn.world.isRemote);
        if (!playerIn.world.isRemote) {
            if (getSkillLevelFromAssociatedPower(playerIn, triggeredPower) != 0) {
            	ISkillContainer poder = getSkill(triggeredPower);
                if (consumeMana(playerIn, poder)) {
                    poder.execute(playerIn.world, playerIn);
                }
                else {
                	String message = "Not enough mana.";
                	playerIn.sendMessage(new StringTextComponent(message));
                }
            }
        }
    }
	
	private  ISkillContainer getSkill(PowerEnum triggeredPower) {
		SkillEnum skillEnum =  getSkillsNameOfPowers().get(triggeredPower);
		return singletonSkillFactory.create(skillEnum);
    }

    private int getSkillLevelFromAssociatedPower(PlayerEntity playerIn, PowerEnum triggeredPower) {
    	HashMap<PowerEnum, SkillEnum> powers = getSkillsNameOfPowers();
        HashMap<SkillEnum, Integer> skills = getSkillsPoints();
        return skills.get(powers.get(triggeredPower));
    }
    
    private boolean consumeMana(ServerPlayerEntity playerIn, ISkillContainer poder) {
        float consumedMana = fastManaCostContainer.get(poder.getSkillEnum());
        LazyOptional<IBaseManaCapability> mana = playerIn.getCapability(PlayerManaCapabilityProvider.MANA_CAP, null);
        if (mana.map(x -> x.getMana()).orElse(null) - consumedMana >= 0) {
            mana.ifPresent(m -> m.setMana(mana.map(x -> x.getMana()).orElse(null) - consumedMana));
            LOGGER.info("=====Sending to player mana consumed " + mana.map(x -> x.getMana()).orElse(null) + " Cost: " + consumedMana + " player: " + playerIn.getName());
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> playerIn), new PlayerManaClientServerHandler(mana.map(x -> x.getNBTData()).orElse(null)));
            return true;
        }
        return false;
    }

	public SingletonSkillFactory getSingletonSkillFactory() {
		return singletonSkillFactory;
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
