package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;


public abstract class AbstractPlayerPassive {

    protected AttributeModifier createNewAttributeModifier(String attributeModifierUniqueName, Double value, AttributeModifier.Operation operation) {
        return new AttributeModifier(attributeModifierUniqueName, value, operation);
    }

    protected void removeCurrentModifierFromPlayer(Player playerIn,
                                                   AttributeModifier attributeModifier,
                                                   Attribute attribute) {
        if (attributeModifier != null) {
            playerIn.getAttribute(attribute).removeModifier(attributeModifier.getId());
        }
    }

    protected void addCurrentModifierTransiently(Player playerIn,
                                                 Attribute attribute,
                                                 AttributeModifier attributeModifier) {
        if (playerIn.getAttribute(attribute).getModifier(attributeModifier.getId()) != null)
            playerIn.getAttribute(attribute).removeModifier(attributeModifier.getId());
        playerIn.getAttribute(attribute).addTransientModifier(attributeModifier);
    }

    protected void addCurrentModifierPermanently(Player playerIn,
                                                 Attribute attribute,
                                                 AttributeModifier attributeModifier) {
        if (playerIn.getAttribute(attribute).getModifier(attributeModifier.getId()) != null)
            playerIn.getAttribute(attribute).removeModifier(attributeModifier.getId());
        playerIn.getAttribute(attribute).addPermanentModifier(attributeModifier);
    }

    protected AttributeModifier findAttributeModifierForPlayerByName(Player playerIn,
                                                                     Attribute attribute,
                                                                     String attributeModifierUniqueName) {
        AttributeInstance modifiedAttributeInstance = playerIn.getAttribute(attribute);
        Set<AttributeModifier> modifiers = modifiedAttributeInstance.getModifiers();
        return modifiers.stream().filter(mod -> mod.getName().equals(attributeModifierUniqueName)).findAny()
                .orElse(null);
    }

    protected AttributeModifier getAttributeModifierForPlayer(Player playerIn,
                                                              Attribute attribute,
                                                              UUID attributeUuid) {
        AttributeInstance modifiedAttributeInstance = playerIn.getAttribute(attribute);
        return modifiedAttributeInstance.getModifier(attributeUuid);
    }

    protected void executePassiveChildren(PlayerSkillCapabilityImplementation parentCapability, SkillEnum skillEnum, Level level, Player playerIn) {
        List<SkillEnum> passivesFromActiveSkill = parentCapability.getPassivesFromActiveSkill(skillEnum);
        for (SkillEnum passiveEnum : passivesFromActiveSkill) {
            ISkillContainer loadedSkill = parentCapability.getLoadedSkill(passiveEnum);
            loadedSkill.execute(level, playerIn, new HashMap<>());
        }
    }
}
