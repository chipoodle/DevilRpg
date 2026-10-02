package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.world.VillageGenerator;
import com.chipoodle.devilrpg.world.VillageManager;
import com.chipoodle.devilrpg.world.VillagePantry;
import com.chipoodle.devilrpg.world.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * El <b>GANADERO</b> de la aldea (etapa D): el aldeano que vive en la <b>granja anexa</b>, el corral de animales que
 * está <b>fuera de la valla</b> (lo pidió el jugador: "granja anexa de animales fuera de la valla, con su aldeano y
 * dentro del patrullaje de la guardia").
 * <p>
 * Qué hace, por orden:
 * <ol>
 *   <li><b>Recoge</b> lo que sueltan los animales por el corral (los huevos que ponen las gallinas y lo que deja un
 *       sacrificio) y lo <b>baja al almacén</b>, que es de donde el pueblo saca la comida y los materiales.</li>
 *   <li><b>Cría</b>: lleva comida a la pareja de una especie que esté por debajo de su tope (trigo para vacas y
 *       ovejas, zanahoria/patata/betabel para puercos, semillas para gallinas). La comida sale de la
 *       <b>despensa</b> y solo se usa si al pueblo le <b>sobra</b> (si no, el ganadero se comería el pan de la
 *       aldea para engordar animales).</li>
 *   <li><b>SACRIFICA</b> un adulto cuando hay <b>exceso</b> de esa especie (más que el tope: lo que sobra de criar) o
 *       cuando a la aldea le queda <b>poca comida</b>: así el corral da carne de verdad, que el recolector lleva al
 *       almacén y el granjero pasa a la despensa. La res nunca baja de la pareja: la granja no se mata sola.</li>
 * </ol>
 * Es un <b>puesto fijo</b> del pueblo (pastor, con su telar y su cama en el cobertizo del corral), así que no entra
 * ni en el reparto de obreros ni en la milicia.
 */
public class VillagerAnimalFarmGoal extends Goal {

    /** Distancia a la que ya alcanza al animal para darle de comer (o para el sacrificio). */
    private static final double REACH = 3.5D;
    /**
     * Distancia a la que se da por <b>llegado al portón</b> de por medio (el tramo de la puerta). Tiene que ser
     * <b>menor</b> que {@code VillagerGateGoal.ABRIR} (2,6): al llegar aquí se le manda otra vez al destino real y el
     * goal del portón, con el aldeano ya dentro de su radio y su destino al otro lado, se lo abre.
     */
    private static final double ALCANCE_PORTON = 2.0D;
    /**
     * Distancia a la que se da por <b>llegado</b> al objeto que se recoge del suelo (los huevos y lo que suelta un
     * sacrificio). Es <b>su propio {@link #REACH}</b> —el mismo con el que da de comer y sacrifica—, y no por
     * comodidad: en el corral <b>hay valla de por medio</b>. Medido en su partida (aldea 0, corral en `520,646`,
     * corralillo en `512..516, 638..639`, cota 63; `build/gallinero_medida.py`, que aplica esta misma cuenta celda a
     * celda): <b>ninguna</b> de las 10 celdas del corralillo se alcanza desde fuera con 1,8 (ni con los 2,5 del
     * recolector por oficio) y la <b>fila norte</b> —pegada a la valla del corral— queda a <b>3,04</b> del pasillo de
     * fuera (y a <b>3,35</b> si el huevo está encima de la paja, que está a `cota + 1`). Con 3,5 el corralillo entero
     * se recoge <b>desde el pasillo</b>, sin tener que entrar: y entrar es lo que <b>no</b> puede hacer con
     * fiabilidad (el juego no planifica a través de una puerta de valla cerrada, I96) ni conviene (por el portón se
     * escapan las gallinas). Lo medido: <b>en vivo con el arnés</b> (con el alcance en 2,5) los huevos de encima de la
     * paja —los que con 1,8 <b>no</b> se alcanzaban nunca— ya se los lleva (se ve desaparecer el huevo viejo y subir
     * el inventario), y con 1,8 los del corralillo llegaban a <b>2.000 ticks (100 s)</b> de edad sin recoger; y
     * <b>celda a celda</b> (`build/gallinero_medida.py`, con la misma distancia que el goal) el corralillo
     * <b>entero</b> solo lo cubre 3,5.
     */
    private static final double ALCANCE_RECOGIDA = REACH;
    /** Ticks de faena (dar de comer / sacrificar) antes de que el efecto ocurra. */
    private static final int WORK_TICKS = 25;
    private static final int REST_TICKS = 10;
    /** Sin nada que hacer: a esperar (buscar animales no se hace por tick). */
    private static final int IDLE_REST_TICKS = 120;
    /** Si no logra acercarse en este tiempo, abandona ese animal (invariante I3: atascado = no acercarse). */
    private static final int STUCK_LIMIT = 160;
    private static final float VELOCIDAD = 0.6F;
    /** Hasta dónde se le deja alejar del pueblo (el corral está a 50 + 7 del centro). */
    private static final double RADIO_MAXIMO = VillageGenerator.FENCE_RADIUS + 30.0D;

    /**
     * <b>Tope de animales por especie: DOS, la pareja.</b> Lo que sobra va al sacrificio (lo pidió el jugador, viendo
     * el corral lleno de puercos: *"hay demasiados puercos, el ganadero tiene que sacrificar, que queden 2 por raza"*).
     * <p>
     * Eran <b>6</b>, y con 6 el corral se llenaba de animales que no daban nada: medido en su guardado, <b>7 puercos</b>
     * (más que el tope) y el almacén sin cuero. Con 2, el ciclo es <b>2 → 3 (cría) → sacrificio → 2</b>: la pareja se
     * queda siempre (nunca baja de {@link #PAREJA_MINIMA}) y cada cría acaba en carne, cuero o lana.
     */
    private static final int MAX_POR_ESPECIE = 2;
    /**
     * Y las <b>gallinas</b> siguen con tope alto (8): no se crían para carne, sino por los <b>huevos</b>, y con dos
     * gallinas no habría huevos para el pueblo. Si el jugador las quiere también a dos, es cambiar este número.
     */
    private static final int MAX_GALLINAS = 8;
    /** Puntos de comida que tiene que tener la despensa para que el ganadero se lleve comida a los animales. */
    private static final int COMIDA_PARA_CRIAR = 24;
    /** Por debajo de esto, la aldea está apretada y el ganadero sacrifica un adulto (si hay de sobra). */
    private static final int COMIDA_PARA_SACRIFICAR = 12;
    /**
     * Adultos que forman la <b>pareja</b> de una especie. La misma cifra que usa el pueblo para reponerla
     * ({@link VillageGenerator#PAREJA_MINIMA}): por debajo de ella el ganadero no cría (vanilla pide dos adultos) y
     * nunca sacrifica.
     */
    private static final int PAREJA_MINIMA = VillageGenerator.PAREJA_MINIMA;
    /** Productos que lleva encima antes de bajarlos al almacén. */
    private static final int LLEVAR_AL_ALMACEN = 4;
    /** Radio en el que se recogen los drops de un sacrificio (y los huevos del corral). */
    private static final double RADIO_RECOGIDA = 6.0D;

    private enum Fase { CRIAR, SACRIFICAR, RECOGER, ENTREGAR, RONDAR }

    private final Villager villager;
    private final BlockPos center;
    private final int objectiveIndex;
    @Nullable
    private BlockPos target;
    private Fase fase = Fase.CRIAR;
    /** Especie (tipo de entidad) de la faena en curso: la fija `elegirFaena` y la usa la ejecución. */
    @Nullable
    private EntityType<? extends Animal> especie;
    private int workTicks;
    private int restTicks;
    private int stuckTicks;
    private double mejorDistancia = Double.MAX_VALUE;
    /**
     * El destino del <b>tramo</b> que está andando ahora (el objetivo, o el portón que tiene de por medio).
     */
    @Nullable
    private BlockPos tramo;
    /**
     * ¿Está ya <b>en el portón</b> pidiendo el destino real (enganche del tramo)? Ver {@link #destinoDelTramo}.
     */
    private boolean enElPorton;
    /** La cota del pueblo, calculada UNA vez por goal (la mira el terreno: no se pide en cada tick). */
    private int nivel = Integer.MIN_VALUE;

    public VillagerAnimalFarmGoal(Villager villager, BlockPos center, int objectiveIndex) {
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
        if (villager.getVillagerData().getProfession() != VillagerProfession.SHEPHERD) {
            return false; // solo el ganadero (el gestor le da el goal solo a él)
        }
        // En plena refriega nadie se pone a cuidar animales, y de noche el ganadero duerme en su cobertizo.
        if (VillageManager.isVillageUnderAttack(level, objectiveIndex) || VillageManager.estaDescansando(villager)) {
            return false;
        }
        double dx = villager.getX() - center.getX();
        double dz = villager.getZ() - center.getZ();
        if (dx * dx + dz * dz > RADIO_MAXIMO * RADIO_MAXIMO) {
            return false; // se ha ido demasiado lejos del término del pueblo
        }
        return elegirFaena(level);
    }

    /**
     * ¿Qué toca ahora? Devuelve {@code false} si no hay nada que hacer (entonces el ganadero se queda con sus
     * quehaceres de aldeano: su cama, su telar y el paseo por el corral).
     */
    private boolean elegirFaena(ServerLevel level) {
        List<Animal> corral = VillageGenerator.animalesDelCorral(level, center);
        // 1) Lo que sueltan los animales (huevos, lana, carne de un sacrificio): al almacén.
        if (cuantosLleva() >= LLEVAR_AL_ALMACEN) {
            return irAUnDestinoFijo(level, Fase.ENTREGAR, VillageStorage.puntoDeApoyo(level, center));
        }
        ItemEntity suelto = buscarDropEnElCorral(level);
        if (suelto != null) {
            fase = Fase.RECOGER;
            target = suelto.blockPosition();
            return true;
        }
        int comida = VillagePantry.comida(level, center);
        // 2) SACRIFICIO: por exceso de una especie, o porque a la aldea le queda poca comida.
        Animal presa = elegirSacrificio(level, corral, comida);
        if (presa != null) {
            fase = Fase.SACRIFICAR;
            especie = (EntityType<? extends Animal>) presa.getType();
            target = presa.blockPosition();
            return true;
        }
        // 3) CRÍA. Dos motivos, y el primero NO se salta nunca:
        //    a) REPONER LA PAREJA (lo pidió el jugador): si una especie se ha quedado con menos de dos animales, se
        //       cría aunque al pueblo le quede poca comida. La pareja es la SEMILLA de la granja: sin ella esa especie
        //       no vuelve nunca y el pueblo se queda sin carne, sin lana o sin huevos. Es el mismo criterio que la
        //       guarida, que mantiene su pareja pase lo que pase.
        //    b) CRECER: con comida de sobra en la despensa, se cría hasta el tope de la especie.
        // OJO: se prueban TODAS las especies, no solo una. Antes se elegía UNA (la del hueco más grande) y si a ESA
        // le faltaba su comida, el ganadero se quedaba sin faena aunque hubiera con qué criar otra (las gallinas con
        // semillas, por ejemplo). Medido en el guardado del jugador: el ganadero de la aldea 1 estaba plantado en la
        // plaza con la etiqueta genérica "Trabajando" y el corral lleno (2 vacas, 2 ovejas, 2 puercos y 5 gallinas).
        for (EntityType<? extends Animal> tipo : especiesParaCriar(corral, comida < COMIDA_PARA_CRIAR)) {
            if (!hayComidaParaCriar(level, tipo)) {
                continue; // esa no se puede: se prueba la siguiente
            }
            Animal pareja = adultoSinEnamorar(level, tipo);
            if (pareja == null) {
                continue; // ya están todos enamorados (o criando): no se malgasta la comida
            }
            fase = Fase.CRIAR;
            especie = tipo;
            target = pareja.blockPosition();
            return true;
        }
        // 4) SIN FAENA: se va <b>con el rebaño</b>. El corral es su casa y su puesto de trabajo (allí tiene la cama y
        //    el telar), así que si no hay nada que criar, sacrificar ni recoger, se queda con los animales en vez de
        //    plantarse en la plaza: eso era lo que veía el jugador ("aparece como que está trabajando pero no está
        //    yendo a los establos"). Desde el corral, además, ve los huevos y la lana en cuanto caen.
        if (VillageGenerator.anexoConstruido(level, center)) {
            return irAUnDestinoFijo(level, Fase.RONDAR, VillageGenerator.puntoDeApoyoAnexo(level, center));
        }
        // Nada que hacer: a esperar un poco (y no consumir CPU buscando animales cada tick).
        restTicks = IDLE_REST_TICKS;
        return false;
    }

    /**
     * <b>Los destinos FIJOS del ganadero</b> (el almacén y el punto de apoyo del corral) pasan por aquí, y por un
     * motivo medido: un destino fijo que está <b>aparcado</b> (I33: no se llegó a él hace menos de 5 min) <b>no puede
     * volver a elegirse</b>. Si se elige, el goal arranca, {@code tick} ve el aparcamiento, suelta el destino y
     * {@code canContinueToUse} lo para — y <b>al arrancar había CANCELADO al goal de recogida</b> del aldeano
     * ({@code VillagerPickupGoal}, prioridad 6, las mismas banderas MOVE/LOOK). Con el punto de apoyo del almacén
     * inalcanzable (I95) eso pasaba <b>cada tres ticks</b>: medido en el guardado del jugador (aldea 0), el ganadero
     * <b>Zacarias</b> tenía <b>4 huevos</b> en el inventario, su {@code DevilRpgPuntoFallido} apuntaba justo a
     * {@code (517,64,666)} con {@code DevilRpgPuntoFallidoHasta=80965} (el reloj del mundo en {@code 75127}) y su
     * etiqueta era la del <b>otro</b> goal ("Recogiendo lo suyo"): no podía entregar los huevos ni, con el goal de
     * recogida cancelándose cada pocos ticks, recoger más.
     * <p>
     * Es la misma regla que ya usaba {@code VillagerPickupGoal.canUse} con su destino: si está aparcado, el goal
     * <b>no arranca</b> y espera un rato (no se queda dando vueltas contra la pared).
     */
    private boolean irAUnDestinoFijo(ServerLevel level, Fase destino, @Nullable BlockPos punto) {
        if (punto == null || VillageManager.esPuntoFallido(villager, punto)) {
            restTicks = IDLE_REST_TICKS; // aparcado: se espera, no se arranca para abortar en el tick siguiente
            return false;
        }
        fase = destino;
        target = punto;
        return true;
    }

    /**
     * Las especies que <b>se pueden criar</b> ahora mismo, de la que más hueco tiene a la que menos: pareja hecha
     * (dos adultos) y sitio hasta su tope. Con la aldea apretada de comida ({@code soloPareja}) solo salen las que
     * tienen <b>exactamente</b> la pareja (no se cría para engordar el rebaño, solo para no perder la semilla).
     */
    private List<EntityType<? extends Animal>> especiesParaCriar(List<Animal> corral, boolean soloPareja) {
        List<EntityType<? extends Animal>> candidatas = new ArrayList<>();
        for (EntityType<? extends Animal> tipo : VillageGenerator.especiesDelCorral()) {
            int adultos = contarAdultos(corral, tipo);
            if (adultos < PAREJA_MINIMA) {
                continue; // sin pareja no hay cría posible
            }
            if (soloPareja && adultos > PAREJA_MINIMA) {
                continue; // con el pueblo apretado no se cría para crecer
            }
            // SE CRÍA HASTA EL TOPE (no "por debajo"): con el tope en la PAREJA (2), exigir estar por debajo dejaría al
            // rebaño clavado en 2 y sin crías que sacrificar —ni carne, ni cuero, ni lana—. Criando hasta el tope, el
            // ciclo es 2 → 3 → sacrificio → 2, que es lo que el jugador pidió ("que queden 2 por raza").
            if (contarEspecie(corral, tipo) > topeDe(tipo)) {
                continue; // por encima de su tope: eso ya va al sacrificio
            }
            candidatas.add(tipo);
        }
        // De la que más hueco tiene a la que menos: así el rebaño se reparte en vez de crecer todo por un lado.
        candidatas.sort((a, b) -> Integer.compare(topeDe(b) - contarEspecie(corral, b),
                topeDe(a) - contarEspecie(corral, a)));
        return candidatas;
    }

    @Override
    public void start() {
        workTicks = 0;
        stuckTicks = 0;
        mejorDistancia = Double.MAX_VALUE;
        tramo = null;
        enElPorton = false;
        irAlObjetivo();
    }

    @Override
    public boolean canContinueToUse() {
        if (target != null && stuckTicks >= STUCK_LIMIT) {
            // RENDIRSE = DEJARLO POR UN RATO (I33): el sitio al que no llegó (el animal, lo que iba a recoger, el
            // almacén o el corral) se apunta para no volver a elegir EL MISMO en bucle, que es lo que dejaba al
            // ganadero empujando el mismo obstáculo para siempre.
            VillageManager.marcarPuntoFallido(villager, target);
            return false;
        }
        return target != null && !villager.isBaby() && stuckTicks < STUCK_LIMIT
                && !VillageManager.estaDescansando(villager);
    }

    @Override
    public void tick() {
        if (target == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        // I33 TAMBIÉN PARA LOS DESTINOS FIJOS (el almacén, el punto de apoyo del corral): ésos se calculan fuera de
        // las búsquedas —que son las que saltan lo aparcado—, así que sin esto el ganadero volvía a por el MISMO punto
        // del almacén en bucle (medido con el arnés: 12 veces el mismo `1461,121,1434`). Se suelta y el goal arranca
        // otra vez, que es lo que ya hace con los objetivos de sus búsquedas.
        if (VillageManager.esPuntoFallido(villager, target)) {
            target = null;
            return;
        }
        villager.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
        // EL TRAMO DE AHORA: normalmente el objetivo, pero si hay un PORTÓN DE VALLA CERRADO de por medio (la cerca
        // del corral o el corralillo de las gallinas), el primer tramo es el PORTÓN: el juego no planifica el camino a
        // través de una puerta de valla cerrada, así que yendo directo al objetivo el aldeano se queda pegado a la
        // valla y acaba aparcando el destino (medido con el arnés: el ganadero 16 s en `516,63,636`, pegado a la valla
        // norte del corral, con el huevo de la paja al otro lado). Ver `VillageGenerator.primerTramoDelPorton`.
        BlockPos destino = destinoDelTramo(level);
        // I38: CADA PIERNA TIENE SU CONTADOR. El contador de paciencia y la distancia más corta son DEL TRAMO: si se
        // midieran contra el objetivo final, el tramo del portón (que acaba lejos de él) parecería "no acercarse" y el
        // goal se rendiría a mitad de camino.
        if (tramo == null || !tramo.equals(destino)) {
            tramo = destino;
            mejorDistancia = Double.MAX_VALUE;
            stuckTicks = 0;
        }
        double alcance = !destino.equals(target) ? ALCANCE_PORTON
                : (fase == Fase.ENTREGAR ? VillageStorage.ALCANCE_ALMACEN
                : (fase == Fase.RECOGER ? ALCANCE_RECOGIDA : REACH));
        double distancia = Math.sqrt(villager.distanceToSqr(destino.getX() + 0.5D, destino.getY() + 0.5D,
                destino.getZ() + 0.5D));
        if (distancia > alcance) {
            BlockPos aDonde = destino;
            if (destino.equals(target)) {
                // EL PUNTO DE AHORA (I140), no la celda cruda (I114/I131): lo que recoge puede estar sobre algo que no
                // se pisa —medido el 27-sep-2026: `Vicenta (Ganadero) / Recogiendo el corral` con destino
                // `516,64,639` (`air` con `oak_planks` encima, la mesa de la taberna), una **valla** (`513,63,640`) y
                // `ruta=2 nodos … alcanza=NO`— y si el sitio está lejos se va **por tramos**. El tramo del PORTÓN se
                // deja tal cual: el portón ya es una celda de paso.
                aDonde = VillageManager.elPuntoDeAhora(level, center, villager, destino, null);
            }
            // CADA PUNTO TIENE SU CONTADOR (I38/I140): si el punto de ahora cambia (otro tramo), la paciencia se mide
            // de cero; y el atasco se mide CONTRA EL PUNTO, no contra el objetivo final.
            if (tramo == null || !tramo.equals(aDonde)) {
                tramo = aDonde;
                mejorDistancia = Double.MAX_VALUE;
                stuckTicks = 0;
            }
            VillageManager.caminarHacia(villager, aDonde, VELOCIDAD);
            // Y SI ESTÁ METIDO DENTRO DE UN BLOQUE, NO SE CUENTA ATASCO: SE LE SACA. Es el mismo ayudante compartido
            // que usa el leñador desde I122.
            if (VillageManager.desatascarSiEstaEncajado(villager)) {
                mejorDistancia = Double.MAX_VALUE;
                stuckTicks = 0;
                return;
            }
            double hastaElPunto = Math.sqrt(villager.distanceToSqr(aDonde.getX() + 0.5D, aDonde.getY() + 0.5D,
                    aDonde.getZ() + 0.5D));
            if (hastaElPunto < mejorDistancia - 0.5D) {
                mejorDistancia = hastaElPunto;
                stuckTicks = 0;
            } else {
                stuckTicks++;
            }
            VillageManager.ponerActividad(villager, actividad());
            return;
        }
        VillageManager.parar(villager);
        villager.swing(InteractionHand.MAIN_HAND);
        if (++workTicks < WORK_TICKS) {
            VillageManager.ponerActividad(villager, actividad());
            return;
        }
        workTicks = 0;
        switch (fase) {
            case CRIAR -> criar(level);
            case SACRIFICAR -> sacrificar(level);
            case RECOGER -> recoger(level);
            case ENTREGAR -> entregar(level);
            case RONDAR -> {
                // Estar con el rebaño no es una faena: no hay nada que hacer, solo quedarse ahí (y mirar).
                VillageManager.ponerActividad(villager, actividad());
            }
        }
        target = null;
        restTicks = REST_TICKS;
    }

    @Override
    public void stop() {
        target = null;
        restTicks = REST_TICKS;
        villager.getNavigation().stop();
    }

    // --- las faenas ---------------------------------------------------------------------------------

    /**
     * <b>Cría</b>: le da de comer a la pareja. Se alimenta a <b>dos</b> adultos de esa especie que no estén ya
     * enamorados (vanilla necesita dos para que salga la cría) y se gasta una unidad de comida por animal, sacada de
     * la despensa. El parto lo hace el propio juego: aquí solo se les pone el "enamorado".
     */
    private void criar(ServerLevel level) {
        if (especie == null || target == null) {
            return;
        }
        ItemStack comida = comidaParaCriar(especie);
        if (comida.isEmpty()) {
            return;
        }
        int alimentados = 0;
        AABB cerca = new AABB(target).inflate(RADIO_RECOGIDA);
        for (Animal animal : level.getEntitiesOfClass(Animal.class, cerca)) {
            if (alimentados >= 2) {
                break;
            }
            if (animal.getType() != especie || !animal.canFallInLove() || animal.isInLove()) {
                continue;
            }
            if (sacarDeLaDespensa(level, comida, 1) <= 0) {
                break; // la despensa se quedó sin esa comida
            }
            animal.setInLove(null);
            level.sendParticles(ParticleTypes.HEART, animal.getX(), animal.getY() + 0.6D, animal.getZ(),
                    3, 0.2D, 0.2D, 0.2D, 0.0D);
            alimentados++;
        }
        if (alimentados > 0) {
            VillageManager.ponerSuceso(villager, "Dio de comer a los animales");
            DevilRpg.LOGGER.info("[Village] El ganadero: alimenta a {} animal(es) de {} para criar",
                    alimentados, especie.getDescription().getString());
        }
    }

    /**
     * <b>Sacrificio</b>: mata a un adulto (sin bajar de la pareja) y <b>recoge lo que suelta</b>, que es la carne, el
     * cuero, la lana o las plumas que luego baja al almacén. Se hace con daño de la aldea (no del jugador) para que
     * los drops sean los de siempre.
     */
    private void sacrificar(ServerLevel level) {
        if (especie == null || target == null) {
            return;
        }
        Animal presa = null;
        AABB cerca = new AABB(target).inflate(RADIO_RECOGIDA);
        for (Animal animal : level.getEntitiesOfClass(Animal.class, cerca)) {
            if (animal.getType() == especie && !animal.isBaby()) {
                presa = animal;
                break;
            }
        }
        if (presa == null) {
            return;
        }
        BlockPos donde = presa.blockPosition();
        presa.hurt(level.damageSources().mobAttack(villager), Float.MAX_VALUE);
        level.playSound(null, donde, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.NEUTRAL, 0.7F, 0.9F);
        // Los drops se recogen en el acto: si se dejan en el suelo, el ganadero tendría que volver a por ellos.
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(donde).inflate(2.5D))) {
            ItemStack resto = guardarEnInventario(drop.getItem().copy());
            if (resto.isEmpty()) {
                drop.discard();
            } else {
                drop.setItem(resto);
            }
        }
        VillageManager.ponerSuceso(villager, "Sacrifico un animal");
        DevilRpg.LOGGER.info("[Village] El ganadero: sacrifica un {} (comida de la aldea {})",
                especie.getDescription().getString(), VillagePantry.comida(level, center));
    }

    /** Recoge del suelo del corral lo que haya suelto (los huevos de las gallinas, sobre todo). */
    private void recoger(ServerLevel level) {
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(target).inflate(2.0D))) {
            ItemStack resto = guardarEnInventario(drop.getItem().copy());
            if (resto.isEmpty()) {
                drop.discard();
                VillageManager.ponerSuceso(villager, "Recogio lo del corral");
            } else {
                drop.setItem(resto);
            }
        }
    }

    /** Baja al almacén lo que lleva encima (es lo que alimenta al pueblo: carne, cuero, lana, plumas, huevos). */
    private void entregar(ServerLevel level) {
        int guardados = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            ItemStack resto = VillageStorage.guardar(level, center, s.copy());
            guardados += s.getCount() - resto.getCount();
            villager.getInventory().setItem(i, resto);
        }
        if (guardados > 0) {
            VillageManager.ponerSuceso(villager, "Bajo " + guardados + " cosas del corral");
            DevilRpg.LOGGER.info("[Village] El ganadero: {} cosa(s) del corral al almacen", guardados);
        }
    }

    // --- elección de la faena -----------------------------------------------------------------------

    /**
     * ¿A qué animal se sacrifica? Primero por <b>exceso</b> (la especie que pase de su tope) y, si a la aldea le
     * queda poca comida, cualquiera que esté por encima de la pareja mínima. Nunca crías (hay que dejar que crezcan).
     */
    @Nullable
    private Animal elegirSacrificio(ServerLevel level, List<Animal> corral, int comida) {
        EntityType<? extends Animal> elegida = null;
        int mejorSobra = 0;
        for (EntityType<? extends Animal> tipo : VillageGenerator.especiesDelCorral()) {
            if (contarAdultos(corral, tipo) <= PAREJA_MINIMA) {
                continue; // la PAREJA no se toca nunca: sin dos adultos esa especie no vuelve a criar
            }
            int sobra = contarEspecie(corral, tipo) - topeDe(tipo);
            if (sobra > mejorSobra) {
                mejorSobra = sobra;
                elegida = tipo;
            }
        }
        if (elegida == null && comida < COMIDA_PARA_SACRIFICAR) {
            // La aldea está apretada: se sacrifica de la especie más numerosa, pero nunca por debajo de la pareja.
            int mas = 0;
            for (EntityType<? extends Animal> tipo : VillageGenerator.especiesDelCorral()) {
                int adultos = contarAdultos(corral, tipo);
                if (adultos > PAREJA_MINIMA && adultos > mas) {
                    mas = adultos;
                    elegida = tipo;
                }
            }
        }
        if (elegida == null) {
            return null;
        }
        return adultoDe(level, elegida);
    }

    /**
     * ¿Hay en la despensa la comida de cría de esa especie (y de sobra para el pueblo)? */
    private boolean hayComidaParaCriar(ServerLevel level, EntityType<? extends Animal> tipo) {
        Container despensa = VillagePantry.despensa(level, center);
        if (despensa == null) {
            return false;
        }
        ItemStack comida = comidaParaCriar(tipo);
        return !comida.isEmpty() && VillagePantry.contar(despensa, s -> s.is(comida.getItem())) >= 2;
    }

    /**
     * La comida de cría de cada especie (las de vanilla): <b>trigo</b> para vacas y ovejas, <b>zanahoria/patata/
     * betabel</b> para puercos y <b>semillas</b> para gallinas. Las semillas no se comen, así que las gallinas son la
     * cría "barata".
     */
    private static ItemStack comidaParaCriar(EntityType<? extends Animal> tipo) {
        if (tipo == EntityType.COW || tipo == EntityType.SHEEP) {
            return new ItemStack(Items.WHEAT);
        }
        if (tipo == EntityType.PIG) {
            return new ItemStack(Items.CARROT);
        }
        if (tipo == EntityType.CHICKEN) {
            return new ItemStack(Items.WHEAT_SEEDS);
        }
        return ItemStack.EMPTY;
    }

    /** Le da una unidad de esa comida al ganadero sacándola de la despensa (lo que come el pueblo). */
    private int sacarDeLaDespensa(ServerLevel level, ItemStack comida, int cuantas) {
        Container despensa = VillagePantry.despensa(level, center);
        if (despensa == null) {
            return 0;
        }
        // Se mira QUÉ es antes de sacarlo (`sacar` devuelve cuántas unidades, no el objeto).
        ItemStack modelo = ItemStack.EMPTY;
        for (int i = 0; i < despensa.getContainerSize(); i++) {
            if (despensa.getItem(i).is(comida.getItem())) {
                modelo = new ItemStack(comida.getItem(), 1);
                break;
            }
        }
        if (modelo.isEmpty() || VillagePantry.sacar(despensa, s -> s.is(comida.getItem()), cuantas) <= 0) {
            return 0;
        }
        ItemStack resto = guardarEnInventario(new ItemStack(comida.getItem(), cuantas));
        if (!resto.isEmpty()) {
            VillagePantry.guardar(despensa, resto); // no se pierde lo que no quepa
        }
        return cuantas;
    }

    // --- búsquedas ----------------------------------------------------------------------------------

    @Nullable
    private Animal adultoDe(ServerLevel level, EntityType<? extends Animal> tipo) {
        Animal mejor = null;
        double mejorDist = Double.MAX_VALUE;
        for (Animal animal : VillageGenerator.animalesDelCorral(level, center)) {
            if (animal.getType() != tipo || animal.isBaby()) {
                continue;
            }
            if (VillageManager.esPuntoFallido(villager, animal.blockPosition())) {
                continue; // a ese animal no llegó hace poco: se prueba con el siguiente (I33)
            }
            double d = villager.distanceToSqr(animal);
            if (d < mejorDist) {
                mejorDist = d;
                mejor = animal;
            }
        }
        return mejor;
    }

    /** Un adulto de esa especie que <b>pueda</b> enamorarse (para no gastar comida en uno que ya está en ello). */
    @Nullable
    private Animal adultoSinEnamorar(ServerLevel level, EntityType<? extends Animal> tipo) {
        Animal mejor = null;
        double mejorDist = Double.MAX_VALUE;
        for (Animal animal : VillageGenerator.animalesDelCorral(level, center)) {
            if (animal.getType() != tipo || !animal.canFallInLove() || animal.isInLove()) {
                continue;
            }
            if (VillageManager.esPuntoFallido(villager, animal.blockPosition())) {
                continue; // a ese animal no llegó hace poco: se prueba con el siguiente (I33)
            }
            double d = villager.distanceToSqr(animal);
            if (d < mejorDist) {
                mejorDist = d;
                mejor = animal;
            }
        }
        return mejor;
    }

    /** El objeto suelto más cercano dentro del corral (huevos, drops de un sacrificio...). */
    @Nullable
    private ItemEntity buscarDropEnElCorral(ServerLevel level) {
        BlockPos base = VillageGenerator.baseDeAnexo(center);
        AABB corral = new AABB(base).inflate(VillageGenerator.ANEXO_RADIO + 1, 8.0D, VillageGenerator.ANEXO_RADIO + 1);
        ItemEntity mejor = null;
        double mejorDist = Double.MAX_VALUE;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, corral)) {
            if (!item.isAlive() || item.getItem().isEmpty()) {
                continue;
            }
            if (VillageManager.esPuntoFallido(villager, item.blockPosition())) {
                continue; // a ese objeto no llegó hace poco: se prueba con el siguiente (I33)
            }
            // Y NI LOS QUE NO ESTÁN EN UNA CASILLA DE PIE (28-sep-2026). MEDIDO en `build/medida-tanda1.log` y otra vez
            // en la 4: el destino del ganadero caía **en la valla del corral** (`destino=oak_fence encima=oak_fence`,
            // `ruta=1 nodos … alcanza=NO`) —un huevo dentro de la valla doble, el mapa del corral lo enseña— y, ya con
            // el primer filtro, **debajo de la mesa** (`destino=air encima=oak_planks`): la celda es aire, así que
            // `isAir()` lo dejaba pasar, pero tiene **tablones en la cabeza**. Y como **cada huevo es un ítem NUEVO**,
            // el punto fallido no cubre al siguiente: bucle. Un ítem al que no se puede **estar de pie** no se puede
            // recoger: no se elige.
            if (!VillageManager.esCeldaDePie(level, item.blockPosition())) {
                continue;
            }
            double d = villager.distanceToSqr(item);
            if (d < mejorDist) {
                mejorDist = d;
                mejor = item;
            }
        }
        return mejor;
    }

    private static int contarEspecie(List<Animal> corral, EntityType<? extends Animal> tipo) {
        int n = 0;
        for (Animal animal : corral) {
            if (animal.getType() == tipo) {
                n++;
            }
        }
        return n;
    }

    /** Cuántos <b>adultos</b> de esa especie hay en el corral (solo ellos crían y solo ellos cuentan como pareja). */
    private static int contarAdultos(List<Animal> corral, EntityType<? extends Animal> tipo) {
        int n = 0;
        for (Animal animal : corral) {
            if (animal.getType() == tipo && !animal.isBaby()) {
                n++;
            }
        }
        return n;
    }

    private static int topeDe(EntityType<? extends Animal> tipo) {
        return tipo == EntityType.CHICKEN ? MAX_GALLINAS : MAX_POR_ESPECIE;
    }

    // --- utilidades ---------------------------------------------------------------------------------

    private String actividad() {
        return switch (fase) {
            case CRIAR -> "Cuidando el ganado";
            case SACRIFICAR -> "Sacrificando un animal";
            case RECOGER -> "Recogiendo el corral";
            case ENTREGAR -> "Bajando lo del corral";
            case RONDAR -> "Con el rebano";
        };
    }

    private int cuantosLleva() {
        int n = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (!s.isEmpty()) {
                n += s.getCount();
            }
        }
        return n;
    }

    private void irAlObjetivo() {
        if (target != null && villager.level() instanceof ServerLevel level) {
            VillageManager.caminarHacia(villager, destinoDelTramo(level), VELOCIDAD);
        } else if (target != null) {
            VillageManager.caminarHacia(villager, target, VELOCIDAD);
        }
    }

    /**
     * <b>El destino del tramo de ahora.</b> Si el objetivo está al otro lado de un <b>portón de valla cerrado</b> del
     * anexo (la cerca del corral o el corralillo de las gallinas), el primer tramo es <b>el portón</b>; y cuando ya
     * está <b>en</b> él (a {@link #ALCANCE_PORTON}, dentro del radio con el que el goal del portón lo abre), el tramo
     * vuelve a ser el <b>objetivo</b>: con su destino al otro lado, el goal del portón se lo abre y le hace rehacer el
     * camino con la puerta ya abierta (I44/I96).
     * <p>
     * <b>Y NO VUELVE A OSCILAR</b> (el enganche, {@link #enElPorton}): en cuanto llega al portón se queda pidiendo el
     * destino <b>real</b> hasta que la puerta se abra (o ya no haya que cruzar). Sin el enganche, en cuanto se
     * alejaba dos bloques volvía al tramo del portón, y el vaivén tenía dos consecuencias medidas con el arnés:
     * (1) el aldeano se quedaba <b>oscilando</b> en la puerta sin cruzarla nunca y (2) la <b>espera del portón</b>
     * ("con un animal en el hueco no se abre... pero no para siempre", {@code ESPERA_MAXIMA}) se <b>reiniciaba</b> al
     * salirse del radio antes de cumplirse, así que la puerta <b>no se abría jamás</b> (medido: el ganadero 30 s
     * yendo y viniendo en `514,63,642` / `516,63,641` con el portón del gallinero en `open=false` y **2 gallinas en
     * el hueco**, y los huevos del corralillo con 2.000 ticks de edad sin recoger).
     */
    private BlockPos destinoDelTramo(ServerLevel level) {
        if (nivel == Integer.MIN_VALUE) {
            nivel = VillageGenerator.cotaDeLaPlaza(level, center);
        }
        BlockPos porton = VillageGenerator.primerTramoDelPorton(level, center, nivel,
                villager.blockPosition(), target);
        if (porton == null) {
            enElPorton = false; // no hay puerta de por medio (o ya está abierta): se va al destino, y a cruzar
            return VillageManager.casillaDePieCercaDe(level, target);
        }
        double d = Math.sqrt(villager.distanceToSqr(porton.getX() + 0.5D, porton.getY() + 0.5D, porton.getZ() + 0.5D));
        if (d <= ALCANCE_PORTON) {
            enElPorton = true;
        }
        return enElPorton ? VillageManager.casillaDePieCercaDe(level, target) : porton;
    }

    /**
     * <b>La casilla donde se camina hacia esa faena</b>: si la celda de la faena ya es una casilla de pie, ella misma;
     * si no (el animal está <b>sobre una valla</b>, en la paja, en el agua…), la casilla de pie <b>más cercana</b> de
     * alrededor. Es la regla de <b>I114</b> aplicada al ganadero, y sale de una medida (26-sep-2026): se rendía con
     * `ruta=1 nodos … alcanza=NO` yendo a celdas de aire a 4-6 bloques (`525,63,651`, `526,63,648`, `524,63,642`) —
     * el planificador no puede meterlo en una celda que no se pisa y devuelve una ruta de un solo punto—. El
     * {@code target} del goal <b>no se toca</b>: {@code recoger} lo usa para saber qué objeto coger.
     */
    private BlockPos casillaDePieCercaDe(ServerLevel level, BlockPos faena) {
        if (VillageManager.esCeldaDePie(level, faena)) {
            return faena;
        }
        BlockPos mejor = null;
        double mejorDist = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = faena.offset(dx, dy, dz);
                    if (!VillageManager.esCeldaDePie(level, p)) {
                        continue;
                    }
                    double d = p.distSqr(faena);
                    if (d < mejorDist) {
                        mejorDist = d;
                        mejor = p;
                    }
                }
            }
        }
        return mejor != null ? mejor : faena;
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
}
