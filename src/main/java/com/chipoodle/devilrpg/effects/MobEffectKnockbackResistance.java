package com.chipoodle.devilrpg.effects;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.init.ModEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class MobEffectKnockbackResistance extends MobEffect {
    private static final ResourceLocation KNOCKBACK_RESISTANCE_ID =
            ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "knockback_resistance");
    private AttributeModifier attMod = null;

    public MobEffectKnockbackResistance() {
        super(MobEffectCategory.BENEFICIAL, 0xFF00FF); // Adjust the color as needed
    }

    @Override
    public boolean applyEffectTick(@NotNull LivingEntity entity, int amplifier) {
        amplifier = Math.max(1, Math.min(amplifier + 1, 5));// se suma uno porque  el indice empieza en 0 pero el nivel es de 1 a 5
        // Calculate the knockback resistance based on the amplifier
        double knockbackResistance = 0.2 * amplifier;

        if (attMod == null) {
            attMod = new AttributeModifier(KNOCKBACK_RESISTANCE_ID, knockbackResistance, AttributeModifier.Operation.ADD_VALUE);
        }

        if (!Objects.requireNonNull(entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE)).hasModifier(attMod.id())) {
            // Apply the knockback resistance attribute modifier
            Objects.requireNonNull(entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE)).addTransientModifier(attMod);
            DevilRpg.LOGGER.debug("{} has knockback resistance: {} amplifier {}", entity.getName().getString(), knockbackResistance, amplifier);
        }
        return true;
    }

    public void removeAttributeModifiers(LivingEntity entityLivingBaseIn, @NotNull AttributeMap attributeMapIn, int amplifier) {
        // Remove the knockback resistance attribute modifier when the effect is removed
        Objects.requireNonNull(entityLivingBaseIn.getAttribute(Attributes.KNOCKBACK_RESISTANCE)).removeModifier(KNOCKBACK_RESISTANCE_ID);
        attMod = null;
        DevilRpg.LOGGER.debug(entityLivingBaseIn.getName().getString() + "'s knockback resistance effect has worn off.");
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // This method controls how often the applyEffectTick method is called
        // You can adjust it based on your needs
        return true;
    }

    public static MobEffectInstance createInstance(int duration, int amplifier) {
        // Create an instance of your custom MobEffect with the specified duration and amplifier
        return new MobEffectInstance(ModEffects.KNOCKBACK_RESISTANCE, duration, amplifier);
    }
}
