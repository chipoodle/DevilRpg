package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.spawnprofile.NormalZombieSpawnProfile;
import com.chipoodle.devilrpg.spawnprofile.SpawnScaleProfile;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/**
 * Zombie "normal" del mod: se comporta como un zombie vanilla (se quema al sol, ataca de noche), pero
 * tiene su propia {@code EntityType} para que el {@code CustomSpawner} lo cuente por separado del zombie
 * agresivo. Es la amenaza nocturna de base que presiona al jugador desde el inicio, incluso cerca del
 * spawn (su perfil {@code NormalZombieSpawnProfile} no tiene zona protegida).
 */
public class NormalZombieEntity extends Zombie {

    public NormalZombieEntity(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    /** Atributos base tomados del perfil del zombie normal (valores vanilla). */
    public static AttributeSupplier.Builder setAttributes() {
        SpawnScaleProfile p = NormalZombieSpawnProfile.INSTANCE;
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, p.baseHealth())
                .add(Attributes.MOVEMENT_SPEED, p.baseSpeed())
                .add(Attributes.ATTACK_DAMAGE, p.baseDamage());
    }

    /** XP fija (base) del perfil; el zombie normal no escala con la distancia. */
    @Override
    protected int getBaseExperienceReward() {
        return NormalZombieSpawnProfile.INSTANCE.baseXp();
    }
}
