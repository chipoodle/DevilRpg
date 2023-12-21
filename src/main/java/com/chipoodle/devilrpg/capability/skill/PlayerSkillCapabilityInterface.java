package com.chipoodle.devilrpg.capability.skill;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.ResourceType;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.model.ClientSkillBuilderFromJson;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.PowerEnum;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.UUID;

public interface PlayerSkillCapabilityInterface extends IGenericCapability {

    HashMap<PowerEnum, SkillEnum> getSkillsNameOfPowers();

    void setSkillsNameOfPowers(HashMap<PowerEnum, SkillEnum> names, Player player);

    HashMap<SkillEnum, Integer> getSkillsPoints();

    void setSkillsPoints(HashMap<SkillEnum, Integer> points, Player player);

    HashMap<SkillEnum, Integer> getMaxSkillsPoints();

    void setMaxSkillsPoints(HashMap<SkillEnum, Integer> points, Player player);

    HashMap<SkillEnum, Integer> getResourceCostPoints();

    void setResourceCostPoints(HashMap<SkillEnum, Integer> points, Player player);

    HashMap<SkillEnum, ResourceType> getResourceType();


    void setResourceType(HashMap<SkillEnum, ResourceType> points, Player player);

    HashMap<String, UUID> getAttributeModifiers();

    void setAttributeModifiers(HashMap<String, UUID> modifiers, Player player);

    SkillEnum getSkillFromByteArray(CompoundTag triggeredskill);

    CompoundTag setSkillToByteArray(SkillEnum skillEnum);

    void triggerAction(ServerPlayer playerIn, PowerEnum triggeredPower);

    void triggerPassive(ServerPlayer sender, CompoundTag triggeredPassive);

    ISkillContainer getLoadedSkillExecutor(SkillEnum skillEnum);

    ISkillContainer createSkillExecutor(SkillEnum skillEnum);

    ClientSkillBuilderFromJson getClientSkillBuilder();
}
