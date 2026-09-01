package com.chipoodle.devilrpg.skillsystem.skillinstance;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;

import java.util.HashMap;

public interface ICapabilityAttributeModifier {

    default String removeAttributeFromCapability(HashMap<String, String> attributeModifiers, Holder<Attribute> attribute) {
        return attributeModifiers.remove(attribute.value().getDescriptionId());
    }

    default void addAttributeToCapability(HashMap<String, String> attributeModifiers, Holder<Attribute> attribute, ResourceLocation attributeModifierId) {
        attributeModifiers.put(attribute.value().getDescriptionId(), attributeModifierId.toString());
    }
}