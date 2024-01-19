package com.chipoodle.devilrpg.capability.skill;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.ResourceType;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.SkillElement;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.model.ClientSkillBuilderFromJson;
import com.chipoodle.devilrpg.skillsystem.AbstractSkillContainer;
import com.chipoodle.devilrpg.util.PowerEnum;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.List;
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

    @SuppressWarnings("unchecked")
    HashMap<SkillEnum, ResourceLocation> getImagesOfSkills();

    void setImagesOfSkills(HashMap<SkillEnum, ResourceLocation> names, Player player);

    void triggerAction(Player playerIn, PowerEnum triggeredPower);

    void triggerPassive(Player sender, CompoundTag triggeredPassive);

    AbstractSkillContainer getLoadedSkillExecutor(SkillEnum skillEnum);

    AbstractSkillContainer createSkillExecutor(SkillEnum skillEnum);

    ClientSkillBuilderFromJson getClientSkillBuilder();

    List<SkillEnum> getPassivesFromActiveSkill(SkillEnum skillEnum);

    SkillElement getSkillElementByEnum(SkillEnum skillEnum);
}
