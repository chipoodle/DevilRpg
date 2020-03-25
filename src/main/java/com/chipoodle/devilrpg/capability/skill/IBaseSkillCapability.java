package com.chipoodle.devilrpg.capability.skill;

import net.minecraft.nbt.CompoundNBT;

public interface IBaseSkillCapability {
	public int getPaSoulWolf();
	public int getPaWispHealth();
	public int getPaWispSpeed();
	public int getPaFireBall();
	public int getPaSoulBear();
	public int getPaWereWolf();
	
	public void setPaSoulWolf(int paSoulWolf);
	public void setPaWispHealth(int paWispHealth);
	public void setPaWispSpeed(int paWispSpeed);
	public void setPaFireBall(int paFireBall);
	public void setPaSoulBear(int paSoulBear);
	public void setPaWereWolf(int paWereWolf);
	
	public int getMaxSoulWolf();
	public int getMaxWispHealth();
	public int getMaxWispSpeed();
	public int getMaxFireBall();
	public int getMaxSoulBear();
	public int getMaxWereWolf();
	
	public void setMaxSoulWolf(int maxPoints);
	public void setMaxWispHealth(int maxPoints);
	public void setMaxWispSpeed(int maxPoints);
	public void setMaxFireBall(int maxPoints);
	public void setMaxSoulBear(int maxPoints);
	public void setMaxWereWolf(int maxPoints);
	
	public String getPower1Name();
	public String getPower2Name();
	public String getPower3Name();
	public String getPower4Name();
	
	public void setPower1Name(String name);
	public void setPower2Name(String name);
	public void setPower3Name(String name);
	public void setPower4Name(String name);
	
	
	public CompoundNBT getNBTData();
	public void setNBTData(CompoundNBT nbt);
}
