package com.chipoodle.devilrpg.capability.skill;

import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerManaClientServerHandler;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.fml.common.thread.SidedThreadGroups;

public class PlayerSkillCapability implements IBaseSkillCapability {

    protected int paSoulWolf;
    protected int paWispHealth;
    protected int paWispSpeed;
    protected int paFireBall;
    protected int paSoulBear;
    protected int paWereWolf;
    protected int maxSoulWolf = 20;
    protected int maxWispHealth = 20;
    protected int maxWispSpeed = 20;
    protected int maxFireBall = 20;
    protected int maxSoulBear = 20;
    protected int maxWereWolf = 20;
    protected String power1Name = "";
    protected String power2Name = "";
    protected String power3Name = "";
    protected String power4Name = "";

    @Override
    public int getPaSoulWolf() {
        return paSoulWolf;
    }

    @Override
    public int getPaWispHealth() {
        return paWispHealth;
    }

    @Override
    public int getPaWispSpeed() {
        return paWispSpeed;
    }

    @Override
    public int getPaFireBall() {
        return paFireBall;
    }

    @Override
    public int getPaSoulBear() {
        return paSoulBear;
    }

    @Override
    public int getPaWereWolf() {
        return paWereWolf;
    }

    @Override
    public int getMaxSoulWolf() {
        return maxSoulWolf;
    }

    @Override
    public int getMaxWispHealth() {
        return maxWispHealth;
    }

    @Override
    public int getMaxWispSpeed() {
        return maxWispSpeed;
    }

    @Override
    public int getMaxFireBall() {
        return maxFireBall;
    }

    @Override
    public int getMaxSoulBear() {
        return maxSoulBear;
    }

    @Override
    public int getMaxWereWolf() {

        return maxWereWolf;
    }

    @Override
    public String getPower1Name() {
        return power1Name;
    }

    @Override
    public String getPower2Name() {
        return power2Name;
    }

    @Override
    public String getPower3Name() {
        return power3Name;
    }

    @Override
    public String getPower4Name() {
        return power4Name;
    }

    @Override
    public void setPaSoulWolf(int paSoulWolf) {
        this.paSoulWolf = paSoulWolf;
        if (Thread.currentThread().getThreadGroup() == SidedThreadGroups.CLIENT) {
        sendSkillChangesToServer();
        }
    }

    @Override
    public void setPaWispHealth(int paWispHealth) {
        this.paWispHealth = paWispHealth;
        if (Thread.currentThread().getThreadGroup() == SidedThreadGroups.CLIENT) {
        sendSkillChangesToServer();
        }
    }

    @Override
    public void setPaWispSpeed(int paWispSpeed) {
        this.paWispSpeed = paWispSpeed;
        if (Thread.currentThread().getThreadGroup() == SidedThreadGroups.CLIENT) {
        sendSkillChangesToServer();
        }
    }

    @Override
    public void setPaFireBall(int paFireBall) {
        this.paFireBall = paFireBall;
        if (Thread.currentThread().getThreadGroup() == SidedThreadGroups.CLIENT) {
        sendSkillChangesToServer();
        }
    }

    @Override
    public void setPaSoulBear(int paSoulBear) {
        this.paSoulBear = paSoulBear;
        if (Thread.currentThread().getThreadGroup() == SidedThreadGroups.CLIENT) {
        sendSkillChangesToServer();
        }
    }

    @Override
    public void setPaWereWolf(int paWereWolf) {
        this.paWereWolf = paWereWolf;
        if (Thread.currentThread().getThreadGroup() == SidedThreadGroups.CLIENT) {
        sendSkillChangesToServer();
        }
    }

    @Override
    public void setMaxSoulWolf(int maxPoints) {
        this.maxSoulWolf = maxPoints;
        if (Thread.currentThread().getThreadGroup() == SidedThreadGroups.CLIENT) {
        sendSkillChangesToServer();
        }

    }

    @Override
    public void setMaxWispHealth(int maxPoints) {
        this.maxWispHealth = maxPoints;
        if (Thread.currentThread().getThreadGroup() == SidedThreadGroups.CLIENT) {
        sendSkillChangesToServer();
        }

    }

    @Override
    public void setMaxWispSpeed(int maxPoints) {
        this.maxWispSpeed = maxPoints;
        if (Thread.currentThread().getThreadGroup() == SidedThreadGroups.CLIENT) {
        sendSkillChangesToServer();
        }

    }

    @Override
    public void setMaxFireBall(int maxPoints) {
        this.maxFireBall = maxPoints;
        if (Thread.currentThread().getThreadGroup() == SidedThreadGroups.CLIENT) {
        sendSkillChangesToServer();
        }

    }

    @Override
    public void setMaxSoulBear(int maxPoints) {
        this.maxSoulBear = maxPoints;
        if (Thread.currentThread().getThreadGroup() == SidedThreadGroups.CLIENT) {
        sendSkillChangesToServer();
        }

    }

    @Override
    public void setMaxWereWolf(int maxPoints) {
        this.maxWereWolf = maxPoints;
        if (Thread.currentThread().getThreadGroup() == SidedThreadGroups.CLIENT) {
        sendSkillChangesToServer();
        }

    }

    @Override
    public void setPower1Name(String name) {
        this.power1Name = name;
        if (Thread.currentThread().getThreadGroup() == SidedThreadGroups.CLIENT) {
        sendSkillChangesToServer();
        }
    }

    @Override
    public void setPower2Name(String name) {
        this.power2Name = name;
        if (Thread.currentThread().getThreadGroup() == SidedThreadGroups.CLIENT) {
        sendSkillChangesToServer();
        }
    }

    @Override
    public void setPower3Name(String name) {
        this.power3Name = name;
        if (Thread.currentThread().getThreadGroup() == SidedThreadGroups.CLIENT) {
        sendSkillChangesToServer();
        }
    }

    @Override
    public void setPower4Name(String name) {
        this.power4Name = name;
        if (Thread.currentThread().getThreadGroup() == SidedThreadGroups.CLIENT) {
        sendSkillChangesToServer();
        }
    }

    @Override
    public CompoundNBT getNBTData() {
        CompoundNBT nbt = new CompoundNBT();
        nbt.putInt("paSoulWolf", paSoulWolf);
        nbt.putInt("paWispHealth", paWispHealth);
        nbt.putInt("paWispSpeed", paWispSpeed);
        nbt.putInt("paFireBall", paFireBall);
        nbt.putInt("paSoulBear", paSoulBear);
        nbt.putInt("paWereWolf", paWereWolf);
        nbt.putInt("maxSoulWolf", maxSoulWolf);
        nbt.putInt("maxWispHealth", maxWispHealth);
        nbt.putInt("maxWispSpeed", maxWispSpeed);
        nbt.putInt("maxFireBall", maxFireBall);
        nbt.putInt("maxSoulBear", maxSoulBear);
        nbt.putInt("maxWereWolf", maxWereWolf);
        nbt.putString("power1Name", power1Name);
        nbt.putString("power2Name", power2Name);
        nbt.putString("power3Name", power3Name);
        nbt.putString("power4Name", power4Name);
        return nbt;
    }

    @Override
    public void setNBTData(CompoundNBT compound) {
        paSoulWolf = compound.getInt("paSoulWolf");
        paWispHealth = compound.getInt("paWispHealth");
        paWispSpeed = compound.getInt("paWispSpeed");
        paFireBall = compound.getInt("paFireBall");
        paSoulBear = compound.getInt("paSoulBear");
        paWereWolf = compound.getInt("paWereWolf");
        maxSoulWolf = compound.getInt("maxSoulWolf");
        maxWispHealth = compound.getInt("maxWispHealth");
        maxWispSpeed = compound.getInt("maxWispSpeed");
        maxFireBall = compound.getInt("maxFireBall");
        maxSoulBear = compound.getInt("maxSoulBear");
        maxWereWolf = compound.getInt("maxWereWolf");
        power1Name = compound.getString("power1Name");
        power2Name = compound.getString("power2Name");
        power3Name = compound.getString("power3Name");
        power4Name = compound.getString("power4Name");
    }

    private void sendSkillChangesToServer() {
        ModNetwork.CHANNEL.sendToServer(new PlayerManaClientServerHandler(getNBTData()));
    }
}
