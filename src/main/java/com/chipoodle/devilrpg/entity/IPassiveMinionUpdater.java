package com.chipoodle.devilrpg.entity;

import java.util.Map;

import com.chipoodle.devilrpg.DevilRpg;

import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;

public interface IPassiveMinionUpdater<T extends ITameableEntity> {
	
	public default void applyPassives(Map<Attribute, AttributeModifier> attributes, T t) {
		attributes.forEach((key, value)->{
			ModifiableAttributeInstance attribute = t.getAttribute(key);
			DevilRpg.LOGGER.info("||---->IPassiveMinionUpdater applyPassives Minion: {} apply: {}, amount: {}",
					t.getDisplayName().getContents(),value.getName(),value.getAmount() );
			
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
