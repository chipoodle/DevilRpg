package com.chipoodle.devilrpg.entity;


import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.Team;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/**
 * Entities that implment this interface carry the TamableMinion data attachment (see {@link com.chipoodle.devilrpg.init.ModCapabilities}).
 */
public interface ITamableEntity extends IAttachmentHolder, OwnableEntity, Leashable {

    @NotNull Level level();

    PathNavigation getNavigation();
    /*@Nullable
    LivingEntity getOwner();*/

    boolean isOrderedToSit();

    void setOrderedToSit(boolean sit);

    double distanceToSqr(LivingEntity livingentity);

    float getPathfindingMalus(PathType water);

    void setPathfindingMalus(PathType water, float f);

    int getMaxHeadXRot();

    LookControl getLookControl();

    boolean isPassenger();

    float getXRot();

    float getYRot();

    void moveTo(double d, double p_226328_2_, double e, float getyRot, float getxRot);

    Vec3i blockPosition();

    Entity getEntity();

    AABB getBoundingBox();


    default RandomSource getRandom() {
        return RandomSource.create();
    }

    boolean isTame();

    default boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (target instanceof ITamableEntity entity) {
            return !entity.isTame() || !Objects.equals(entity.getOwnerUUID(), owner.getUUID());
        } else if (target instanceof Player && owner instanceof Player && !((Player) owner).canHarmPlayer((Player) target)) {
            return false;
        } else return !(target instanceof AbstractHorse) || !((AbstractHorse) target).isTamed();
    }

    AttributeInstance getAttribute(Holder<Attribute> key);

    //ITextComponent getDisplayName();

    float getMaxHealth();

    void setHealth(float maxHealth);

    default boolean isEntitySameOwnerAsThis(Entity entityIn, ITamableEntity entityThis) {
        boolean isSameOwner = false;
        if (entityIn instanceof ITamableEntity && ((ITamableEntity) entityIn).getOwner() != null)
            isSameOwner = ((ITamableEntity) entityIn).getOwner().equals(entityThis.getOwner());
        return isSameOwner;
    }

    Team getTeam();

    boolean isAlliedTo(Entity p_184191_1_);

    @Nullable
    UUID getOwnerUUID();

    void setOwnerUUID(@Nullable UUID uuid);

    boolean hurt(DamageSource damageSource, float maxValue);


    boolean canAttack(LivingEntity p_213336_1_);

    /**
     * ¿Esa criatura es <b>pacífica o neutral</b> (o sea: <b>no</b> hostil)? Es a lo que un <b>minion</b> no ataca por
     * su cuenta: el ganado y los animales del mundo, los monstruos que solo se defienden (lobos, osos polares, abejas,
     * llamas, cabras, delfines...), los peces, los murciélagos, los aldeanos y los guardianes del pueblo (golems).
     * <p>
     * Lo pidió el jugador: <i>"haz que todos mis minions no ataquen a las criaturas neutrales a menos que yo los
     * golpee primero; los mobs hostiles sí los atacan tal como está ahora"</i>. Los <b>hostiles</b> (los que
     * implementan {@code Enemy}: zombis, esqueletos, creepers, arácnidos, hoglins, piglins...) devuelven {@code false}
     * y se siguen atacando igual; y si el <b>dueño</b> pega primero a un neutral, el minion va a por él de todas
     * formas, porque {@code OwnerHurtTargetGoal} (lo que ataca mi dueño) y {@code OwnerHurtByTargetGoal} (quien ataca
     * a mi dueño) van <b>por encima</b> de esta regla.
     */
    /**
     * <b>¿El dueño está metido en la pelea con ese monstruo?</b> Lo pidió el jugador: *"si yo, jugador, llego a atacar
     * alguno, o si alguno de los poderes atacan (como la enfermedad que genera el hongo y el liquen cuando se avienta
     * a alguna entidad), esta se vuelve enemigo y se debe atacar por los minions"*.
     * <p>
     * Se mira el <b>último al que atacó el dueño</b> ({@code getLastHurtMob}) y también si el monstruo le tiene a él como
     * su agresor ({@code getLastHurtByMob}: es lo que pasa al pegarle a un lobo o a un oso polar). Vale para las
     * <b>manos</b> y para los <b>poderes</b>: el daño del hongo y del liquen va con el jugador como atacante
     * ({@code playerAttack(owner)} / {@code explosion(…, owner)}), así que el monstruo queda marcado igual.
     */
    static boolean elDuenoLeEstaAtacando(@Nullable Entity dueno, Entity quien) {
        if (!(dueno instanceof net.minecraft.world.entity.player.Player owner) || !(quien instanceof LivingEntity vivo)) {
            return false;
        }
        return owner.getLastHurtMob() == vivo || vivo.getLastHurtByMob() == owner;
    }

    static boolean esCriaturaPacificaONeutral(Entity entity) {
        if (entity instanceof net.minecraft.world.entity.monster.Enemy) {
            return false; // hostil: a esos SÍ se les ataca, como hasta ahora
        }
        return entity instanceof net.minecraft.world.entity.NeutralMob
                || entity instanceof net.minecraft.world.entity.animal.Animal
                || entity instanceof net.minecraft.world.entity.animal.WaterAnimal
                || entity instanceof net.minecraft.world.entity.npc.AbstractVillager
                || entity instanceof net.minecraft.world.entity.animal.IronGolem
                || entity instanceof net.minecraft.world.entity.animal.SnowGolem;
    }

    default boolean isOwnedBy(LivingEntity p_152114_1_) {
        return p_152114_1_ == this.getOwner();
    }

    void tame(Player p_193101_1_);

    DamageSources damageSources();

    /**
     * El dueño para poder quitarse de la lista de minions al morir.
     * <p>
     * {@code getOwner()} solo busca al jugador <b>en su propio nivel</b>, así que si el jugador está en otra
     * dimensión (o desconectado) devuelve {@code null}. Con el {@code getOwner() != null} que había en los
     * {@code die()} de lobo, oso y wisp, un minion que moría con el dueño en otra dimensión <b>no se quitaba
     * nunca de la lista</b>: ahí se acumulaban UUIDs de minions muertos (que después desbordaban el cupo).
     * Aquí se busca al jugador en <b>todo el servidor</b> por su UUID.
     */
    @Nullable
    default Player resolveOwnerForRemoval() {
        if (getOwner() instanceof Player player) {
            return player;
        }
        UUID ownerId = getOwnerUUID();
        net.minecraft.server.MinecraftServer server = getEntity().getServer();
        if (server == null || ownerId == null) {
            return null;
        }
        return server.getPlayerList().getPlayer(ownerId);
    }
}
