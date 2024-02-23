package com.chipoodle.devilrpg.effects;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.init.ModEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public class MobEffectSlowness extends MobEffect {
    private static final UUID SLOWNESS_UUID = UUID.fromString("AE8B1E3F-3328-4C0A-AB47-5BA2CB9DBEF3");
    private AttributeModifier attMod = null;

    public MobEffectSlowness() {
        super(MobEffectCategory.HARMFUL, 0xEEA0EE); // Adjust the color as needed
    }

    public static MobEffectInstance createInstance(int duration, int amplifier) {
        // Create an instance of your custom MobEffect with the specified duration and amplifier
        return new MobEffectInstance(ModEffects.SLOWNESS.get(), duration, amplifier);
    }

    @Override
    public void applyEffectTick(@NotNull LivingEntity entity, int amplifier) {
        // Calculate the knockback resistance based on the amplifier
        float percentage = amplifier * 0.1f;
        AttributeInstance movementSpeed = Objects.requireNonNull(entity.getAttribute(Attributes.MOVEMENT_SPEED));
        double movementSpeedValue = movementSpeed.getValue();
        double slowness = (1 - percentage) * movementSpeedValue;
        if (attMod == null) {
            attMod = new AttributeModifier(SLOWNESS_UUID, "Slowness", slowness, AttributeModifier.Operation.MULTIPLY_TOTAL);
        }

        if (!movementSpeed.hasModifier(attMod)) {
            // Apply the knockback resistance attribute modifier
            movementSpeed.addTransientModifier(attMod);
            DevilRpg.LOGGER.debug(entity.getName().getString() + " Slowness: " + slowness);
        }
    }

    @Override
    public void removeAttributeModifiers(LivingEntity entityLivingBaseIn, @NotNull AttributeMap attributeMapIn, int amplifier) {
        // Remove the knockback resistance attribute modifier when the effect is removed
        Objects.requireNonNull(entityLivingBaseIn.getAttribute(Attributes.MOVEMENT_SPEED)).removeModifier(SLOWNESS_UUID);
        attMod = null;
        DevilRpg.LOGGER.debug(entityLivingBaseIn.getName().getString() + "'s slowness effect has worn off.");
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // This method controls how often the applyEffectTick method is called
        // You can adjust it based on your needs
        return true;
    }
}