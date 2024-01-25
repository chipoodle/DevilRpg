package com.chipoodle.devilrpg.effects;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.init.ModEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public class MobEffectKnockbackResistance extends MobEffect {
    private static final UUID KNOCKBACK_RESISTANCE_UUID = UUID.fromString("AF8B6E3F-3328-4C0A-BB47-5BA2BB9DBEF3");
    private AttributeModifier attMod = null;

    public MobEffectKnockbackResistance() {
        super(MobEffectCategory.BENEFICIAL, 0xFF00FF); // Adjust the color as needed
    }

    @Override
    public void applyEffectTick(@NotNull LivingEntity entity, int amplifier) {
        // Calculate the knockback resistance based on the amplifier
        double knockbackResistance = 0.2 * amplifier;

        if(attMod == null) {
            attMod = new AttributeModifier(KNOCKBACK_RESISTANCE_UUID, "Knockback Resistance", knockbackResistance, AttributeModifier.Operation.ADDITION);
        }

        if(!Objects.requireNonNull(entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE)).hasModifier(attMod)) {
            // Apply the knockback resistance attribute modifier
            Objects.requireNonNull(entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE)).addTransientModifier(attMod);
            DevilRpg.LOGGER.debug(entity.getName().getString() + " has knockback resistance: " + knockbackResistance);
        }
    }

    @Override
    public void removeAttributeModifiers(LivingEntity entityLivingBaseIn, @NotNull AttributeMap attributeMapIn, int amplifier) {
        // Remove the knockback resistance attribute modifier when the effect is removed
        Objects.requireNonNull(entityLivingBaseIn.getAttribute(Attributes.KNOCKBACK_RESISTANCE)).removeModifier(KNOCKBACK_RESISTANCE_UUID);
        attMod = null;
        DevilRpg.LOGGER.debug(entityLivingBaseIn.getName().getString() + "'s knockback resistance effect has worn off.");
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // This method controls how often the applyEffectTick method is called
        // You can adjust it based on your needs
        return true;
    }

    public static MobEffectInstance createInstance(int duration, int amplifier) {
        // Create an instance of your custom MobEffect with the specified duration and amplifier
        return new MobEffectInstance(ModEffects.KNOCKBACK_RESISTANCE.get(), duration, amplifier);
    }
}