package com.chipoodle.devilrpg.entity;


import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;

public class AggressiveZombieEntity extends Zombie {

    public AggressiveZombieEntity(EntityType<? extends Zombie> type, Level world) {
        super(type, world);
    }

    // Configurar atributos personalizados
    public static AttributeSupplier.Builder setAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D) // Más salud
                .add(Attributes.MOVEMENT_SPEED, 0.4D) // Más rápido
                .add(Attributes.ATTACK_DAMAGE, 6.5D) // Más daño
                .add(Attributes.FOLLOW_RANGE, 64.0D); // Detección al doble de distancia
    }

    public static boolean checkSpawnRules(
            EntityType<AggressiveZombieEntity> entityType,
            LevelAccessor level,
            MobSpawnType spawnType,
            BlockPos pos,
            RandomSource random
    ) {

        DevilRpg.LOGGER.info("--------------------------> spawning aggressive zombie");
        return true; // Permitir spawn en cualquier condición
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new FloatGoal(this)); // Flotar en agua
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, false)); // Ataque cuerpo a cuerpo más rápido
        this.goalSelector.addGoal(3, new FireballAttackGoal(this)); // Lanzar fuego como los Blaze
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true)); // Detectar jugadores
    }

    @Override
    public boolean isPreventingPlayerRest(Player player) {
        return true; // Hace que los jugadores no puedan dormir si hay un Flaming Zombie cerca
    }

    @Override
    public boolean checkSpawnRules(LevelAccessor world, MobSpawnType reason) {

        return true; // Ignora reglas de spawn, permitiendo aparecer de día
    }

    @Override
    public boolean isSunSensitive() {
        return false; // No se quema con el sol
    }

    // Clase interna para el comportamiento de lanzar fuego
    static class FireballAttackGoal extends Goal {
        private final AggressiveZombieEntity zombie;
        private int attackTimer;

        public FireballAttackGoal(AggressiveZombieEntity zombie) {
            this.zombie = zombie;
        }

        @Override
        public boolean canUse() {
            return zombie.getTarget() != null && zombie.getTarget().isAlive();
        }

        @Override
        public void tick() {
            Entity target = zombie.getTarget();
            if (target == null) return;

            if (--attackTimer <= 0) {
                double d0 = 4.0D; // Distancia máxima para lanzar fuego
                double d1 = target.getX() - zombie.getX();
                double d2 = target.getY(0.5D) - zombie.getY(0.5D);
                double d3 = target.getZ() - zombie.getZ();
                SmallFireball fireball = new SmallFireball(zombie.level, zombie, d1 + zombie.getRandom().nextGaussian() * d0, d2, d3 + zombie.getRandom().nextGaussian() * d0);
                fireball.setPos(fireball.getX(), zombie.getY(0.5D) + 0.5D, fireball.getZ());
                zombie.level.addFreshEntity(fireball);
                attackTimer = 60; // Tiempo entre ataques
            }
        }
    }

}
