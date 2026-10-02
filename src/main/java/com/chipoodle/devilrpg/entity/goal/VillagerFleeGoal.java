package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.world.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * <b>HUIR DEL PELIGRO</b> (I165, 30-sep-2026). Lo pidió el jugador: *«los aldeanos no huyen ante los zombies
 * agresivos… ¿qué es lo que pasa?»*.
 * <p>
 * <b>LO QUE PASABA</b>, y está en el propio código de la aldea: el <b>pánico del aldeano existe en su cerebro</b>
 * (el juego se lo da hecho), pero <b>los goals del oficio cogen el flag {@code MOVE}</b> —el granjero, el minero, el
 * herrero, la taberna…— y un goal con {@code MOVE} <b>excluye</b> a cualquier otro que también lo pida. Así que el
 * pánico <b>no podía mover al aldeano</b>: lo dejaba plantado mientras el asaltante lo alcanzaba. Medido en el
 * registro del jugador: `Dionisio (Granjero) was slain by Zombie`, `Jacinto (Granjero) was slain by Aggressive
 * Zombie` — muertos <b>sin haber dado un paso</b>, con el zombi a su lado.
 * <p>
 * <b>ESTE GOAL ES LA PIEZA QUE FALTABA</b>: prioridad <b>0</b> (por delante de todos los oficios) y con
 * {@link Flag#MOVE}, de modo que cuando hay un enemigo cerca <b>el aldeano suelta el trabajo y corre</b>. Y corre
 * <b>hacia su casa</b> —la puerta de su casa, que es lo que el jugador espera ver: el pueblo entrando en las casas y
 * cerrándose— y, si no tiene casa cerca, <b>en dirección contraria</b> al enemigo.
 * <p>
 * No se le pone a los <b>guardias</b>: esos pelean (ver {@code VillagerGuardGoal}); el que corre es el paisano.
 */
public class VillagerFleeGoal extends Goal {

    /** A esta distancia de un enemigo, el aldeano suelta lo que esté haciendo y sale corriendo. */
    private static final double RADIO_DE_PANICO = 12.0D;
    /** Y no se le pasa el susto hasta que el enemigo se aleja más que esto (si no, oscila en el borde). */
    private static final double RADIO_DE_CALMA = 22.0D;
    /** Corriendo: el asaltante ataca a 1.2, así que un aldeano a 1.35 puede ganarle la carrera a la casa. */
    private static final float VELOCIDAD = 1.35F;

    private final Villager villager;
    /** El enemigo del que huye ahora (para no recalcularlo en cada tick). */
    @Nullable
    private Monster peligro;

    public VillagerFleeGoal(Villager villager) {
        this.villager = villager;
        // CON `MOVE`: es justo lo que le faltaba al pánico del juego para poder mover al aldeano.
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (villager.isBaby() && villager.isPassenger()) {
            return false; // un crío a cuestas no corre: lo lleva su madre
        }
        peligro = enemigoMasCercano(RADIO_DE_PANICO);
        return peligro != null;
    }

    @Override
    public boolean canContinueToUse() {
        peligro = enemigoMasCercano(RADIO_DE_CALMA);
        return peligro != null;
    }

    @Override
    public void start() {
        villager.getNavigation().stop();
        VillageManager.ponerActividad(villager, "Huyendo");
    }

    @Override
    public void tick() {
        if (peligro == null) {
            return;
        }
        // ¿A DÓNDE CORRE? A su CASA si la tiene a mano (es lo que hace un aldeano de verdad: se mete en casa), y si
        // no, en línea recta alejándose del enemigo.
        BlockPos destino = haciaDondeHuir();
        // El rumbo se escribe EN CADA TICK: el paseo del cerebro del juego también escribe `WALK_TARGET` (ver
        // `VillageManager.caminarHacia`) y, si no se defiende, el aldeano se va de paseo con el zombi detrás.
        VillageManager.caminarHacia(villager, destino, VELOCIDAD);
        VillageManager.ponerActividad(villager, "Huyendo");
    }

    @Override
    public void stop() {
        peligro = null;
        villager.getNavigation().stop();
    }

    /** El enemigo vivo más cercano dentro de ese radio, o {@code null}. */
    @Nullable
    private Monster enemigoMasCercano(double radio) {
        if (!(villager.level() instanceof ServerLevel nivel)) {
            return null;
        }
        List<Monster> enemigos = nivel.getEntitiesOfClass(Monster.class, new AABB(villager.blockPosition()).inflate(radio));
        Monster masCercano = null;
        double mejor = Double.MAX_VALUE;
        for (Monster m : enemigos) {
            if (!m.isAlive() || m.isRemoved()) {
                continue;
            }
            double d = m.distanceToSqr(villager);
            if (d < mejor) {
                mejor = d;
                masCercano = m;
            }
        }
        return masCercano;
    }

    /** La puerta de su casa si está lejos del peligro, o un punto alejándose del enemigo. */
    private BlockPos haciaDondeHuir() {
        BlockPos suCasa = casaDelAldeano();
        if (suCasa != null && peligro != null
                && suCasa.distToCenterSqr(peligro.position()) > 100.0D) { // a más de 10 bloques del enemigo: su casa
            return suCasa;
        }
        // Alejarse: un punto a 12 bloques en la dirección contraria al enemigo, a la altura del pueblo.
        int dx = Double.compare(villager.getX(), peligro.getX());
        int dz = Double.compare(villager.getZ(), peligro.getZ());
        if (dx == 0 && dz == 0) {
            dx = 1;
        }
        return villager.blockPosition().offset(dx * 12, 0, dz * 12);
    }

    /** La cama del aldeano (que es su casa), o {@code null} si no tiene. */
    @Nullable
    private BlockPos casaDelAldeano() {
        var memoria = villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME);
        return memoria.map(global -> global.pos()).orElse(null);
    }
}
