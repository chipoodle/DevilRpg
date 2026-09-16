package com.chipoodle.devilrpg.eventsubscriber.common;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.ExplodingSporeBullet;
import com.chipoodle.devilrpg.util.ObjetivosAmistosos;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Las <b>bombas de esporas del jugador</b> (la skill de los mushroom) <b>no tocan a los aldeanos</b>. Lo pidió el
 * jugador con urgencia: "las bombas que sacan los mushroom de mi skill no deben ir contra los aldeanos".
 * <p>
 * Son <b>dos capas</b>, porque con una sola se seguía matando gente:
 * <ol>
 *   <li>El <b>objetivo</b>: la espora ya no <i>busca</i> aldeanos ni bichos de casa (ver
 *       {@link ExplodingSporeBullet#esObjetivoDeLasEsporas}).</li>
 *   <li>El <b>estallido</b> (esto): aunque la espora fuera a por un zombie, si el zombie se mete en el pueblo la
 *       explosión salpicaba a los aldeanos que hubiera al lado. Aquí se quitan de la lista de afectados de la
 *       explosión, así que no les hace <b>daño ni empuje</b> (el juego recorre esa misma lista para las dos cosas:
 *       {@code Explosion.explode} → {@code entity.hurt} + retroceso).</li>
 * </ol>
 * Solo se toca la explosión de <b>nuestras</b> esporas ({@code getDirectSourceEntity()}), no las de vanilla: una
 * explosión de creeper o de TNT sigue haciendo lo de siempre.
 */
@EventBusSubscriber(modid = DevilRpg.MODID)
public class CommonForgeExplosionEventSubscriber {

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide()) {
            return; // el daño se decide en el servidor
        }
        Entity fuente = event.getExplosion().getDirectSourceEntity();
        if (!(fuente instanceof ExplodingSporeBullet)) {
            return; // no es una bomba de esporas: no se toca
        }
        event.getAffectedEntities().removeIf(CommonForgeExplosionEventSubscriber::esDeCasa);
    }

    /**
     * ¿Es "de casa"? Aldeanos (incluido el comerciante errante), golems de hierro, llamas, mascotas y animales de
     * corral (salvo los que atacan, como el hoglin): a esos no les llega la bomba.
     */
    private static boolean esDeCasa(Entity entity) {
        return ObjetivosAmistosos.esDeCasa(entity);
    }
}
