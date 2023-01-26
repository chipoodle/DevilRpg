package com.chipoodle.devilrpg.skillsystem.skillinstance;

import net.minecraft.entity.ai.attributes.Attribute;

import java.util.HashMap;
import java.util.UUID;

public interface ICapabilityAttributeModifier {

    default UUID removeAttributeFromCapability(HashMap<String, UUID> attributeModifiers, Attribute attribute) {
        return attributeModifiers.remove(attribute.getDescriptionId());
    }

    default void addAttributeToCapability(HashMap<String, UUID> attributeModifiers, Attribute attribute, UUID attributeModifierId) {
        attributeModifiers.put(attribute.getDescriptionId(), attributeModifierId);
    }
}
