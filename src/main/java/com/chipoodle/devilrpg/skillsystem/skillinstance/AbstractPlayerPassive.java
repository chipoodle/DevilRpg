package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public abstract class AbstractPlayerPassive {

    protected AttributeModifier createNewAttributeModifier(String attributeModifierUniqueName, Double value, AttributeModifier.Operation operation) {
        return new AttributeModifier(attributeModifierUniqueName, value, operation);
    }

    protected void removeCurrentModifierFromPlayer(PlayerEntity playerIn,
                                                   AttributeModifier attributeModifier,
                                                   Attribute attribute) {
        if (attributeModifier != null) {
            playerIn.getAttribute(attribute).removeModifier(attributeModifier.getId());
        }
    }

    protected void addCurrentModifierTransiently(PlayerEntity playerIn,
                                                 Attribute attribute,
                                                 AttributeModifier attributeModifier) {
        if (playerIn.getAttribute(attribute).getModifier(attributeModifier.getId()) != null)
            playerIn.getAttribute(attribute).removeModifier(attributeModifier.getId());
        playerIn.getAttribute(attribute).addTransientModifier(attributeModifier);
    }

    protected void addCurrentModifierPermanently(PlayerEntity playerIn,
                                                 Attribute attribute,
                                                 AttributeModifier attributeModifier) {
        if (playerIn.getAttribute(attribute).getModifier(attributeModifier.getId()) != null)
            playerIn.getAttribute(attribute).removeModifier(attributeModifier.getId());
        playerIn.getAttribute(attribute).addPermanentModifier(attributeModifier);
    }

    protected AttributeModifier findAttributeModifierForPlayerByName(PlayerEntity playerIn,
                                                                     Attribute attribute,
                                                                     String attributeModifierUniqueName) {
        ModifiableAttributeInstance modifiedAttributeInstance = playerIn.getAttribute(attribute);
        Set<AttributeModifier> modifiers = modifiedAttributeInstance.getModifiers();
        return modifiers.stream().filter(mod -> mod.getName().equals(attributeModifierUniqueName)).findAny()
                .orElse(null);
    }

    protected AttributeModifier getAttributeModifierForPlayer(PlayerEntity playerIn,
                                                              Attribute attribute,
                                                              UUID attributeUuid) {
        ModifiableAttributeInstance modifiedAttributeInstance = playerIn.getAttribute(attribute);
        return modifiedAttributeInstance.getModifier(attributeUuid);
    }

    protected void executePassiveChildren(PlayerSkillCapability parentCapability, SkillEnum skillEnum, World worldIn, PlayerEntity playerIn){
        List<SkillEnum> passivesFromActiveSkill = parentCapability.getPassivesFromActiveSkill(skillEnum);
        for (SkillEnum passiveEnum : passivesFromActiveSkill) {
            ISkillContainer loadedSkill = parentCapability.getLoadedSkill(passiveEnum);
            loadedSkill.execute(worldIn, playerIn, new HashMap<>());
        }
    }
}
