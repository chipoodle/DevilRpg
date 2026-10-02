package com.chipoodle.devilrpg.eventsubscriber.common;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.world.VillageGenerator;
import com.chipoodle.devilrpg.world.VillageManager;
import com.chipoodle.devilrpg.world.VillageSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.IronGolem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * <b>NINGÚN GOLEM DENTRO DE LA HUERTA</b> (lo pidió el jugador: *"hay un golem dentro de una de las parcelas, quítalo
 * de ahí y que ningún golem pueda spawnear dentro de parcelas"*).
 * <p>
 * El golem de hierro de la aldea lo pone el <b>juego</b>, no el mod: el aldeano que junta suficientes quejas
 * <b>convoca</b> uno y lo crea <b>a su lado</b> ({@code Villager.spawnGolemIfNeeded}), así que si el que lo convoca es
 * un <b>granjero dentro de su parcela</b>, el golem <b>nace en la huerta</b> — y ahí no solo estorba: la tierra de
 * cultivo pisada se vuelve tierra y la parcela se pierde.
 * <p>
 * Aquí se corta <b>en el momento de entrar en el mundo</b>: si el que llega es un golem y la casilla cae sobre la
 * huella de una parcela de una aldea,
 * <ul>
 *   <li>si <b>acaba de nacer</b> ahí: <b>no entra</b> (el suceso se cancela) y queda en el log;</li>
 *   <li>si <b>viene del guardado</b> (o de otra dimensión): no se le borra nada, se le <b>saca a la calle</b>, que es
 *       lo que pidió el jugador con el que ya tenía dentro.</li>
 * </ul>
 * La <b>red de seguridad</b> para el que ande suelto y se cuele por una compuerta abierta está en el latido
 * ({@code VillageManager.sacarLosGolemsDeLaHuerta}).
 * <p>
 * Se registra en el bus de juego por defecto (sin {@code bus = ...}, como {@code CommonForgeBlockEventSubscriber}): el
 * {@code EntityJoinLevelEvent} es del bus de juego, no del de mods.
 */
@EventBusSubscriber(modid = DevilRpg.MODID)
public class CommonForgeGolemEventSubscriber {

    /** Radio alrededor de una aldea en el que merece la pena mirar (las parcelas están a 30-45 del centro). */
    private static final double RADIO_DE_LA_ALDEA = 100.0D;

    @SubscribeEvent
    public static void onGolemEntersTheWorld(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof IronGolem golem) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = golem.blockPosition();
        for (int index : VillageSavedData.get(level).generatedIndices()) {
            BlockPos center = VillageManager.centroDe(level, index);
            if (center == null) {
                continue;
            }
            double dx = pos.getX() - center.getX();
            double dz = pos.getZ() - center.getZ();
            if (dx * dx + dz * dz > RADIO_DE_LA_ALDEA * RADIO_DE_LA_ALDEA) {
                continue; // lejos de esa aldea
            }
            if (!VillageGenerator.sobreLaHuellaDeUnBancal(center, pos)) {
                continue; // no está sobre la huerta
            }
            if (event.loadedFromDisk()) {
                BlockPos fuera = VillageManager.casillaDeLaCalle(level, center);
                golem.teleportTo(fuera.getX() + 0.5D, fuera.getY(), fuera.getZ() + 0.5D);
                DevilRpg.LOGGER.info("[Village] Habia un golem dentro de la huerta de la aldea {} ({}): se le saca a"
                        + " la calle ({})", index, pos.toShortString(), fuera.toShortString());
            } else {
                event.setCanceled(true);
                DevilRpg.LOGGER.info("[Village] Un golem iba a nacer DENTRO de la huerta de la aldea {} ({}): no se"
                        + " deja", index, pos.toShortString());
            }
            return;
        }
    }
}
