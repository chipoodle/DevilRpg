package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;


public class SoulWispArcher extends SoulWisp implements RangedAttackMob {

    private static final float RADIUS = 0.15f;
    //private final RangedCreatureGoal<SoulWispArcherEntity> bowGoal = new RangedCreatureGoal<>(this, 1.0D, 20, 15.0F);

    // --- Pasivo "Ice spear volley" (skill wisp_ice_spear) -------------------------------------------
    /** Probabilidad (en %) POR PUNTO de que un disparo sea la andanada de lanzas de hielo. 5 puntos = 35%. */
    private static final int ICE_SPEAR_PROBABILITY_PER_POINT = 7;
    /** Cuántas lanzas lanza el poder especial. */
    private static final int ICE_SPEAR_COUNT = 3;
    /** Apertura del abanico: fracción de la distancia al objetivo que se desvía cada lanza a los lados. */
    private static final double ICE_SPEAR_SPREAD = 0.16D;
    /** Velocidad con la que sale cada lanza (luego el guiado de {@link IceSpear} manda). */
    private static final float ICE_SPEAR_LAUNCH_SPEED = 1.4F;

    public SoulWispArcher(EntityType<? extends SoulWispArcher> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        // super.registerGoals();

        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomFlyingGoal(this, 1.0D));
        this.goalSelector.addGoal(1, new RangedAttackGoal(this, 1.25D, 20, 10.0F));
        this.goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.0D, 10.0F, 2.0F));
        this.goalSelector.addGoal(9, new SoulWisp.WanderGoal());
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false, (entity) -> Math.abs(entity.getY() - this.getY()) <= 4.0D &&
                !(entity instanceof Villager) &&
                !(entity instanceof Llama) &&
                !(entity instanceof Turtle) &&
                !(entity instanceof IronGolem)));
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
    }

    @Override
    public void performRangedAttack(LivingEntity target, float p_82196_2_) {
        double d0 = target.getX() - this.getX();
        double d1 = target.getY(0.3333333333333333D) - this.getY();
        double d2 = target.getZ() - this.getZ();
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);

        // Puntos del dueño (null-safe: un wisp sin dueño, p. ej. de huevo de spawn, dispara igual).
        HashMap<SkillEnum, Integer> skills = null;
        if (this.getOwner() instanceof Player owner) {
            PlayerSkillCapabilityInterface capability = IGenericCapability.getUnwrappedPlayerCapability(owner, PlayerSkillCapability.INSTANCE);
            if (capability != null) {
                skills = capability.getSkillsPoints();
            }
        }
        int archerPoints = skills == null ? 0 : skills.getOrDefault(SkillEnum.SUMMON_WISP_ARCHER, 0);
        int spearPoints = skills == null ? 0 : skills.getOrDefault(SkillEnum.WISP_ICE_SPEAR, 0);

        // PASIVO "Ice spear volley": con una probabilidad que sube con los puntos, el disparo normal se
        // convierte en una andanada de 3 lanzas de hielo grandes, explosivas y que persiguen al enemigo.
        if (spearPoints > 0 && this.getRandom().nextInt(100) < spearPoints * ICE_SPEAR_PROBABILITY_PER_POINT) {
            this.shootIceSpears(d0, d1, d2, d3, archerPoints);
            return;
        }

        var snowballEntity = new FrostBall(this.level(), this);
        snowballEntity.updateLevel(this, archerPoints);
        snowballEntity.shoot(d0, d1 + d3 * (double) 0.1F, d2, 1.6F, (float) (14 - this.level().getDifficulty().getId() * 4));
        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
        this.level().addFreshEntity(snowballEntity);
        //this.setHealth(this.getHealth() -1.5F);
    }

    /**
     * Lanza las 3 lanzas de hielo del pasivo en abanico. El abanico se abre en perpendicular a la línea de
     * tiro, así que las tres salen hacia el enemigo pero cubriendo un poco a los lados: aunque se mueva, alguna
     * llega. Cada lanza persigue a su objetivo por su cuenta y estalla con salpicadura (ver {@link IceSpear}).
     */
    private void shootIceSpears(double dx, double dy, double dz, double horizontal, int archerPoints) {
        double perpendicularX = horizontal < 1.0E-4D ? 1.0D : -dz / horizontal;
        double perpendicularZ = horizontal < 1.0E-4D ? 0.0D : dx / horizontal;
        for (int i = 0; i < ICE_SPEAR_COUNT; i++) {
            double offset = (i - (ICE_SPEAR_COUNT - 1) / 2.0D) * ICE_SPEAR_SPREAD * Math.max(horizontal, 1.0D);
            IceSpear spear = new IceSpear(this.level(), this);
            spear.updateLevel(this, archerPoints);
            spear.shoot(dx + perpendicularX * offset, dy + horizontal * (double) 0.1F, dz + perpendicularZ * offset,
                    ICE_SPEAR_LAUNCH_SPEED, 1.0F);
            this.level().addFreshEntity(spear);
        }
        this.playSound(SoundEvents.EVOKER_CAST_SPELL, 1.0F, 1.3F);
        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 0.8F);
    }

    public void updateLevel(Player owner) {
        super.updateLevel(owner, null, null, SkillEnum.SUMMON_WISP_ARCHER, true);
    }

    @Nullable
    @Override
    public SoulWispArcher getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob ageableMob) {
        return ModEntities.WISP_ARCHER.get().create(level);
    }
}
