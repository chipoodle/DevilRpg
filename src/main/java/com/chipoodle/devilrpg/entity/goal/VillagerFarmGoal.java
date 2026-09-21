package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.world.VillageGenerator;
import com.chipoodle.devilrpg.world.VillageManager;
import com.chipoodle.devilrpg.world.VillagePantry;
import com.chipoodle.devilrpg.world.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Goal del <b>granjero</b>: la cadena de suministro de verdad de la aldea.
 * <ol>
 *   <li><b>Cosecha</b> los cultivos maduros de las parcelas de la aldea (y los <b>replanta</b>).</li>
 *   <li><b>Planta</b> en la tierra de cultivo vacía (semillas de su inventario o de la despensa).</li>
 *   <li><b>Abona TODO el plantío</b> con <b>harina de huesos</b>: no una planta por salida, sino las que le quepan en
 *       la tanda (16 por viaje), repartiéndolas por la parcela y sin repetir en las que ya fue.</li>
 *   <li><b>Llena el compostero</b> con las semillas que le <b>sobran</b> (trigo y betabel): es de donde sale la harina
 *       de huesos del paso 3.</li>
 *   <li><b>Lleva el trigo a la despensa</b> y allí lo convierte en <b>pan</b> (3 de trigo por hogaza), vacía el
 *       compostero ya lleno y se trae los recambios (semillas y abono).</li>
 * </ol>
 * Con esto la comida de la aldea ya no es un contador abstracto: sale del trigo que este aldeano cultiva de verdad
 * (ver {@link VillagePantry}).
 */
public class VillagerFarmGoal extends Goal {

    /** Distancia a la que ya se considera que llega al cultivo. */
    private static final double REACH = 3.0D;
    /** Ticks de faena por acción (medio segundo): se le ve dar el golpe sin eternizarse. */
    private static final int WORK_TICKS = 10;
    /** Descanso entre acción y acción. */
    private static final int REST_TICKS = 15;
    /** Descanso cuando no hay nada que hacer (4 s). Buscar cultivos recorre las parcelas, no se hace cada tick. */
    private static final int IDLE_REST_TICKS = 80;
    /** Si no logra acercarse en este tiempo, abandona el objetivo. */
    private static final int STUCK_LIMIT = 120;
    /** Si se aleja más de esto del centro, deja de trabajar (derivado del radio de la aldea). */
    private static final double MAX_DISTANCE_FROM_CENTER = VillageGenerator.FENCE_RADIUS + 12.0D;
    /**
     * Unidades (trigo + vegetales) que lleva encima antes de ir a la despensa: cada 8 cosechas baja a guardarlo y
     * hornear.
     * <p>
     * Eran <b>4</b>, y con la despensa en la taberna (a 40-55 bloques de los bancales) eso es un paseo de ida y vuelta
     * por cada 4 puntos de comida: con 9-12 bocas comiendo 1 punto por minuto, la aldea vivía al filo (medido en el
     * guardado del jugador: ratos de "comida 0 puntos, 0 raciones"). El jugador pidió subirlo ("los granjeros deben
     * cosechar más rápido o mayor cantidad"): con 8 se entrega el doble por viaje y sigue bajando a menudo (la parcela
     * tiene 72 celdas de cultivo). El límite no es el hueco —la mochila del aldeano aguanta 64 por hueco—, era solo
     * una decisión de ritmo.
     */
    private static final int LLEVAR_TRIGO = 8;
    /** Semillas que se guarda como mucho: si lleva más, las suelta (si no, se le llena el inventario y no le cabe el trigo). */
    private static final int SEMILLAS_MAX = 8;
    /**
     * Semillas de SOBRA que guarda para el <b>compostero</b> (además de las que necesita para sembrar). Antes las
     * tiraba al suelo en cuanto pasaba de {@link #SEMILLAS_MAX}: el compostero NUNCA se llenaba (nadie le echaba
     * nada), así que no había harina de huesos y el abono se quedaba sin hacer.
     */
    private static final int SEMILLAS_PARA_COMPOSTAR = 16;
    /** Semillas que echa al compostero por visita (no se queda plantado allí). */
    private static final int COMPOSTAR_MAX = 16;
    /** Si la despensa tiene MÁS semillas que esto, se lleva unas cuantas para el compostero. */
    private static final int SEMILLAS_SOBRANTES_EN_DESPENSA = 32;
    /**
     * Hogazas como mucho por visita (para que se le vea trabajar). Con el lote de 8 unidades que ahora se lleva,
     * hornear 2 hogazas (6 de trigo) deja el viaje bien aprovechado: el pan vale 4 puntos y el trigo suelto 1.
     */
    private static final int HORNEAR_MAX = 2;
    /**
     * Harina de huesos que se lleva encima como mucho. Antes 4: con eso abonaba UNA planta por visita (lo pidió el
     * jugador: "que abone todo el plantío, no nada más una planta"), así que ahora carga una tanda de 16 y las gasta
     * seguidas por toda la parcela.
     */
    private static final int HARINA_MAX = 16;
    /** Plantas que abona como mucho en una misma salida (para no echar la tarde abonando sin llevar nada al cofre). */
    private static final int ABONAR_MAX = 32;
    /** Cuánto puede traerse del almacén a la despensa en una visita (comida, semillas y abono del recolector). */
    private static final int TRAER_DEL_ALMACEN = 64;
    /**
     * Con la despensa por debajo de estos puntos de comida y comida esperando en el <b>almacén</b>, el granjero deja
     * lo que esté haciendo y va a por ella.
     * <p>
     * Hace falta porque el <b>ganadero</b> (y el recolector) dejan la carne en el <b>almacén</b> y el contador de
     * comida de la aldea —y las raciones— miran <b>la despensa</b>: sin este viaje, la carne del corral se quedaba
     * muerta de risa en el almacén y la aldea <b>seguía pasando hambre con el almacén lleno</b>, que es justo lo que
     * reportó el jugador ("el que cuida los animales no produce carne aún": la producía, pero no llegaba al pueblo).
     */
    private static final int DESPENSA_VACIA = 8;

    private enum Tarea { COSECHAR, LABRAR, PLANTAR, FERTILIZAR, COMPOSTAR, DESPENSA, SALIR }

    private final Villager villager;
    private final BlockPos center;
    private final int objectiveIndex;
    @Nullable
    private BlockPos target;
    private Tarea tarea = Tarea.COSECHAR;
    /**
     * Plantas que YA ha abonado en esta salida: así la harina de huesos se reparte por TODA la parcela en vez de
     * gastarse entera en la primera planta que encuentra (que es lo que pasaba al no recordar por dónde iba).
     */
    private final Set<Long> abonadas = new HashSet<>();
    private int workTicks;
    private int restTicks;
    /** Ticks SIN ACERCARSE al objetivo (ver {@code tick}): andar hacia él no cuenta como estar atascado. */
    private int stuckTicks;
    /** Distancia más corta lograda en este viaje: mientras baje, el granjero está avanzando. */
    private double mejorDistancia = Double.MAX_VALUE;
    /** Turno del granjero cuando hay calva Y cultivo maduro: alterna una labrada y una cosecha (ver {@code canUse}). */
    private boolean turnoDeLabrar;
    /**
     * <b>El bancal de ESTE granjero</b> (índice en {@code FARM_PLOTS}), o {@code -1} si todavía no se ha calculado en
     * esta salida. Sale de su <b>puesto de trabajo</b> (el compostero de su bancal: ver {@link #miParcela}).
     */
    private int miParcela = -1;
    /** En qué bancal está <b>lo que va a hacer ahora</b> (lo pone {@link #buscarEnLasParcelas}), o {@code -1}. */
    private int parcelaDelObjetivo = -1;
    /** Lo más cerca que ha estado de la <b>puerta</b> por la que entra al bancal, aparte del objetivo (I38). */
    private double mejorDistanciaEntrada = Double.MAX_VALUE;
    /** Ticks sin acercarse a esa puerta. */
    private int stuckEntrada;

    public VillagerFarmGoal(Villager villager, BlockPos center, int objectiveIndex) {
        this.villager = villager;
        this.center = center;
        this.objectiveIndex = objectiveIndex;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (restTicks > 0) {
            restTicks--;
            return false;
        }
        if (villager.isBaby() || !(villager.level() instanceof ServerLevel level)) {
            return false;
        }
        if (villager.getVillagerData().getProfession() != net.minecraft.world.entity.npc.VillagerProfession.FARMER) {
            return false;
        }
        // En plena refriega nadie se pone a sembrar.
        if (VillageManager.isVillageUnderAttack(level, objectiveIndex)) {
            return false;
        }
        // EL QUE SE QUEDA ENCERRADO AL ANOCHECER CON SU CAMA (O SU NOCHE) FUERA DEL BANCAL: se le manda a la
        // COMPUERTA. El juego NO deja que un aldeano abra una puerta de valla —por eso el pueblo tiene su
        // `VillageGateGoal`, que se la abre al tenerlo al lado—, así que desde dentro del bancal el aldeano no puede
        // PLANIFICAR la salida: su ruta a la cama se corta en la valla. Medido con el arnés: la ruta de Isidoro
        // (bancal 2) a su cama acababa en la propia compuerta, `alcance=NO`, y con TODAS las camas libres de la
        // aldea igual. Se quedaba de pie en la huerta toda la noche (lo que vio el jugador: "Sin cama" con la aldea
        // llena de camas). Y hay algo peor, medido a resolución de tick: vanilla, cuando un aldeano lleva 1200 ticks
        // (60 s) sin poder llegar a su cama, se la BORRA (`SetWalkTargetFromBlockMemory`: `releasePoi` + `erase`
        // cuando `CANT_REACH_WALK_TARGET_SINCE` pasa del minuto), así que el latido se la daba, él no llegaba y a los
        // 60 s se la volvían a borrar: un bucle sin salida. Sacándolo a la compuerta se rompe, porque al pisarla el
        // portón se abre y su cama vuelve a estar a su alcance.
        int donde = parcelaDondeEsta(level);
        if (donde >= 0 && VillageManager.estaDescansando(villager)) {
            var cama = villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME);
            boolean camaFuera = cama.isEmpty() || !VillageGenerator.estaDentroDeLaParcela(center, donde,
                    cama.get().pos().getY(), cama.get().pos());
            if (camaFuera) {
                tarea = Tarea.SALIR;
                parcelaDelObjetivo = donde;
                target = VillageGenerator.salidaDeLaParcela(center, donde,
                        VillageGenerator.cotaDeLaPlaza(level, center), villager.blockPosition());
                return target != null;
            }
        }
        // Ni en su hora de descanso: el granjero también se va a la cama.
        if (VillageManager.estaDescansando(villager)) {
            return false;
        }
        // Distancia HORIZONTAL al centro: la Y del centro puede ser la del spawn del jugador y no debe contar (la
        // aldea es un recinto en el plano XZ).
        double dxCentro = villager.getX() - center.getX();
        double dzCentro = villager.getZ() - center.getZ();
        if (dxCentro * dxCentro + dzCentro * dzCentro > MAX_DISTANCE_FROM_CENTER * MAX_DISTANCE_FROM_CENTER) {
            return false;
        }
        Container despensa = VillagePantry.despensa(level, center);
        // 1) Con trigo o vegetales suficientes encima, A LA DESPENSA (aunque queden cultivos maduros): si el depósito
        // se deja para el final, en una parcela grande SIEMPRE hay algo maduro y el granjero se pasa la vida
        // cosechando sin llevar NADA al cofre. Se va cada 4 unidades entre trigo y vegetales.
        if (trigoEnMano() + vegetalesEnMano() >= LLEVAR_TRIGO && despensa != null) {
            tarea = Tarea.DESPENSA;
            target = VillagePantry.puntoDeApoyo(level, center);
            return true;
        }
        // 2) LA COMIDA ESTÁ EN EL ALMACÉN Y LA DESPENSA VACÍA: a por ella. El ganadero sube la carne del corral y el
        //    recolector barre lo que cae por el pueblo, y todo eso va al ALMACÉN; pero el contador de comida (y las
        //    raciones de los aldeanos) miran LA DESPENSA. Sin este viaje la carne se quedaba en el almacén y el
        //    pueblo seguía hambriento con el almacén lleno (lo reportó el jugador: "no produce carne aún").
        if (despensa != null && VillagePantry.comida(level, center) < DESPENSA_VACIA
                && hayComidaEnElAlmacen(level)) {
            tarea = Tarea.DESPENSA;
            target = VillagePantry.puntoDeApoyo(level, center);
            return true;
        }
        // 3) Cultivo maduro: a cosecharlo. Y 3b) la CALVA del bancal (una celda pisoteada que tiene que volver a ser
        //    tierra de cultivo: vanilla convierte la tierra de cultivo en tierra al saltar encima y, pegada al césped,
        //    la tierra vuelve a ser césped). El granjero la VUELVE A LABRAR antes de sembrar: el que siembra es él, así
        //    que es él quien tiene que dejar la parcela cultivable.
        //    OJO CON EL ORDEN (lo reportó el jugador: "los granjeros deberían poder reponer su tierra de cultivo
        //    cuando esta se estropea"): labrar iba SIEMPRE detrás de cosechar, y con TRES bancales siempre hay algo
        //    maduro en alguno, así que el paso de labrar no se alcanzaba NUNCA y las calvas se quedaban en tierra para
        //    siempre. Ahora, cuando hay calva Y cultivo maduro, el granjero ALTERNA una cosecha y una labrada: la
        //    parcela se repara al momento y la cosecha no se para.
        BlockPos maduro = buscarCultivo(level, true);
        BlockPos calva = buscarCalva(level);
        turnoDeLabrar = !turnoDeLabrar;
        if (calva != null && (turnoDeLabrar || maduro == null)) {
            target = calva;
            tarea = Tarea.LABRAR;
            return true;
        }
        if (maduro != null) {
            target = maduro;
            tarea = Tarea.COSECHAR;
            return true;
        }
        // 3) Tierra de cultivo vacía: a plantar. SOLO si lleva semillas EN LA MANO: `plantar()` las saca de su
        // inventario, así que mandarlo a sembrar "porque en la despensa hay semillas" no hacía nada y lo dejaba en
        // bucle igual que el paso 5 (si le faltan, el paso 5 lo manda a la despensa a por ellas).
        if (tieneSemillas()) {
            target = buscarTierraVacia(level);
            if (target != null) {
                tarea = Tarea.PLANTAR;
                return true;
            }
        }
        // 4) Cultivo creciendo: a fertilizar. SOLO si lleva harina de huesos encima, por el mismo motivo (si no la
        // tiene, se la trae de la despensa en el paso 6). Se saltan los que YA abonó en esta salida: así el abono
        // se reparte por toda la parcela (ver `abonadas`).
        if (harinaEnMano() > 0 && abonadas.size() < ABONAR_MAX) {
            target = buscarCultivoSinAbonar(level);
            if (target != null) {
                tarea = Tarea.FERTILIZAR;
                return true;
            }
        }
        // 5) COMPOSTERO: las semillas que le SOBRAN (más de las que necesita para sembrar) van al compostero, que es
        // lo que produce la harina de huesos con la que abona. Va DESPUÉS de sembrar y abonar (primero lo urgente) y
        // sin él la harina se acababa y el abono se quedaba sin hacer, porque nadie llenaba nunca el compostero.
        // OJO: se cuentan solo las COMPOSTABLES (trigo y betabel). Contando también la zanahoria y la patata, un
        // granjero cargado de vegetales se pasaría el día yendo al compostero a no echar nada (bucle).
        if (semillasCompostablesSobrantes() > 0) {
            target = buscarCompostero(level);
            if (target != null) {
                tarea = Tarea.COMPOSTAR;
                return true;
            }
        }
        // 6) Recambios: a la despensa, pero SOLO si allí está lo que le falta. Antes bastaba con que le faltara algo
        // en la mano, así que con la despensa sin harina de huesos (lo normal hasta que el compostero se llena) el
        // granjero iba al kiosco, no hacía nada, volvía a elegir la misma tarea y se quedaba PLANTADO allí en bucle,
        // con la etiqueta "Llevando la cosecha" y sin llevar nada encima (medido en el guardado del jugador:
        // inventario con 5 semillas de trigo, 3 panes y 14 de betabel, y NINGUNA cosecha).
        boolean haySemillas = VillagePantry.contar(despensa, VillagerFarmGoal::esSemilla) > 0;
        boolean hayAbono = VillagePantry.contar(despensa, s -> s.is(Items.BONE_MEAL)) > 0;
        if ((!tieneSemillas() && haySemillas) || (harinaEnMano() == 0 && hayAbono)) {
            tarea = Tarea.DESPENSA;
            target = VillagePantry.puntoDeApoyo(level, center);
            return true;
        }
        // Nada que hacer (ni cultivo maduro, ni tierra libre, ni abono, ni recambios): a esperar. El cerebro del
        // aldeano lo tiene paseando mientras, que es lo que hace un aldeano sin tarea.
        restTicks = IDLE_REST_TICKS;
        return false;
    }

    @Override
    public void start() {
        workTicks = 0;
        stuckTicks = 0;
        mejorDistancia = Double.MAX_VALUE;
        abonadas.clear();
        miParcela = -1; // se vuelve a mirar cuál es su bancal (su puesto puede haber cambiado)
        parcelaDelObjetivo = -1;
        mejorDistanciaEntrada = Double.MAX_VALUE;
        stuckEntrada = 0;
        irAlObjetivo();
    }

    @Override
    public boolean canContinueToUse() {
        if (tarea == Tarea.SALIR) {
            // La pierna de SALIR dura hasta que PISA FUERA del bancal, y a propósito NO se corta por descansar: es
            // justo lo que está haciendo, irse a dormir. Si se atasca en la compuerta, el tope de siempre (I33).
            if (!(villager.level() instanceof ServerLevel level)) {
                return false;
            }
            return target != null && !villager.isBaby() && stuckTicks < STUCK_LIMIT
                    && parcelaDondeEsta(level) == parcelaDelObjetivo;
        }
        if (target != null && stuckTicks >= STUCK_LIMIT) {
            // RENDIRSE = DEJARLO POR UN RATO (I33): el sitio al que no llegó (la mata, la calva, el compostero o la
            // despensa) se apunta para no volver a elegir EL MISMO en bucle, que es lo que dejaba al granjero
            // empujando el mismo obstáculo para siempre.
            VillageManager.marcarPuntoFallido(villager, target);
            return false;
        }
        return target != null && !villager.isBaby() && stuckTicks < STUCK_LIMIT
                && !VillageManager.estaDescansando(villager);
    }

    /** El índice del bancal en el que está <b>metido</b> el granjero ahora mismo, o {@code -1} si está fuera de todos. */
    private int parcelaDondeEsta(ServerLevel level) {
        int cota = VillageGenerator.cotaDeLaPlaza(level, center);
        for (int i = 0; i < VillageGenerator.parcelasDeGranja(); i++) {
            if (VillageGenerator.estaDentroDeLaParcela(center, i, cota, villager.blockPosition())) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void tick() {
        if (target == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        villager.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
        // LA SALIDA DEL BANCAL (la pierna espejo de la de entrar): a la compuerta, y en cuanto la pisa el portón se
        // abre (`VillageGateGoal`, a 2,6). Cuando está fuera, el goal se corta (`canContinueToUse`) y el cerebro de
        // vanilla —con su cama ya alcanzable— se encarga de llevarlo a dormir.
        if (tarea == Tarea.SALIR) {
            if (parcelaDondeEsta(level) != parcelaDelObjetivo) {
                return; // ya está fuera del bancal: a dormir (lo lleva el cerebro)
            }
            VillageManager.caminarHacia(villager, target, 0.6F);
            VillageManager.ponerActividad(villager, "Saliendo de la huerta");
            // La pierna de la compuerta se mide aparte de la del objetivo (I38: dos piernas, dos contadores).
            double hastaLaPuerta = Math.sqrt(villager.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D,
                    target.getZ() + 0.5D));
            if (hastaLaPuerta < mejorDistanciaEntrada - 0.5D) {
                mejorDistanciaEntrada = hastaLaPuerta;
                stuckTicks = 0;
            } else {
                stuckTicks++;
            }
            return;
        }
        // LAS FAENAS DE LA HUERTA SE HACEN DENTRO DEL BANCAL. El alcance de la faena son 3 bloques, así que un
        // granjero parado FUERA de la valla alcanzaba las matas de la primera fila y las cosechaba A TRAVÉS de la reja:
        // no le hacía falta entrar (lo reportó el jugador: "los granjeros no están entrando a la granja") y las matas
        // del centro se quedaban sin cosechar. Si el objetivo está en un bancal y él está fuera, se le manda a la
        // PUERTA más cercana: al ponerse a su lado, `VillagerGateGoal` se la abre (a 2,6) y entra.
        boolean faenaDeHuerta = tarea == Tarea.COSECHAR || tarea == Tarea.LABRAR
                || tarea == Tarea.PLANTAR || tarea == Tarea.FERTILIZAR;
        if (faenaDeHuerta && parcelaDelObjetivo >= 0
                && !VillageGenerator.estaDentroDeLaParcela(center, parcelaDelObjetivo, target.getY(),
                villager.blockPosition())) {
            BlockPos entrada = VillageGenerator.entradaDeLaParcela(center, parcelaDelObjetivo, target.getY(),
                    villager.blockPosition());
            VillageManager.caminarHacia(villager, entrada, 0.6F);
            VillageManager.ponerActividad(villager, "Entrando a la huerta");
            // La pierna de la PUERTA se mide aparte de la del objetivo (I38: dos piernas, dos contadores).
            double hastaLaPuerta = Math.sqrt(villager.distanceToSqr(entrada.getX() + 0.5D, entrada.getY() + 0.5D,
                    entrada.getZ() + 0.5D));
            if (hastaLaPuerta < mejorDistanciaEntrada - 0.5D) {
                mejorDistanciaEntrada = hastaLaPuerta;
                stuckEntrada = 0;
            } else if (++stuckEntrada >= STUCK_LIMIT) {
                // No consigue entrar (una puerta tapada, la valla rota...): se rinde con este objetivo (I33) y el
                // latido/la próxima salida lo volverá a intentar cuando el mundo cambie.
                DevilRpg.LOGGER.info("[Village] El granjero no consigue entrar al bancal {} (puerta {}): lo deja por"
                        + " un rato", parcelaDelObjetivo, entrada.toShortString());
                stuckTicks = STUCK_LIMIT;
            }
            return;
        }
        // Para la despensa vale un alcance mayor (el cofre está dentro del kiosco y no se navega hacia él).
        double alcance = tarea == Tarea.DESPENSA ? VillagePantry.ALCANCE_DESPENSA : REACH;
        double distancia = Math.sqrt(villager.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D));
        if (distancia > alcance) {
            // El rumbo se le da POR EL CEREBRO en cada tick (ver VillageManager.caminarHacia): navegando a mano, el
            // cerebro del aldeano lo manda a su puesto, a la plaza o a pasear y se va a otro lado a mitad de camino
            // ("primero da vueltas y se va a otro lado antes de recogerlos").
            VillageManager.caminarHacia(villager, target, 0.6F);
            // ATASCADO = NO ACERCARSE, no "estar andando": contar cada tick mandaba al granjero a empezar de cero cada
            // 6 s (120 ticks) aunque fuera avanzando, así que un viaje a la despensa no lo terminaba NUNCA y se quedaba
            // ciclado ("no sube al kiosco a poner la cosecha").
            if (distancia < mejorDistancia - 0.5D) {
                mejorDistancia = distancia;
                stuckTicks = 0;
            } else {
                stuckTicks++;
            }
            return;
        }
        VillageManager.parar(villager);
        villager.swing(InteractionHand.MAIN_HAND);
        if (++workTicks < WORK_TICKS) {
            return;
        }
        workTicks = 0;
        switch (tarea) {
            case COSECHAR -> {
                VillageManager.ponerActividad(villager, "Cosechando");
                cosechar(level);
            }
            case LABRAR -> {
                VillageManager.ponerActividad(villager, "Labrando la huerta");
                labrar(level);
            }
            case PLANTAR -> {
                VillageManager.ponerActividad(villager, "Sembrando");
                plantar(level);
            }
            case FERTILIZAR -> {
                VillageManager.ponerActividad(villager, "Abonando");
                fertilizar(level);
                // ABONAR TODO EL PLANTÍO, no una sola planta (lo pidió el jugador): si le queda harina y hay otro
                // cultivo creciendo al que no haya ido todavía, SIGUE con él en la misma salida (el goal no termina).
                // Antes el goal acababa tras una planta y volvía a elegir tarea, así que el abono se gastaba de uno
                // en uno y la parcela no se abonaba nunca.
                abonadas.add(target.asLong());
                if (harinaEnMano() > 0 && abonadas.size() < ABONAR_MAX) {
                    BlockPos siguiente = buscarCultivoSinAbonar(level);
                    if (siguiente != null) {
                        target = siguiente;
                        mejorDistancia = Double.MAX_VALUE;
                        stuckTicks = 0;
                        return; // el goal sigue vivo con el siguiente cultivo
                    }
                }
            }
            case COMPOSTAR -> {
                VillageManager.ponerActividad(villager, "Llenando el compostero");
                compostar(level);
            }
            case DESPENSA -> {
                // La etiqueta dice lo que de verdad va a hacer: si lleva cosecha encima, la lleva; si no, va a por
                // recambios. Antes decía siempre "Llevando la cosecha" aunque no llevara nada (el jugador lo veía
                // plantado en el kiosco con esa etiqueta y sin poner nada en el cofre).
                VillageManager.ponerActividad(villager,
                        trigoEnMano() + vegetalesEnMano() > 0 ? "Llevando la cosecha" : "Buscando recambios");
                enLaDespensa(level);
            }
        }
        target = null;
        restTicks = REST_TICKS;
    }

    @Override
    public void stop() {
        target = null;
        restTicks = REST_TICKS;
        abonadas.clear(); // la próxima salida vuelve a poder abonar desde el principio
        villager.getNavigation().stop();
    }

    // --- las faenas -------------------------------------------------------------------------------

    private void cosechar(ServerLevel level) {
        BlockState state = level.getBlockState(target);
        // OJO: la edad se lee con la propiedad del PROPIO cultivo (el betabel es 0-3 y el trigo 0-7).
        if (!(state.getBlock() instanceof CropBlock crop)
                || VillageGenerator.edadDelCultivo(state) != crop.getMaxAge()) {
            return;
        }
        List<ItemStack> drops = Block.getDrops(state, level, target, null);
        level.destroyBlock(target, false);
        level.setBlock(target, crop.getStateForAge(0), Block.UPDATE_ALL); // replantado en el sitio
        for (ItemStack drop : drops) {
            // Las SEMILLAS solo hasta un tope: si se le llenan los 8 huecos con semillas, el trigo ya no le cabe, se le
            // cae al suelo y nunca acumula las 3 unidades que disparan el viaje a la despensa (por eso el cofre seguía
            // con las 12 semillas iniciales y la aldea pasaba hambre). El tope incluye las que guarda para el
            // COMPOSTERO (`SEMILLAS_PARA_COMPOSTAR`): antes las soltaba al suelo y el compostero seguía vacío.
            if (esSemilla(drop) && semillasEnMano() >= SEMILLAS_MAX + SEMILLAS_PARA_COMPOSTAR) {
                level.addFreshEntity(new ItemEntity(level, target.getX() + 0.5D, target.getY() + 0.5D,
                        target.getZ() + 0.5D, drop));
                continue;
            }
            ItemStack resto = guardarEnInventario(drop);
            if (!resto.isEmpty()) {
                level.addFreshEntity(new ItemEntity(level, target.getX() + 0.5D, target.getY() + 0.5D,
                        target.getZ() + 0.5D, resto));
            }
        }
        level.playSound(null, target, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.7F, 1.0F);
    }

    private void plantar(ServerLevel level) {
        ItemStack semilla = sacarSemilla();
        if (semilla.isEmpty()) {
            return;
        }
        BlockState cultivo = cultivoDe(semilla);
        if (cultivo == null) {
            return;
        }
        level.setBlock(target, cultivo, Block.UPDATE_ALL);
        level.playSound(null, target, cultivo.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.7F, 1.0F);
    }

    /**
     * Vuelve a <b>labrar</b> la calva en la que está parado: la tierra (o el césped) de una celda del bancal pasa a
     * ser <b>tierra de cultivo</b>. Se pone <b>regada</b> como la pondría el juego ({@code tierraDeCultivo} mira el
     * agua de al lado), no seca.
     * <p>
     * El objetivo es la casilla de <b>aire</b> de encima (la capa por la que se anda, igual que al sembrar: navegar
     * hacia un bloque del suelo no da camino), así que el bloque que se labra es el de <b>debajo</b>.
     */
    private void labrar(ServerLevel level) {
        BlockPos tierra = target.below();
        if (!VillageGenerator.esCalvaDeBancal(level, tierra)) {
            return; // se adelantó otro aldeano, o el jugador ya puso algo: no se toca
        }
        level.setBlock(tierra, VillageGenerator.tierraDeCultivo(level, tierra), Block.UPDATE_ALL);
        level.playSound(null, tierra, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 0.7F, 1.0F);
        VillageManager.ponerSuceso(villager, "Labro la huerta");
        DevilRpg.LOGGER.info("[Village] El granjero: Labro la huerta en {}", tierra);
    }

    private void fertilizar(ServerLevel level) {
        ItemStack harina = sacarHarina();
        if (harina.isEmpty()) {
            return;
        }
        if (net.minecraft.world.item.BoneMealItem.growCrop(harina, level, target)) {
            level.playSound(null, target, net.minecraft.sounds.SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 0.8F, 1.0F);
            VillageManager.ponerSuceso(villager, "Abono la huerta");
        }
    }

    /** En la despensa: guarda el trigo, hornea pan, coge semillas y harina de huesos, y vacía el compostero. */
    private void enLaDespensa(ServerLevel level) {
        Container despensa = VillagePantry.despensa(level, center);
        if (despensa == null) {
            DevilRpg.LOGGER.warn("[Village] El granjero llego al kiosco y NO encontro la despensa (aldea en {})", center);
            return;
        }
        int guardados = 0;
        // 1) Compostero lleno -> harina de huesos para la despensa (el abono de la aldea lo produce ella misma).
        //    OJO con la celda (el mismo desvío que en `buscarCompostero`): el compostero va a
        //    `composteroDeLaParcela` (una celda MÁS AFUERA que la valla desde la migración 64); aquí se buscaba en
        //    `p.offset(-1, 0, 0)`, que es la columna de la valla, así que el compostero lleno NO SE VACIABA NUNCA y
        //    la harina de huesos no llegaba a la despensa.
        int cota = VillageGenerator.cotaDeLaPlaza(level, center);
        for (int i = 0; i < VillageGenerator.parcelasDeGranja(); i++) {
            BlockPos comp = VillageGenerator.composteroDeLaParcela(center, i, cota);
            for (int dy = -2; dy <= 2; dy++) {
                BlockPos q = comp.offset(0, dy, 0);
                BlockState s = level.getBlockState(q);
                if (s.is(Blocks.COMPOSTER) && s.getValue(ComposterBlock.LEVEL) == 8) {
                    level.setBlock(q, s.setValue(ComposterBlock.LEVEL, 0), Block.UPDATE_ALL);
                    VillagePantry.guardar(despensa, new ItemStack(Items.BONE_MEAL));
                }
            }
        }
        // 2) TODO lo comestible que lleve encima, a la despensa: el trigo (para el pan) y los VEGETALES (zanahoria,
        // patata y betabel). Antes solo se guardaba el TRIGO, así que lo demás se quedaba en su inventario o se caía al
        // suelo: el contador de comida de la aldea mira LO QUE HAY EN LA DESPENSA, no lo plantado, así que la aldea
        // pasaba hambre con la huerta llena (y moría gente teniendo comida).
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.is(Items.WHEAT) || VillagePantry.esVegetal(s)) {
                int antes = s.getCount();
                ItemStack resto = VillagePantry.guardar(despensa, s.copy());
                guardados += antes - resto.getCount();
                villager.getInventory().setItem(i, resto);
            }
        }
        // 3) LO DEL ALMACÉN, A LA DESPENSA: el recolector (holgazán) recoge del suelo lo que se cae por el pueblo
        // —incluido lo que deja caer el propio juego cuando SU aldeano granjero cosecha, que tira el grano al
        // suelo— y lo guarda en el almacén. Como el contador de comida de la aldea mira LA DESPENSA, esa comida se
        // quedaba muerta de risa en el almacén y la aldea pasaba hambre con el almacén lleno. El granjero hace de
        // puente en cada visita: se trae la comida, las semillas y el abono que el recolector haya guardado.
        int traidos = VillagePantry.traspasar(VillageStorage.almacen(level, center), despensa,
                s -> s.is(Items.WHEAT) || s.is(Items.BREAD) || s.is(Items.WHEAT_SEEDS) || s.is(Items.BEETROOT_SEEDS)
                        || s.is(Items.BONE_MEAL) || VillagePantry.esVegetal(s)
                        || VillagePantry.esCarneCruda(s) || VillagePantry.esCarneCocida(s),
                TRAER_DEL_ALMACEN);
        // 4) Hornear: 3 de trigo por hogaza (la receta de vanilla), como mucho HORNEAR_MAX por visita.
        int horneadas = 0;
        while (horneadas < HORNEAR_MAX
                && VillagePantry.sacar(despensa, s -> s.is(Items.WHEAT), VillagePantry.WHEAT_PER_BREAD)
                    == VillagePantry.WHEAT_PER_BREAD) {
            VillagePantry.guardar(despensa, new ItemStack(Items.BREAD));
            horneadas++;
            level.playSound(null, target, net.minecraft.sounds.SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.2F);
        }
        // 5) Recambios: semillas y harina de huesos, si le faltan.
        if (!tieneSemillas()) {
            for (ItemStack plantable : List.of(new ItemStack(Items.WHEAT_SEEDS, 4), new ItemStack(Items.CARROT, 3),
                    new ItemStack(Items.POTATO, 3), new ItemStack(Items.BEETROOT_SEEDS, 3))) {
                if (VillagePantry.sacar(despensa, s -> ItemStack.isSameItem(s, plantable), plantable.getCount())
                        == plantable.getCount()) {
                    guardarEnInventario(plantable);
                    break;
                }
            }
        }
        if (harinaEnMano() == 0) {
            int coge = VillagePantry.sacar(despensa, s -> s.is(Items.BONE_MEAL), HARINA_MAX);
            if (coge > 0) {
                guardarEnInventario(new ItemStack(Items.BONE_MEAL, coge));
            }
        }
        // 6) SEMILLAS DE SOBRA PARA EL COMPOSTERO: si la despensa va llena de semillas (el recolector barre las que
        //    se caen por el pueblo) se lleva unas cuantas y las composta, que es de donde sale la harina de huesos con
        //    la que abona. Solo cuando él no tiene ya de sobra, para no llenarse los huecos de semillas.
        if (semillasSobrantes() == 0
                && VillagePantry.contar(despensa, VillagerFarmGoal::esSemilla) > SEMILLAS_SOBRANTES_EN_DESPENSA) {
            for (net.minecraft.world.item.Item semilla : List.of(Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS)) {
                int cogidas = VillagePantry.sacar(despensa, s -> s.is(semilla), COMPOSTAR_MAX);
                if (cogidas > 0) {
                    guardarEnInventario(new ItemStack(semilla, cogidas));
                    break;
                }
            }
        }
        // LO QUE ACABA DE HACER, a la cabeza (y al log): es más informativo que el verbo de lo que está haciendo, y
        // es lo que el jugador necesita para saber si la cadena de comida funciona sin abrir el log.
        String suceso;
        if (horneadas > 0 && guardados > 0) {
            suceso = "Guardo " + guardados + " y horneo " + horneadas + " pan(es)";
        } else if (horneadas > 0) {
            suceso = "Horneo " + horneadas + " pan(es) en la despensa";
        } else if (guardados > 0) {
            suceso = "Guardo " + guardados + " en la despensa";
        } else if (traidos > 0) {
            suceso = "Trajo " + traidos + " del almacen a la despensa";
        } else {
            suceso = null; // no había nada que hacer: no se anuncia nada
        }
        if (suceso != null) {
            VillageManager.ponerSuceso(villager, suceso);
            DevilRpg.LOGGER.info("[Village] El granjero: {}", suceso);
        } else {
            DevilRpg.LOGGER.debug("[Village] El granjero visito la despensa (no habia nada que hacer)");
        }
    }

    // --- utilidades --------------------------------------------------------------------------------

    private void irAlObjetivo() {
        if (target != null) {
            VillageManager.caminarHacia(villager, target, 0.6F);
        }
    }

    /** Busca en las parcelas un cultivo maduro (o creciendo, si {@code maduro} es false). */
    @Nullable
    private BlockPos buscarCultivo(ServerLevel level, boolean maduro) {
        return buscarCultivo(level, maduro, Set.of());
    }

    /**
     * <b>El bancal de ESTE granjero.</b> Su <b>puesto de trabajo</b> es el compostero de un bancal (vanilla: la
     * estación del granjero es el compostero, y la aldea pone <b>uno por bancal</b>: ver
     * {@code VillageGenerator.composteroDeLaParcela} e I36), así que el puesto <b>dice cuál es su bancal</b>.
     * <p>
     * Es lo que hace que los tres granjeros <b>no se amontonen en el mismo huerto</b> (lo reportó el jugador: *"los
     * granjeros cosechan los 3 en un solo huerto, cuando lo ideal es que cosechen cada uno en el suyo"*). Antes nadie
     * miraba el puesto: los tres barrían la lista de bancales <b>en el mismo orden</b> y el primero con algo maduro se
     * llevaba a los tres.
     * <p>
     * Si no se le reconoce el puesto (una aldea a medio migrar, un granjero recién ascendido), se reparte por
     * <b>UUID</b>: es estable y reparte, que es lo que hace falta.
     */
    private int miParcela(ServerLevel level) {
        if (miParcela >= 0) {
            return miParcela;
        }
        int cota = VillageGenerator.cotaDeLaPlaza(level, center);
        Optional<GlobalPos> puesto = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (puesto.isPresent()) {
            BlockPos p = puesto.get().pos();
            for (int i = 0; i < VillageGenerator.parcelasDeGranja(); i++) {
                BlockPos comp = VillageGenerator.composteroDeLaParcela(center, i, cota);
                // Su compostero, esté ya en su sitio o todavía donde estaba antes de la migración 64 (una celda al
                // lado, mismo z): las dos cosas valen para saber de qué bancal es.
                if (p.getZ() == comp.getZ() && Math.abs(p.getX() - comp.getX()) <= 1
                        && Math.abs(p.getY() - cota) <= 2) {
                    miParcela = i;
                    return i;
                }
            }
        }
        miParcela = Math.floorMod(villager.getUUID().hashCode(), VillageGenerator.parcelasDeGranja());
        return miParcela;
    }

    /**
     * Los bancales <b>en el orden en que ESTE granjero los trabaja</b>: <b>el suyo primero</b> y después los demás
     * <b>por cercanía</b>, dejando para el final los que ya está trabajando <b>otro</b> granjero (así, si el suyo no
     * tiene nada que hacer, ayuda en otro en vez de pisarse con el compañero).
     */
    private List<Integer> parcelasEnOrden(ServerLevel level) {
        int mia = miParcela(level);
        List<Integer> libres = new ArrayList<>();
        List<Integer> ocupadas = new ArrayList<>();
        int cota = VillageGenerator.cotaDeLaPlaza(level, center);
        for (int i = 0; i < VillageGenerator.parcelasDeGranja(); i++) {
            if (i == mia) {
                continue;
            }
            if (otroGranjeroTrabajandoEn(level, i, cota)) {
                ocupadas.add(i);
            } else {
                libres.add(i);
            }
        }
        Comparator<Integer> porCercania = Comparator.comparingDouble(i -> {
            BlockPos e = VillageGenerator.esquinaDeLaParcela(center, i, cota);
            return villager.distanceToSqr(e.getX() + 0.5D, e.getY() + 0.5D, e.getZ() + 0.5D);
        });
        libres.sort(porCercania);
        ocupadas.sort(porCercania);
        List<Integer> orden = new ArrayList<>();
        orden.add(mia);
        orden.addAll(libres);
        orden.addAll(ocupadas);
        return orden;
    }

    /** ¿Hay <b>otro</b> granjero trabajando en ese bancal? (se mira si está dentro de su valla, o encima de ella) */
    private boolean otroGranjeroTrabajandoEn(ServerLevel level, int parcela, int cota) {
        BlockPos e = VillageGenerator.esquinaDeLaParcela(center, parcela, cota);
        AABB caja = new AABB(e.getX() - 2, e.getY() - 3, e.getZ() - 2,
                e.getX() + VillageGenerator.PLOT_WIDTH + 2, e.getY() + 4,
                e.getZ() + VillageGenerator.PLOT_DEPTH + 2);
        for (Villager otro : level.getEntitiesOfClass(Villager.class, caja)) {
            if (otro == villager || otro.isBaby()) {
                continue;
            }
            if (otro.getVillagerData().getProfession() == VillagerProfession.FARMER) {
                return true;
            }
        }
        return false;
    }

    /** Lo que se busca en cada columna de un bancal: la celda a la que ir, o {@code null} si ahí no hay nada. */
    @FunctionalInterface
    private interface Candidata {
        @Nullable
        BlockPos en(BlockPos parcela, int dx, int dz);
    }

    /**
     * <b>Busca en los bancales en el orden de este granjero</b> ({@link #parcelasEnOrden}) y, dentro de cada bancal,
     * <b>la celda MÁS CERCANA</b> que cumpla lo pedido.
     * <p>
     * Dos cosas que antes no se hacían y que son la mitad del arreglo:
     * <ul>
     *   <li><b>El bancal suyo manda</b>: si en el suyo hay faena, no se va a otro. Y solo mira los demás si el suyo no
     *       tiene nada (entonces ayuda, que es lo que pidió el jugador).</li>
     *   <li><b>La más cercana, no la primera de la lista</b>: la búsqueda recorría el bancal en orden fijo (dx, dz) y
     *       devolvía la primera mata, así que el granjero cruzaba el huerto entero para coger una del rincón y dejaba
     *       sin cosechar las de al lado (el jugador: *"para cosechar está poco optimizado... dejan sin cosechar unos y
     *       dejan otros cosechando"*). Yendo a la de al lado, el bancal se limpia de dentro hacia fuera.</li>
     * </ul>
     * Las celdas <b>aparcadas</b> (I33) se siguen saltando.
     */
    @Nullable
    private BlockPos buscarEnLasParcelas(ServerLevel level, Candidata candidata) {
        int cota = VillageGenerator.cotaDeLaPlaza(level, center);
        for (int i : parcelasEnOrden(level)) {
            BlockPos parcela = VillageGenerator.esquinaDeLaParcela(center, i, cota);
            BlockPos mejor = null;
            double mejorDistancia = Double.MAX_VALUE;
            for (int dx = 0; dx < VillageGenerator.PLOT_WIDTH; dx++) {
                for (int dz = 0; dz < VillageGenerator.PLOT_DEPTH; dz++) {
                    BlockPos r = candidata.en(parcela, dx, dz);
                    if (r == null || VillageManager.esPuntoFallido(villager, r)) {
                        continue; // a esa celda no llegó hace poco: se prueba la siguiente (I33)
                    }
                    double d = villager.distanceToSqr(r.getX() + 0.5D, r.getY() + 0.5D, r.getZ() + 0.5D);
                    if (d < mejorDistancia) {
                        mejorDistancia = d;
                        mejor = r;
                    }
                }
            }
            if (mejor != null) {
                parcelaDelObjetivo = i;
                // Objetivo nuevo: las dos piernas (la puerta y la mata) se miden de cero (I38).
                mejorDistanciaEntrada = Double.MAX_VALUE;
                stuckEntrada = 0;
                return mejor;
            }
        }
        parcelaDelObjetivo = -1;
        return null;
    }

    /** Igual, pero saltando las posiciones de {@code saltar} (las plantas que ya abonó en esta salida). */
    @Nullable
    private BlockPos buscarCultivo(ServerLevel level, boolean maduro, Set<Long> saltar) {
        return buscarEnLasParcelas(level, (parcela, dx, dz) -> {
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos r = parcela.offset(dx, dy, dz);
                if (!saltar.isEmpty() && saltar.contains(r.asLong())) {
                    continue;
                }
                BlockState s = level.getBlockState(r);
                if (s.getBlock() instanceof CropBlock crop
                        && (VillageGenerator.edadDelCultivo(s) == crop.getMaxAge()) == maduro) {
                    return r;
                }
            }
            return null;
        });
    }

    /**
     * Un cultivo que esté <b>creciendo</b> y al que <b>todavía no haya ido</b> en esta salida: es lo que hace que la
     * harina de huesos se reparta por la parcela (abonar el plantío entero) en vez de gastarse en la primera planta
     * que encuentre, que era lo que pasaba al no acordarse de por dónde iba.
     */
    @Nullable
    private BlockPos buscarCultivoSinAbonar(ServerLevel level) {
        return buscarCultivo(level, false, abonadas);
    }

    /**
     * El <b>compostero</b> de la aldea que todavía <b>no está lleno</b>, o {@code null} si no hay. El compostero está
     * pegado a la esquina de cada parcela (ver {@code VillageGenerator.plot}).
     */
    @Nullable
    private BlockPos buscarCompostero(ServerLevel level) {
        int cota = VillageGenerator.cotaDeLaPlaza(level, center);
        // OJO con la celda: el compostero va a `composteroDeLaParcela` (una celda MÁS AFUERA que la valla, desde la
        // migración 64). Aquí se buscaba en `parcela.offset(-1, 0, 0)`, que es la columna de la VALLA, así que el
        // granjero NO ENCONTRABA NUNCA su compostero: no compostaba, no había harina de huesos y no abonaba (medido
        // con el arnés: `Lleno el compostero` no salía ni una vez). La celda sale de un solo sitio (I4).
        for (int i : parcelasEnOrden(level)) {
            BlockPos comp = VillageGenerator.composteroDeLaParcela(center, i, cota);
            for (int dy = -2; dy <= 2; dy++) {
                BlockPos q = comp.offset(0, dy, 0);
                BlockState s = level.getBlockState(q);
                if (s.is(Blocks.COMPOSTER) && s.getValue(ComposterBlock.LEVEL) < ComposterBlock.MAX_LEVEL) {
                    // Se camina a la celda de AL LADO (el compostero es sólido: ver `puntoDeApoyoDelCompostero`).
                    BlockPos apoyo = VillageGenerator.puntoDeApoyoDelCompostero(level, q);
                    if (VillageManager.esPuntoFallido(villager, apoyo)) {
                        continue; // a ese compostero no llegó hace poco: se prueba el siguiente (I33)
                    }
                    return apoyo;
                }
            }
        }
        return null;
    }

    /**
     * Echa al <b>compostero</b> las semillas que le <b>sobran</b> (más de {@link #SEMILLAS_MAX}, que es lo que
     * necesita para sembrar). De trigo y betabel: la <b>zanahoria y la patata NO</b>, que son semilla <b>y</b> comida.
     * <p>
     * El compostero lleno (nivel {@code READY}) lo vacía en la despensa al visitarla (ver {@link #enLaDespensa}), y esa
     * harina de huesos es la que después usa para abonar. Sin este paso el compostero se quedaba <b>vacío para
     * siempre</b> (nadie le echaba nada), la harina se acababa y el abono se quedaba sin hacer.
     */
    private void compostar(ServerLevel level) {
        if (target == null) {
            return;
        }
        // El `target` es la celda de AL LADO a la que se camina, no el compostero: el compostero es un bloque SÓLIDO
        // y navegar hacia un bloque sólido deja al aldeano dando vueltas (ver
        // `VillageGenerator.puntoDeApoyoDelCompostero`; medido con el arnés: la granjera se perdió y acabó subiéndose a
        // la valla). Así que el compostero se busca en la celda a la que fue y a sus cuatro vecinas.
        BlockPos comp = elComposteroDe(level, target);
        if (comp == null) {
            return;
        }
        BlockState state = level.getBlockState(comp);
        if (!state.is(Blocks.COMPOSTER)) {
            return;
        }
        int echadas = 0;
        // `ComposterBlock.insertItem` gasta UNA unidad del stack que se le pasa (y no la gasta si el compostero ya
        // está lleno o si el objeto no es compostable), así que se le da un stack de 1 y se mira si se ha vaciado:
        // así el bucle no se puede quedar dando vueltas.
        while (echadas < COMPOSTAR_MAX && state.getValue(ComposterBlock.LEVEL) < ComposterBlock.MAX_LEVEL) {
            ItemStack una = sacarSemillaSobrante();
            if (una.isEmpty()) {
                break; // no le quedan semillas de sobra
            }
            state = ComposterBlock.insertItem(villager, state, level, una, comp);
            if (!una.isEmpty()) {
                guardarEnInventario(una); // no era compostable (no debería pasar): se le devuelve
                break;
            }
            echadas++;
        }
        if (echadas > 0) {
            level.levelEvent(1500, comp, 1); // el humo del compostero, como cuando lo llena el jugador
            level.playSound(null, comp, net.minecraft.sounds.SoundEvents.COMPOSTER_FILL_SUCCESS,
                    SoundSource.BLOCKS, 0.7F, 1.0F);
            VillageManager.ponerSuceso(villager, "Lleno el compostero (" + echadas + ")");
            DevilRpg.LOGGER.info("[Village] El granjero: Lleno el compostero con {} semilla(s)", echadas);
        }
    }

    /** El compostero que hay en esa celda o pegada a ella (la celda a la que se camina, o el bloque mismo). */
    @Nullable
    private BlockPos elComposteroDe(ServerLevel level, BlockPos celda) {
        for (BlockPos p : new BlockPos[]{celda, celda.north(), celda.south(), celda.east(), celda.west(),
                celda.below(), celda.above()}) {
            if (level.getBlockState(p).is(Blocks.COMPOSTER)) {
                return p;
            }
        }
        return null;
    }

    /** Cuántas semillas lleva encima que le <b>sobran</b> (más de las que necesita para sembrar). */
    private int semillasSobrantes() {
        return Math.max(0, semillasEnMano() - SEMILLAS_MAX);
    }

    /**
     * Semillas <b>compostables</b> (trigo y betabel) que le sobran. La zanahoria y la patata <b>no</b> cuentan: son
     * semilla <b>y</b> comida, así que no se tiran al compostero — y contarlas mandaba al granjero al compostero a no
     * echar nada (bucle de viajes vacíos).
     */
    private int semillasCompostablesSobrantes() {
        return Math.max(0, semillasCompostablesEnMano() - SEMILLAS_MAX);
    }

    /** Semillas de trigo y betabel que lleva encima. */
    private int semillasCompostablesEnMano() {
        int n = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.is(Items.WHEAT_SEEDS) || s.is(Items.BEETROOT_SEEDS)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Saca del inventario UNA semilla compostable que le sobre (trigo o betabel), o vacío si no tiene de sobra. */
    private ItemStack sacarSemillaSobrante() {
        if (semillasCompostablesSobrantes() <= 0) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.is(Items.WHEAT_SEEDS) || s.is(Items.BEETROOT_SEEDS)) {
                ItemStack una = s.copyWithCount(1);
                s.shrink(1);
                if (s.isEmpty()) {
                    villager.getInventory().setItem(i, ItemStack.EMPTY);
                }
                return una;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Busca tierra de cultivo con el hueco de arriba libre (para plantar). */
    @Nullable
    private BlockPos buscarTierraVacia(ServerLevel level) {
        return buscarEnLasParcelas(level, (parcela, dx, dz) -> {
            for (int dy = -1; dy <= 0; dy++) {
                BlockPos tierra = parcela.offset(dx, dy, dz);
                if (level.getBlockState(tierra).is(Blocks.FARMLAND) && level.getBlockState(tierra.above()).isAir()) {
                    return tierra.above();
                }
            }
            return null;
        });
    }

    /**
     * La <b>calva</b> del bancal a la que ir a labrar, o {@code null} si no hay ninguna: una celda que debería ser
     * <b>tierra de cultivo</b> y ahora es tierra o césped (alguien la pisó), con la <b>acequia a mano</b> y el hueco
     * de arriba <b>libre</b>.
     * <p>
     * Devuelve la casilla de <b>aire</b> de encima (la capa por la que se anda), no la tierra: es la posición a la
     * que se navega, igual que en {@link #buscarTierraVacia}.
     * <p>
     * El aire encima no es un detalle: es lo que garantiza que <b>no se arranca ningún cultivo</b> ni se toca nada de
     * lo que crece dentro del bancal (I11).
     */
    @Nullable
    private BlockPos buscarCalva(ServerLevel level) {
        return buscarEnLasParcelas(level, (parcela, dx, dz) -> {
            BlockPos tierra = parcela.offset(dx, -1, dz);
            if (!VillageGenerator.esCeldaDeCultivo(center, parcela.getY(), tierra)) {
                return null; // la acequia no se labra (y fuera del bancal no se toca nada)
            }
            return VillageGenerator.esCalvaDeBancal(level, tierra) ? tierra.above() : null;
        });
    }

    private int trigoEnMano() {
        int n = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.is(Items.WHEAT)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Vegetales (zanahoria, patata, betabel) que lleva encima: también son comida de la aldea. */
    private int vegetalesEnMano() {
        int n = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (VillagePantry.esVegetal(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private int harinaEnMano() {
        int n = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.is(Items.BONE_MEAL)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private boolean tieneSemillas() {
        return semillasEnMano() > 0;
    }

    /**
     * ¿Hay <b>comida esperando en el almacén</b>? Es la que dejan ahí el <b>ganadero</b> (la carne y la lana del
     * corral) y el <b>recolector</b> (lo que se cae por el pueblo). Mientras el contador de comida de la aldea y las
     * raciones miran <b>la despensa</b>, esta comida no cuenta: el granjero hace de puente en cada visita (ver el
     * paso 3 de {@link #enLaDespensa}) y, si la despensa está vacía, va a por ella aunque no tenga nada que llevar
     * (paso 2 de {@link #elegirFaena}).
     */
    private boolean hayComidaEnElAlmacen(ServerLevel level) {
        Container almacen = VillageStorage.almacen(level, center);
        if (almacen == null) {
            return false;
        }
        return VillagePantry.contar(almacen, s -> s.is(Items.WHEAT) || s.is(Items.BREAD)
                || VillagePantry.esVegetal(s) || VillagePantry.esCarneCruda(s) || VillagePantry.esCarneCocida(s)) > 0;
    }

    /** Cuántas semillas lleva encima (sumando todos los tipos). */
    private int semillasEnMano() {
        int n = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (esSemilla(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Saca una semilla del inventario (y devuelve la semilla que debe plantarse, o vacío). */
    private ItemStack sacarSemilla() {
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (esSemilla(s)) {
                ItemStack una = s.copyWithCount(1);
                s.shrink(1);
                if (s.isEmpty()) {
                    villager.getInventory().setItem(i, ItemStack.EMPTY);
                }
                return una;
            }
        }
        return ItemStack.EMPTY;
    }

    private ItemStack sacarHarina() {
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.is(Items.BONE_MEAL)) {
                ItemStack una = s.copyWithCount(1);
                s.shrink(1);
                if (s.isEmpty()) {
                    villager.getInventory().setItem(i, ItemStack.EMPTY);
                }
                return una;
            }
        }
        return ItemStack.EMPTY;
    }

    private ItemStack guardarEnInventario(ItemStack stack) {
        ItemStack resto = stack.copy();
        for (int i = 0; i < villager.getInventory().getContainerSize() && !resto.isEmpty(); i++) {
            ItemStack dentro = villager.getInventory().getItem(i);
            if (!dentro.isEmpty() && ItemStack.isSameItemSameComponents(dentro, resto)) {
                int espacio = dentro.getMaxStackSize() - dentro.getCount();
                int mete = Math.min(espacio, resto.getCount());
                dentro.grow(mete);
                resto.shrink(mete);
            }
        }
        for (int i = 0; i < villager.getInventory().getContainerSize() && !resto.isEmpty(); i++) {
            if (villager.getInventory().getItem(i).isEmpty()) {
                villager.getInventory().setItem(i, resto.copy());
                resto = ItemStack.EMPTY;
            }
        }
        return resto;
    }

    /** Las semillas que siembra. La lista vive en {@link VillagePantry#esSemilla} para no tener dos copias. */
    public static boolean esSemilla(ItemStack s) {
        return VillagePantry.esSemilla(s);
    }

    /** El cultivo que crece de esa semilla. */
    @Nullable
    private static BlockState cultivoDe(ItemStack semilla) {
        if (semilla.is(Items.WHEAT_SEEDS)) return Blocks.WHEAT.defaultBlockState();
        if (semilla.is(Items.CARROT)) return Blocks.CARROTS.defaultBlockState();
        if (semilla.is(Items.POTATO)) return Blocks.POTATOES.defaultBlockState();
        if (semilla.is(Items.BEETROOT_SEEDS)) return Blocks.BEETROOTS.defaultBlockState();
        return null;
    }
}
