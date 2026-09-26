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
import net.minecraft.world.level.block.FenceGateBlock;
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
     * Unidades (trigo + vegetales) que lleva encima antes de ir a la despensa: cada 16 cosechas baja a guardarlas.
     * <p>
     * Eran <b>4</b>, y con la despensa en la taberna (a 40-55 bloques de los bancales) eso es un paseo de ida y vuelta
     * por cada 4 puntos de comida: con 9-12 bocas comiendo 1 punto por minuto, la aldea vivía al filo (medido en el
     * guardado del jugador: ratos de "comida 0 puntos, 0 raciones"). El jugador pidió subirlo ("los granjeros deben
     * cosechar más rápido o mayor cantidad"): con 8 se entrega el doble por viaje y sigue bajando a menudo (la parcela
     * tiene 72 celdas de cultivo). El límite no es el hueco —la mochila del aldeano aguanta 64 por hueco—, era solo
     * una decisión de ritmo.
     * <p>
     * Y el jugador lo volvió a pedir, ya con el pan en manos del cocinero: *"que los granjeros lleven de una vez
     * {@code LLEVAR_TRIGO = 16}"*. Con 16 el viaje a la taberna rinde el doble (16 puntos de comida por paseo en vez
     * de 8) y el bancal se queda menos veces a medias: el hueco de trigo aguanta 64, así que 16 no compromete la
     * barrida de la parcela (ver {@code SEMILLAS_PARA_COMPOSTAR}, que es lo que se la cortaba).
     */
    private static final int LLEVAR_TRIGO = 16;
    /** Semillas que se guarda como mucho: si lleva más, las suelta (si no, se le llena el inventario y no le cabe el trigo). */
    private static final int SEMILLAS_MAX = 8;
    /**
     * Semillas de SOBRA que guarda para el <b>compostero</b> (además de las que necesita para sembrar). Antes eran
     * <b>64</b> —y por eso se guardaba hasta 72 semillas, o sea <b>dos huecos del zurrón</b>—, que es lo que le
     * <b>cortaba la barrida del bancal</b>: con los huecos llenos de semillas, `leCabeLaCosecha` decía que no y el
     * granjero se iba a la despensa a media parcela (medido con el arnés: entregas de 8-11 unidades y el bancal
     * quedándose en 12-15 plantas maduras para siempre).
     * <p>
     * Con <b>8</b> le basta: al compostero se va en cuanto tiene {@link #SEMILLAS_MINIMAS_PARA_COMPOSTAR} de sobra
     * (el paso 5 de {@code canUse}) y el tope sigue siendo {@link #SEMILLAS_MAX} para sembrar, así que las que pasan
     * de 16 se caen al suelo (son el abono) y el zurrón queda libre para la cosecha, que es lo que pidió el jugador:
     * *"revisa TODA la parcela y cosecha TODAS las que ya están maduras; si llegan a sobrar, que pare cuando llegue a
     * su límite de capacidad"*.
     */
    private static final int SEMILLAS_PARA_COMPOSTAR = 8;
    /**
     * Semillas que echa al compostero por visita (no se queda plantado allí). <b>Eran 16 y no daban abasto</b>: el
     * jugador vio la despensa llena de semillas (*"se están acumulando demasiadas semillas; lo ideal es que 2/3 partes
     * las ocupen los mismos granjeros para hacer composta y acelerar el proceso de cosecha"*) y, medido en su
     * guardado, había <b>261 semillas de trigo y 425 de betabel</b> (686) con los granjeros echando 16 por viaje: el
     * grifo del recolector abierto y el desagüe del compostero tapado. Con 64 por viaje y tres granjeros, el montón
     * baja de verdad (cada visita saca varias harinas de hueso).
     */
    private static final int COMPOSTAR_MAX = 64;
    /**
     * Si la despensa tiene MÁS semillas que esto, se lleva unas cuantas para el compostero: es la <b>reserva de
     * siembra</b> (los tres bancales necesitan ~27 semillas), así que todo lo que pase de aquí acaba en <b>composta</b>
     * —que es justo lo que pidió el jugador: que la mayor parte de las semillas las gasten los granjeros—.
     */
    private static final int SEMILLAS_SOBRANTES_EN_DESPENSA = 32;
    // OJO, NO VOLVER A PONER EL HORNO AQUÍ: este goal horneaba el pan (3 de trigo por hogaza) y aquí vivía el tope
    // `RESERVA_DE_TRIGO_PARA_CRIAR`. Ya no: el pan lo hace el COCINERO en el ahumador de su cocina
    // (`VillagerCookGoal.hornear`) y la reserva de trigo para criar —la que necesita el ganadero para vacas y ovejas—
    // se mudó con él a `VillagePantry.RESERVA_DE_TRIGO_PARA_CRIAR`. El granjero solo deja el trigo en la despensa. Si
    // el granjero vuelve a hornear, el ganadero se queda sin cría y sin ella no hay cuero ni lana (medido en el
    // guardado del jugador: el corral con 3 vacas de 6, la despensa con 0 de trigo y el almacén con 0 de cuero).
    /**
     * Harina de huesos que se lleva encima como mucho. Antes 4: con eso abonaba UNA planta por visita (lo pidió el
     * jugador: "que abone todo el plantío, no nada más una planta"), así que ahora carga una tanda de 16 y las gasta
     * seguidas por toda la parcela.
     */
    private static final int HARINA_MAX = 64;
    /** Plantas que abona como mucho en una misma salida (para no echar la tarde abonando sin llevar nada al cofre). */
    private static final int ABONAR_MAX = 64;
    /** Cuánto puede traerse del almacén a la despensa en una visita (comida, semillas y abono del recolector). */
    private static final int TRAER_DEL_ALMACEN = 64;

    /**
     * Harina de huesos por debajo de la cual el granjero <b>va al compostero ANTES que a la tierra</b>. Es lo que
     * pidió el jugador: *"los granjeros tampoco nunca deben olvidar de hacer polvo de hueso además de cultivar,
     * cosechar y entregar vegetales"*.
     * <p>
     * Hace falta una regla así porque el compostero era el paso que <b>no se alcanzaba nunca</b>: con los tres bancales
     * (216 celdas) siempre hay algo maduro, así que el turno no llegaba al paso 5 (el mismo fallo que tuvo la siembra,
     * que iba detrás de las dos faenas de la tierra). Medido en la partida del jugador (aldea 0, cota 63): <b>0 de
     * polvo de hueso en toda la aldea</b> y los tres composteros a nivel <b>1, 1 y 5</b> de 8 —sin haber producido ni
     * una harina—, con la arboleda del pueblo esperando abono para sus 10 plantones y un hueso guardado sin moler.
     */
    private static final int HARINA_MINIMA = 8;
    /**
     * Semillas <b>compostables de sobra</b> que junta antes de ir al compostero: con una o dos no vale la pena el
     * viaje (y era lo que le hacía ir y volver con UNA semilla por paseo, medido en su log). Si falta harina de
     * huesos de verdad se va con lo que tenga.
     */
    private static final int SEMILLAS_MINIMAS_PARA_COMPOSTAR = 4;
    /**
     * A qué distancia de la compuerta de su bancal el granjero <b>se la abre él mismo</b> (el juego no deja que un
     * aldeano abra una puerta de valla). Un poco más que el 2,6 con el que la abre {@code VillagerGateGoal}, para que
     * esté abierta <b>antes</b> de llegar y la ruta nueva cruce de verdad.
     */
    private static final double ABRIR_DESDE = 3.0D;
    /** Huesos que muele de una vez en el kiosco (la receta de vanilla: 1 hueso = 3 de polvo de hueso). */
    private static final int MOLER_MAX = 16;
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

    private enum Tarea { COSECHAR, LABRAR, PLANTAR, FERTILIZAR, COMPOSTAR, DESPENSA, SALIR, RECOGER }

    private final Villager villager;
    private final BlockPos center;
    private final int objectiveIndex;
    @Nullable
    private BlockPos target;
    private Tarea tarea = Tarea.COSECHAR;
    /**
     * ¿La última visita a la despensa <b>no le dejó hueco</b> (no le cupo nada de lo que llevaba)? Entonces no se le
     * manda otra vez por lo mismo: cosecha y lo que no le quepa se cae al suelo, que el mismo granjero barre luego
     * ({@link #Tarea.RECOGER}). Sin esto, con la despensa llena el granjero se quedaría en un bucle de viajes sin
     * cosechar nada.
     */
    private boolean despensaNoTraga;
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
    /**
     * Las <b>tres faenas de la tierra</b> que el granjero <b>rota</b> cuando hay varias a la vez: cosechar lo maduro,
     * labrar la calva y <b>sembrar la celda vacía</b>. La rotación es lo que reparte el turno; ver {@code canUse}.
     */
    private static final Tarea[] FAENAS_DE_LA_TIERRA = {Tarea.COSECHAR, Tarea.LABRAR, Tarea.PLANTAR};
    /** Turno del granjero entre las tres faenas de la tierra (ver {@code canUse}). */
    private int turnoDeFaena;
    /** Cosechas seguidas antes de atender la tierra (sembrar/labrar): ver {@link #COSECHAS_POR_TIERRA}. */
    private int cosechasSeguidas;
    /** Cuántas cosechas seguidas antes de una faena de tierra. */
    private static final int COSECHAS_POR_TIERRA = 2;
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
        // 1b) SIN HUECO EN EL ZURRÓN, A LA DESPENSA: si no le cabe nada más de lo que cosecha (ni un hueco libre ni una
        //     pila a medias de trigo o verdura), seguir cosechando es TIRAR la cosecha al suelo, que es justo lo que el
        //     jugador veía ("los granjeros están dejando muchos vegetales en el suelo cuando cosechan"). Va a
        //     descargar (y de paso trae recambios) antes de seguir con la huerta.
        if (despensa != null && !hayHuecoParaLaCosecha() && trigoEnMano() + vegetalesEnMano() > 0) {
            tarea = Tarea.DESPENSA;
            target = VillagePantry.puntoDeApoyo(level, center);
            return true;
        }
        // 1c) LO QUE SE HA CAÍDO EN SU BANCAL, AL ZURRÓN. Hace falta porque no todo lo que cae al suelo lo tira este
        //     goal: el propio CEREBRO del aldeano también tiene su faena de granjero (`HarvestFarmland`) y recoge el
        //     cultivo con el `destroyBlock(..., true)` del juego, que suelta los vegetales al suelo para que él los
        //     pise y los recoja. Con el pueblo llevando al aldeano a lo suyo (o a la despensa), esos vegetales se
        //     quedaban ahí hasta pudrirse: medido con el arnés, con el zurrón a MEDIO llenar (2 huecos libres de 8) y
        //     sin que este goal hubiera tirado nada —así que no eran suyos— había 8 objetos en el bancal 2 (patatas y
        //     zanahorias de 5 a 50 s). Y el RECOLECTOR no puede entrar: las parcelas están cercadas y las compuertas
        //     de valla no las abre un aldeano (por eso el granjero tiene su propia tarea de salir, `Tarea.SALIR`). El
        //     granjero es el único que puede barrer su bancal, así que lo barre él.
        if (parcelaDondeEsta(level) >= 0 && hayHuecoParaLaCosecha()) {
            BlockPos caido = buscarCaidoEnElBancal(level);
            if (caido != null) {
                target = caido;
                tarea = Tarea.RECOGER;
                return true;
            }
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
        // 3b) EL POLVO DE HUESO NO SE OLVIDA (lo pidió el jugador: *"los granjeros tampoco nunca deben olvidar de hacer
        //     polvo de hueso además de cultivar, cosechar y entregar vegetales"*) — pero VA DESPUÉS DE LA TIERRA (ver
        //     el paso 3 de abajo). Estaba antes y era el tapón de la cosecha: con la despensa con poca harina de huesos
        //     (< HARINA_MINIMA, lo normal, porque el leñador también la gasta en la arboleda) y **una** semilla de
        //     sobra, el granjero se iba al compostero con esa semilla —y volvía— una y otra vez, mientras el bancal se
        //     llenaba de plantas maduras.
        BlockPos maduro = buscarCultivo(level, true);
        BlockPos calva = buscarCalva(level);
        // COSECHAR MANDA, y la rotación es solo para lo demás (labrar y sembrar). Lo pidió el jugador: *"también están
        // tardando mucho en cosechar; hay campos llenos"*. Antes el turno rotaba SIEMPRE, así que con los tres
        // bancales llenos solo se cosechaba uno de cada tres turnos: la cosecha se quedaba atrás y el trigo se pasaba
        // de maduro. Lo que la rotación protegía (que se sembraran las celdas vacías) no se pierde: cuando el bancal
        // no tiene nada maduro —que es la mitad del ciclo de un campo sano— siguen labrando y sembrando.
        if (maduro != null) {
            target = maduro;
            tarea = Tarea.COSECHAR;
            return true;
        }
        turnoDeFaena = (turnoDeFaena + 1) % FAENAS_DE_LA_TIERRA.length;
        for (int intento = 0; intento < FAENAS_DE_LA_TIERRA.length; intento++) {
            switch (FAENAS_DE_LA_TIERRA[(turnoDeFaena + intento) % FAENAS_DE_LA_TIERRA.length]) {
                case COSECHAR -> {
                    if (maduro != null) {
                        target = maduro;
                        tarea = Tarea.COSECHAR;
                        return true;
                    }
                }
                case LABRAR -> {
                    if (calva != null) {
                        target = calva;
                        tarea = Tarea.LABRAR;
                        return true;
                    }
                }
                case PLANTAR -> {
                    // SOLO si lleva semillas EN LA MANO: `plantar()` las saca de su inventario, así que mandarlo a
                    // sembrar "porque en la despensa hay semillas" no hacía nada y lo dejaba en bucle igual que el
                    // paso 6 (si le faltan, el paso 6 lo manda a la despensa a por ellas).
                    if (tieneSemillas()) {
                        BlockPos vacia = buscarTierraVacia(level);
                        if (vacia != null) {
                            target = vacia;
                            tarea = Tarea.PLANTAR;
                            return true;
                        }
                    }
                }
                default -> {
                    // Las otras faenas (fertilizar, compostar...) no son de este turno.
                }
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
        // lo que produce la harina de huesos con la que abona. Va DESPUÉS de la tierra (cosechar, labrar y sembrar
        // mandan) y sin él la harina se acababa y el abono se quedaba sin hacer, porque nadie llenaba nunca el
        // compostero. OJO: AQUÍ YA NO HAY NADA MADURO (el paso 3 habría mandado a cosechar), así que este viaje no le
        // quita el turno a la cosecha — que es justo lo que pasaba cuando este paso iba ANTES.
        // Y SE VA CON UN PUÑADO, NO CON UNA SEMILLA: si solo le sobra una o dos, no vale la pena el paseo (su log
        // cantaba "Lleno el compostero con 1 semilla(s)" una y otra vez). La excepción es que falte harina de verdad.
        // OJO: se cuentan solo las COMPOSTABLES (trigo y betabel). Contando también la zanahoria y la patata, un
        // granjero cargado de vegetales se pasaría el día yendo al compostero a no echar nada (bucle).
        int sobrantes = semillasCompostablesSobrantes();
        boolean faltaHarina = despensa != null
                && VillagePantry.contar(despensa, s -> s.is(Items.BONE_MEAL)) < HARINA_MINIMA;
        if (sobrantes >= SEMILLAS_MINIMAS_PARA_COMPOSTAR || (faltaHarina && sobrantes > 0)) {
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
            // Y TAMBIÉN A PASO EXACTO (es el mismo caso: al salir hay que pisar la celda de la compuerta, y con la
            // tolerancia de 1 bloque se quedaba plantado dentro).
            VillageManager.caminarHaciaExacto(villager, target, 0.6F);
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
            BlockPos entrada = mejorEntradaLibre(level, parcelaDelObjetivo, target.getY());
            if (entrada == null) {
                // LAS CUATRO COMPUERTAS APARCADAS (I33): no se puede entrar por ninguna. Se deja la mata por un rato
                // y a otra cosa; volverá a intentarlo cuando se le pase el aparcado.
                target = null;
                restTicks = IDLE_REST_TICKS;
                return;
            }
            // Y SI LA TIENE AL LADO, SE LA ABRE ÉL (el juego no deja que un aldeano abra una puerta de valla: ver
            // `VillagerGateGoal.abrirParaUnAldeano`). Sin esto la entrada depende del ciclo del goal de los portones,
            // y cuando el servidor va justo el granjero se queda pegado a la valla.
            abrirLaCompuertaDeAlLado(level, entrada);
            // PASO EXACTO, NO "A UN BLOQUE" (medido el 26-sep-2026): con la tolerancia de 1 bloque el planificador da
            // por LLEGADO un destino que esté a un paso, devuelve una ruta de un solo punto (la celda donde el
            // granjero ya está) y el granjero NO SE MUEVE: este tramo no avanza nunca, a los 120 ticks aparca esa
            // entrada y prueba otra compuerta (hasta las cuatro). Ver `VillageManager.caminarHaciaExacto`.
            VillageManager.caminarHaciaExacto(villager, entrada, 0.6F);
            VillageManager.ponerActividad(villager, "Entrando a la huerta");
            // La pierna de la PUERTA se mide aparte de la del objetivo (I38: dos piernas, dos contadores).
            double hastaLaPuerta = Math.sqrt(villager.distanceToSqr(entrada.getX() + 0.5D, entrada.getY() + 0.5D,
                    entrada.getZ() + 0.5D));
            if (hastaLaPuerta < mejorDistanciaEntrada - 0.5D) {
                mejorDistanciaEntrada = hastaLaPuerta;
                stuckEntrada = 0;
            } else if (++stuckEntrada >= STUCK_LIMIT) {
                // NO SE PUEDE ENTRAR POR AQUÍ: SE APARCA LA ENTRADA, NO LA MATA. Antes se aparcaba la mata que quería
                // cosechar (con `stuckTicks = STUCK_LIMIT` el `canContinueToUse` aparcaba el OBJETIVO), así que cada
                // intento fallido se llevaba por delante una planta del borde y el bancal se quedaba sin cosechar
                // "sin que se supiera por qué". Medido en su partida: `no consigue llegar a 440,63,668` cada 14 s con
                // las matas del borde aparcadas una detrás de otra — y el bancal tiene CUATRO compuertas.
                DevilRpg.LOGGER.info("[Village] El granjero no consigue entrar al bancal {} por {}: lo deja por un"
                        + " rato y probara otra compuerta", parcelaDelObjetivo, entrada.toShortString());
                VillageManager.marcarPuntoFallido(villager, entrada);
                mejorDistanciaEntrada = Double.MAX_VALUE;
                stuckEntrada = 0;
                return; // el goal sigue vivo: la próxima pasada elegirá otra entrada
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
                if (!despensaNoTraga && !leCabeLaCosecha(level, target)) {
                    // NO SE COSECHA LO QUE NO LE CABE: lo que no entra en el zurrón se cae al suelo, y eso es justo lo
                    // que el jugador veía ("los granjeros están dejando muchos vegetales en el suelo cuando cosechan").
                    // Primero va a la despensa a descargar —viaje que además le trae recambios— y la cosecha se queda
                    // en la planta, que no se pierde.
                    tarea = Tarea.DESPENSA;
                    target = VillagePantry.puntoDeApoyo(level, center);
                    VillageManager.ponerActividad(villager, "Zurron lleno: a la despensa");
                    return; // el goal sigue vivo con el viaje
                }
                despensaNoTraga = false;
                VillageManager.ponerActividad(villager, "Cosechando");
                boolean segada = cosechar(level);
                // LA BARRIDA DEL BANCAL (lo pidió el jugador: *"una vez que un granjero está en una parcela, revise
                // TODA y coseche TODAS las que ya están maduras; si llegan a sobrar, que pare cuando llegue a su
                // límite de capacidad y deje sin cosechar las que sobran, entonces es cuando ya puede ir a la despensa
                // a dejar todo"*). Mientras esté DENTRO del bancal y le quepa otra madura, SIGUE con ella sin soltar
                // la faena: así el bancal se limpia de una pasada y no se va a la despensa cada 8 unidades —que era
                // lo que dejaba el resto a medias—. Solo para cuando no queda ninguna madura, cuando la siguiente ya
                // no le cabe (entonces el zurrón está lleno: a la despensa) o cuando se le acaba el tiempo.
                if (segada) {
                    cosechasSeguidas++;
                    BlockPos siguiente = buscarCultivoEnLaParcela(level, parcelaDelObjetivo);
                    if (siguiente != null && leCabeLaCosecha(level, siguiente)) {
                        target = siguiente;
                        mejorDistancia = Double.MAX_VALUE;
                        stuckTicks = 0;
                        return; // el goal sigue vivo con la siguiente mata
                    }
                }
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
            case RECOGER -> {
                VillageManager.ponerActividad(villager, "Recogiendo lo que se cayo");
                recoger(level);
                // Y SIGUE CON EL SIGUIENTE: si en su bancal queda otro objeto caído y le cabe, no se va a otra faena
                // entre medias. Barriendo de una pasada el bancal se queda limpio de verdad (midiendo, con el barrido
                // de uno en uno los objetos vivían hasta 54 s; el que se cae lo recoge él enseguida).
                if (hayHuecoParaLaCosecha()) {
                    BlockPos siguiente = buscarCaidoEnElBancal(level);
                    if (siguiente != null) {
                        target = siguiente;
                        mejorDistancia = Double.MAX_VALUE;
                        stuckTicks = 0;
                        return; // el goal sigue vivo con el siguiente objeto
                    }
                }
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

    /**
     * Cosecha la mata del objetivo (y la deja sembrada en el sitio). Devuelve {@code true} si de verdad la ha
     * cosechado: es lo que deja seguir con la <b>barrida del bancal</b> (ver {@code tick}) sin riesgo de quedarse
     * dando vueltas sobre la misma celda si el cultivo no estaba maduro.
     */
    private boolean cosechar(ServerLevel level) {
        BlockState state = level.getBlockState(target);
        // OJO: la edad se lee con la propiedad del PROPIO cultivo (el betabel es 0-3 y el trigo 0-7).
        if (!(state.getBlock() instanceof CropBlock crop)
                || VillageGenerator.edadDelCultivo(state) != crop.getMaxAge()) {
            return false;
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
        return true;
    }

    /**
     * La mata <b>MADURA más cercana dentro de ese bancal</b> (y solo en ese), o {@code null}. Es la <b>barrida del
     * bancal</b>: una vez dentro, el granjero no sale hasta acabar con lo maduro —o hasta que no le quepa más—, que
     * es lo que pidió el jugador (*"una vez que un granjero está en una parcela, revise TODA y coseche TODAS las que
     * ya están maduras"*).
     */
    @Nullable
    private BlockPos buscarCultivoEnLaParcela(ServerLevel level, int parcela) {
        if (parcela < 0 || parcela >= VillageGenerator.parcelasDeGranja()) {
            return null;
        }
        int cota = VillageGenerator.cotaDeLaPlaza(level, center);
        BlockPos esquina = VillageGenerator.esquinaDeLaParcela(center, parcela, cota);
        BlockPos mejor = null;
        double mejorDistancia = Double.MAX_VALUE;
        for (int dx = 0; dx < VillageGenerator.PLOT_WIDTH; dx++) {
            for (int dz = 0; dz < VillageGenerator.PLOT_DEPTH; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    BlockPos r = esquina.offset(dx, dy, dz);
                    BlockState s = level.getBlockState(r);
                    if (!(s.getBlock() instanceof CropBlock crop)
                            || VillageGenerator.edadDelCultivo(s) != crop.getMaxAge()) {
                        continue;
                    }
                    if (VillageManager.esPuntoFallido(villager, r)) {
                        continue; // a esa no llegó hace poco (I33): se prueba con la siguiente
                    }
                    double d = villager.distanceToSqr(r.getX() + 0.5D, r.getY() + 0.5D, r.getZ() + 0.5D);
                    if (d < mejorDistancia) {
                        mejorDistancia = d;
                        mejor = r;
                    }
                }
            }
        }
        return mejor;
    }

    /**
     * La casilla de <b>ENTRADA</b> al bancal (la de dentro, un paso del portón hacia el centro: ver
     * {@code VillageGenerator.entradaDeLaParcela}) más cercana que <b>no esté aparcada</b> (I33), o {@code null} si lo
     * están las cuatro.
     * <p>
     * El bancal tiene <b>cuatro compuertas</b>: si una no se puede cruzar (un animal pegado, el jugador delante, un
     * escalón, un bloque puesto ahí...), lo que hay que hacer es <b>probar otra</b>, no rendirse con la mata. Medido en
     * su partida: el granjero se quedaba en una sola compuerta, aparcaba la mata del borde que quería cosechar y el
     * bancal se quedaba sin cosechar con las otras tres compuertas libres.
     */
    @Nullable
    private BlockPos mejorEntradaLibre(ServerLevel level, int parcela, int cota) {
        BlockPos esquina = VillageGenerator.esquinaDeLaParcela(center, parcela, cota);
        BlockPos centro = esquina.offset(VillageGenerator.PLOT_WIDTH / 2, 0, VillageGenerator.PLOT_DEPTH / 2);
        BlockPos mejor = null;
        double mejorDist = Double.MAX_VALUE;
        for (BlockPos porton : VillageGenerator.portonesDeLaParcela(center, parcela, cota)) {
            BlockPos dentro = porton.offset(Integer.signum(centro.getX() - porton.getX()), 0,
                    Integer.signum(centro.getZ() - porton.getZ()));
            if (VillageManager.esPuntoFallido(villager, dentro)) {
                continue; // por ahí ya no pudo entrar hace poco (I33)
            }
            double d = villager.distanceToSqr(dentro.getX() + 0.5D, dentro.getY() + 0.5D, dentro.getZ() + 0.5D);
            if (d < mejorDist) {
                mejorDist = d;
                mejor = dentro;
            }
        }
        return mejor;
    }

    /** Si ya está al lado de la compuerta de esa entrada, <b>se la abre él</b> (ver {@code VillagerGateGoal}). */
    private void abrirLaCompuertaDeAlLado(ServerLevel level, BlockPos entrada) {
        if (villager.distanceToSqr(entrada.getX() + 0.5D, entrada.getY() + 0.5D, entrada.getZ() + 0.5D)
                > ABRIR_DESDE * ABRIR_DESDE) {
            return;
        }
        for (BlockPos p : new BlockPos[]{entrada, entrada.north(), entrada.south(), entrada.east(),
                entrada.west(), entrada.below()}) {
            if (!(level.getBlockState(p).getBlock() instanceof FenceGateBlock)) {
                continue;
            }
            if (!VillagerGateGoal.abrirParaUnAldeano(level, p)) {
                return; // ya estaba abierta (o ya no es un portón): no hay nada que rehacer
            }
            // Y SE LE HACE REHACER EL CAMINO CON LA COMPUERTA YA ABIERTA: la ruta que traía se calculó con ella
            // CERRADA —el juego no deja planificar a través de una puerta de valla cerrada—, así que acaba en su
            // propia casilla, pegado a la valla, y se queda ahí. Borrándole el destino, el `caminarHacia` de este
            // mismo goal pide una ruta nueva que SÍ cruza (es el mismo remedio que usa `VillagerGateGoal.abrir`,
            // medido allí con Isidoro: sin esto el aldeano se pasaba la noche en la celda de dentro del portón).
            villager.getNavigation().stop();
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.getBrain().eraseMemory(MemoryModuleType.PATH);
            return;
        }
    }

    /**
     * <b>Recoge UN objeto del suelo del bancal</b> (lo que se cayó al cosechar: lo que el juego suelta con su propia
     * faena de granjero, lo que no le cupo a él, o las semillas que sobran) y lo mete en el zurrón.
     * <p>
     * Se coge solo el de la <b>celda a la que ha ido</b> (el objetivo), no todo lo que haya alrededor: así el granjero
     * no se queda pegado a un montón y va eligiendo el más cercano en cada pasada. Lo que no le quepa <b>se queda en el
     * suelo</b> (nunca se borra un objeto del pueblo).
     */
    private void recoger(ServerLevel level) {
        if (target == null) {
            return;
        }
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(target).inflate(1.0D))) {
            if (!item.isAlive() || item.getItem().isEmpty()
                    || !item.blockPosition().equals(target)) {
                continue;
            }
            int antes = item.getItem().getCount();
            ItemStack resto = guardarEnInventario(item.getItem().copy());
            if (resto.getCount() == antes) {
                return; // no le cabía nada: se queda donde está
            }
            if (resto.isEmpty()) {
                item.discard();
            } else {
                item.setItem(resto);
            }
            level.playSound(null, target, net.minecraft.sounds.SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 1.4F);
        }
    }

    /**
     * El objeto que se ha caído <b>en el bancal en el que está el granjero</b> y que le vale (trigo, zanahoria, patata,
     * betabel, y las semillas si no lleva ya de sobra —las de más son del compostero—), el más cercano.
     * <p>
     * No se sale de su bancal a propósito: barrer el pueblo es del <b>recolector</b>; aquí solo se recoge lo que el
     * granjero mismo dejó atrás al cosechar, que es lo que nadie más puede alcanzar (la parcela está cercada).
     */
    @Nullable
    private BlockPos buscarCaidoEnElBancal(ServerLevel level) {
        int donde = parcelaDondeEsta(level);
        if (donde < 0) {
            return null;
        }
        int cota = VillageGenerator.cotaDeLaPlaza(level, center);
        ItemEntity mejor = null;
        double mejorDist = Double.MAX_VALUE;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class,
                new AABB(center).inflate(MAX_DISTANCE_FROM_CENTER))) {
            if (!item.isAlive() || item.getItem().isEmpty()) {
                continue;
            }
            if (!VillageGenerator.estaDentroDeLaParcela(center, donde, cota, item.blockPosition())) {
                continue;
            }
            if (!leValeLoCaido(item.getItem())) {
                continue;
            }
            if (VillageManager.esPuntoFallido(villager, item.blockPosition())) {
                continue; // a ese no llegó hace poco (I33): se prueba con el siguiente
            }
            double d = item.distanceToSqr(villager);
            if (d < mejorDist) {
                mejorDist = d;
                mejor = item;
            }
        }
        return mejor == null ? null : mejor.blockPosition();
    }

    /**
     * ¿Ese objeto del suelo es de los que el granjero se lleva? Trigo y verduras siempre (son la cosecha y la comida
     * del pueblo); las <b>semillas</b> solo si no lleva ya de sobra, porque las que sobran son del <b>compostero</b>
     * (para eso se caen: ver {@code cosechar}).
     */
    private boolean leValeLoCaido(ItemStack s) {
        if (s.is(Items.WHEAT) || VillagePantry.esVegetal(s)) {
            return true;
        }
        if (s.is(Items.WHEAT_SEEDS) || s.is(Items.BEETROOT_SEEDS)) {
            return semillasCompostablesSobrantes() <= 0;
        }
        return false;
    }

    /** ¿Le cabe algo más de cosecha en el zurrón? (un hueco libre, o una pila a medias de trigo o verdura) */
    private boolean hayHuecoParaLaCosecha() {
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.isEmpty()) {
                return true;
            }
            if ((s.is(Items.WHEAT) || VillagePantry.esVegetal(s)) && s.getCount() < s.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    /**
     * ¿Le cabe en el zurrón <b>todo</b> lo que va a soltar ese cultivo al cosecharlo? Se mira lo que el cultivo suelta
     * ({@code Block.getDrops}) <b>antes</b> de romperlo: si algo no cabe, no se cosecha todavía y el granjero se va
     * antes a la despensa a descargar.
     * <p>
     * Las <b>semillas de más</b> no cuentan: las que pasan del tope ({@link #SEMILLAS_MAX} +
     * {@link #SEMILLAS_PARA_COMPOSTAR}) se caen <b>a propósito</b> (son el abono del compostero), así que no bloquean
     * la cosecha.
     */
    private boolean leCabeLaCosecha(ServerLevel level, BlockPos cultivo) {
        BlockState state = level.getBlockState(cultivo);
        for (ItemStack drop : Block.getDrops(state, level, cultivo, null)) {
            boolean semillaDeCompostero = drop.is(Items.WHEAT_SEEDS) || drop.is(Items.BEETROOT_SEEDS);
            if (semillaDeCompostero
                    && semillasCompostablesEnMano() + drop.getCount() > SEMILLAS_MAX + SEMILLAS_PARA_COMPOSTAR) {
                continue; // ésas son del compostero: se caen y las recoge (o las composta) el pueblo
            }
            if (!leCabe(drop)) {
                return false;
            }
        }
        return true;
    }

    /** ¿Ese objeto cabe en el zurrón tal como está (en una pila igual a medias o en un hueco libre)? */
    private boolean leCabe(ItemStack stack) {
        int restante = stack.getCount();
        for (int i = 0; i < villager.getInventory().getContainerSize() && restante > 0; i++) {
            ItemStack dentro = villager.getInventory().getItem(i);
            if (dentro.isEmpty()) {
                return true; // una pila nueva cabe entera en un hueco libre
            }
            if (ItemStack.isSameItemSameComponents(dentro, stack)) {
                restante -= dentro.getMaxStackSize() - dentro.getCount();
            }
        }
        return restante <= 0;
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
        // 1b) LOS HUESOS, A POLVO DE HUESO: la receta de vanilla (1 hueso = 3 de polvo, ver
        //     `VillagePantry.molerHuesos`), con los huesos que el RECOLECTOR ha guardado —los sueltan los esqueletos
        //     que mata la milicia— y los que haya en la despensa. Es el abono de la aldea, y el que abona la ARBOLEDA
        //     DEL PUEBLO del leñador: hasta ahora nadie los molía y se quedaban guardados (medido en su partida: 0 de
        //     polvo de hueso en toda la aldea con un hueso en el almacén).
        int molidos = VillagePantry.molerHuesos(despensa, VillageStorage.almacen(level, center), MOLER_MAX);
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
        // ¿Le ha dejado hueco la visita? Si no le cupo NADA de lo que llevaba (despensa llena), se apunta para no
        // mandarlo otra vez por lo mismo (ver `despensaNoTraga`).
        despensaNoTraga = guardados == 0 && trigoEnMano() + vegetalesEnMano() > 0;
        // 3) LO DEL ALMACÉN, A LA DESPENSA: el recolector (holgazán) recoge del suelo lo que se cae por el pueblo
        // —incluido lo que deja caer el propio juego cuando SU aldeano granjero cosecha, que tira el grano al
        // suelo— y lo guarda en el almacén. Como el contador de comida de la aldea mira LA DESPENSA, esa comida se
        // quedaba muerta de risa en el almacén y la aldea pasaba hambre con el almacén lleno. El granjero hace de
        // puente en cada visita: se trae la comida, las semillas y el abono que el recolector haya guardado.
        int traidos = VillagePantry.traspasar(VillageStorage.almacen(level, center), despensa,
                s -> s.is(Items.WHEAT) || s.is(Items.BREAD) || s.is(Items.WHEAT_SEEDS) || s.is(Items.BEETROOT_SEEDS)
                        || s.is(Items.BONE_MEAL) || VillagePantry.esVegetal(s)
                        // LA PATATA ASADA también es comida del pueblo (`VillagePantry.comida` la cuenta) y faltaba
                        // aquí: si acababa en el almacén (el recolector la recoge del suelo, o la trae el jugador) se
                        // quedaba allí para siempre y no se comía nunca.
                        || s.is(Items.BAKED_POTATO)
                        || VillagePantry.esCarneCruda(s) || VillagePantry.esCarneCocida(s)
                        // Y LOS HUEVOS: el ganadero los sube al ALMACÉN (son del corral) y el cocinero los necesita en
                        // la DESPENSA para hacer huevos estrellados. Sin esta línea el huevo se quedaba en el almacén
                        // y el cocinero no lo veía nunca (lo reportó el jugador: "todavía no veo cocinado ningún huevo
                        // estrellado y los huevos están en el almacén").
                        || s.is(Items.EGG) || VillagePantry.esHuevoEstrellado(s),
                TRAER_DEL_ALMACEN);
        // 4) EL PAN NO SE HORNEA AQUÍ: LO HACE EL COCINERO. Lo pidió el jugador: *"el cocimiento de los panes no lo
        //    debería hacer el granjero sino el COCINERO"*. El granjero deja el TRIGO en la despensa (paso 2) y el
        //    cocinero lo hornea en el ahumador de su cocina, con la misma reserva de trigo para criar que se dejaba
        //    aquí (`VillagePantry.RESERVA_DE_TRIGO_PARA_CRIAR`): sin ella el ganadero no puede criar vacas ni ovejas y
        //    el pueblo se queda sin cuero y sin lana. Así cada oficio hace lo suyo: el granjero cultiva y cosecha, y
        //    el cocinero cocina (carne, patatas, huevos y pan).
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
        if (molidos > 0) {
            // El polvo de hueso manda en el anuncio: es lo que el jugador quiere ver ("que no se olviden de hacerlo").
            suceso = "Hizo " + (molidos * VillagePantry.POLVO_DE_HUESO_POR_HUESO) + " polvo de hueso (de " + molidos
                    + " hueso(s))";
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
        // SIN PUESTO RECONOCIDO: primero el bancal que NO tenga dueño (para no pisarse con un compañero y dejar otro
        // sin nadie) y, si están todos cogidos, el reparto por UUID (estable, y reparte igual).
        int libre = bancalSinDueno(level);
        miParcela = libre >= 0 ? libre
                : Math.floorMod(villager.getUUID().hashCode(), VillageGenerator.parcelasDeGranja());
        return miParcela;
    }

    /**
     * <b>El bancal que NO tiene dueño</b> (ninguno de los otros granjeros lo tiene como puesto de trabajo), o
     * {@code -1} si están todos cogidos.
     * <p>
     * Es la red de seguridad de {@link #miParcela}: si a un granjero se le perdió la estación (o nunca llegó a
     * reclamarla), el reparto por <b>UUID</b> puede mandarlo al bancal de <b>otro</b> compañero —los dos al mismo— y
     * dejar <b>otro bancal sin nadie</b>, que es exactamente lo que el jugador vio: *"otra vez los granjeros están
     * dejando demasiadas parcelas sin cosechar"*. Medido con el arnés: la tercera granjera, con `job=SIN PUESTO` y el
     * compostero del bancal 0 libre (`poi=SI`, dueño NADIE), trabajaba el bancal de una compañera y el bancal 0 se
     * quedaba con <b>37 plantas maduras</b> que no bajaban ni una en cuatro minutos.
     */
    private int bancalSinDueno(ServerLevel level) {
        int cota = VillageGenerator.cotaDeLaPlaza(level, center);
        Set<Integer> cogidos = new HashSet<>();
        for (Villager otro : level.getEntitiesOfClass(Villager.class,
                new AABB(center).inflate(MAX_DISTANCE_FROM_CENTER))) {
            if (otro == villager || otro.isBaby()
                    || otro.getVillagerData().getProfession() != VillagerProfession.FARMER) {
                continue;
            }
            Optional<GlobalPos> suyo = otro.getBrain().getMemory(MemoryModuleType.JOB_SITE);
            if (suyo.isEmpty()) {
                continue;
            }
            for (int i = 0; i < VillageGenerator.parcelasDeGranja(); i++) {
                BlockPos comp = VillageGenerator.composteroDeLaParcela(center, i, cota);
                if (suyo.get().pos().getZ() == comp.getZ()
                        && Math.abs(suyo.get().pos().getX() - comp.getX()) <= 1) {
                    cogidos.add(i);
                }
            }
        }
        for (int i = 0; i < VillageGenerator.parcelasDeGranja(); i++) {
            if (!cogidos.contains(i)) {
                return i;
            }
        }
        return -1;
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
