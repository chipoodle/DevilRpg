package com.chipoodle.devilrpg.effects;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.init.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class MobEffectVineFleshPuppet extends MobEffect {

    private Player owner;

    Shulker shulker;

    public MobEffectVineFleshPuppet() {
        super(MobEffectCategory.HARMFUL, 0xBFFAFF);
    }

    public static MobEffectInstance createInstance(int duration, int amplifier, Player owner) {
        // Create an instance of your custom MobEffect with the specified duration and amplifier
        MobEffectVineFleshPuppet mobEffect = (MobEffectVineFleshPuppet) ModEffects.VINE_FLESH_PUPPET.get();
        mobEffect.setOwner(owner);
        //DevilRpg.LOGGER.debug("--- crated. duration {} amplifier {}, seconds {}", duration, amplifier, duration / 20);
        return new MobEffectInstance(mobEffect, duration, amplifier);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        // Check if the entity is dead and if it was killed by the player
        if (!entity.isAlive() && entity.getLastDamageSource() instanceof DamageSource && entity.getLastDamageSource().getEntity() instanceof Player) {
            Level world = entity.level;
            BlockPos spawnPos = entity.blockPosition();
            Player player = (Player) entity.getLastDamageSource().getEntity();

            // Revive the enemy as a minion for 1 minute
            shulker = EntityType.SHULKER.create(world);
            shulker.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
            //Shulker minion = new MinionEntity(world, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, player);
            world.addFreshEntity(shulker);
        }
    }

    @Override
    public void removeAttributeModifiers(LivingEntity entityLivingBaseIn, @NotNull AttributeMap attributeMapIn, int amplifier) {
        // Remove the entangling  resistance attribute modifier when the effect is removed
        DevilRpg.LOGGER.debug(entityLivingBaseIn.getName().getString() + "'s vine flesh puppet has worn off.");
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // This method controls how often the applyEffectTick method is called
        // You can adjust it based on your needs
        return true;
    }

    private void setOwner(Player owner) {
        this.owner = owner;
    }

}
