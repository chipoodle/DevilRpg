package com.chipoodle.devilrpg.capability.skill;

import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.model.ClientSkillBuilder;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.PowerEnum;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.HashMap;
import java.util.UUID;

public interface PlayerSkillCapabilityInterface extends INBTSerializable<CompoundTag> {

    HashMap<PowerEnum, SkillEnum> getSkillsNameOfPowers();

    void setSkillsNameOfPowers(HashMap<PowerEnum, SkillEnum> names, Player player);

    HashMap<SkillEnum, Integer> getSkillsPoints();

    void setSkillsPoints(HashMap<SkillEnum, Integer> points, Player player);

    HashMap<SkillEnum, Integer> getMaxSkillsPoints();

    void setMaxSkillsPoints(HashMap<SkillEnum, Integer> points, Player player);

    HashMap<SkillEnum, Integer> getManaCostPoints();

    void setManaCostPoints(HashMap<SkillEnum, Integer> points, Player player);

    HashMap<String, UUID> getAttributeModifiers();

    void setAttributeModifiers(HashMap<String, UUID> modifiers, Player player);

    SkillEnum getSkillFromByteArray(CompoundTag triggeredskill);

    CompoundTag setSkillToByteArray(SkillEnum skillEnum);

    void triggerAction(ServerPlayer sender, PowerEnum triggeredPower);

    void triggerPassive(ServerPlayer sender, CompoundTag triggeredPassive);

    ISkillContainer getLoadedSkill(SkillEnum skillEnum);

    ISkillContainer create(SkillEnum skillEnum);

    ClientSkillBuilder getClientSkillBuilder();
}
