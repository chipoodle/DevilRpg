package com.chipoodle.devilrpg.effects;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.SunflowerShulker;
import com.chipoodle.devilrpg.init.ModEffects;
import com.chipoodle.devilrpg.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.UUID;

public class MobEffectVineFleshPuppet extends MobEffect {

    private final HashMap<UUID, Boolean> alive;
    private Player owner;

    public MobEffectVineFleshPuppet() {
        super(MobEffectCategory.HARMFUL, 0xFA4A4A);
        alive = new HashMap<>();
    }

    public static MobEffectInstance createInstance(int duration, int amplifier, Player owner) {
        // Create an instance of your custom MobEffect with the specified duration and amplifier
        MobEffectVineFleshPuppet mobEffect = (MobEffectVineFleshPuppet) ModEffects.VINE_FLESH_PUPPET.get();
        mobEffect.setOwner(owner);
        MobEffectInstance mobEffectInstance = new MobEffectInstance(mobEffect, duration, amplifier);
        DevilRpg.LOGGER.debug("--- MobEffectInstance crated. duration {} amplifier {}, seconds {}", duration, amplifier, duration / 20);
        return mobEffectInstance;
    }

    @Override
    public void applyEffectTick(@NotNull LivingEntity entity, int amplifier) {
        entity.hurt(entity.level.damageSources().playerAttack(owner), 1.0F);
        // Check if the entity is dead and if it was killed by the player
        if (!entity.isAlive()) {
            if (alive.getOrDefault(entity.getUUID(),Boolean.FALSE)) {
                Level world = entity.level;
                BlockPos spawnPos = entity.blockPosition();
                //Player player = (Player) Objects.requireNonNull(entity.getLastDamageSource()).getEntity();

                // Revive the enemy as a minion for 1 minute
                SunflowerShulker shulker = ModEntities.SUNFLOWER_SHULKER.get().create(world);
                shulker.updateLevel(owner);
                shulker.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                //Shulker minion = new MinionEntity(world, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, player);
                world.addFreshEntity(shulker);
                DevilRpg.LOGGER.info("===>MobEffectInstance applyEffectTick created sunflower from dead entity {} ", entity.getUUID());
                alive.put(entity.getUUID(), false);
            }
        } else {
            alive.put(entity.getUUID(), true);
        }

    }

    @Override
    public void removeAttributeModifiers(LivingEntity entity, @NotNull AttributeMap attributeMapIn, int amplifier) {
        DevilRpg.LOGGER.debug(entity.getName().getString() + "'s vine flesh puppet has worn off. Owner; {}", owner);
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
