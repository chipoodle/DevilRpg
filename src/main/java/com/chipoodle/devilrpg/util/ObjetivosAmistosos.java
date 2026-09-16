package com.chipoodle.devilrpg.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Quién es <b>"de casa"</b> y a quién <b>no</b> se le puede tirar una bomba ni poner como objetivo.
 * <p>
 * Sale de un aviso urgente del jugador: <i>"las bombas que sacan los mushroom de mi skill no deben ir contra los
 * aldeanos"</i>. En el código había exclusiones de aldeanos <b>comentadas</b>, así que los minichampiñones
 * ({@code SunflowerShulker}) ponían a los aldeanos de objetivo y les lanzaban las esporas encima; y la propia
 * explosión tampoco distinguía a nadie.
 * <p>
 * Regla única, para no volver a repetir la lista en cada bicho del mod:
 * <ul>
 *   <li><b>Gente del pueblo</b>: aldeanos (y el comerciante errante, que es {@link AbstractVillager}), golems de
 *       hierro y llamas. Nunca se atacan.</li>
 *   <li><b>Bichos de casa</b>: mascotas ({@link TamableAnimal}) y cualquier {@link OwnableEntity} del <b>mismo
 *       dueño</b> — los propios minions del jugador. Tampoco se atacan.</li>
 *   <li><b>Animales de corral</b> ({@link Animal}) salvo los que <b>atacan</b> ({@link Enemy}: hoglin, zoglin), que
 *       son enemigos de verdad y sí se atacan.</li>
 * </ul>
 * Lo que sí se ataca: monstruos y cualquier otra criatura hostil, incluidos los agresivos del mod.
 */
public final class ObjetivosAmistosos {

    private ObjetivosAmistosos() {
    }

    /** ¿Es de casa? (gente del pueblo, mascota o animal de corral que no ataca). */
    public static boolean esDeCasa(Entity entity) {
        if (entity instanceof AbstractVillager || entity instanceof IronGolem || entity instanceof Llama) {
            return true;
        }
        return entity instanceof TamableAnimal || (entity instanceof Animal && !(entity instanceof Enemy));
    }

    /**
     * ¿Puede {@code atacante} ponerse a {@code objetivo} como objetivo (o tirarle una bomba)? {@code false} para todo
     * lo que es de casa y para los bichos del <b>mismo dueño</b> (que el minion del jugador no se pegue con la
     * mascota del jugador). Si el atacante no tiene dueño, solo se aplica la regla de "de casa".
     */
    public static boolean sePuedeAtacar(@Nullable Entity atacante, Entity objetivo) {
        if (esDeCasa(objetivo)) {
            return false;
        }
        if (atacante == null || !(objetivo instanceof OwnableEntity conDueno)) {
            return true;
        }
        if (!(atacante instanceof OwnableEntity atacanteConDueno)) {
            return true;
        }
        return !Objects.equals(conDueno.getOwnerUUID(), atacanteConDueno.getOwnerUUID());
    }
}
