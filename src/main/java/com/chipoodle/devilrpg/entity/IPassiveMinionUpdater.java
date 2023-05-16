package com.chipoodle.devilrpg.entity;

import java.util.Map;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;


public interface IPassiveMinionUpdater<T extends ITamableEntity> {
	
	default void applyPassives(Map<Attribute, AttributeModifier> attributes, T t) {
		attributes.forEach((key, value)->{
			AttributeInstance attribute = t.getAttribute(key);
			boolean hasModifier = attribute.hasModifier(value);
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
