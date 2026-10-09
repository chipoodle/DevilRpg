package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.world.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

/**
 * <b>M1 · EL DESPACHADOR</b> (fase 2 del cerebro propio de la aldea; el plan está en
 * {@code docs/aldea-cerebro.md}).
 * <p>
 * <b>OJO: ESTE GOAL NO ESTÁ CABLEADO, Y POR TANTO NO SE EJECUTA</b> (dicho claro el 3-oct-2026, ronda 39, porque sus
 * comentarios decían lo contrario y me costó dos rondas entender por qué sus arreglos «no hacían nada»: los empujones
 * de I190 y la supervisión de movimiento de I192 salían **0 veces** en todas las corridas). Lo dice el propio
 * {@code VillageManager.caminarHacia}: *«el despachador (M1) —el que apuntaba el recado aquí y lo defendía— se probó y
 * se retiró… el módulo ({@code VillageDispatcherGoal}) y {@code apuntarElRecado} se quedan sin cablear como base del
 * nivel 3»*. Se conserva como base de ese nivel, pero <b>nadie lo instancia</b>.
 * <p>
 * <b>Y POR ESO la red que sí funciona es I198</b>, en el latido del pueblo ({@code VillageManager}): al aldeano con
 * faena del mod que lleva 2 s sin moverse se le para la navegación para que su goal vuelva a pedir el camino. Medido:
 * 177 y 206 empujones en dos corridas de 3 minutos, con los avisos en el mejor nivel de la sesión (0 · 1).
 * <p>
 * Es <b>el único dueño del rumbo</b> de un aldeano de la aldea. El problema medido que resuelve: el aldeano tiene dos
 * voces que le dicen a dónde ir —el <b>goal del mod</b> (el trabajo, que pide el destino con {@code caminarHacia}) y el
 * <b>cerebro del propio juego</b>, que con sus paseos y su «anda hacia donde miras» escribe <b>el mismo</b>
 * {@code WALK_TARGET}— y cuando el paseo escribe después, el aldeano se va por ahí mientras su goal cree que va al
 * trabajo: el goal ve que no se acerca, se impacienta y se rinde. Medido: <b>11.039 de 57.663</b> recados (**19 %**).
 * <p>
 * <b>Por qué NO coge el flag {@code MOVE}</b>: un goal con MOVE <b>excluye</b> a todos los demás goals con MOVE, y los
 * oficios del pueblo (granjero, minero, herrero…) son goals con MOVE. Cogiéndolo, el despachador caminaría bien y
 * <b>el pueblo dejaría de trabajar</b>. Sin flags, corre <b>en paralelo</b> con el oficio: el oficio hace su faena y
 * pide el destino, y el despachador se encarga de que el aldeano <b>vaya de verdad</b> a donde le mandaron.
 * <p>
 * <b>Y NO ES UN «SOSTENEDOR» A CIEGAS</b> (eso se probó y se retiró: media 17,5 frente a 10,25, porque obligaba al
 * aldeano a insistir en recados imposibles). Aquí hay <b>supervisión</b> (M4): si el aldeano <b>no consume nodos de su
 * ruta</b> durante {@link VillageManager#RECADO_PRESUPUESTO} ticks, el recado se <b>abandona</b> —se aparca el punto y
 * el goal que lo pidió elegirá otro—, así que <b>nunca se ronda</b>: siempre hay un paso siguiente.
 */
public class VillageDispatcherGoal extends Goal {

    private final Villager villager;
    /** Ticks seguidos sin consumir un nodo de la ruta (ver la supervisión de M4). */
    private int ticksSinAvanzar;
    /** Cada cuántos ticks sin avanzar se le da un empujón (parar la navegación y volver a mandar el rumbo). */
    private static final int EMPUJON_CADA = 40;
    /** Cuántos empujones como mucho a un mismo recado antes de abandonarlo (el presupuesto sigue siendo el de M4). */
    private static final int EMPUJONES_POR_RECADO = 3;
    /** Cuántos empujones se han dado en total: es una MEDIDA del pueblo (ver el registro). */
    private static int empujonesDados = 0;
    /** Empujones dados a este recado (se reinicia al avanzar, al abandonar y al arrancar). */
    private int empujonesDeEsteRecado;

    public VillageDispatcherGoal(Villager villager) {
        this.villager = villager;
        // SIN setFlags: ver el javadoc (con MOVE bloquearía a los oficios).
    }

    @Override
    public boolean canUse() {
        return !villager.isBaby() && VillageManager.elRecadoDeAhora(villager) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse() && !VillageManager.estaDescansando(villager);
    }

    @Override
    public void start() {
        ticksSinAvanzar = 0;
        empujonesDeEsteRecado = 0;
    }

    /** Cuántos empujones se han dado en toda la partida (para el registro). */
    public static int empujonesDados() {
        return empujonesDados;
    }

    @Override
    public void tick() {
        @Nullable
        BlockPos recado = VillageManager.elRecadoDeAhora(villager);
        if (recado == null) {
            return;
        }
        // 1) EL RUMBO, ESCRITO AQUÍ Y EN CADA TICK: es lo que le quita el mando al paseo del cerebro.
        VillageManager.escribirElRumboDelRecado(villager, recado);
        // 2) LA SUPERVISIÓN (M4): ¿está avanzando por su ruta? Si no, se le da un presupuesto y, agotado, se ABANDONA
        //    el recado (el punto queda aparcado y el oficio elegirá otro). Nunca se queda rondando.
        if (VillageManager.avanzaPorLaRuta(villager) || VillageManager.seHaMovidoHacePoco(villager, 40)) {
            // I192 · PROGRESO = nodos de ruta **O** haberse movido de verdad en los últimos 2 s (3-oct-2026, MEDIDO).
            // El índice de nodo puede subir con el aldeano plantado: **todos** los avisos que quedan traen `ANDADO`
            // entre 0,0 y 0,9 bloques con la ruta viva (`alcanza=SI`) y el despachador no lo veía — los empujones de
            // I190 saltaron **0 veces** en 20 minutos (corridas 72 y 76-78). Con esto el aldeano plantado sí empieza a
            // contar, entra el empujón (parar la navegación y volver a mandar el rumbo) y solo después se abandona.
            ticksSinAvanzar = 0;
            empujonesDeEsteRecado = 0;
        } else {
            ticksSinAvanzar++;
            // I190 · EL EMPUJÓN ANTES DE RENDIRSE (3-oct-2026, MEDIDO). El residuo que queda tiene **una firma común**:
            // el aldeano **no se mueve nada** (`ANDADO` 0,0-0,9 bloques) y **con la ruta llegando** (`alcanza=SI`).
            // Medido en el lote largo y en las corridas 64-67: `Isidoro / Yendo a la taberna` a un bloque de su casilla
            // de pie, los guardias atrapados en el patio de tiro (`519, 79, 594`), el ganadero a 0,8 bloques de su
            // sitio... y en todos, el recado se **abandonaba** sin haber intentado nada: parar la navegación y volver a
            // mandar el rumbo. Es el mismo remedio que el guardia ya usa en su ronda («se reafirma el destino y se
            // sigue», I125). **No se rinde: se le vuelve a empujar.**
            if (ticksSinAvanzar % EMPUJON_CADA == 0 && empujonesDeEsteRecado < EMPUJONES_POR_RECADO) {
                villager.getNavigation().stop();
                VillageManager.escribirElRumboDelRecado(villager, recado);
                empujonesDeEsteRecado++;
                empujonesDados++;
                DevilRpg.LOGGER.info("[Village] empujon {}/{} al recado de {} ({}): {} ticks sin avanzar con la ruta "
                        + "viva; se le para la navegacion y se le vuelve a mandar", empujonesDeEsteRecado,
                        EMPUJONES_POR_RECADO, villager.getUUID(), recado.toShortString(), ticksSinAvanzar);
            }
            if (ticksSinAvanzar > VillageManager.RECADO_PRESUPUESTO) {
                VillageManager.abandonarElRecado(villager);
                ticksSinAvanzar = 0;
                empujonesDeEsteRecado = 0;
            }
        }
    }
}
