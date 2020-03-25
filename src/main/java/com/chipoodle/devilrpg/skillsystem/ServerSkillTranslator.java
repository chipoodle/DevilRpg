package com.chipoodle.devilrpg.skillsystem;

import com.chipoodle.devilrpg.capability.skill.IBaseSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityProvider;
import com.chipoodle.devilrpg.util.ConstantesPower;
import com.chipoodle.devilrpg.util.ConstantesSkillName;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;

public class ServerSkillTranslator {

    public ServerSkillTranslator() {

    }

    public IPowerContainer getSkill(World worldIn, PlayerEntity playerIn, ConstantesPower triggeredPower) {
        return getCurrentPowerFromUserCapability(playerIn, triggeredPower);
    }

    public float getManaCost(IPowerContainer power, PlayerEntity playerIn) {
        if (power instanceof PowerSummonSoulWolf) {
            return 20f;
        }
        /*if(power instanceof PowerFireball)
			return 50f;
		if(power instanceof PowerSummonWisp)
			return 20f;
		if(power instanceof PowerSummonSoulBear)
			return 40f;
		if(power instanceof PowerWereWolf)
			return 35f;*/
        return 50f;
    }

    public IPowerContainer getCurrentPowerFromUserCapability(PlayerEntity playerIn, ConstantesPower power) {
        LazyOptional<IBaseSkillCapability> skill = playerIn.getCapability(PlayerSkillCapabilityProvider.SKILL_CAP, null);

        if (power.equals(ConstantesPower.POWER1)) {
            return getPowerByName(skill.map(x -> x.getPower1Name()).orElse(""));
        }
        if (power.equals(ConstantesPower.POWER2)) {
            return getPowerByName(skill.map(x -> x.getPower2Name()).orElse(""));
        }
        if (power.equals(ConstantesPower.POWER3)) {
            return getPowerByName(skill.map(x -> x.getPower3Name()).orElse(""));
        }
        if (power.equals(ConstantesPower.POWER4)) {
            return getPowerByName(skill.map(x -> x.getPower4Name()).orElse(""));
        }
        throw new IndexOutOfBoundsException("No existe el poder con ese nombre o no está inicializado");
    }

    private IPowerContainer getPowerByName(String name) {
        if (name.equals(ConstantesSkillName.SUMMON_SOUL_WOLF.getName())) {
            return PowerSummonSoulWolf.getInstance();
        }
        /*if(name.equals("SummonSoulBear"))
			return PowerSummonSoulBear.getInstance();
		if(name.equals("SummonWispHealth"))
			return PowerSummonWisp.getInstance(EntityWispHealth.class);
		if(name.equals("SummonWispSpeed"))
			return PowerSummonWisp.getInstance(EntityWispSpeed.class);
		if(name.equals("Fireball"))
			return new PowerFireball();
		if(name.equals("TransformWereWolf"))
			return PowerWereWolf.getInstance();*/

        return null;

    }

    public int getSkillLevelFromUserCapability(PlayerEntity playerIn, IPowerContainer power) {

        LazyOptional<IBaseSkillCapability> skill = playerIn.getCapability(PlayerSkillCapabilityProvider.SKILL_CAP, null);

        if (power instanceof PowerSummonSoulWolf) {
            return skill.map((IBaseSkillCapability x) -> x.getPaSoulWolf()).orElse(0);
        }
        /*if(power instanceof PowerSummonSoulBear)
				return playerCapability.getPaSoulBear();
			if(power instanceof PowerFireball)
				return playerCapability.getPaFireBall();
			if(power instanceof PowerSummonWisp){
				if(((PowerSummonWisp)power).getWispType().getSimpleName().equals(EntityWispHealth.class.getSimpleName())){
					return playerCapability.getPaWispHealth();
				}
				if(((PowerSummonWisp)power).getWispType().getSimpleName().equals(EntityWispSpeed.class.getSimpleName())){
					return playerCapability.getPaWispSpeed();
				}
			}
			if(power instanceof PowerWereWolf)
				return playerCapability.getPaWereWolf();*/

        return 0;
    }
}
