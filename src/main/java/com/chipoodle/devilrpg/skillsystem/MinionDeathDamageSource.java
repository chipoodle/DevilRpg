package com.chipoodle.devilrpg.skillsystem;


import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

public class MinionDeathDamageSource extends DamageSource {

    public MinionDeathDamageSource(String damageTypeIn) {
        super(damageTypeIn);
    }

    /**
     * Gets the death message that is displayed when the player dies
     */
    public Component getDeathMessage(LivingEntity entityLivingBaseIn) {
        LivingEntity livingentity = entityLivingBaseIn.getKillCredit();
        return null;//new TranslationTextComponent("");
    }

    public Component getLocalizedDeathMessage(LivingEntity p_19343_) {
        LivingEntity livingentity = p_19343_.getKillCredit();
        String s = "death.attack." + this.msgId;
        String s1 = s + ".player";
        return livingentity != null ? Component.translatable(s1, p_19343_.getDisplayName(), livingentity.getDisplayName()) : Component.translatable(s, p_19343_.getDisplayName());
    }

}
