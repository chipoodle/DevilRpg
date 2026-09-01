package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.skillsystem.AbstractSkillExecutor;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * Base class for passive skills that apply transient attribute modifiers to the player.
 * In 1.20.5+ attribute modifiers are identified by {@link ResourceLocation} instead of UUID.
 */
public abstract class AbstractPlayerPassiveAttributeExecutor extends AbstractSkillExecutor {

    public AbstractPlayerPassiveAttributeExecutor(PlayerSkillCapabilityInterface parentCapability) {
        super(parentCapability);
    }

    protected AttributeModifier createNewAttributeModifier(String attributeModifierUniqueName, Double value) {
        return new AttributeModifier(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, attributeModifierUniqueName), value, AttributeModifier.Operation.ADD_VALUE);
    }

    protected void removeCurrentModifierFromPlayer(Player playerIn,
                                                   AttributeModifier attributeModifier,
                                                   Holder<Attribute> attribute) {
        if (attributeModifier != null) {
            Objects.requireNonNull(playerIn.getAttribute(attribute)).removeModifier(attributeModifier.id());
        }
    }

    protected void addCurrentModifierTransiently(Player playerIn,
                                                 Holder<Attribute> attribute,
                                                 AttributeModifier attributeModifier) {
        AttributeInstance instance = Objects.requireNonNull(playerIn.getAttribute(attribute));
        if (instance.getModifier(attributeModifier.id()) != null)
            instance.removeModifier(attributeModifier.id());
        instance.addTransientModifier(attributeModifier);
    }

    protected void addCurrentModifierPermanently(Player playerIn,
                                                 Holder<Attribute> attribute,
                                                 AttributeModifier attributeModifier) {
        AttributeInstance instance = Objects.requireNonNull(playerIn.getAttribute(attribute));
        if (instance.getModifier(attributeModifier.id()) != null)
            instance.removeModifier(attributeModifier.id());
        instance.addPermanentModifier(attributeModifier);
    }

    protected AttributeModifier findAttributeModifierForPlayerByName(Player playerIn,
                                                                     Holder<Attribute> attribute,
                                                                     String attributeModifierUniqueName) {
        AttributeInstance modifiedAttributeInstance = playerIn.getAttribute(attribute);
        assert modifiedAttributeInstance != null;
        Set<AttributeModifier> modifiers = modifiedAttributeInstance.getModifiers();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, attributeModifierUniqueName);
        return modifiers.stream().filter(mod -> mod.id().equals(id)).findAny()
                .orElse(null);
    }

    protected AttributeModifier getAttributeModifierForPlayer(Player playerIn,
                                                              Holder<Attribute> attribute,
                                                              ResourceLocation attributeModifierId) {
        AttributeInstance modifiedAttributeInstance = playerIn.getAttribute(attribute);
        assert modifiedAttributeInstance != null;
        return modifiedAttributeInstance.getModifier(attributeModifierId);
    }

    /**
     * Mus be executed after the skill executor method.
     * @param skillEnum
     * @param level
     * @param playerIn
     */
    protected void executePassiveChildren(SkillEnum skillEnum, Level level, Player playerIn) {
        List<SkillEnum> passivesFromActiveSkill = parentCapability.getPassivesFromActiveSkill(skillEnum);
        for (SkillEnum passiveEnum : passivesFromActiveSkill) {
            AbstractSkillExecutor loadedSkill = parentCapability.getLoadedSkillExecutor(passiveEnum);
            loadedSkill.execute(level, playerIn, new HashMap<>());
        }
    }

    protected void executePassiveChildren(SkillEnum skillEnum, Level level, Player playerIn, HashMap<String,String> parameters) {
        List<SkillEnum> passivesFromActiveSkill = parentCapability.getPassivesFromActiveSkill(skillEnum);
        for (SkillEnum passiveEnum : passivesFromActiveSkill) {
            AbstractSkillExecutor loadedSkill = parentCapability.getLoadedSkillExecutor(passiveEnum);
            loadedSkill.execute(level, playerIn, parameters);
        }
    }
}
