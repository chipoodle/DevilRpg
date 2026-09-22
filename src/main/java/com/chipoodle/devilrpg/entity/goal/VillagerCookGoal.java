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
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * El <b>COCINERO</b> de la aldea (etapa E): el aldeano que <b>cocina</b> en el <b>ahumador de la cocina del kiosco</b>
 * lo que el pueblo tiene crudo —la carne del corral anexo y las patatas de la huerta— y lo devuelve a la
 * <b>despensa</b>.
 * <p>
 * Por qué importa tanto: en el contador de comida de la aldea la <b>carne cruda vale 2 puntos y la cocinada 4</b> (y la
 * patata 2, la asada 4). O sea que cocinar <b>duplica</b> la comida que ya había: es la palanca de hambre del pueblo,
 * y por eso el cocinero es un puesto fijo y no un adorno. Sin él, toda la carne que sube el ganadero del corral se
 * come cruda y vale la mitad.
 * <p>
 * Cómo cocina: igual que el granjero hornea el pan, <b>en su puesto y con los objetos de verdad</b> (saca la carne de
 * la despensa, la convierte y la vuelve a guardar), con su sonido y su humo.
 * <p>
 * <b>Y EL FUEGO SE PAGA CON LEÑA DEL ALMACÉN</b> (lo pidió el jugador: *"el smoker, el furnance y todos los aparatos
 * donde se tenga que quemar necesitan ir por logs al almacén para que se use de combustible y funcionen"*). Antes el
 * humo salía de la nada. Ahora cada tanda de cocina <b>quema un tronco</b>: el cocinero <b>va al almacén</b> a por
 * leña ({@link #LENA_POR_VIAJE} troncos de una vez, que cunden para varias tandas), la lleva encima y la gasta al
 * cocinar. Y si el almacén no tiene leña <b>por encima de la {@link VillageStorage#RESERVA_LENA reserva}</b> (la
 * madera es también la materia prima del herrero), no cocina y lo dice en su etiqueta: un aparato sin combustible no
 * funciona, no se inventa el humo.
 */
public class VillagerCookGoal extends Goal {

    /** Cuántas piezas cocina por visita a la despensa (ni una más: el resto sigue crudo hasta la próxima vuelta). */
    private static final int COCINAR_MAX = 8;
    /** Ticks de faena antes de que la cocción ocurra (se le ve trabajar en el ahumador). */
    private static final int WORK_TICKS = 30;
    private static final int REST_TICKS = 20;
    /** Sin nada que cocinar: a esperar (mirar la despensa no se hace por tick). */
    private static final int IDLE_REST_TICKS = 200;
    /** Si no logra acercarse en este tiempo, abandona (invariante I3: atascado = no acercarse). */
    private static final int STUCK_LIMIT = 200;
    /** Velocidad de paseo del cocinero (igual que los demás goals del pueblo). */
    private static final float VELOCIDAD = 0.6F;
    /**
     * Alcance a la <b>casilla de la cocina</b> (la de delante del ahumador): el cocinero tiene que estar <b>en ella</b>
     * para cocinar. Antes eran 6,5 —«que llegue de sobra»— y por eso el cocinero cocinaba <b>desde fuera de la
     * taberna</b>: el jugador lo vio con captura, plantado en la plaza cocinando a través de la pared (y medido con el
     * arnés: {@code pos=1446,120,1425 … dCasilla=5,76 VEelAhumador=NO}). Con 2,0 está en la casilla o pegada a ella.
     */
    private static final double REACH = 2.0D;
    /**
     * Alcance al <b>ahumador</b>, que está <b>dentro del kiosco</b> (sobre la plataforma, un bloque más arriba). Es
     * el que de verdad decide si trabaja: sin él, el cocinero "cocinaría" desde la otra punta de la plaza. Con 8
     * entra de sobra desde el patio (el punto de apoyo está a ~5,5 del ahumador), así que solo salta si el aldeano
     * se quedó corto por el camino.
     */
    private static final double ALCANCE_AHUMADOR = 8.0D;
    /** Troncos que el cocinero se trae del almacén de una vez: cuatro tandas de cocina sin volver a cruzar el pueblo. */
    private static final int LENA_POR_VIAJE = 4;
    /**
     * Paciencia yendo al almacén por leña: es el mismo caso que el agua del clérigo —el almacén está al otro lado del
     * pueblo, a ~20-25 bloques de la taberna—, así que con los 200 ticks (10 s) del puesto se rendiría a mitad de
     * camino y aparcaría el almacén para siempre. Veinte segundos dan de sobra y siguen cortando el bucle.
     */
    private static final int STUCK_LENA = 20 * 20;

    private final Villager villager;
    private final BlockPos center;
    private final int objectiveIndex;
    @Nullable
    private BlockPos target;
    /** El ahumador del kiosco (el puesto de trabajo). Se mide en {@link #canUse}, no en cada tick. */
    @Nullable
    private BlockPos puesto;
    private int workTicks;
    private int restTicks;
    private int stuckTicks;
    private double mejorDistancia = Double.MAX_VALUE;
    /** ¿La vuelta que está haciendo AHORA es la del almacén (a por leña) y no la de la cocina? */
    private boolean yendoPorLena;
    /** Contador y mejor distancia <b>de la pierna de la leña</b>: medidos aparte, como en el clérigo (ver {@link #STUCK_LENA}). */
    private int stuckLena;
    private double mejorDistanciaLena = Double.MAX_VALUE;
    /** Ya se avisó de que no hay leña: no se repite la línea del log en cada intento. */
    private boolean avisadoSinLena;

    public VillagerCookGoal(Villager villager, BlockPos center, int objectiveIndex) {
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
        if (villager.getVillagerData().getProfession() != VillagerProfession.BUTCHER) {
            return false; // solo el cocinero (el gestor le da el goal solo a él)
        }
        // En plena refriega nadie cocina, y de noche el cocinero se va a la cama como todos.
        if (VillageManager.isVillageUnderAttack(level, objectiveIndex) || VillageManager.estaDescansando(villager)) {
            return false;
        }
        double dx = villager.getX() - center.getX();
        double dz = villager.getZ() - center.getZ();
        if (dx * dx + dz * dz > (VillageGenerator.FENCE_RADIUS + 10.0D) * (VillageGenerator.FENCE_RADIUS + 10.0D)) {
            return false; // se ha ido lejos del pueblo
        }
        // El puesto (el ahumador de la TABERNA desde la etapa F), medido UNA vez por intento: `puestoDelCocinero`
        // pregunta la cota de la plaza (un barrido del terreno) y eso no se hace en cada tick. Sin ahumador no hay
        // cocina a la que ir.
        puesto = VillageGenerator.puestoDelCocinero(level, center);
        if (!level.getBlockState(puesto).is(Blocks.SMOKER)) {
            restTicks = IDLE_REST_TICKS;
            return false;
        }
        // Solo va si de verdad hay algo que cocinar (si no, no se queda plantado en el ahumador).
        if (contarCrudoEnLaDespensa(level) <= 0) {
            restTicks = IDLE_REST_TICKS;
            return false;
        }
        // SIN LEÑA ENCIMA, LO PRIMERO ES IR A POR ELLA al almacén: el ahumador no funciona sin combustible. Si ya
        // lleva, se va derecho a la cocina.
        if (!llevaLena()) {
            if (!irPorLena(level)) {
                restTicks = IDLE_REST_TICKS;
                return false;
            }
            return true;
        }
        // Con leña: camina a la casilla de DELANTE del ahumador (la cocina de la taberna), donde puede estar de pie.
        target = vistaDeLaCocina();
        if (VillageManager.esPuntoFallido(villager, target)) {
            // A esa casilla de la cocina no llegó hace poco (I33): no se queda plantado empujando, espera un rato.
            restTicks = IDLE_REST_TICKS;
            return false;
        }
        yendoPorLena = false;
        return target != null;
    }

    @Override
    public void start() {
        workTicks = 0;
        stuckTicks = 0;
        mejorDistancia = Double.MAX_VALUE;
        stuckLena = 0;
        mejorDistanciaLena = Double.MAX_VALUE;
        irAlDestino();
    }

    @Override
    public boolean canContinueToUse() {
        if (target != null && !yendoPorLena && stuckTicks >= STUCK_LIMIT) {
            // RENDIRSE = DEJARLO POR UN RATO (I33): el punto de la cocina al que no llegó se apunta para no volver a
            // él en bucle, que es lo que dejaba al cocinero empujando el mismo obstáculo para siempre. (La pierna de
            // la leña tiene su propio contador y su propio aparcado: ver `tickDeLaLena`.)
            VillageManager.marcarPuntoFallido(villager, target);
            return false;
        }
        return target != null && !villager.isBaby() && stuckTicks < STUCK_LIMIT
                && !VillageManager.estaDescansando(villager);
    }

    @Override
    public void tick() {
        if (target == null || puesto == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        if (yendoPorLena) {
            tickDeLaLena(level);
            return;
        }
        // Camina a la CASILLA DE LA COCINA (la de delante del ahumador) y trabaja SOLO desde ahí: nunca HACIA el
        // ahumador, que es un bloque sólido al que la navegación no puede llegar.
        double distancia = Math.sqrt(villager.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D,
                target.getZ() + 0.5D));
        double alAhumador = Math.sqrt(villager.distanceToSqr(puesto.getX() + 0.5D, puesto.getY() + 0.5D,
                puesto.getZ() + 0.5D));
        villager.getLookControl().setLookAt(puesto.getX() + 0.5D, puesto.getY() + 0.5D, puesto.getZ() + 0.5D);
        // ¿ESTÁ DE VERDAD EN LA COCINA? No basta con estar cerca: hay que <b>ver el ahumador</b>, sin nada sólido en
        // medio. Sin esto cocinaba también desde la plaza (al otro lado de la pared de la taberna: lo reportó el
        // jugador) y desde el comedor a través del tabique de la cocina; medido con el arnés, a 5,19 bloques del
        // puesto con el tabique en medio y desde fuera a 6,44.
        boolean enLaCocina = distancia <= REACH && alAhumador <= ALCANCE_AHUMADOR
                && VillageManager.hayVistaLibre(level, villager.blockPosition(), puesto, villager);
        if (!enLaCocina) {
            VillageManager.caminarHacia(villager, target, VELOCIDAD);
            if (distancia < mejorDistancia - 0.5D) {
                mejorDistancia = distancia;
                stuckTicks = 0;
            } else {
                stuckTicks++;
            }
            VillageManager.ponerActividad(villager, "Yendo a la cocina");
            return;
        }
        VillageManager.parar(villager);
        villager.swing(InteractionHand.MAIN_HAND);
        if (++workTicks < WORK_TICKS) {
            VillageManager.ponerActividad(villager, "Cocinando");
            return;
        }
        workTicks = 0;
        // LA TANDA SE PAGA CON UN TRONCO. Si se le acabó la leña por el camino (o se la quitó alguien), vuelve al
        // almacén a por más en vez de cocinar de la nada.
        if (!quemarLena()) {
            if (!irPorLena(level)) {
                restTicks = IDLE_REST_TICKS;
            }
            return;
        }
        cocinar(level);
        target = null;
        restTicks = REST_TICKS;
    }

    @Override
    public void stop() {
        target = null;
        puesto = null;
        yendoPorLena = false;
        restTicks = REST_TICKS;
        villager.getNavigation().stop();
    }

    // --- el fuego: la leña del almacén ------------------------------------------------------------------

    /** La casilla de DELANTE del ahumador (la cocina de la taberna): donde el cocinero puede estar de pie. */
    private BlockPos vistaDeLaCocina() {
        return new BlockPos(puesto.getX(), puesto.getY(), puesto.getZ() - 1);
    }

    /**
     * Manda al cocinero al <b>almacén</b> a por leña ({@code true} si hay a dónde ir). El punto es el de apoyo del
     * cobertizo (una casilla libre del suelo: al cofre no se navega, es sólido), el mismo que usa el herrero.
     * <p>
     * Lo <b>primero</b> que se mira es si queda leña <b>por encima de la reserva</b>
     * ({@link VillageStorage#hayLenaParaQuemar}): si no, el ahumador se queda apagado y el cocinero no cruza el
     * pueblo para nada (medido con el arnés: con el almacén en la reserva, el viaje era un paseo en balde).
     */
    private boolean irPorLena(ServerLevel level) {
        if (!VillageStorage.hayLenaParaQuemar(level, center)) {
            if (!avisadoSinLena) {
                avisadoSinLena = true;
                DevilRpg.LOGGER.info("[Village] El cocinero no cocina: el almacen no tiene lena por encima de la"
                                + " reserva de {} (aldea {}), asi que el ahumador se queda apagado",
                        VillageStorage.RESERVA_LENA, objectiveIndex);
            }
            VillageManager.ponerActividad(villager, "Sin lena para el ahumador");
            return false;
        }
        BlockPos almacen = VillageStorage.puntoDeApoyo(level, center);
        if (almacen == null || VillageManager.esPuntoFallido(villager, almacen)) {
            // Al almacén no llegó hace poco (I33: el cofre está tapado o rodeado): a esperar, no a empujar la pared.
            restTicks = IDLE_REST_TICKS;
            return false;
        }
        target = almacen;
        yendoPorLena = true;
        stuckLena = 0;
        mejorDistanciaLena = Double.MAX_VALUE;
        VillageManager.ponerActividad(villager, "A por lena al almacen");
        return true;
    }

    /**
     * La pierna de la leña: camina al almacén y coge {@link #LENA_POR_VIAJE} troncos del excedente
     * ({@link VillageStorage#quitarLena}: nunca toca la reserva). Al cogerlos, la vuelta a la cocina se mide
     * <b>de cero</b> —el mismo bug que se midió en el clérigo: con el contador compartido, la caminata de vuelta
     * parecía "no acercarse" y el aldeano aparcaba su propio puesto a mitad de camino—.
     */
    private void tickDeLaLena(ServerLevel level) {
        double distancia = Math.sqrt(villager.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D,
                target.getZ() + 0.5D));
        villager.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
        if (distancia > VillageStorage.ALCANCE_ALMACEN) {
            VillageManager.caminarHacia(villager, target, VELOCIDAD);
            VillageManager.ponerActividad(villager, "A por lena al almacen");
            if (distancia < mejorDistanciaLena - 0.5D) {
                mejorDistanciaLena = distancia;
                stuckLena = 0;
            } else if (++stuckLena >= STUCK_LENA) {
                DevilRpg.LOGGER.info("[Village] El cocinero se atasca yendo por lena al almacen ({}): lo deja por un"
                        + " rato", villager.blockPosition().toShortString());
                VillageManager.marcarPuntoFallido(villager, target);
                target = null;
                puesto = null;
                yendoPorLena = false;
                restTicks = IDLE_REST_TICKS;
            }
            return;
        }
        VillageManager.parar(villager);
        ItemStack lena = VillageStorage.quitarLena(level, center, LENA_POR_VIAJE);
        if (lena == null || lena.isEmpty()) {
            // Se la han llevado por delante (u otro aparato se comió el excedente): a esperar, sin cocinar de la nada.
            target = null;
            puesto = null;
            yendoPorLena = false;
            restTicks = IDLE_REST_TICKS;
            return;
        }
        ItemStack resto = guardarEnInventario(lena);
        if (!resto.isEmpty()) {
            VillageStorage.guardar(level, center, resto); // no le cupo (raro): de vuelta al almacén
        }
        DevilRpg.LOGGER.info("[Village] El cocinero: cogio {} tronco(s) del almacen para el ahumador (aldea {};"
                + " quedan {} en el almacen)", lena.getCount(), objectiveIndex,
                VillageStorage.cuentaLena(level, center));
        VillageManager.ponerSuceso(villager, "Cogi " + lena.getCount() + " tronco(s) para el ahumador");
        avisadoSinLena = false;
        yendoPorLena = false;
        target = vistaDeLaCocina();
        // La vuelta se mide de cero (el contador de la ida valía para el almacén, no para la cocina).
        mejorDistancia = Double.MAX_VALUE;
        stuckTicks = 0;
        mejorDistanciaLena = Double.MAX_VALUE;
        stuckLena = 0;
    }

    /** ¿Lleva leña encima (troncos en el zurrón)? */
    private boolean llevaLena() {
        var mochila = villager.getInventory();
        for (int i = 0; i < mochila.getContainerSize(); i++) {
            if (VillageStorage.esLena(mochila.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    /** Quema <b>un tronco</b> de los que lleva encima (la tanda de cocina). {@code false} si no le queda ninguno. */
    private boolean quemarLena() {
        var mochila = villager.getInventory();
        for (int i = 0; i < mochila.getContainerSize(); i++) {
            ItemStack s = mochila.getItem(i);
            if (!VillageStorage.esLena(s)) {
                continue;
            }
            s.shrink(1);
            if (s.isEmpty()) {
                mochila.setItem(i, ItemStack.EMPTY);
            }
            return true;
        }
        return false;
    }

    /** Guarda en el zurrón del cocinero (lo que no quepa se devuelve). */
    private ItemStack guardarEnInventario(ItemStack stack) {
        ItemStack resto = stack.copy();
        var mochila = villager.getInventory();
        for (int i = 0; i < mochila.getContainerSize() && !resto.isEmpty(); i++) {
            ItemStack dentro = mochila.getItem(i);
            if (!dentro.isEmpty() && ItemStack.isSameItemSameComponents(dentro, resto)) {
                int espacio = dentro.getMaxStackSize() - dentro.getCount();
                int mete = Math.min(espacio, resto.getCount());
                dentro.grow(mete);
                resto.shrink(mete);
            }
        }
        for (int i = 0; i < mochila.getContainerSize() && !resto.isEmpty(); i++) {
            if (mochila.getItem(i).isEmpty()) {
                mochila.setItem(i, resto.copy());
                resto = ItemStack.EMPTY;
            }
        }
        return resto;
    }

    // --- la faena -----------------------------------------------------------------------------------

    /**
     * Cocina: saca de la despensa lo que se puede cocinar ({@link VillagePantry#sePuedeCocinar}) y devuelve el
     * equivalente cocinado ({@link VillagePantry#cocinar}). Una pieza por una: no se inventa comida, solo se
     * <b>transforma</b> la que ya había (y por eso el contador de la aldea sube al doble con la carne). El
     * <b>combustible</b> lo gasta antes {@link #quemarLena()} (un tronco por tanda).
     */
    private void cocinar(ServerLevel level) {
        Container despensa = VillagePantry.despensa(level, center);
        if (despensa == null || puesto == null || !level.getBlockState(puesto).is(Blocks.SMOKER)) {
            return;
        }
        int cocinadas = 0;
        // Se busca una pieza cruda, se saca del barril y se guarda su versión cocinada.
        for (ItemStack cruda : CRUDAS) {
            while (cocinadas < COCINAR_MAX
                    && VillagePantry.sacar(despensa, s -> s.is(cruda.getItem()), 1) == 1) {
                ItemStack hecha = VillagePantry.cocinar(new ItemStack(cruda.getItem(), 1));
                // Si no cabe lo cocinado (despensa llena de crudo, que no se apila con lo cocido) se DEVUELVE
                // crudo lo que se sacó: ni se pierde la pieza ni se cocina para tirarlo.
                if (hecha.isEmpty() || !VillagePantry.guardar(despensa, hecha).isEmpty()) {
                    VillagePantry.guardar(despensa, new ItemStack(cruda.getItem(), 1));
                    break;
                }
                cocinadas++;
            }
        }
        // Y SI EN LA DESPENSA NO HABÍA NADA, SE COCINAN LOS HUEVOS DEL ALMACÉN. El jugador: *"no veo que el cocinero
        // haga huevos estrellados"*, y la causa es el PUENTE granjero→despensa: el ganadero deja los huevos en el
        // almacén, el granjero los trae… pero cuando la despensa va llena de verdura y semillas (medido: 432
        // zanahorias, 155 patatas, 261 semillas) **no le caben** (`despensaNoTraga`) y el cocinero nunca los ve. Los
        // huevos son la materia prima del cocinero y el almacén es de donde salen, así que se fríen de allí y las
        // tortillas se dejan en el almacén, de donde el granjero las sube a la despensa como cualquier comida.
        if (cocinadas == 0) {
            Container almacen = VillageStorage.almacen(level, center);
            if (almacen != null) {
                while (cocinadas < COCINAR_MAX
                        && VillagePantry.sacar(almacen, s -> s.is(Items.EGG), 1) == 1) {
                    ItemStack hecha = VillagePantry.cocinar(new ItemStack(Items.EGG, 1));
                    if (hecha.isEmpty() || !VillageStorage.guardar(level, center, hecha).isEmpty()) {
                        VillageStorage.guardar(level, center, new ItemStack(Items.EGG, 1)); // no cabe: se devuelve crudo
                        break;
                    }
                    cocinadas++;
                }
            }
        }
        if (cocinadas > 0) {
            level.playSound(null, puesto, SoundEvents.SMOKER_SMOKE, SoundSource.BLOCKS, 0.7F, 1.0F);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, puesto.getX() + 0.5D, puesto.getY() + 1.0D,
                    puesto.getZ() + 0.5D, 6, 0.15D, 0.15D, 0.15D, 0.01D);
            VillageManager.ponerSuceso(villager, "Cocino " + cocinadas + " piezas");
            DevilRpg.LOGGER.info("[Village] El cocinero: {} pieza(s) cocinadas con un tronco del almacen (aldea {})",
                    cocinadas, objectiveIndex);
        }
    }

    /** Lo que se puede cocinar, en el orden en que el cocinero lo va sacando del barril. */
    private static final List<ItemStack> CRUDAS = List.of(
            new ItemStack(Items.BEEF), new ItemStack(Items.PORKCHOP), new ItemStack(Items.CHICKEN),
            new ItemStack(Items.MUTTON), new ItemStack(Items.RABBIT), new ItemStack(Items.COD),
            new ItemStack(Items.SALMON), new ItemStack(Items.POTATO),
            // Y LOS HUEVOS de las gallinas del corral: el cocinero los hace estrellados (lo pidió el jugador) y, como
            // valen ración completa en la despensa, el pueblo entero come mejor con ellos.
            new ItemStack(Items.EGG));

    private int contarCrudoEnLaDespensa(ServerLevel level) {
        return VillagePantry.contar(VillagePantry.despensa(level, center), VillagePantry::sePuedeCocinar);
    }

    private void irAlDestino() {
        if (target != null) {
            VillageManager.caminarHacia(villager, target, VELOCIDAD);
        }
    }
}
