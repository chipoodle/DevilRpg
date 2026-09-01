package com.chipoodle.devilrpg.entity;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.Map;


public interface IPassiveMinionUpdater<T extends ITamableEntity> {
	
	default void applyPassives(Map<Holder<Attribute>, AttributeModifier> attributes, T t) {
		attributes.forEach((key, value)->{
			AttributeInstance attribute = t.getAttribute(key);
			boolean hasModifier = attribute.hasModifier(value.id());
			if(hasModifier)
				attribute.removeModifier(value);			
			attribute.addTransientModifier(value);
			t.setHealth(t.getMaxHealth());
			/*if(attribute.getAttribute().equals(Attributes.MAX_HEALTH)) {
				t.setHealth((float) (t.getHealth()+attribute.getBaseValue()));
				setSaludMaxima(t.getHealth());
			}*/
			
		});
	}
}
