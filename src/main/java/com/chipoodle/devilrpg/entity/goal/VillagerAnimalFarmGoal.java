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

    /** Tope de animales por especie (vacas, ovejas, puercos). Lo que sobra, al sacrificio. */
    private static final int MAX_POR_ESPECIE = 6;
    /** Tope de gallinas (son más pequeñas y se crían solas). */
    private static final int MAX_GALLINAS = 8;
    /** Puntos de comida que tiene que tener la despensa para que el ganadero se lleve comida a los animales. */
    private static final int COMIDA_PARA_CRIAR = 24;
    /** Por debajo de esto, la aldea está apretada y el ganadero sacrifica un adulto (si hay de sobra). */
    private static final int COMIDA_PARA_SACRIFICAR = 12;
    /** Animales menos uno: nunca se baja de esta pareja (la granja tiene que poder seguir criando). */
    private static final int PAREJA_MINIMA = 2;
    /** Productos que lleva encima antes de bajarlos al almacén. */
    private static final int LLEVAR_AL_ALMACEN = 4;
    /** Radio en el que se recogen los drops de un sacrificio (y los huevos del corral). */
    private static final double RADIO_RECOGIDA = 6.0D;

    private enum Fase { CRIAR, SACRIFICAR, RECOGER, ENTREGAR }

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
            fase = Fase.ENTREGAR;
            target = VillageStorage.puntoDeApoyo(level, center);
            return target != null;
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
        // 3) CRÍA: solo si al pueblo le sobra comida y a esa especie le queda hueco.
        if (comida >= COMIDA_PARA_CRIAR) {
            EntityType<? extends Animal> aCriar = elegirEspecieACriar(corral);
            if (aCriar != null && hayComidaParaCriar(level, aCriar)) {
                Animal pareja = adultoSinEnamorar(level, aCriar);
                if (pareja != null) {
                    fase = Fase.CRIAR;
                    especie = aCriar;
                    target = pareja.blockPosition();
                    return true;
                }
            }
        }
        // Nada que hacer: a esperar un poco (y no consumir CPU buscando animales cada tick).
        restTicks = IDLE_REST_TICKS;
        return false;
    }

    @Override
    public void start() {
        workTicks = 0;
        stuckTicks = 0;
        mejorDistancia = Double.MAX_VALUE;
        irAlObjetivo();
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && !villager.isBaby() && stuckTicks < STUCK_LIMIT
                && !VillageManager.estaDescansando(villager);
    }

    @Override
    public void tick() {
        if (target == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        villager.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
        double alcance = fase == Fase.ENTREGAR ? VillageStorage.ALCANCE_ALMACEN
                : (fase == Fase.RECOGER ? 1.8D : REACH);
        double distancia = Math.sqrt(villager.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D,
                target.getZ() + 0.5D));
        if (distancia > alcance) {
            VillageManager.caminarHacia(villager, target, VELOCIDAD);
            if (distancia < mejorDistancia - 0.5D) {
                mejorDistancia = distancia;
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
            int cuantos = contarEspecie(corral, tipo);
            int sobra = cuantos - topeDe(tipo);
            if (sobra > mejorSobra) {
                mejorSobra = sobra;
                elegida = tipo;
            }
        }
        if (elegida == null && comida < COMIDA_PARA_SACRIFICAR) {
            // La aldea está apretada: se sacrifica de la especie más numerosa, pero nunca por debajo de la pareja.
            int mas = 0;
            for (EntityType<? extends Animal> tipo : VillageGenerator.especiesDelCorral()) {
                int cuantos = contarEspecie(corral, tipo);
                if (cuantos > PAREJA_MINIMA && cuantos > mas) {
                    mas = cuantos;
                    elegida = tipo;
                }
            }
        }
        if (elegida == null) {
            return null;
        }
        return adultoDe(level, elegida);
    }

    /** La especie a la que le toca cría: la que esté más lejos de su tope (y por debajo de él). */
    @Nullable
    private EntityType<? extends Animal> elegirEspecieACriar(List<Animal> corral) {
        EntityType<? extends Animal> elegida = null;
        int mejorHueco = 0;
        for (EntityType<? extends Animal> tipo : VillageGenerator.especiesDelCorral()) {
            int hueco = topeDe(tipo) - contarEspecie(corral, tipo);
            if (hueco > mejorHueco) {
                mejorHueco = hueco;
                elegida = tipo;
            }
        }
        return elegida;
    }

    /** ¿Hay en la despensa la comida de cría de esa especie (y de sobra para el pueblo)? */
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
        if (target != null) {
            VillageManager.caminarHacia(villager, target, VELOCIDAD);
        }
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
