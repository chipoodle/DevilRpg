package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.block.DoubleGateBlock;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.config.DevilRpgConfig;
import com.chipoodle.devilrpg.spawnprofile.AggressiveZombieSpawnProfile;
import com.chipoodle.devilrpg.spawnprofile.SpawnScaleProfile;
import com.chipoodle.devilrpg.survival.ThreatLevel;
import com.chipoodle.devilrpg.world.LairGenerator;
import com.chipoodle.devilrpg.world.VillageGenerator;
import com.chipoodle.devilrpg.world.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Set;

public class AggressiveZombieEntity extends Zombie {

    // Los limites de distancia (200..1500) y la base/escala de atributos viven en
    // AggressiveZombieSpawnProfile (SpawnScaleProfile), compartidos con su spawnRule sin acoplamiento.
    private static final SpawnScaleProfile SPAWN_PROFILE = AggressiveZombieSpawnProfile.INSTANCE;
    /** Planificador por pasos: los goals deciden el destino y los traversers siguen encargándose del terreno. */
    private final AggressiveZombiePathPlanner pathPlanner = new AggressiveZombiePathPlanner(this);

    /**
     * A2 · Navegación propia (APAGADA por defecto; ver {@link NavFeature}). Cuando está encendida, es el ÚNICO
     * dueño del movimiento. Las políticas se construyen delegando en las reglas que ya existen en esta clase
     * ({@link #protegidoPorLaAldea(BlockPos)}) para NO duplicar ni debilitar la protección de la aldea.
     */
    private final com.chipoodle.devilrpg.entity.nav.NavExecutor navExecutor;
    private final com.chipoodle.devilrpg.entity.nav.NavObstruction.Policies navPolicies;
    /** A2 REMEDIACIÓN 2 · goal de combate real; la suspensión del ejecutor depende de si ESTE goal mueve. */
    private PursueTargetGoal pursueTargetGoal;

    /** Contador de instrumentación: cuántas veces el ejecutor propio pidió una recuperación. */
    private int navRecoveryRequests;

    private com.chipoodle.devilrpg.entity.nav.NavObstruction.Policies buildNavPolicies() {
        return new com.chipoodle.devilrpg.entity.nav.NavObstruction.Policies() {
            @Override
            public boolean isProtected(BlockPos pos) {
                // A2 FASE 1 · "PROTEGIDO" NO PUEDE SIGNIFICAR "NO SE PUEDE ANDAR".
                // El planificador usaba `isProtected` como puerta de ENTRADA: cualquier celda del recinto se
                // clasificaba PROTECTED y nunca se encolaba, asi que la mision exterior (radio 64) era el unico
                // destino alcanzable y el asaltante no podia llegar NUNCA al interior real.
                // La regla de la casa ("dentro de la aldea no se rompe ni se construye") se conserva intacta en
                // `canBreak`/`canPlace`, que son las puertas de MODIFICACION. Caminar por el interior es
                // justo lo que un asedio tiene que poder hacer para llegar al centro.
                return false;
            }

            @Override
            public boolean canBreak(BlockPos pos) {
                return !protegidoPorLaAldea(pos);
            }

            @Override
            public boolean canPlace(BlockPos pos) {
                return !protegidoPorLaAldea(pos);
            }

            @Override
            public int maxSafeDrop() {
                return 3;
            }

            @Override
            public int waterDepthForSafeDrop() {
                return 2;
            }
        };
    }

    /**
     * A2 · <b>Objetivo de aproximación</b> para la navegación propia.
     * <p>
     * MEDIDO (`a2n-20261015-175840`): el centro de la aldea sale <b>{@code PROTECTED}</b> en
     * {@code NavObstruction.classify} (la comprobación de protección va PRIMERO, L141), y {@code PROTECTED} tiene
     * coste {@code -1} en el planificador, así que la celda <b>nunca se encola</b> ({@code metaEncolada=false}) y el
     * asaltante no se mueve: 0/8. No se toca la política de protección; lo que estaba mal era <b>pedir</b> una celda
     * que la propia política prohíbe.
     * <p>
     * MEDIDO también (`a2n-20261015-181227`): existe celda aceptable (p.ej. {@code 741, 69, 533 = JUMP_UP}) sobre la
     * recta de aproximación, fuera de {@code FENCE_RADIUS = 62} (con {@code LEVEL_RADIUS = 64}).
     * <p>
     * Por eso el destino es <b>el punto del propio rumbo del asaltante a distancia {@link #RADIO_DE_APROXIMACION}
     * del centro</b>: queda justo por fuera del muro (radio 62), así que es no protegido y alcanzable, y es
     * determinista (no depende de dónde esté el asaltante ni de buscar a ciegas). No se inventa una coordenada de
     * muro concreta: se usa la geometría generada por el propio generador.
     */
    public static final int RADIO_DE_APROXIMACION = 64;

    /** Celda de aproximación en el rumbo actual del asaltante, justo por fuera del muro. */
    public BlockPos objetivoDeAproximacion(final BlockPos centro) {
        return objetivoDeAproximacion(centro, RADIO_DE_APROXIMACION);
    }

    /** Punto de aproximación a un radio concreto del centro, con la Y tomada de la superficie. */
    public BlockPos objetivoDeAproximacion(final BlockPos centro, final int radio) {
        if (centro == null) {
            return null;
        }
        BlockPos yo = blockPosition();
        double dx = yo.getX() - centro.getX();
        double dz = yo.getZ() - centro.getZ();
        double len = Math.hypot(dx, dz);
        if (len < 1.0D) {
            return centro;
        }
        int x = centro.getX() + (int) Math.round(dx / len * radio);
        int z = centro.getZ() + (int) Math.round(dz / len * radio);
        // LA ALTURA SALE DE LA SUPERFICIE, NO DEL ASALTANTE. Medido (`a2n-20261015-190149`): tomando la Y del
        // propio asaltante (y=63, al pie del cerro) el objetivo quedaba DENTRO de la roca (BREAKABLE, fits=false)
        // igual que sus seis vecinos, y 6 de 8 asaltantes no conseguian ruta (moves=0, metaEncolada=false).
        // Se usa la MISMA convencion que `NavPlanner.buildCandidates`: `MOTION_BLOCKING_NO_LEAVES.getY()` es ya la
        // celda de los PIES (la primera celda libre por encima del bloque que bloquea el movimiento).
        int y = level().getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(x, centro.getY(), z)).getY();
        return new BlockPos(x, y, z);
    }

    // ================================================================================================
    // A2 FASE 1 · DESTINO FINAL = INTERIOR REAL DE LA ALDEA.
    // El defecto medido: la cadena de aproximacion terminaba en `RADIO_DE_APROXIMACION = 64`, es decir en el
    // ANILLO EXTERIOR (el muro esta en 62). Un asaltante podia "llegar" a su mision sin haber entrado nunca.
    // El destino final pasa a ser una celda PISABLE y ESTRICTAMENTE INTERIOR, derivada del centro autoritativo
    // persistido (Incremento A) y validada con la MISMA verdad de recinto que usa la victoria del asedio.
    // El anillo exterior se conserva como waypoint TACTICO previo (ver `cadenaDeAproximacion`).
    // ================================================================================================
    /** Margen interior respecto del muro: el destino nunca cae sobre el anillo (radio 62). */
    private static final int MARGEN_INTERIOR_DE_MISION = 4;

    /** Cache del destino interior: la busqueda solo se repite si cambia el centro o la cota. */
    private BlockPos misionInteriorCache;
    private BlockPos misionInteriorCentro;
    private int misionInteriorCota = Integer.MIN_VALUE;
    /** A2 FASE 1 · tambien se memoriza el resultado NEGATIVO, con reintento acotado (REMEDIACIÓN 2). */
    private final com.chipoodle.devilrpg.entity.nav.NegativeDestinationCache misionInteriorRetry =
            new com.chipoodle.devilrpg.entity.nav.NegativeDestinationCache();

    /**
     * ¿Es esta celda un destino de mision valido? De pie en ella: aire en pies y cuerpo, sin fluidos, con
     * soporte con colision debajo y <b>dentro del recinto real</b> (la misma funcion que cuenta invasores).
     */
    public boolean esCeldaDeMisionInteriorValida(final BlockPos pos, final BlockPos centro, final int cota) {
        if (pos == null || centro == null) {
            return false;
        }
        if (!level().hasChunkAt(pos)) {
            return false;
        }
        if (!level().getBlockState(pos).isAir() || !level().getBlockState(pos.above()).isAir()) {
            return false;
        }
        if (!level().getFluidState(pos).isEmpty() || !level().getFluidState(pos.above()).isEmpty()) {
            return false;
        }
        if (!com.chipoodle.devilrpg.entity.nav.NavObstruction.hasSolidFace(level(), pos.below())) {
            return false;
        }
        // LA VERDAD UNICA DEL RECINTO: radio del muro + banda de altura de la plaza. Es la misma que decide
        // si un asediador "ha entrado" y la que cuenta la victoria.
        return VillageManager.dentroDelRecinto(pos, centro, cota, VillageGenerator.FENCE_RADIUS);
    }

    /**
     * A2 FASE 1 · destino final de la mision: la celda pisable estrictamente interior mas cercana al centro
     * autoritativo. Busqueda ACOTADA (anillos hasta radio 58 y banda de altura del recinto); si no existe,
     * devuelve {@code null} y el llamante NO debe inventar una coordenada ni completar la mision fuera.
     */
    public BlockPos destinoInteriorDeLaAldea(final BlockPos centro) {
        if (centro == null) {
            return null;
        }
        final int cota = level() instanceof ServerLevel serverLevel
                ? VillageGenerator.cotaDeLaPlaza(serverLevel, centro)
                : centro.getY();
        if (misionInteriorCache != null && centro.equals(misionInteriorCentro) && cota == misionInteriorCota
                && esCeldaDeMisionInteriorValida(misionInteriorCache, centro, cota)) {
            return misionInteriorCache;
        }
        // A2 REMEDIACIÓN 2 · un fallo anterior no bloquea para siempre: se reintenta cada RETRY_TICKS.
        long ahora = level().getGameTime();
        if (!misionInteriorRetry.puedeBuscar(ahora)) {
            return null;
        }
        com.chipoodle.devilrpg.entity.nav.MissionDestination.Cell cell =
                com.chipoodle.devilrpg.entity.nav.MissionDestination.findInterior(
                        centro.getX(), centro.getZ(), cota,
                        VillageGenerator.FENCE_RADIUS, MARGEN_INTERIOR_DE_MISION,
                        VillageManager.RECINTO_DY_ABAJO, VillageManager.RECINTO_DY_ARRIBA,
                        (x, y, z) -> esCeldaDeMisionInteriorValida(new BlockPos(x, y, z), centro, cota));
        if (cell == null) {
            misionInteriorCache = null;
            misionInteriorCentro = centro.immutable();
            misionInteriorCota = cota;
            misionInteriorRetry.fallo(ahora);
            int intentos = misionInteriorRetry.intentosFallidos();
            if (intentos == 1 || intentos % 10 == 0) {
                DevilRpg.LOGGER.info("[Siege] asaltante #{}: sin celda interior valida en {} (intento {});"
                                + " se reintentara en {} ticks", getId(), centro.toShortString(), intentos,
                        com.chipoodle.devilrpg.entity.nav.NegativeDestinationCache.RETRY_TICKS);
            }
            return null;
        }
        int intentosPrevios = misionInteriorRetry.intentosFallidos();
        misionInteriorRetry.exito();
        misionInteriorCache = new BlockPos(cell.x(), cell.y(), cell.z()).immutable();
        misionInteriorCentro = centro.immutable();
        misionInteriorCota = cota;
        if (intentosPrevios > 0) {
            DevilRpg.LOGGER.info("[Siege] asaltante #{}: destino interior recuperado en {} tras {} intentos",
                    getId(), misionInteriorCache.toShortString(), intentosPrevios);
        }
        return misionInteriorCache;
    }

    /** Invalida el destino interior memorizado (centro nuevo, mundo cambiado, etc.). */
    public void olvidarDestinoInterior() {
        misionInteriorCache = null;
        misionInteriorCentro = null;
        misionInteriorCota = Integer.MIN_VALUE;
        misionInteriorRetry.reiniciar();
    }

    /**
     * A2 · <b>Cadena de aproximación en varias etapas</b>, para el problema VERTICAL.
     * <p>
     * MEDIDO (`a2n-20261015-204036`): con el objetivo de superficie 6 de 8 asaltantes ya consiguen ruta, pero
     * <b>z3 y z4 no</b>: arrancan a y=95 y y=115 y el objetivo está a y=71, es decir 24 y 44 bloques de desnivel
     * contra un {@code VERTICAL_RANGE = 6} del planificador. En una sola etapa ese salto es <b>inalcanzable por
     * construccion</b>, por mucho presupuesto que se le dé.
     * <p>
     * La solucion NO toca el planificador: se le dan <b>etapas intermedias</b> a radios decrecientes, todas con la
     * Y de la superficie en su propio punto. Cada etapa es un salto pequeno y legal, y el ejecutor va avanzando a la
     * siguiente al llegar. No se cambia el rango vertical, ni la heuristica, ni los costes, ni la seguridad de caida.
     */
    public java.util.List<BlockPos> cadenaDeAproximacion(final BlockPos centro) {
        java.util.List<BlockPos> etapas = new java.util.ArrayList<>();
        if (centro == null) {
            return etapas;
        }
        // A2 FASE 1 · EL DESTINO FINAL ES EL INTERIOR REAL, no el anillo exterior.
        BlockPos destinoInterior = destinoInteriorDeLaAldea(centro);
        if (destinoInterior == null) {
            // FAIL-CLOSED: sin celda interior valida no se entrega una cadena que pudiera "completarse" fuera
            // del recinto. Se deja constancia y el goal no envia objetivo (no se finge una llegada).
            // El aviso se limita: la cache negativa puede estar en espera y no debe llenar el log cada tick.
            long ahora = level().getGameTime();
            if (ultimoAvisoSinDestino == Long.MIN_VALUE || ahora - ultimoAvisoSinDestino >= 200L) {
                ultimoAvisoSinDestino = ahora;
                DevilRpg.LOGGER.warn("[Siege] asaltante #{}: la aldea {} no tiene celda interior pisable valida;"
                                + " NO se envia cadena de mision (no se puede completar fuera del recinto)",
                        getId(), centro.toShortString());
            }
            return etapas;
        }
        // SI YA ESTA DENTRO (o en el anillo de aproximacion), no se le manda volver a salir: el unico destino
        // que queda es el interior. Esto evita la cadena [waypoint exterior -> interior] cuando el asaltante
        // ya ha cruzado el muro.
        int cota = level() instanceof ServerLevel serverLevel
                ? VillageGenerator.cotaDeLaPlaza(serverLevel, centro)
                : centro.getY();
        double dx = blockPosition().getX() - centro.getX();
        double dz = blockPosition().getZ() - centro.getZ();
        if (Math.hypot(dx, dz) <= RADIO_DE_APROXIMACION
                || VillageManager.dentroDelRecinto(blockPosition(), centro, cota, VillageGenerator.FENCE_RADIUS)) {
            etapas = com.chipoodle.devilrpg.entity.nav.MissionDestination.conDestinoFinal(etapas, destinoInterior);
            registrarCadena(centro, destinoInterior, etapas);
            return etapas;
        }
        // ETAPA TACTICA PREVIA: el punto de aproximacion de superficie, justo por fuera del muro. Sigue siendo
        // un waypoint SEPARADO: llegar aqui solo avanza de etapa, NUNCA completa la mision.
        BlockPos finalAprox = objetivoDeAproximacion(centro, RADIO_DE_APROXIMACION);
        // CAUSA CONFIRMADA (Stage 4F -> 4G): la escalera se sembraba con `alturaPrevia = desde.getY()`, donde
        // `desde` era la ULTIMA etapa ya generada, cuya Y es la del objetivo final (71). Con alturaPrevia=71, la
        // guarda `y >= alturaPrevia` rechazaba TODAS las columnas y la cadena se quedaba en 3 etapas
        // (`de 3` en todas las transiciones de `a2n-20261015-215325`).
        // La escalera tiene que bajar desde la ALTURA REAL DEL ASALTANTE hasta el objetivo, no desde el objetivo.
        etapas.addAll(escaleraDescendente(centro, finalAprox));
        if (finalAprox != null && (etapas.isEmpty() || !etapas.get(etapas.size() - 1).equals(finalAprox))) {
            etapas.add(finalAprox);
        }
        // Y SIEMPRE, al final, el interior. Si el waypoint exterior ya fuera el interior, no se duplica.
        etapas = com.chipoodle.devilrpg.entity.nav.MissionDestination.conDestinoFinal(etapas, destinoInterior);
        // DIAGNOSTICO: solo se registra cuando la cadena normalizada CAMBIA (no por tick).
        registrarCadena(centro, destinoInterior, etapas);
        return etapas;
    }

    /** Firma de la ultima cadena diagnosticada, para no registrar en cada tick. */
    private int firmaCadenaDiagnosticada;
    /** A2 FASE 8 · la cadena puede cambiar al moverse; el log se limita a una linea cada 2 s. */
    private long ultimoCadenaLogTick = Long.MIN_VALUE;
    /** A2 REMEDIACIÓN 2 · el aviso de "sin destino interior" no puede salir en cada tick de espera. */
    private long ultimoAvisoSinDestino = Long.MIN_VALUE;

    /** Registra la cadena generada SOLO si su contenido normalizado ha cambiado. */
    private void registrarCadena(final BlockPos centro, final BlockPos finalAprox,
                                 final java.util.List<BlockPos> etapas) {
        int firma = 1;
        for (BlockPos p : etapas) {
            firma = 31 * firma + p.hashCode();
        }
        if (firma == firmaCadenaDiagnosticada) {
            return;
        }
        firmaCadenaDiagnosticada = firma;
        long ahora = level().getGameTime();
        if (ultimoCadenaLogTick != Long.MIN_VALUE && ahora - ultimoCadenaLogTick < 40L) {
            return; // el diagnostico no puede convertirse en un log por tick
        }
        ultimoCadenaLogTick = ahora;
        StringBuilder sb = new StringBuilder();
        sb.append("CADENA-DIAG yo=").append(blockPosition().toShortString())
                .append(" miY=").append(blockPosition().getY())
                .append(" centro=").append(centro.toShortString())
                .append(" final=").append(finalAprox == null ? "-" : finalAprox.toShortString())
                .append(" etapas=").append(etapas.size())
                .append(" firma=").append(firma);
        for (int i = 0; i < etapas.size() && i < 16; i++) {
            BlockPos p = etapas.get(i);
            int ySup = level().getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(p.getX(), centro.getY(), p.getZ())).getY();
            sb.append(" | e").append(i).append('=').append(p.toShortString())
                    .append("(sup=").append(ySup)
                    .append(protegidoPorLaAldea(p) ? ",PROTEGIDA" : "")
                    .append(')');
        }
        // VALIDACION GEOMETRICA DE CADA ETAPA: soporte real, hueco, proteccion y caida respecto a la anterior.
        for (int i = 0; i < etapas.size() && i < 16; i++) {
            BlockPos p = etapas.get(i);
            boolean soporte = soporteRealYHueco(p);
            String caida = i == 0 ? "n/a" : String.valueOf(etapas.get(i - 1).getY() - p.getY());
            sb.append(" | v").append(i).append(" soporte=").append(soporte)
                    .append(" caida=").append(caida)
                    .append(soporte ? "" : " INVALIDA-SIN-SOPORTE");
        }
        sb.append(" | necesidadConstruccion=").append(ultimaNecesidadDeConstruccion)
                .append(" | ajusteAterrizaje=").append(ultimoAjusteDeAterrizaje)
                .append(" | rechazoAterrizaje=").append(ultimoRechazoDeAterrizaje);
        DevilRpg.LOGGER.info("[Siege] {}", sb);
    }

    /**
     * ESCALERA DESCENDENTE de etapas: baja desde la ALTURA ACTUAL del asaltante hasta {@code objetivo}, avanzando
     * por columnas hacia el centro y emitiendo un escalon cada vez que la superficie real baja, partiendo cualquier
     * salto mayor que {@code VERTICAL_RANGE_POR_ESCALON} (para que cada etapa siga siendo visible al planificador,
     * cuyo VERTICAL_RANGE no se toca). Solo celdas no protegidas.
     */
    private java.util.List<BlockPos> escaleraDescendente(final BlockPos centro, final BlockPos objetivo) {
        java.util.List<BlockPos> salida = new java.util.ArrayList<>();
        if (centro == null || objetivo == null) {
            return salida;
        }
        BlockPos yo = blockPosition();
        double dx = yo.getX() - centro.getX();
        double dz = yo.getZ() - centro.getZ();
        double len = Math.hypot(dx, dz);
        if (len < 1.0D) {
            return salida;
        }
        int radioInicial = (int) Math.round(len);
        // ALTURA DE PARTIDA = la del asaltante (el error de 4F era usar aqui la del objetivo).
        int alturaPrevia = yo.getY();
        int emitidas = 0;
        // CAUSA CONFIRMADA (4G -> 4H): las etapas se emitian a ALTURAS INTERPOLADAS por encima de la superficie real
        // (`e0=597,90,597` con `sup=71`), es decir puntos FLOTANDO 19 bloques sobre el suelo: no son ocupables y por
        // eso ningun asaltante avanzaba de la etapa 0. Ahora cada etapa se emite EN LA SUPERFICIE muestreada, y el
        // descenso entre etapas consecutivas se limita al MAXIMO DE CAIDA SEGURA (3). Si el terreno cae mas de 3
        // bloques de golpe, la etapa intermedia se emite a `alturaPrevia - 3` SOLO si esa celda tiene soporte real;
        // si no lo tiene, NO se inventa la coordenada: se anota como decision de CONSTRUCCION y se corta la escalera.
        for (int radio = radioInicial; radio > RADIO_DE_APROXIMACION && emitidas < MAX_ETAPAS_DE_ESCALERA;
                radio -= 2) {
            int x = centro.getX() + (int) Math.round(dx / len * radio);
            int z = centro.getZ() + (int) Math.round(dz / len * radio);
            int y = level().getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, centro.getY(), z)).getY();
            BlockPos muestreada = new BlockPos(x, y, z);
            if (protegidoPorLaAldea(muestreada)) {
                continue; // dentro de la aldea no se construye ni se pisa: la politica no se toca
            }
            // VALIDACION DEL ATERRIZAJE (Stage 4I): la Y de la heightmap puede ser una celda de borde de
            // acantilado SIN soporte debajo. Si no vale, se busca hacia abajo en rango acotado un aterrizaje real.
            BlockPos cand = validarAterrizaje(muestreada);
            if (cand == null) {
                ultimoRechazoDeAterrizaje = muestreada.toShortString()
                        + " (heightmap sup=" + y + "): sin aterrizaje con soporte real en "
                        + BUSQUEDA_DE_ATERRIZAJE_MAXIMA + " bloques hacia abajo";
                continue;
            }
            if (y != cand.getY()) {
                ultimoAjusteDeAterrizaje = muestreada.toShortString() + " -> " + cand.toShortString()
                        + " (heightmap sin soporte; bajado " + (y - cand.getY()) + ")";
            }
            if (cand.getY() >= alturaPrevia) {
                continue; // solo interesa donde el terreno BAJA
            }
            int caida = alturaPrevia - cand.getY();
            if (caida <= CAIDA_SEGURA_MAXIMA) {
                salida.add(cand);
                emitidas++;
                alturaPrevia = cand.getY();
                continue;
            }
            // Caida mayor que el maximo seguro: se intenta un escalon intermedio a alturaPrevia - 3 con soporte real.
            BlockPos intermedio = new BlockPos(cand.getX(), alturaPrevia - CAIDA_SEGURA_MAXIMA, cand.getZ());
            if (!soporteRealYHueco(intermedio)) {
                // NO se inventa: se registra la decision de construccion y se corta (el terreno no da mas).
                ultimaNecesidadDeConstruccion = "caida de " + caida + " bloques en " + cand.toShortString()
                        + " desde " + new BlockPos(x, alturaPrevia, z).toShortString()
                        + ": hace falta escalon construido (ponerEscalon) porque la celda intermedia "
                        + intermedio.toShortString() + " no tiene soporte";
                break;
            }
            salida.add(intermedio);
            emitidas++;
            salida.add(cand);
            emitidas++;
            alturaPrevia = cand.getY();
        }
        return salida;
    }

    /** Ultima decision de construccion registrada (diagnostico): vacio si el terreno basto. */
    private String ultimaNecesidadDeConstruccion = "-";

    /** ¿La celda tiene bloque con colision justo debajo Y hueco libre para el cuerpo? (convencion del planificador) */
    private boolean soporteRealYHueco(final BlockPos celda) {
        net.minecraft.world.level.block.state.BlockState debajo = level().getBlockState(celda.below());
        if (debajo.isAir() || debajo.getCollisionShape(level(), celda.below()).isEmpty()) {
            return false;
        }
        return level().getBlockState(celda).isAir() && level().getBlockState(celda.above()).isAir();
    }
    /** Maximo de caida segura por etapa: el mismo limite que respeta la recuperacion. */
    private static final int CAIDA_SEGURA_MAXIMA = 3;

    /** Rango ACOTADO de busqueda hacia abajo para encontrar un aterrizaje con soporte real. */
    private static final int BUSQUEDA_DE_ATERRIZAJE_MAXIMA = 6;

    /**
     * A2 Stage 4I - VALIDACION DEL ATERRIZAJE. La Y de la heightmap NO garantiza suelo debajo: medido en
     * a2n-20261015-234056, e1=574,113,533(sup=113) salia con soporte=false (celda de borde de acantilado).
     * Si la celda muestreada no vale, se busca HACIA ABAJO en rango acotado el primer aterrizaje soportado y
     * despejado. Devuelve null si no hay ninguno: se RECHAZA, no se inventa la coordenada.
     */
    private BlockPos validarAterrizaje(final BlockPos muestreada) {
        if (soporteRealYHueco(muestreada) && !protegidoPorLaAldea(muestreada)) {
            return muestreada;
        }
        for (int dy = 1; dy <= BUSQUEDA_DE_ATERRIZAJE_MAXIMA; dy++) {
            BlockPos abajo = muestreada.below(dy);
            if (soporteRealYHueco(abajo) && !protegidoPorLaAldea(abajo)) {
                return abajo;
            }
        }
        return null;
    }

    /** Diagnostico: ultimo ajuste y ultimo rechazo de aterrizaje. */
    private String ultimoAjusteDeAterrizaje = "-";
    private String ultimoRechazoDeAterrizaje = "-";
    /** Tope de etapas de descenso: mantiene la cadena acotada. */
    private static final int MAX_ETAPAS_DE_ESCALERA = 14;

    /** Radios de las etapas de aproximación, de fuera hacia dentro. */
    private static final int[] RADIOS_DE_APROXIMACION = {96, 80, RADIO_DE_APROXIMACION};

    /** El ejecutor propio, para instrumentación. */
    public com.chipoodle.devilrpg.entity.nav.NavExecutor navExecutor() {
        return navExecutor;
    }

    /** Las políticas de obstrucción del ejecutor, para poder fotografiar una celda igual que lo hace el planner. */
    public com.chipoodle.devilrpg.entity.nav.NavObstruction.Policies navPolicies() {
        return navPolicies;
    }

    /** Recuperaciones pedidas por el ejecutor propio (instrumentación). */
    public int navRecoveryRequests() {
        return navRecoveryRequests;
    }

    /**
     * Punto de entrada ÚNICO de la navegación propia: los goals envían un objetivo, no navegan ellos. Devuelve
     * {@code true} si el ejecutor propio está al mando (y por tanto el goal NO debe llamar a su planner).
     */
    public boolean navegarConEjecutorPropio(final BlockPos destino) {
        if (!com.chipoodle.devilrpg.entity.nav.NavFeature.customNavigation()) {
            return false;
        }
        navExecutor.setObjective(destino);
        // A2 Stage 6A · la MISION del plan de construccion es el destino original; el plan solo produce
        // sub-objetivos temporales y al terminar se vuelve a planificar hacia esta misma mision.
        navExecutor.setPlanMision(destino);
        return true;
    }

    /**
     * A2 FASE 4 · OBJETIVO TACTICO (orilla, escape, reposicionamiento). NO toca la mision: el ejecutor
     * conserva {@code misionFinal} y, al llegar al destino tactico, vuelve a ella en vez de declarar ARRIVED.
     * Es la separacion que faltaba: {@link #navegarConEjecutorPropio(BlockPos)} hacia que la orilla pasara a
     * ser la mision y el asedio se "completara" en el agua.
     */
    public boolean navegarTacticamente(final BlockPos destino) {
        if (!com.chipoodle.devilrpg.entity.nav.NavFeature.customNavigation()) {
            return false;
        }
        return navExecutor.setTacticalObjective(destino);
    }

    private double spawnDistance = 0;  // Se guarda al spawnear el zombie
    private double spawnThreat = 1.0;  // Amenaza global al spawnear (combina con la distancia)
    private boolean attributesAdjusted = false; // Para asegurarnos de que solo se ajusta una vez

    /** Centro de la aldea objetivo (para que los zombies del asedio converjan hacia él). */
    private BlockPos villageCenter = null;
    /** El anillo es el mismo que genera la muralla; se calcula una vez por centro y se reutiliza en cada búsqueda. */
    private BlockPos centroAnilloCache = null;
    private Set<Long> columnasAnilloCache = Set.of();
    /** Una sola lectura de geometría y bloque para decidir si romper una posición abre una brecha. */
    private record EvaluacionMuro(BlockPos centroAldea, int cota, int dy, boolean columnaAnillo,
            boolean capaValida, boolean materialValido, boolean materialYCapaValidas, boolean esMuro) {}

    /** Si el asedio sigue activo (el zombie sigue marchando al centro). Se desactiva cuando la aldea cae. */
    private boolean goToCenterActive = true;
    /** "Hogar" del zombie (p. ej. el núcleo de una guarida): patrulla un radio alrededor suyo. */
    private BlockPos homePos = null;
    private int homeRadius = 0;
    /**
     * Índice de la aldea a la que va esta horda ({@code -1} = no va a ninguna aldea). Lo usan <b>los dos</b>
     * asedios: el clásico que dispara el jugador al llegar y las hordas que manda el mundo. Sirve para dos
     * cosas: apuntar como <b>defensores</b> a los jugadores que le peguen (y a sus minions) y descontar su
     * muerte de la ola, de forma que la recompensa solo se pague si de verdad se limpió la horda.
     */
    private int worldSiegeIndex = -1;
    /** A2 REMEDIACIÓN 2 · una sola notificación de baja confirmada por entidad. */
    private boolean bajaDeAsedioNotificada;
    /** Umbral de distancia para que el zombie pueda romper obsidiana (más lejos = más nivel). */
    private static final double OBSIDIAN_THRESHOLD = 700;
    /**
     * Tope del escalado que se aplica a la <b>velocidad</b> del asaltante (I165, 30-sep-2026): la vida y el daño
     * escalan con la distancia/amenaza (hasta ×4), pero la velocidad solo hasta <b>+60 %</b>. Con la base del zombi
     * normal (0.23) eso deja un asaltante que <b>siempre corre</b> —0.23 en la primera aldea, 0.37 en el extremo— en vez
     * de uno que va a un 31 % de la velocidad de un zombi hasta que el escalado lo rescata.
     */
    private static final double VELOCIDAD_TOPE_DE_ESCALA = 1.6D;

    public AggressiveZombieEntity(EntityType<? extends Zombie> type, Level world) {
        super(type, world);
        this.navPolicies = buildNavPolicies();
        this.navExecutor = new com.chipoodle.devilrpg.entity.nav.NavExecutor(this, this.navPolicies);
        // La recuperación del ejecutor propio REUTILIZA la que ya existe (romper/puentear/apilar/cavar), para no
        // duplicar lógica ni reglas de protección.
        // A2 Stage 6E · FIX B · si la ruta actual pide ROMPER/PUENTEAR una celda CONCRETA, se actúa sobre ESA celda
        // (el nodo llega en `nodoDeRuta`); en cualquier otro caso se sigue trabajando hacia la misión.
        this.navExecutor.setRecoveryAction((towards, nodoDeRuta, transicionDeRuta) -> {
            navRecoveryRequests++;
            if (nodoDeRuta != null
                    && transicionDeRuta == com.chipoodle.devilrpg.entity.nav.NavRoute.Transition.BREAK) {
                return romperNodoDeRuta(nodoDeRuta);
            }
            if (nodoDeRuta != null
                    && transicionDeRuta == com.chipoodle.devilrpg.entity.nav.NavRoute.Transition.BRIDGE) {
                return ponerEscalonConSoporteLateral(nodoDeRuta, towards);
            }
            return recoverNavigation(towards);
        });
        // A2 Stage 4Q · primitiva de COLOCACION para la transicion BUILD de la ruta. Reutiliza el camino de la casa
        // (`ponerEscalon`), que ya valida celda vacia, dos huecos, soporte con colision y proteccion. No se relaja.
        // A2 Stage 6E · FIX A · el plan de construccion del ejecutor usa ademas la variante ADHERIDA A UNA CARA, que
        // es la que permite bajar por una cara vertical; la primitiva de la casa queda intacta para el resto de usos.
        this.navExecutor.setPlacement(cell -> {
            if (protegidoPorLaAldea(cell)) {
                return false;
            }
            boolean ok = ponerEscalonConSoporteLateral(cell, villageCenter != null ? villageCenter : cell);
            // Verificacion REAL: solo se acepta si el bloque quedo colocado.
            return ok && !level().getBlockState(cell).isAir();
        });
    }

    /** Asigna el centro de la aldea a la que apunta este zombie. */
    public void setVillageCenter(BlockPos center) {
        if (center == null || !center.equals(this.villageCenter)) {
            olvidarDestinoInterior();
            // El centro cambio: la cadena de aproximacion ya no describe la misma aldea.
            firmaCadenaDiagnosticada = 0;
        }
        this.villageCenter = center;
    }

    // --- Abrirse paso cuando la geografía bloquea la marcha a la aldea ---------------------------------
    /**
     * Bloques que puede taladrar un zombie en UNA marcha a la aldea. El túnel es <b>lento y con presupuesto</b> a
     * propósito: una montaña grande tiene que poder aguantar y la aldea salvarse (lo pidió el jugador).
     */
    private static final int TUNEL_PRESUPUESTO = 40;
    /** Ticks entre bloque y bloque del túnel (2 s): lento a propósito. */
    private static final int TUNEL_ENTRE_BLOQUES = 40;
    /** Ticks entre tabla y tabla del puente (1 s): un abismo se cruza despacio, tablón a tablón. */
    private static final int PUENTE_ENTRE_BLOQUES = 20;
    /**
     * Radio (a partir del centro) dentro del cual el zombie <b>no construye</b>: solo tiene que <b>LLEGAR</b> al
     * perímetro. Lo usa el puente ({@link #puentearHacia}) para no tender tablones encima de la aldea ni de su muro.
     * <p>
     * <b>OJO</b>: esto es <b>horizontal y con 2 bloques de margen</b> (radio de la valla + 2) a propósito, porque el
     * muro está grueso (una columna, celdas en diagonal) y el puente no debe caerle encima. El veto de <b>ROMPER</b> no
     * vive aquí: es {@link #protegidoPorLaAldea}, que pregunta por el recinto de verdad
     * ({@link VillageManager#dentroDelRecinto}, con la altura del pueblo) y es el que deja la cara de fuera del muro
     * rompible para que el asedio abra la brecha.
     */
    private static final double NO_TOCAR_LA_ALDEA = VillageGenerator.FENCE_RADIUS + 2.0D;
    /** Altura máxima del muro generado desde la cota: columnas, dinteles y remates llegan a cota+3. */
    private static final int MURALLA_ALTO = 3;

    /** Bloques de túnel/puente que le quedan a este zombie en la marcha actual. */
    private int tunelRestante = TUNEL_PRESUPUESTO;

    /** I358 · Enfriamiento de las trazas del asedio: sin esto, un "no pica" sale cada tick y llena el registro. */
    private long ultimoAvisoDelAsedio = 0L;

    /** I359 · Para el informe de posicion (cada 5 s) y para avisar UNA sola vez de que ha entrado en el recinto. */
    private long ultimoInformeDePosicion = 0L;

    /** I359 · Ya avise de que este asaltante entro en el recinto. */
    private boolean aviseQueEntro = false;
    private int tunelEspera;
    private int puenteEspera;
    private long siguienteIntentoDeRecuperacion;
    private int fallosDeRecuperacion;
    /** A5.32/IMPLEMENT-01 · último bloque que se intentó romper y su veto temporal para replanificar un desvío. */
    private BlockPos ultimoIntentoDeRotura;
    private long evitarRoturaHasta;
    private int roturasFallidas;
    /** A5.32/IMPLEMENT-02 · límite compartido de 3 roturas por intento de recuperación. */
    private int roturasEnIntento;

    /** Vuelve a darle presupuesto de túnel (al empezar una marcha nueva a la aldea). */
    public void recargarTunel() {
        this.tunelRestante = TUNEL_PRESUPUESTO;
        // I358 · RENUEVA LA MARCHA: queda en el registro (con su altura) para poder seguirle la pista a un asaltante concreto.
        DevilRpg.LOGGER.info("[Siege] asaltante #{} RENUEVA la marcha en {} (y={}): tunel {} de nuevo", getId(),
                blockPosition().toShortString(), blockPosition().getY(), TUNEL_PRESUPUESTO);
        this.tunelEspera = 0;
        this.puenteEspera = 0;
        recargarAtravesadores();
    }

    /** Ticks de espera del túnel/puente. Se llama en cada tick del zombie (ver {@link #aiStep}). */
    private void tickTunel() {
        if (tunelEspera > 0) {
            tunelEspera--;
        }
        if (puenteEspera > 0) {
            puenteEspera--;
        }
        if (apilarEspera > 0) {
            apilarEspera--;
        }
        if (cavarEspera > 0) {
            cavarEspera--;
        }
    }

    public BlockPos getVillageCenter() {
        return villageCenter;
    }

    /**
     * Asigna un "hogar" con un radio de patrulla (p. ej. el núcleo de una guarida): el zombie se queda
     * rondando esa zona en vez de alejarse o pegarse al centro exacto.
     */
    public void setHome(BlockPos home, int radius) {
        this.homePos = home;
        this.homeRadius = radius;
    }

    public BlockPos getHomePos() {
        return homePos;
    }

    public int getHomeRadius() {
        return homeRadius;
    }

    /** Siempre que se asigne centro, el asedio arranca activo (y con presupuesto de túnel nuevo). */
    public void setGoToCenterActive(boolean active) {
        this.goToCenterActive = active;
        if (active) {
            recargarTunel();
        }
    }

    public boolean isGoToCenterActive() {
        return goToCenterActive;
    }

    /** Diagnóstico de aceptación N4: si el goal de persecución posee MOVE ahora. */
    public boolean pursueTargetActiveDiag() {
        return pursueTargetGoal != null && pursueTargetGoal.estaActivo();
    }

    /** Diagnóstico de aceptación N4: goals actualmente en ejecución. */
    public String runningGoalsDiag() {
        StringBuilder sb = new StringBuilder();
        for (WrappedGoal wrapped : goalSelector.getAvailableGoals()) {
            if (wrapped.isRunning()) {
                if (!sb.isEmpty()) {
                    sb.append(',');
                }
                sb.append(wrapped.getGoal().getClass().getSimpleName());
            }
        }
        return sb.isEmpty() ? "-" : sb.toString();
    }

    /** Marca este zombie como parte de una horda del mundo que va a por la aldea de ese objetivo. */
    public void setWorldSiegeIndex(int objectiveIndex) {
        if (this.worldSiegeIndex != objectiveIndex) {
            this.bajaDeAsedioNotificada = false;
        }
        this.worldSiegeIndex = objectiveIndex;
    }

    public int getWorldSiegeIndex() {
        return worldSiegeIndex;
    }

    /**
     * Si este zombie va a por una aldea, quien le haga daño (el jugador o un minion suyo) queda apuntado como
     * defensor de esa aldea. La recompensa por rechazar la horda se paga <b>solo a quien participa</b>: si la
     * aldea se defiende sola con sus golems, no cobra nadie.
     */
    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (worldSiegeIndex >= 0 && !level().isClientSide && level() instanceof ServerLevel serverLevel) {
            VillageManager.registerDefender(serverLevel, worldSiegeIndex, source.getEntity());
        }
        return super.hurt(source, amount);
    }

    /**
     * Al morir, se quita de la lista de atacantes VIVOS de su horda. La recompensa por rechazarla se paga solo
     * cuando esa lista se queda vacía, es decir cuando de verdad se han matado todos: así no se cobra por
     * alejarse y dejar que los chunks se descarguen (que a efectos del nivel es como si no existieran).
     */
    @Override
    public void die(@NotNull DamageSource cause) {
        // A2 REMEDIACIÓN 2 · la baja se confirma DESPUÉS de `super.die`: si otro mod cancela la muerte
        // (LivingDeathEvent) el raider sigue vivo y NO puede desaparecer del libro mayor.
        super.die(cause);
        if (isDeadOrDying()) {
            notificarBajaDeAsedioConfirmada();
        }
    }

    /** A2 REMEDIACIÓN 2 · una sola notificación de baja confirmada por entidad. */
    private void notificarBajaDeAsedioConfirmada() {
        if (bajaDeAsedioNotificada || worldSiegeIndex < 0 || level().isClientSide
                || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        bajaDeAsedioNotificada = true;
        VillageManager.onSiegeAttackerKilled(serverLevel, getUUID());
    }

    /**
     * Un <b>asediador</b> (asedio clásico o horda del mundo) <b>NO se descarta por alejarse</b> mientras siga en
     * campaña contra una aldea.
     * <p>
     * Es la causa medida de que el jugador se quedara <b>sin su punto</b> al ganar la escaramuza inicial: la ola se
     * lanza a 39-47 bloques del centro, así que los que caen a más de 32 del jugador <b>despawnean solos</b> (hostiles:
     * 1/800 por tick cuando llevan 600 ticks sin acción) y la lista de atacantes vivos se queda con sus UUID. Al no
     * morir nadie, el asedio se resuelve como "salvada sin limpiar la horda" y <b>no paga</b>. En el log del jugador:
     * *"Aldea 0 salvada sin limpiar la horda (3 atacantes sin confirmar): sin recompensa"*.
     * <p>
     * El descarte por lejanía es justo lo que hace el juego en dos sitios (a más de 128 bloques, y al azar a más de
     * 32), y los dos pasan por aquí, así que basta con este método. Al resolverse el asedio, el gestor les quita el
     * destino y el índice ({@code VillageManager.disableGoToCenter}), y entonces vuelven a poder descartarse como
     * cualquier otro zombie agresivo (no se acumulan por el mundo).
     */
    @Override
    public boolean removeWhenFarAway(double distanciaAlJugador) {
        return getWorldSiegeIndex() < 0 && super.removeWhenFarAway(distanciaAlJugador);
    }

    /**
     * Red de seguridad: si a un asediador se lo lleva el juego <b>sin morir</b> (un {@code /kill}, un descarte de
     * otro mod...), se le saca de la lista de atacantes vivos. Así un asedio no se queda <b>imposible de cobrar</b>
     * por un enemigo que ya no existe y que nadie puede matar.
     */
    /** A2: diagnostico de retirada, APAGADO por defecto. Se enciende con -Ddevilrpg.a2n.diagRetirada=true. */
    private static final boolean DIAGNOSTICO_DE_RETIRADA =
            Boolean.getBoolean("devilrpg.a2n.diagRetirada");

    @Override
    public void remove(@NotNull RemovalReason motivo) {
        if (DIAGNOSTICO_DE_RETIRADA && !level().isClientSide) {
            // Diagnostico A2: causa AUTORITATIVA de retirada de una entidad (no implica muerte).
            DevilRpg.LOGGER.info("[A2N] RETIRADA uuid={} motivo={} pos={} salud={} indiceAsedio={}",
                    getUUID(), motivo, blockPosition().toShortString(), getHealth(), worldSiegeIndex);
            DevilRpg.LOGGER.info("[A2N] RETIRADA-STACK", new Exception("retirada " + motivo));
        }
        // A2 REMEDIACIÓN 2 · KILLED/DISCARDED NO son muertes de combate confirmadas: `/kill` o un descarte
        // externo dejan el UUID en el libro mayor como DESCONOCIDO y el asedio se cierra por plazo, sin
        // recompensa. La única baja confirmada es la que pasa por `die()` y sobrevive a `super.die()`.
        super.remove(motivo);
    }

    /** ¿Puede este zombie romper obsidiana? (depende de su nivel = distancia de spawn). */
    public boolean canBreakObsidian() {
        return spawnDistance >= OBSIDIAN_THRESHOLD;
    }

    /** ¿Puede romper este bloque? (nunca los inquebrantables; obsidiana solo si su nivel lo permite). */
    private boolean canBreakBlock(BlockState state) {
        Block b = state.getBlock();
        if (b == Blocks.BEDROCK || b == Blocks.WATER || b == Blocks.LAVA || b == Blocks.AIR) {
            return false;
        }
        // Cualquier bloque inquebrantable (dureza -1), como el SELLO que blinda el núcleo de una guarida:
        // si el zombie pudiera picarlo, se saltaría el requisito de matar al cultivador.
        if (b.defaultDestroyTime() < 0.0F) {
            return false;
        }
        if (b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN) {
            return false; // IMPLEMENT-06: ordinary recovery never breaks obsidian
        }
        return true; // madera, tierra, grava, arena, lana...
    }

    /**
     * Rompe el bloque en la posición, respetando el límite de obsidiana y los bloques inquebrantables.
     * <p>
     * <b>Y ES AQUÍ DONDE SE CUMPLE LA REGLA DEL JUGADOR</b> (5-oct-2026): *"solo el muro perimetral es rompible; una vez
     * dentro ya no puede romper nada"*. Este es el ÚNICO sitio del asaltante que destruye un bloque, así que el veto de
     * {@link #protegidoPorLaAldea} se comprueba <b>en la puerta</b>: por muchos caminos que abran bloques (la marcha, el
     * paso hacia un objetivo y el escalón), <b>ninguno</b> puede colar una celda de dentro. Medido con el arnés: con la
     * comprobación solo en los caminos, el asaltante del asedio inicial se metía <b>24 bloques dentro</b> de la aldea
     * (r=65 → r=44 picando tierra y piedra del pueblo); la traza enseñó la celda abierta con
     * {@code dentroDeLaAldea=true}, o sea que la puerta es la que tiene que decidir, no el que llama.
     */
    /**
     * <b>INTERRUPTOR DE MEDIDA</b> (5-oct-2026, se quita al cerrar la medida): con esto en {@code false} la regla del
     * jugador queda <b>puenteada</b> en la puerta ({@link #breakBlockAt}), que es lo que mide <b>qué haría el asaltante
     * sin la regla</b>. El arnés lo imprime en su primera línea de la medida del muro, así que ninguna corrida se puede
     * confundir con la otra. En {@code true} —lo que va al jugador— la regla está puesta.
     */
    public static final boolean REGLA_DEL_MURO_ACTIVA = true;

    private static final boolean DIAG_BREAK = Boolean.getBoolean("devilrpg.a2n.diagBreak");

    private boolean breakBlockAt(BlockPos pos) {   // I356: dice si DE VERDAD rompio algo (el presupuesto solo se cobra entonces)
        if (DIAG_BREAK && !level().isClientSide && pos.getX() == 305 && pos.getY() == 120 && pos.getZ() == 300) {
            DevilRpg.LOGGER.info("[BREAKDBG] CALLER target={} estado={}", pos.toShortString(),
                    navExecutor.describe());
            DevilRpg.LOGGER.info("[BREAKDBG] STACK", new Exception("breakBlockAt caller"));
        }
        BlockPos atacantePos = blockPosition().immutable();
        BlockPos objetivo = pos.immutable();
        BlockState bs = level().getBlockState(objetivo);
        if (bs.hasBlockEntity()) {
            return false; // IMPLEMENT-06: block entities are never destroyed by recovery
        }
        int cota = cotaParaElRecinto();
        EvaluacionMuro muro = evaluarMuro(objetivo, cota, bs);
        if (REGLA_DEL_MURO_ACTIVA && protegidoPorLaAldea(objetivo, cota, muro.esMuro())) {
            registrarDecisionMuro(atacantePos, objetivo, bs, muro, false, null, "BLOQUEADO_PROTEGIDO");
            avisoDeQueNoPica("esta protegido por la aldea (dentro no se pica)", null, objetivo);
            return false; // dentro de la aldea no se pica; el muro perimetral sí (es la brecha)
        }
        // Y LA OBRA DEL PROPIO ASEDIO NO SE PICA (I239, medido el 9-oct-2026): los tablones que tiende el puente son su
        // camino, no un estorbo —el romper y el puente se peleaban y el monstruo se destruía su propio puente—. Va AQUÍ, en
        // la ÚNICA puerta que rompe, para que valga para los tres caminos que pican (el romper, el abridor de huecos y
        // el taladro del túnel) y no solo para el que me acordé de mirar.
        if (esTablonDelAsalto(objetivo)) {
            registrarDecisionMuro(atacantePos, objetivo, bs, muro, false, null, "BLOQUEADO_OBRA_DEL_ASEDIO");
            avisoDeQueNoPica("es obra del propio asedio (el puente)", null, objetivo);
            return false;
        }
        Block b = bs.getBlock();
        // I297 · SE EVITA PICAR ÁRBOLES (medido el 11-oct-2026): los troncos y hojas del terreno no son lo que cierra
        // el pueblo. Excepción estrecha para los troncos de la muralla: solo pasan si `esLaMuralla` verificó el anillo
        // generado, la capa y el tipo de bloque; así no se descarta el muro de logs ni se abren árboles cercanos.
        if ((bs.is(net.minecraft.tags.BlockTags.LOGS) && !muro.esMuro())
                || bs.is(net.minecraft.tags.BlockTags.LEAVES)) {
            registrarDecisionMuro(atacantePos, objetivo, bs, muro, false, null, "BLOQUEADO_MADERA_AJENA");
            avisoDeQueNoPica("es MADERA, y la madera no es lo que cierra el pueblo (I297)", b, objetivo);
            return false;
        }
        if (b.defaultDestroyTime() < 0.0F) {
            registrarDecisionMuro(atacantePos, objetivo, bs, muro, false, null, "BLOQUEADO_INQUEBRANTABLE");
            avisoDeQueNoPica("es inquebrantable", b, objetivo);
            return false; // inquebrantable
        }
        if (b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN) {
            registrarDecisionMuro(atacantePos, objetivo, bs, muro, false, null, "BLOQUEADO_OBSIDIANA");
            avisoDeQueNoPica("obsidiana fuera del alcance de la recuperacion ordinaria", b, objetivo);
            return false;
        }
        // IMPLEMENT-08 · acquire the exact breach target immediately before destruction. This is an
        // atomic first-claim-wins on the server thread; it does not replace the village objective.
        boolean claimed = false;
        ServerLevel claimedLevel = null;
        if (worldSiegeIndex >= 0 && level() instanceof ServerLevel serverLevel) {
            if (!VillageManager.tryClaimBreach(serverLevel, worldSiegeIndex, getUUID(), objetivo)) {
                return false;
            }
            claimed = true;
            claimedLevel = serverLevel;
        }
        // La clasificación de muro se conserva desde antes de romper: después de destroyBlock el bloque ya puede ser
        // aire y no debe volver a evaluarse para decidir si el suceso fue una brecha.
        boolean destruido = level().destroyBlock(objetivo, true);
        if (claimed) {
            VillageManager.releaseBreach(claimedLevel, worldSiegeIndex, getUUID(), objetivo);
        }
        String decisionBrecha = destruido && muro.esMuro() ? "EMITE_BRECHA"
                : (destruido ? "SIN_BRECHA_NO_ES_MURO" : "SIN_BRECHA_NO_SE_DESTRUYO");
        registrarDecisionMuro(atacantePos, objetivo, bs, muro, true, destruido, decisionBrecha);
        if (!destruido) {
            return false;
        }
        // Queda en el log (INFO, una línea por bloque que sí desapareció): distingue el intento de la destrucción real.
        // EL IDENTIFICADOR DEL ASALTANTE VA EN LA TRAZA (I241, 9-oct-2026): sin el, todas las lineas del asedio salen con
        // la misma aldea y NO se puede contar los bloques que lleva UN monstruo concreto (medido en la montana gruesa:
        // con mas atacantes del mundo no se podia aislar el tope de 40 bloques por marcha). `getId()` es corto y estable
        // mientras la entidad vive.
        DevilRpg.LOGGER.info("[Siege] asaltante #{} de la aldea {} pica {} en {} (le quedan {} de tunel)", getId(),
                worldSiegeIndex, b, objetivo.toShortString(), tunelRestante);
        // El evento depende de la destrucción válida, no de entrar al recinto: pasar por un portón abierto sin romper
        // un bloque no genera este suceso.
        if (muro.esMuro()) {
            DevilRpg.LOGGER.info("[Siege] asaltante #{} ABRE BRECHA en el muro: {} (tunel {}/{})", getId(),
                    objetivo.toShortString(), tunelRestante, TUNEL_PRESUPUESTO);
        }
        return true;
    }

    private void registrarDecisionMuro(BlockPos atacantePos, BlockPos pos, BlockState state, EvaluacionMuro muro,
            boolean intentoDestruccion, Boolean destruido, String decisionBrecha) {
        // Solo se escribe para posiciones que parecen muro por su columna o por material/capa, no para cada bloque
        // ajeno que el asaltante considere durante una marcha.
        if (!muro.columnaAnillo() && !muro.materialValido()) {
            return;
        }
        DevilRpg.LOGGER.info("[Siege] evaluacion muro atacante=#{} atacantePos={} objetivo={} bloque={} aldea={} "
                        + "cota={} dy={} "
                        + "columnaAnillo={} capaValida={} materialValido={} materialYCapaValidas={} esMuro={} "
                        + "intentoDestruccion={} destroyBlock={} decisionBrecha={}",
                getId(), atacantePos.toShortString(), pos.toShortString(), state,
                muro.centroAldea() == null ? "null" : muro.centroAldea().toShortString(), muro.cota(), muro.dy(),
                muro.columnaAnillo(), muro.capaValida(), muro.materialValido(), muro.materialYCapaValidas(),
                muro.esMuro(), intentoDestruccion, destruido == null ? "no_intentado" : destruido,
                decisionBrecha);
    }

    /**
     * Busca el bloque sólido rompible más cercano en la dirección de {@code towards} y lo rompe de forma
     * AGRESIVA: destruye el bloque del cuerpo (+1) y el de encima para abrir un hueco de dos bloques; si el objetivo
     * está arriba (necesita saltar en diagonal), también limpia el siguiente bloque para dejar espacio al escalón.
     * Además, aleatoriamente destruye bloques contiguos al central (misma fila, +1/+2 pegados) para
     * ensanchar el paso. Usado al ir al centro cuando el zombie está bloqueado por el muro.
     */
    /**
     * A5.32 · recuperación acotada de agua: rompe como máximo tres bloques que estorban en la dirección
     * hacia la orilla, reutilizando la única puerta segura ({@link #breakBlockAt}). No crea una ruta de
     * destrucción nueva ni salta protecciones.
     */
    public int romperRecuperacionHacia(BlockPos towards) {
        if (towards == null) {
            return 0;
        }
        BlockPos zPos = blockPosition();
        int sx = Integer.signum(towards.getX() - zPos.getX());
        int sz = Integer.signum(towards.getZ() - zPos.getZ());
        int rotos = 0;
        for (int dy = 0; dy <= 2 && rotos < 3; dy++) {
            for (int dx = -1; dx <= 1 && rotos < 3; dx++) {
                for (int dz = -1; dz <= 1 && rotos < 3; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    if (dx * sx + dz * sz < 0) {
                        continue; // no romper hacia atrás
                    }
                    BlockPos candidate = new BlockPos(zPos.getX() + dx, zPos.getY() + dy, zPos.getZ() + dz);
                    if (protegidoPorLaAldea(candidate)) {
                        continue;
                    }
                    BlockState state = level().getBlockState(candidate);
                    if (state.isAir() || state.getCollisionShape(level(), candidate).isEmpty()
                            || !canBreakBlock(state)) {
                        continue;
                    }
                    if (breakBlockAt(candidate)) {
                        rotos++;
                    }
                }
            }
        }
        return rotos;
    }

    private boolean breakBlockTowards(BlockPos towards) {
        // TÚNEL LENTO Y CON PRESUPUESTO: un bloque cada TUNEL_ENTRE_BLOQUES (2 s) y como mucho TUNEL_PRESUPUESTO por
        // marcha. Una montaña grande aguanta y la aldea se salva (lo pidió el jugador).
        if (tunelRestante <= 0 || tunelEspera > 0) {
            if (tunelRestante <= 0) {
                // I358 · SE LE ACABARON LOS 40: antes esto era mudo y parecia que el asaltante no hacia nada.
                avisoDeQueNoPica("se le ACABARON los 40 bloques de esta marcha", null, blockPosition());
            }
            return false;
        }
        roturasEnIntento = 0;
        if (Boolean.getBoolean("n4.trace")) {
            DevilRpg.LOGGER.info("[RECOVERY] attempt-start towards={} pos={}", towards.toShortString(),
                    blockPosition().toShortString());
        }
        BlockPos zPos = blockPosition();
        int sx = Integer.signum(towards.getX() - zPos.getX());
        int sz = Integer.signum(towards.getZ() - zPos.getZ());
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        // I353 - TAMBIEN HACIA ARRIBA, hasta la altura del objetivo (medido en su asedio del 12-oct-2026: 228 picados a
        // y=60-61, POR DEBAJO del suelo del pueblo a 63, con 0 brechas: cavaban a ras de lago y agotaban los 40 sin entrar).
        // El tope es 3 y se mantiene la penalizacion de dy, que hace preferir lo bajo.
        int altoQueCavar = Math.max(1, Math.min(3, towards.getY() - zPos.getY() + 1));
        for (int dy = 0; dy <= altoQueCavar; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos candidate = new BlockPos(zPos.getX() + dx, zPos.getY() + dy, zPos.getZ() + dz);
                    if (protegidoPorLaAldea(candidate)) {
                        continue; // a la aldea no se le cava (la MURALLA sí: es la brecha del asedio)
                    }
                    BlockState bs = level().getBlockState(candidate);
                    // ¿ESTORBA? Por la FORMA DE COLISIÓN, no por `isSolid()`: la valla no es un cubo y quedaba fuera,
                    // y es justo lo que cierra la aldea (ver `blockingBlockAhead`, con la medida del arnés).
                    if (!bs.isAir() && !bs.getCollisionShape(level(), candidate).isEmpty() && canBreakBlock(bs)) {
                        double score = Math.abs(dx - sx) + Math.abs(dz - sz) + dy * 0.5D;
                        if (score < bestScore) {
                            bestScore = score;
                            best = candidate;
                        }
                    }
                }
            }
        }
        if (best == null) {
            avisoDeQueNoPica("no tiene nada rompible alrededor", null, blockPosition());
            return false;
        }
        // I358 · LA TRAZA QUE HACE FALTA PARA DEDUCIRLO DEL REGISTRO: donde esta y a que altura, donde esta el OBJETIVO y a
        // que altura, el desnivel, hasta que dy va a cavar (la alturaQueCavar de I353) y el tunel que le queda. Con
        // enfriamiento. Con esto se puede ver si cava A LA ALTURA DEL SUELO o a ras de lago, que es el fallo de I353.
        if (level().getGameTime() - ultimoAvisoDelAsedio > 40L) {
            ultimoAvisoDelAsedio = level().getGameTime();
            DevilRpg.LOGGER.info("[Siege] asaltante #{} esta en {} (y={}) · OBJETIVO {} (y={}) · desnivel {} · va a cavar"
                            + " hasta dy={} · tunel {}/{}", getId(), blockPosition().toShortString(), blockPosition().getY(),
                    towards.toShortString(), towards.getY(), towards.getY() - blockPosition().getY(),
                    Math.max(1, Math.min(3, towards.getY() - blockPosition().getY() + 1)), tunelRestante, TUNEL_PRESUPUESTO);
        }
        // Destruir el bloque principal.
        // I356 · EL PRESUPUESTO SOLO SE COBRA SI SE ROMPIO DE VERDAD. Antes se cobraba siempre, asi que cada tronco que la
        // regla de I297 se NIEGA a picar le costaba un bloque de los 40: con la arboleda alrededor del pueblo, los 40 se iban
        // en arboles que no se rompian y el asaltante se quedaba fuera (medido en su registro: 228 picados, casi todos a
        // y=60-61, con 0 brechas). Ahora, si no rompe, NO gasta: sigue buscando por donde entrar.
        ultimoIntentoDeRotura = best.immutable();
        boolean roto = romperAcotado(best);
        if (roto) {
            if (tunelRestante == TUNEL_PRESUPUESTO) {
                // Primer bloque de esta marcha: queda en el log (INFO) para poder comprobar que la horda se abre paso.
                DevilRpg.LOGGER.info("[Siege] asaltante #{} empieza a TALADRAR hacia la aldea en {} (presupuesto {} bloques)",
                        getId(), best, TUNEL_PRESUPUESTO);
            }
            tunelRestante--;
            tunelEspera = TUNEL_ENTRE_BLOQUES;
            particulasDeTrabajo(best);
        }
        // Un bloque del cuerpo sin el espacio libre encima no abre paso por un muro de dos bloques, aunque el destino
        // esté a la misma altura. Romper ambos niveles conserva el hueco transitable y aplica la regla del recinto a
        // cada celda por separado.
        BlockPos above = best.above();
        boolean trabajoRealizado = roto;
        if (canBreakBlock(level().getBlockState(above)) && !protegidoPorLaAldea(above)) {
            trabajoRealizado |= romperAcotado(above);
        }
        // Dirección principal hacia el objetivo (para saber si el objetivo está más arriba).
        boolean targetAbove = towards.getY() > zPos.getY();
        int dirX = Integer.signum(best.getX() - zPos.getX());
        int dirZ = Integer.signum(best.getZ() - zPos.getZ());
        // Si el objetivo está arriba, destruir también el bloque +2 para dejar espacio al salto/escalón.
        if (targetAbove) {
            BlockPos higher = above.above();
            if (canBreakBlock(level().getBlockState(higher)) && !protegidoPorLaAldea(higher)) {
                trabajoRealizado |= romperAcotado(higher);
            }
        }
        // Aleatoriamente destruir un bloque contiguo al central (misma fila +1, o +1/+2 al lado) para
        // ensanchar el paso, con probabilidad moderada.
        RandomSource random = level().random;
        if (random.nextFloat() < 0.5F) {
            int side = random.nextBoolean() ? 1 : -1;
            BlockPos adjacent;
            if (dirX != 0) {
                adjacent = new BlockPos(best.getX(), best.getY(), best.getZ() + side);
            } else {
                adjacent = new BlockPos(best.getX() + side, best.getY(), best.getZ());
            }
            if (canBreakBlock(level().getBlockState(adjacent)) && !protegidoPorLaAldea(adjacent)) {
                trabajoRealizado |= romperAcotado(adjacent);
            }
            // Si objetivo arriba, también el +2 del contiguo.
            if (targetAbove) {
                BlockPos adjacentAbove = adjacent.above();
                if (canBreakBlock(level().getBlockState(adjacentAbove)) && !protegidoPorLaAldea(adjacentAbove)) {
                    trabajoRealizado |= romperAcotado(adjacentAbove);
                }
            }
        }
        return trabajoRealizado;
    }

    /**
     * Recuperación física cuando el planner no encuentra ruta o se atasca. Hace una acción acotada por llamada y
     * aumenta la espera tras cada fallo para no repetir el picado en el mismo sitio bloqueado.
     */
    private boolean romperAcotado(BlockPos pos) {
        if (!com.chipoodle.devilrpg.entity.nav.ObstructionRecoveryPolicy.puedeRomper(roturasEnIntento)) {
            return false;
        }
        BlockState state = level().getBlockState(pos);
        boolean protectedTarget = protegidoPorLaAldea(pos);
        boolean wall = esLaMuralla(pos, cotaParaElRecinto());
        com.chipoodle.devilrpg.entity.nav.BlockInteractionPolicy.Decision decision =
                com.chipoodle.devilrpg.entity.nav.BlockInteractionPolicy.decide(
                        new com.chipoodle.devilrpg.entity.nav.BlockInteractionPolicy.Candidate(
                                protectedTarget, wall, esTablonDelAsalto(pos), state.hasBlockEntity(),
                                state.getBlock() == Blocks.BEDROCK || state.getBlock().defaultDestroyTime() < 0.0F,
                                state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN), false,
                                !state.isAir() && !state.getCollisionShape(level(), pos).isEmpty(), false, false,
                                roturasEnIntento, level().getGameTime(), evitarRoturaHasta));
        if (decision.action() != com.chipoodle.devilrpg.entity.nav.BlockInteractionPolicy.Action.BREAK) {
            return false;
        }
        if (breakBlockAt(pos)) {
            roturasEnIntento++;
            if (Boolean.getBoolean("n4.trace")) {
                DevilRpg.LOGGER.info("[RECOVERY] removed target={} attemptCount={}/3", pos.toShortString(),
                        roturasEnIntento);
            }
            return true;
        }
        return false;
    }

    boolean recoverNavigation(BlockPos destination) {
        // A2 Stage 4J/4K: si el descenso esta bloqueado por un salto mayor que la caida segura, la recuperacion
        // que toca es CONSTRUIR un escalon descendente.
        // CAUSA CONFIRMADA (Stage 4J -> 4K): la guarda anterior era `&& escaleraPisada()`, y `escaleraPisada()`
        // exige `escaleraUltimoColocado != null`, campo que SOLO se inicializa DENTRO del constructor de escalera.
        // Las dos condiciones eran mutuamente excluyentes en la primera llamada, asi que la construccion NUNCA
        // empezaba (`colocados=0`, `rechazo=-` en `a2n-20261016-004834`). Aqui la guarda se limita a "este descenso
        // exige construir"; el ancla la siembra el propio constructor desde una posicion VALIDADA.
        if (descensoExigeConstruir(destination)) {
            boolean actuado = construirEscalonDescendente(destination);
            if (actuado) {
                return true;
            }
            // Si no pudo colocar, se sigue con la recuperacion normal (no se bloquea el resto de la recuperacion).
        }
        long now = level().getGameTime();
        if (now < siguienteIntentoDeRecuperacion) {
            return false;
        }
        boolean evitarRotura = ultimoIntentoDeRotura != null
                && !com.chipoodle.devilrpg.entity.nav.ObstructionRecoveryPolicy.shouldAttemptBreach(
                        roturasFallidas, now, evitarRoturaHasta);
        ultimoIntentoDeRotura = null;
        boolean acted = false;
        if (!evitarRotura) {
            acted = breakBlockTowards(destination);
            if (!acted && ultimoIntentoDeRotura != null
                    && !level().getBlockState(ultimoIntentoDeRotura).isAir()) {
                roturasFallidas++;
                evitarRoturaHasta = now + (roturasFallidas >= 3 ? 400L : 200L);
                com.chipoodle.devilrpg.DevilRpg.LOGGER.info("[Siege] asaltante #{}: rotura fallida en {} ({}/3);"
                                + " se replanifica un desvío", getId(), ultimoIntentoDeRotura.toShortString(),
                        roturasFallidas);
            }
        }
        if (!acted) {
            acted = puentearHacia(destination);
        }
        if (!acted && destination.getY() > blockPosition().getY() + 1) {
            acted = apilarBloqueParaSubir(destination);
        }
        if (!acted && destination.getY() < blockPosition().getY() - 1) {
            acted = cavarHaciaAbajo(destination);
        }
        if (acted) {
            fallosDeRecuperacion = 0;
            roturasFallidas = 0;
            ultimoIntentoDeRotura = null;
            siguienteIntentoDeRecuperacion = now + 40L;
            pathPlanner.terrainChanged();
        } else {
            fallosDeRecuperacion++;
            int backoff = Math.min(200, 20 << Math.min(fallosDeRecuperacion, 3));
            siguienteIntentoDeRecuperacion = now + backoff;
        }
        return acted;
    }

    // No es sensible al sol: puede patrullar tanto de dia como de noche sin quemarse.
    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    /**
     * ¿Esa posición cae <b>dentro del recinto</b> de la aldea (o en su muro)? Ahí el zombie no rompe ni construye: lo
     * único que tiene que hacer es <b>llegar</b> al muro. El muro, las casas, la huerta y el kiosco están todos dentro
     * de ese recinto, así que esta sola comprobación protege toda la obra del pueblo.
     * <p>
     * <b>EL RECINTO ES EL DE LA ALDEA, NO UN DISCO</b> (5-oct-2026): antes era solo la distancia <b>horizontal</b>
     * ({@code NO_TOCAR_LA_ALDEA} = radio de la valla + 2) y la ÚNICA verdad de «dentro de la aldea» del proyecto es
     * {@link VillageManager#dentroDelRecinto}, que además exige estar <b>a la altura del pueblo</b>: un asaltante en
     * una cueva 60 bloques por debajo de la plaza <b>no</b> está dentro (no ha pasado los muros) y con el disco se le
     * vetaba picar su propio túnel. Se pregunta a la misma función que usa {@code hayEnemigosDentro}, para que «dentro»
     * signifique lo mismo en todo el mod.
     */
    private boolean dentroDeLaAldea(BlockPos pos, int cota) {
        if (villageCenter == null) {
            return false;
        }
        // LA PREGUNTA ES LA MISMA DEL PROYECTO (`VillageManager.dentroDelRecinto`, recinto en XZ + banda de altura
        // sobre la cota), con el monstruo puesto DE PIE EN ESA CELDA: el bloque que se va a picar está dentro si un monstruo
        // de pie ahí lo estaría. Así la cara de FUERA del muro —el pie de la brecha, a radio 62,5— queda fuera del
        // recinto y el asedio puede abrirla, y la de dentro no.
        return VillageManager.dentroDelRecinto(pos, villageCenter, cota, VillageGenerator.FENCE_RADIUS);
    }

    /**
     * El <b>disco horizontal</b> de la aldea con sus dos bloques de margen ({@link #NO_TOCAR_LA_ALDEA}), que es la
     * única regla que usa el <b>puente</b> para no tender tablones sobre el pueblo ni sobre su muro. No lleva altura a
     * propósito: lo que se veta ahí es <b>construir</b> encima, no romper (para romper está {@link #protegidoPorLaAldea},
     * que sí pregunta por el recinto de verdad).
     */
    private boolean dentroDelDiscoDeLaAldea(BlockPos pos) {
        if (villageCenter == null) {
            return false;
        }
        double dx = pos.getX() - villageCenter.getX();
        double dz = pos.getZ() - villageCenter.getZ();
        return dx * dx + dz * dz <= NO_TOCAR_LA_ALDEA * NO_TOCAR_LA_ALDEA;
    }

    /** Ticks de juego en los que se midió {@link #cotaDelRecinto} (ver {@link #cotaParaElRecinto()}). */
    private long cotaDelRecintoTick = Long.MIN_VALUE;
    /** Cota medida en {@link #cotaDelRecintoTick}: se pide UNA vez por tick y no por celda candidata. */
    private int cotaDelRecinto;

    /**
     * La <b>cota de la aldea AHORA</b> (el nivel al que se anda), para las dos preguntas del recinto
     * ({@link #dentroDeLaAldea} y {@link #esLaMuralla}). Se mide <b>una vez por tick</b> y se recuerda, porque las
     * decisiones de romper preguntan por 18 celdas candidatas de golpe.
     * <p>
     * Y por qué no vale la Y del {@code villageCenter} (medido con el arnés el 5-oct-2026): ese centro se fija al
     * <b>nacer</b> el asaltante y no se entera de que el pueblo suba de nivel. En la corrida que lo destapó, el
     * asaltante llevaba un centro <b>4 bloques por debajo</b> de la cota real, y con esa Y la banda de la muralla
     * ({@code esLaMuralla}) se estiraba hacia arriba: bloques de tierra y piedra a <b>24 bloques DENTRO</b> del pueblo
     * (r=38..46) contaban como «muralla» y el túnel entraba en la aldea. Es un caso real: la migración del terreno
     * sube el pueblo y la cota de la aldea cambia.
     */
    private int cotaParaElRecinto() {
        long ahora = level().getGameTime();
        if (ahora != cotaDelRecintoTick) {
            cotaDelRecintoTick = ahora;
            cotaDelRecinto = villageCenter == null ? level().getSeaLevel()
                    : (level() instanceof ServerLevel serverLevel
                            ? VillageGenerator.cotaDeLaPlaza(serverLevel, villageCenter)
                            : villageCenter.getY());
        }
        return cotaDelRecinto;
    }

    /**
     * ¿Es un bloque que realmente forma parte del muro generado? Se usa la misma lista rasterizada que {@code fence},
     * no una banda de radio: el muro no cae exactamente sobre el círculo matemático. También se comprueban la cota
     * actual, la capa y el material para que un bloque corriente dentro del recinto siga protegido.
     */
    private boolean esLaMuralla(BlockPos pos, int cota) {
        return evaluarMuro(pos, cota, level().getBlockState(pos)).esMuro();
    }

    private EvaluacionMuro evaluarMuro(BlockPos pos, int cota, BlockState state) {
        int dy = pos.getY() - cota;
        boolean capaValida = dy >= 0 && dy <= MURALLA_ALTO;
        boolean materialValido = state.is(Blocks.OAK_LOG) || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.COBBLESTONE_STAIRS) || state.getBlock() instanceof DoubleGateBlock;
        boolean materialYCapaValidas = capaValida && ((state.is(Blocks.OAK_LOG) && dy <= 1)
                || (state.is(Blocks.COBBLESTONE) && dy <= MURALLA_ALTO)
                || (state.is(Blocks.COBBLESTONE_STAIRS) && dy == MURALLA_ALTO)
                || (state.getBlock() instanceof DoubleGateBlock && dy <= 2));
        if (villageCenter == null) {
            return new EvaluacionMuro(null, cota, dy, false, capaValida, materialValido,
                    materialYCapaValidas, false);
        }
        BlockPos centroXZ = new BlockPos(villageCenter.getX(), 0, villageCenter.getZ());
        if (!centroXZ.equals(centroAnilloCache)) {
            centroAnilloCache = centroXZ;
            columnasAnilloCache = VillageGenerator.columnasDelAnilloDelMuro(centroXZ);
        }
        BlockPos columnaXZ = new BlockPos(pos.getX(), 0, pos.getZ());
        boolean columnaAnillo = columnasAnilloCache.contains(columnaXZ.asLong());
        return new EvaluacionMuro(villageCenter, cota, dy, columnaAnillo, capaValida, materialValido,
                materialYCapaValidas, columnaAnillo && materialYCapaValidas);
    }

    /**
     * ¿Esa posición está <b>protegida</b> por la <b>aldea</b> (no se pica)? Es la única regla que usan los dos caminos
     * que rompen bloques (la marcha al centro y el que se abre paso hacia un objetivo), para que no se pueda colar por
     * un lado lo que se veta por el otro.
     * <p>
     * <b>LA REGLA DEL JUGADOR</b> (5-oct-2026): *"solo el muro perimetral es rompible; una vez dentro ya no puede
     * romper nada"*. Traducida a esta función:
     * <ul>
     *   <li><b>Fuera del recinto</b>: campo abierto — se pica, y ahí es donde el asedio <b>abre la brecha</b> en el
     *       muro.</li>
     *   <li><b>Dentro del recinto</b>: solo se exceptúa el bloque material del muro que coincide con una columna del
     *       anillo generado y su capa real. Las celdas interiores vecinas, las casas, la huerta, el kiosco y la plaza
     *       siguen protegidos.</li>
     * </ul>
     * <p>
     * <b>EL CAMPO DE FUERZA ES DE LA ALDEA YA GANADA</b> (lo corrigió el jugador: *"los zombies en el asedio inicial,
     * cuando se llega a la aldea, SÍ pueden romper todo lo necesario; pero cuando se gana el asedio la aldea genera un
     * campo de fuerza que no permite spawneo de ningún enemigo dentro y tampoco les permite romper nada"*). Estaba
     * <b>siempre</b> activo, así que en el asedio inicial —el que el jugador tiene que ganar— los asaltantes no podían
     * romper nada de dentro y se quedaban fuera (medido con el arnés: un asaltante contra un cerco de piedra cerrado
     * picó 2 bloques en 3 minutos y no entró).
     * <ul>
     *   <li>Asedio <b>sin resolver</b> (el inicial): la aldea no está protegida — se rompe <b>lo que haga falta</b>
     *       <b>de fuera adentro</b>, empezando por el muro, para llegar al centro.</li>
     *   <li>Asedio <b>ya ganado</b>: la <b>muralla</b> (el anillo de la valla) y todo lo de fuera siguen rompibles,
     *       para que los asaltos posteriores puedan entrar y hacerse escaleras, pero una vez dentro no tocan nada.</li>
     * </ul>
     */
    private boolean protegidoPorLaAldea(BlockPos pos) {
        int cota = cotaParaElRecinto(); // UNA medida por tick, y la MISMA para las dos preguntas
        EvaluacionMuro muro = evaluarMuro(pos, cota, level().getBlockState(pos));
        return protegidoPorLaAldea(pos, cota, muro.esMuro());
    }

    private boolean protegidoPorLaAldea(BlockPos pos, int cota, boolean esMuro) {
        if (!dentroDeLaAldea(pos, cota)) {
            return false; // fuera: campo abierto (aquí se hacen las escaleras de bloques para entrar)
        }
        if (esMuro) {
            return false; // solo el bloque real del anillo generado: es la brecha por la que entra un asedio
        }
        boolean saved = false;
        boolean siege = false;
        boolean uncertain = true;
        if (level() instanceof ServerLevel serverLevel) {
            saved = com.chipoodle.devilrpg.world.VillageManager.estaProtegida(serverLevel, pos);
            siege = worldSiegeIndex >= 0
                    && com.chipoodle.devilrpg.world.VillageManager.isVillageUnderAttack(serverLevel, worldSiegeIndex);
            uncertain = worldSiegeIndex < 0 && !saved;
        }
        return !com.chipoodle.devilrpg.world.VillageTerrainPolicy.allowsModification(
                true, saved, saved, siege, uncertain, esMuro);
    }

    /**
     * ¿La aldea de este asaltante es de las que <b>ya se ganaron</b> (tiene su campo de fuerza)? Un asaltante sin aldea
     * asignada ({@code worldSiegeIndex < 0}) no tiene campo de fuerza: es el caso del asedio inicial.
     */
    private boolean elAsedioYaSeGano() {
        if (worldSiegeIndex < 0 || !(level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        return com.chipoodle.devilrpg.world.VillageSavedData.get(serverLevel).isSiegeResolved(worldSiegeIndex);
    }

    /**
     * <b>Puente</b>: si hay un hueco a su alrededor (dos bloques de aire con un vacío de 2 o más debajo), pone un
     * adoquín a la altura de los pies para poder pisar. Un abismo se cruza así, tablón a tablón y despacio.
     * <p>
     * <b>MIRA LOS CUATRO LADOS, NO SOLO DE FRENTE</b> (5-oct-2026, y esto salió de medir): antes probaba únicamente la
     * dirección exacta del objetivo —primero la diagonal y luego los dos ejes— y con un asaltante <b>saltando en el
     * borde</b> de la zanja el hueco unas veces caía de frente y otras <b>debajo o al lado</b>, así que el puente
     * disparaba una vez de cada diez (medido: 1 puente en la corrida 34 y 0 en la 36). Ahora se miran los cuatro lados
     * y se elige <b>el que más apunta al objetivo</b>: cruzar un abismo no es cuestión de puntería, y un tablón puesto
     * en el lado bueno es el que deja seguir andando.
     *
     * @return {@code true} si ha puesto algo (entonces no hace falta taladrar)
     */
    private boolean puentearHacia(BlockPos towards) {
        // A2 Stage 7F · una accion GENERICA no puede tocar la ruta activa.
        if (celdaBloqueadaPorRutaActiva(towards)) {
            return false;
        }
        if (tunelRestante <= 0 || puenteEspera > 0) {
            return false;
        }
        BlockPos zPos = blockPosition();
        // Si debajo ya hay vacío se está cayendo y no es el momento de tender nada.
        if (level().getBlockState(zPos.below()).isAir()) {
            return false;
        }
        double dx = towards.getX() - zPos.getX();
        double dz = towards.getZ() - zPos.getZ();
        int[][] lados = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        // El lado que más apunta al objetivo, primero: con cuatro candidatos y el orden fijo, un asaltante que va en
        // diagonal tendería el tablón en el lado equivocado.
        java.util.Arrays.sort(lados, (a, b) -> Double.compare(
                -(a[0] * dx + a[1] * dz), -(b[0] * dx + b[1] * dz)));
        for (int[] d : lados) {
            BlockPos delante = new BlockPos(zPos.getX() + d[0], zPos.getY(), zPos.getZ() + d[1]);
            // OJO: el puente sigue usando el disco horizontal con sus 2 bloques de margen (NO_TOCAR_LA_ALDEA), NO el
            // recinto con altura: lo que se veta aquí es TENDER TABLONES sobre la aldea y su muro, y el muro es grueso.
            if (protegidoPorLaAldea(delante) || !level().getBlockState(delante).isAir()
                    || !level().getBlockState(delante.above()).isAir()) {
                continue;
            }
            // ¿Cuánto vacío hay debajo? Con menos de 2 no es un abismo (puede saltar o bajar andando).
            int vacio = 0;
            for (int i = 1; i <= 4; i++) {
                if (!level().getBlockState(delante.below(i)).isAir()) {
                    break;
                }
                vacio++;
            }
            if (vacio < 2) {
                continue;
            }
            BlockState puente = Blocks.COBBLESTONE.defaultBlockState();
            level().setBlock(delante, puente, Block.UPDATE_ALL);
            anotarTablon(delante); // es obra del asedio: el pico no la toca (I239)
            level().playSound(null, delante, SoundType.STONE.getPlaceSound(), SoundSource.BLOCKS, 0.7F, 1.0F);
            particulasDeTrabajo(delante);
            if (puenteEspera == 0 && tunelRestante == TUNEL_PRESUPUESTO) {
                DevilRpg.LOGGER.info("[Siege] asaltante #{} empieza a PONER UN PUENTE hacia la aldea en {}", getId(), delante);
            }
            tunelRestante--;
            puenteEspera = PUENTE_ENTRE_BLOQUES;
            return true;
        }
        return false;
    }

    /**
     * I358 · <b>DEJA EN EL REGISTRO POR QUE NO SE PICA</b> (lo pidió el jugador: *«haz las anotaciones necesarias en el
     * código con log para que tú mismo deduzcas si esto se cumple»*). Antes esto era <b>mudo</b> y por eso costó tanto ver
     * que el presupuesto se cobraba igual (I356) ✗: el asaltante se negaba a picar y no dejaba ni una línea. Con
     * <b>enfriamiento</b> (2 s) para no llenar el registro.
     */
    private void avisoDeQueNoPica(String motivo, Block b, BlockPos pos) {
        if (level().getGameTime() - ultimoAvisoDelAsedio <= 40L) {
            return;
        }
        ultimoAvisoDelAsedio = level().getGameTime();
        DevilRpg.LOGGER.info("[Siege] asaltante #{} NO pica {} en {} porque {} (tunel {}/{})", getId(),
                b == null ? "(ese bloque)" : b, pos.toShortString(), motivo, tunelRestante, TUNEL_PRESUPUESTO);
    }

    /** Partículas del sitio donde el zombie rompe o construye, para que el jugador lo vea desde lejos. */
    private void particulasDeTrabajo(BlockPos pos) {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 4, 0.3D, 0.3D, 0.3D, 0.01D);
        }
    }

    // --- LOS TRES ATRAVESADORES (I212): escalera de bloques, tunel y puente ----------------------------
    /**
     * Ticks entre bloque y bloque de los atravesadores <b>nuevos</b>: {@code APILAR_ENTRE_BLOQUES} (0,5 s) para el
     * escalón y {@code CAVAR_ENTRE_BLOQUES} (0,5 s) para el túnel hacia abajo.
     * <p>
     * <b>MEDIDO Y PUESTO A MEDIA SEGUNDO</b> (5-oct-2026), por la misma razón que el romper: el jugador pidió *«no
     * deben dudar tanto para ocupar estas herramientas»* y el proyecto ya tenía medido que una espera larga deja al
     * asaltante plantado. El puente se queda en su segundo ({@link #PUENTE_ENTRE_BLOQUES}), que es cruzar un abismo
     * tablón a tablón y ahí la lentitud es a propósito.
     */
    private static final int APILAR_ENTRE_BLOQUES = 10;
    /** Ticks entre bloque y bloque del túnel hacia abajo (0,5 s): cava a velocidad normal, sin dudar. */
    private static final int CAVAR_ENTRE_BLOQUES = 10;
    /** Ticks de espera del escalón apilado y del túnel hacia abajo. Se descuentan en {@link #tickTunel()}. */
    private int apilarEspera;
    private int cavarEspera;
    /** Bloques que le quedan de escalera apilada en esta marcha (no se hace una torre al cielo). */
    private static final int APILAR_PRESUPUESTO = 8;
    private int apilarRestante = APILAR_PRESUPUESTO;
    /**
     * <b>LOS TABLONES QUE HA TENDIDO EL ASEDIO</b> (I239, 9-oct-2026): son la obra del puente, y el pico no los toca.
     * Sin esto, el romper y el puente se peleaban: medido con el arnés, de los bloques que picaban los asaltantes,
     * **28-39 eran `cobblestone` justo en el anillo del foso** (r=8..11), o sea **los tablones que acababan de tender**.
     * <p>
     * Va <b>compartida entre todos los asaltantes</b> a propósito, y eso también está medido: con la lista por monstruo los
     * tablones seguían rompiéndose (los otros siete los pisaban y los picaban) — 39 rotos con la lista individual frente
     * a 23 al compartirla. Con tope: al pasar de {@link #TABLONES_MAX} se vacía (los tablones viejos ya son terreno).
     */
    private static final java.util.Set<BlockPos> TABLONES_DEL_ASALTO =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    /** Tope de tablones anotados (ver {@link #TABLONES_DEL_ASALTO}). */
    private static final int TABLONES_MAX = 512;

    /** ¿Esa celda es un tablón que ha tendido el asedio? Entonces no se pica (ver {@link #TABLONES_DEL_ASALTO}). */
    private static boolean esTablonDelAsalto(BlockPos pos) {
        return TABLONES_DEL_ASALTO.contains(pos);
    }

    /** Anota un tablón tendido, con tope (ver {@link #TABLONES_DEL_ASALTO}). */
    private static void anotarTablon(BlockPos pos) {
        if (TABLONES_DEL_ASALTO.size() >= TABLONES_MAX) {
            TABLONES_DEL_ASALTO.clear();
        }
        TABLONES_DEL_ASALTO.add(pos.immutable());
    }
    /** Vuelve a darle presupuesto de escalera (al empezar una marcha nueva). */
    public void recargarAtravesadores() {
        this.apilarRestante = APILAR_PRESUPUESTO;
        this.apilarEspera = 0;
        this.cavarEspera = 0;
    }

    /**
     * <b>ESCALERA DE BLOQUES</b> (lo pidió el jugador: *«que no duden en usar escalera, túnel o puente»*): si el
     * objetivo está <b>por encima</b> y la pared de delante no se puede saltar, el asaltante se <b>apila</b> un bloque
     * bajo los pies y sube un escalón; repite hasta coronar.
     * <p>
     * Es la forma de los atravesadores que ya había (decide <b>en el mismo tick</b>, sin tanteos) y va a <b>velocidad
     * normal</b>: un bloque cada {@link #APILAR_ENTRE_BLOQUES}. Antes de apilar, si lo que estorba es la pared, se le
     * abre el hueco con el camino que ya existe ({@link BreakBlockGoal#breakStepAhead}), que es el que respeta la
     * regla del muro: <b>fuera</b> y en el anillo se pica, <b>dentro de la aldea</b> no.
     *
     * @return {@code true} si ha hecho algo (apilado o abierto el hueco)
     */
    private boolean apilarBloqueParaSubir(BlockPos hacia) {
        if (celdaBloqueadaPorRutaActiva(hacia)) {
            return false;
        }
        if (apilarRestante <= 0 || apilarEspera > 0) {
            return false;
        }
        BlockPos zPos = blockPosition();
        if (hacia.getY() <= zPos.getY() + 1) {
            return false; // el objetivo no está arriba: la escalera no es la herramienta
        }
        int sx = Integer.signum(hacia.getX() - zPos.getX());
        int sz = Integer.signum(hacia.getZ() - zPos.getZ());
        int[][] dirs = (sx == 0 && sz == 0)
                ? new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}
                : new int[][]{{sx, 0}, {0, sz}, {sx, sz}};
        for (int[] d : dirs) {
            if (d[0] == 0 && d[1] == 0) {
                continue;
            }
            // LA COLUMNA DE DELANTE, a un bloque y a dos (no siempre está pegado a la pared).
            for (int dist = 1; dist <= 2; dist++) {
                int px = zPos.getX() + d[0] * dist;
                int pz = zPos.getZ() + d[1] * dist;
                // PRIMERO EL ESCALÓN, Y DESPUÉS ABRIR EL HUECO (I238, medido el 9-oct-2026). Estaba AL REVÉS y eso hacía
                // que la rama que COLOCA el bloque no se alcanzara nunca: `breakStepAheadHacia(..., true)` pica el
                // suelo de delante a la altura de los pies (y uno por debajo) y devolvía `true`, así que el goal se
                // salía sin apilar. Medido con el arnés, con el objetivo ARRIBA sobre suelo llano: **0 escalones en
                // cinco corridas** y **266 `pica`**, casi todos `stone` de la propia plancha — cavaba el suelo en vez
                // de subir. Ahora el orden es el que dice el nombre del goal: se intenta COLOCAR el escalón y, si no
                // se puede (celda ocupada, sin suelo firme debajo, o dentro de la aldea), se abre el hueco como antes.
                BlockPos escalon = new BlockPos(px, zPos.getY(), pz);
                if (ponerEscalon(escalon, hacia)) {
                    return true;
                }
                // Y SI LA CELDA DE LOS PIES ESTÁ OCUPADA (una pared, o el pozo que él mismo acaba de cavar), SE APILA UN
                // BLOQUE UN NIVEL MÁS ARRIBA (I242, medido el 9-oct-2026): es lo que le deja SALIR del pozo. Sin esto,
                // el monstruo se quedaba en el fondo del agujero que se había cavado —medido en la corrida 154: el
                // asaltante #0, OCHO volcados seguidos en la misma celda y OCHO bloques por debajo del suelo, con el
                // pico y la escalera corriendo y sin poder trepar—, y el jugador lo dejó claro: *«si cavan por debajo
                // pero después hacen algo para salir a la superficie, obligatorio, está bien; pero si no, mejor que se
                // queden sólo cavando cuando sea montaña o algo que los bloquee»*.
                if (ponerEscalon(escalon.above(), hacia)) {
                    return true;
                }
                // Y SI NO SE PUEDE COLOCAR, SE ABRE EL HUECO Y SE PICA EL SUELO de esa columna (lo que deja el escalón
                // cuando lo que estorba es una pared): el camino que ya existía, ahora como último recurso.
                if (breakStepAheadHacia(hacia, true)) {
                    apilarEspera = APILAR_ENTRE_BLOQUES;
                    apilarRestante--;
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Coloca <b>un escalón</b> en esa celda si se puede: celda vacía, hueco libre encima (dos bloques), <b>suelo firme
     * debajo</b> y fuera de la aldea. Es la colocación de {@link #apilarBloqueParaSubir}, sacada aquí porque ahora se
     * prueba <b>en dos alturas</b> (a los pies y uno más arriba, I242).
     *
     * @return {@code true} si lo ha puesto
     */
    private boolean ponerEscalon(BlockPos escalon, BlockPos hacia) {
        if (celdaBloqueadaPorRutaActiva(escalon)) {
            return false;
        }
        boolean huecoLibre = level().getBlockState(escalon.above()).isAir()
                && level().getBlockState(escalon.above(2)).isAir();
        if (!huecoLibre || !level().getBlockState(escalon).isAir()
                || level().getBlockState(escalon.below()).isAir()
                || protegidoPorLaAldea(escalon)) {
            return false;
        }
        level().setBlock(escalon, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
        level().playSound(null, escalon, SoundType.STONE.getPlaceSound(), SoundSource.BLOCKS, 0.7F, 1.0F);
        particulasDeTrabajo(escalon);
        DevilRpg.LOGGER.info("[Siege] asaltante #{} pone un ESCALON en {} para subir al objetivo ({})",
                getId(), escalon.toShortString(), hacia.toShortString());
        apilarEspera = APILAR_ENTRE_BLOQUES;
        apilarRestante--;
        return true;
    }

    /**
     * A2 Stage 6E · FIX A · <b>COLOCACIÓN ADHERIDA A UNA CARA.</b>
     * <p>
     * La primitiva de la casa ({@link #ponerEscalon}) exige suelo firme <b>justo debajo</b>, y esa es exactamente la
     * limitación medida en `a2n-20261016-170218`: en una cara vertical la única celda colocable es la que pisa el
     * suelo de la llanura, 5 bloques por debajo del borde de la meseta (&gt; caída segura), así que el descenso no
     * podía empezar. Minecraft <b>sí</b> permite colocar un bloque <b>adherido a una cara sólida</b> ya existente;
     * esta variante añade esa cara lateral <b>sin tocar la primitiva de la casa</b> (apilar/puentear/escalera siguen
     * exactamente igual, con su regla de suelo debajo).
     * <p>
     * No admite nada flotante: exige una cara lateral <b>real</b> (bloque presente y con forma de colisión), la celda
     * vacía, dos huecos encima, permiso de la aldea y que el bloque no caiga dentro del cuerpo del ente.
     */
    private boolean ponerEscalonConSoporteLateral(BlockPos escalon, BlockPos hacia) {
        boolean huecoLibre = level().getBlockState(escalon.above()).isAir()
                && level().getBlockState(escalon.above(2)).isAir();
        if (!huecoLibre || !level().getBlockState(escalon).isAir() || protegidoPorLaAldea(escalon)) {
            return false;
        }
        boolean soporteDebajo = com.chipoodle.devilrpg.entity.nav.NavObstruction
                .hasSolidFace(level(), escalon.below());
        boolean soporteLateral = false;
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            if (com.chipoodle.devilrpg.entity.nav.NavObstruction.hasSolidFace(level(), escalon.relative(d))) {
                soporteLateral = true;
                break;
            }
        }
        if (!soporteDebajo && !soporteLateral) {
            return false; // ni suelo ni cara: sería un bloque flotante
        }
        if (new net.minecraft.world.phys.AABB(escalon).intersects(getBoundingBox().inflate(0.05D))) {
            return false; // nunca dentro del cuerpo del ente
        }
        level().setBlock(escalon, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
        level().playSound(null, escalon, SoundType.STONE.getPlaceSound(), SoundSource.BLOCKS, 0.7F, 1.0F);
        particulasDeTrabajo(escalon);
        DevilRpg.LOGGER.info("[Siege] asaltante #{} pone un ESCALON ADHERIDO A UNA CARA en {} (soporteDebajo={}"
                        + " soporteLateral={}, hacia {})",
                getId(), escalon.toShortString(), soporteDebajo, soporteLateral, hacia.toShortString());
        apilarEspera = APILAR_ENTRE_BLOQUES;
        apilarRestante--;
        return true;
    }

    /**
     * A2 Stage 6E · FIX B · <b>ROMPER EL NODO DE LA RUTA.</b>
     * <p>
     * La recuperación genérica solo recibía el OBJETIVO, así que un nodo {@code BREAK} de la ruta nunca se ejecutaba
     * (medido en `a2n-20261016-170218`: `0:BREAK@120,84,-9` con 10 {@code moveTo} y 0 avance). Aquí se rompe
     * <b>exactamente</b> la celda que pide la transición, con las MISMAS puertas y el MISMO presupuesto que el túnel
     * (todo pasa por {@link #breakBlockAt}, que ya aplica protección, veto de obra propia y veto de árboles) y solo
     * si la celda está dentro del alcance acotado del ente.
     */
    /**
     * A2 Stage 7F · ¿esta celda pertenece a la ruta ACTIVA? Solo la consultan las acciones GENERICAS de
     * recuperacion (puente/escalera/cavado/escalon heredado). Las acciones legitimas de la ruta (BUILD via
     * `ponerEscalonConSoporteLateral` y BREAK via `romperNodoDeRuta`) NO la consultan: siguen funcionando.
     */
    /**
     * A2 Stage 7G · FUENTE DE DECISION INYECTABLE (solo pruebas). Por defecto es `getRandom().nextBoolean()`, es
     * decir el comportamiento de juego NO cambia; el arnes instala una secuencia fija para que el fixture controlado
     * sea reproducible. Medido en Stage 7F: dos corridas del mismo fixture dieron 2/24 y 24/24 porque esta decision
     * cambia el orden (romper/escalar) de la recuperacion generica.
     */
    private static java.util.function.BooleanSupplier fuenteDeDecisionParaPruebas;

    /** A2 Stage 7G · instala (o retira con `null`) la fuente de decision de pruebas. */
    public static void setFuenteDeDecisionParaPruebas(final java.util.function.BooleanSupplier fuente) {
        fuenteDeDecisionParaPruebas = fuente;
    }

    /** A2 Stage 7G · decision romper-vs-escalar: determinista si hay fuente de pruebas, aleatoria si no. */
    private boolean primeroRomperDecidido() {
        java.util.function.BooleanSupplier fuente = fuenteDeDecisionParaPruebas;
        return fuente != null ? fuente.getAsBoolean() : getRandom().nextBoolean();
    }

    private boolean celdaBloqueadaPorRutaActiva(final BlockPos celda) {
        if (celda == null || navExecutor == null) {
            return false;
        }
        return navExecutor.celdaDeRutaActiva(celda);
    }

    private boolean romperNodoDeRuta(final BlockPos nodo) {
        if (nodo == null) {
            return false;
        }
        BlockState bs = level().getBlockState(nodo);
        if (bs.isAir() || bs.getCollisionShape(level(), nodo).isEmpty()) {
            return false; // ya no hay nada que romper en el nodo
        }
        if (!alAlcanceDeRotura(nodo)) {
            return false; // no se pica a distancia
        }
        if (tunelRestante <= 0 || tunelEspera > 0) {
            return false; // mismo presupuesto y misma cadencia que el túnel
        }
        boolean roto = breakBlockAt(nodo);
        if (roto) {
            tunelRestante--;
            tunelEspera = TUNEL_ENTRE_BLOQUES;
        }
        return roto;
    }

    /**
     * A2 Stage 6E · alcance ACOTADO para romper el nodo de una ruta: su celda o la contigua (dx/dz ≤ 2, es decir como
     * mucho UNA celda vacía de separación) y dentro de la misma envolvente que la caída segura (hasta 3 por debajo,
     * 2 por encima de los pies). No se pica a distancia.
     * <p>
     * Medido en `a2n-20261016-172821`: el ejecutor da por alcanzado un nodo a 1,5 bloques (su radio de llegada), así
     * que un alcance de 1 bloque dejaba el nodo de la ruta a 2 bloques del ente y el `BREAK` no se ejecutaba nunca.
     */
    private boolean alAlcanceDeRotura(final BlockPos nodo) {
        BlockPos pies = blockPosition();
        int dx = Math.abs(nodo.getX() - pies.getX());
        int dz = Math.abs(nodo.getZ() - pies.getZ());
        int dy = nodo.getY() - pies.getY();
        return dx <= 2 && dz <= 2 && dy >= -3 && dy <= 2;
    }

    // ================= A2 Stage 4J: CONSTRUCCION DE ESCALERA DESCENDENTE (acotada y persistente) =========
    /** Bloques que puede colocar por intento y presupuesto total de la escalera en curso. */
    private static final int ESCALERA_MAX_BLOQUES = 24;
    /** Cadencia: ticks minimos entre colocaciones (reutiliza el ritmo de la casa). */
    private static final int ESCALERA_ENTRE_BLOQUES = 10;

    /** Estado persistente de la escalera en curso. */
    /** A2 Stage 4O · ANCLA validada: el suelo del que parte la escalera. NUNCA cuenta como escalon construido. */
    private BlockPos escaleraAncla;
    /**
     * A2 Stage 4O · Coordenada del ULTIMO escalon COLOCADO Y VERIFICADO. Solo se asigna tras leer el estado real
     * del bloque; el ancla no puede poblarlo. Es el unico valor que puede contar como "travesado".
     */
    private BlockPos escaleraUltimoColocado;
    /** Tick en que se verifico la colocacion del escalon actual (la travesia debe ser POSTERIOR). */
    private long escaleraTickDeColocacion = -1L;
    private int escaleraColocados;
    private int escaleraRetraso;
    private String escaleraMotivo = "-";
    private String escaleraUltimoRechazo = "-";

    /** Diagnostico de la construccion, para el arnes. */
    public String escaleraDiag() {
        return "colocados=" + escaleraColocados + "/" + ESCALERA_MAX_BLOQUES
                + " ultimo=" + (escaleraUltimoColocado == null ? "-" : escaleraUltimoColocado.toShortString())
                + " motivo=" + escaleraMotivo + " rechazo=" + escaleraUltimoRechazo;
    }

    /**
     * A2 Stage 4J - ESCALERA DESCENDENTE. Un paso por llamada, con cadencia y presupuesto acotados.
     * <p>
     * Dispara solo cuando el descenso esta bloqueado por un salto mayor que la caida segura (el caso medido
     * de z3 y z4: 24 y 41 bloques). La geometria la fija `ponerEscalon`: coloca el bloque en una celda VACIA
     * con DOS de aire encima y BLOQUE debajo, fuera de la aldea. El bloque colocado se VERIFICA leyendo el
     * estado real, y el progreso de la escalera NO avanza por haber intentado: solo cuando el asaltante pisa
     * de verdad el escalon (comprobado en `escaleraPisada`).
     *
     * @return true si coloco y verifico un bloque en esta llamada
     */
    private boolean construirEscalonDescendente(final BlockPos centro) {
        if (centro == null) {
            return false;
        }
        if (escaleraRetraso > 0) {
            escaleraRetraso--;
            return false;
        }
        if (escaleraColocados >= ESCALERA_MAX_BLOQUES) {
            escaleraUltimoRechazo = "presupuesto agotado (" + ESCALERA_MAX_BLOQUES + ")";
            return false;
        }
        // A2 Stage 4O: el ancla se guarda en su PROPIO campo y jamas se asigna a `escaleraUltimoColocado`.
        // El fallo medido en `a2n-20261016-022229` fue exactamente esto: al sembrar `escaleraUltimoColocado` con
        // el suelo que el asaltante ya pisaba, `escaleraPisada()` daba true de inmediato (tick 200) y se registraba
        // como "escalon travesado" un bloque que nunca se habia construido.
        if (escaleraAncla == null) {
            escaleraAncla = blockPosition().below();
        }
        BlockPos base = escaleraAncla;
        int pasoX = Integer.signum(centro.getX() - base.getX());
        int pasoZ = Integer.signum(centro.getZ() - base.getZ());
        if (pasoX == 0 && pasoZ == 0) {
            escaleraUltimoRechazo = "ya estamos sobre el centro";
            return false;
        }
        // A2 Stage 4M - BUSQUEDA ACOTADA Y DETERMINISTA DE CELDA VALIDA.
        // Motivo medido (a2n-20261016-014427): el candidato diagonal 594,93,599 estaba DENTRO del terreno
        // (celdaAire=false, hueco1=false, hueco2=false, soporteSolido=true, permitido=true), asi que ponerEscalon
        // lo rechazaba con razon. En vez de un unico candidato, se recorre un conjunto acotado y ordenado de
        // posiciones alrededor del rumbo de descenso y se coloca en la PRIMERA que cumpla las cinco precondiciones
        // y ademas sea geometricamente apropiada (a la altura de descenso, no dentro del terreno, no protegida).
        StringBuilder intentos = new StringBuilder();
        int probados = 0;
        // A2 Stage 4N: se AMPLIA el alcance horizontal de 2 a ALCANCE_HORIZONTAL_DE_ESCALON (8) bloques.
        // Motivo medido (`a2n-20261016-015724`): los 18 candidatos dentro de 2 bloques eran TODOS roca
        // (`celdaAire=false`), asi que la busqueda agotaba su radio sin alcanzar nunca el aire. El alcance queda
        // ACOTADO y documentado, y el orden sigue siendo determinista (dy creciente, luego dx, luego dz).
        for (int dy = 1; dy <= CAIDA_SEGURA_MAXIMA && probados < MAX_CANDIDATOS_DE_ESCALON; dy++) {
            for (int dx = 0; dx <= ALCANCE_HORIZONTAL_DE_ESCALON && probados < MAX_CANDIDATOS_DE_ESCALON; dx++) {
                for (int dz = 0; dz <= ALCANCE_HORIZONTAL_DE_ESCALON && probados < MAX_CANDIDATOS_DE_ESCALON; dz++) {
                    // Orden determinista: primero el paso recto/diagonal hacia el centro, luego los ensanchamientos.
                    int sx = pasoX * dx;
                    int sz = pasoZ * dz;
                    BlockPos objetivo = new BlockPos(base.getX() + sx, base.getY() - dy, base.getZ() + sz);
                    probados++;
                    String r = evaluarCeldaDeEscalon(objetivo, base, dy);
                    if (r == null) {
                        if (!ponerEscalon(objetivo, centro)) {
                            escaleraUltimoRechazo = objetivo.toShortString() + " ponerEscalon=false";
                            intentos.append(' ').append(objetivo.toShortString()).append("(primitiva=false)");
                            continue;
                        }
                        if (level().getBlockState(objetivo).isAir()) {
                            escaleraUltimoRechazo = objetivo.toShortString() + " NO verificado tras colocar";
                            return false;
                        }
                        escaleraUltimoColocado = objetivo;
                        escaleraTickDeColocacion = level().getGameTime();
                        escaleraColocados++;
                        escaleraRetraso = ESCALERA_ENTRE_BLOQUES;
                        escaleraMotivo = "escalon verificado tras " + probados + " candidatos (dy=" + dy + ")";
                        escaleraUltimoRechazo = "-";
                        DevilRpg.LOGGER.info("[Siege] CONSTRUCCION-ESCALERA #{} bloque {} verificado"
                                        + " ({} de {}) dy={} candidatos={} hacia {}",
                                getId(), objetivo.toShortString(), escaleraColocados,
                                ESCALERA_MAX_BLOQUES, dy, probados, centro.toShortString());
                        return true;
                    }
                    intentos.append(' ').append(objetivo.toShortString()).append('(').append(r).append(')');
                }
            }
        }
        escaleraUltimoRechazo = "ningun candidato valido de " + probados + ":" + intentos;
        escaleraRetraso = ESCALERA_ENTRE_BLOQUES;
        return false;
    }

    /** Tope DURO de candidatos que se prueban por intento de construccion (mantiene el coste acotado). */
    private static final int MAX_CANDIDATOS_DE_ESCALON = 60;
    /** Alcance HORIZONTAL de la busqueda de celda de escalon, en bloques (acotado y documentado). */
    private static final int ALCANCE_HORIZONTAL_DE_ESCALON = 8;

    /**
     * Evalua UNA celda de escalon y devuelve null si es valida, o el motivo exacto del rechazo.
     * Informa por separado las cinco precondiciones y ademas la coherencia geometrica con el descenso.
     */
    private String evaluarCeldaDeEscalon(final BlockPos objetivo, final BlockPos base, final int dy) {
        boolean celdaAire = level().getBlockState(objetivo).isAir();
        boolean hueco1 = level().getBlockState(objetivo.above()).isAir();
        boolean hueco2 = level().getBlockState(objetivo.above(2)).isAir();
        boolean soporteSolido = !level().getBlockState(objetivo.below()).isAir()
                && !level().getBlockState(objetivo.below())
                        .getCollisionShape(level(), objetivo.below()).isEmpty();
        boolean permitido = !protegidoPorLaAldea(objetivo);
        if (!celdaAire || !hueco1 || !hueco2 || !soporteSolido || !permitido) {
            return "celdaAire=" + celdaAire + " hueco1=" + hueco1 + " hueco2=" + hueco2
                    + " soporte=" + soporteSolido + " permitido=" + permitido;
        }
        // Coherencia geometrica: la celda debe estar POR DEBAJO del ancla y como mucho la caida segura.
        if (objetivo.getY() >= base.getY() || dy > CAIDA_SEGURA_MAXIMA) {
            return "altura no descendente";
        }
        // Y no puede ser una celda flotante: debe tener el bloque de apoyo justo debajo (ya comprobado) y el
        // propio escalon debe quedar a la altura del primer bloque de aire sobre el suelo real.
        int superficie = level().getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, objetivo).getY();
        if (objetivo.getY() != superficie) {
            return "no esta sobre la superficie (sup=" + superficie + ")";
        }
        return null;
    }


    /** ¿El asaltante esta pisando el ultimo escalon construido? (progreso solo por travesia REAL) */
    /**
     * A2 Stage 4N - TRAVESIA REAL de un escalon construido. Solo cuenta cuando la posicion real de la entidad
     * demuestra que ha OCUPADO el escalon: o esta de pie encima (`blockPosition().below()` == escalon) o su celda
     * actual es la de encima del escalon. No se infiere de proximidad, ni de una colocacion, ni de una ruta.
     */
    private boolean escaleraPisada() {
        // A2 Stage 4O · La travesia solo puede contar si existe un escalon COLOCADO Y VERIFICADO. El ancla vive en
        // su propio campo, asi que no puede llegar aqui, y si no hay colocacion verificada esto devuelve false.
        if (escaleraUltimoColocado == null || escaleraTickDeColocacion < 0L) {
            return false;
        }
        long ahora = level().getGameTime();
        // (4) La observacion tiene que ser POSTERIOR a la colocacion verificada.
        if (ahora <= escaleraTickDeColocacion) {
            return false;
        }
        // (5) El bloque tiene que seguir existiendo con el estado esperado en el momento de confirmar.
        boolean bloqueSigue = !level().getBlockState(escaleraUltimoColocado).isAir();
        if (!bloqueSigue) {
            escaleraMotivoDeTravesia = "el escalon " + escaleraUltimoColocado.toShortString()
                    + " YA NO existe (tick " + ahora + ")";
            return false;
        }
        boolean encima = blockPosition().below().equals(escaleraUltimoColocado);
        boolean enLaCeldaSuperior = blockPosition().equals(escaleraUltimoColocado.above());
        if (encima || enLaCeldaSuperior) {
            if (!escaleraTravesiaConfirmada) {
                escaleraTravesiaConfirmada = true;
                escaleraTickDeTravesia = ahora;
                escaleraMotivoDeTravesia = (encima ? "de pie ENCIMA del escalon COLOCADO "
                        : "en la celda SUPERIOR del escalon COLOCADO ")
                        + escaleraUltimoColocado.toShortString()
                        + " con la entidad en " + blockPosition().toShortString()
                        + " | colocadoEnTick=" + escaleraTickDeColocacion
                        + " travesadoEnTick=" + escaleraTickDeTravesia
                        + " | bloqueVerificado=" + bloqueSigue
                        + " | ancla=" + (escaleraAncla == null ? "-" : escaleraAncla.toShortString())
                        + " (el ancla NO cuenta como escalon)";
                DevilRpg.LOGGER.info("[Siege] ESCALON-TRAVESADO #{} {}", getId(), escaleraMotivoDeTravesia);
            }
            return true;
        }
        return false;
    }

    /** A2 Stage 4N: estado de travesia del ultimo escalon, para telemetria. */
    private boolean escaleraTravesiaConfirmada;
    private long escaleraTickDeTravesia = -1L;
    private String escaleraMotivoDeTravesia = "-";

    /** Diagnostico de travesia, coherente y sin spam por tick. */
    public String escaleraTravesiaDiag() {
        return "pisada=" + escaleraPisada()
                + " confirmada=" + escaleraTravesiaConfirmada
                + " tickTravesia=" + escaleraTickDeTravesia
                + " tickColocacion=" + escaleraTickDeColocacion
                + " ancla=" + (escaleraAncla == null ? "-" : escaleraAncla.toShortString())
                + " colocados=" + escaleraColocados
                + " ultimo=" + (escaleraUltimoColocado == null ? "-" : escaleraUltimoColocado.toShortString())
                + " entidad=" + blockPosition().toShortString()
                + " motivo=" + escaleraMotivoDeTravesia;
    }

    /**
     * A2 Stage 4K · ¿el OBJETIVO ACTIVO exige construir? Se decide con geometria REAL, no solo con la diferencia
     * vertical: se muestrea la superficie en la columna del propio asaltante y en la del objetivo, y se exige
     * (a) que la caida supere la caida segura Y (b) que la celda intermedia no tenga soporte real. Ese segundo
     * criterio es lo que distingue un acantilado de una ruta compleja normal.
     */
    public boolean objetivoExigeConstruccion(final BlockPos objetivo) {
        if (objetivo == null || !descensoExigeConstruir(objetivo)) {
            return false;
        }
        // Hay que construir solo si NO existe aterrizaje soportado a media caida.
        int miY = blockPosition().getY();
        BlockPos intermedio = new BlockPos(blockPosition().getX(), miY - CAIDA_SEGURA_MAXIMA, blockPosition().getZ());
        return !soporteRealYHueco(intermedio);
    }

    /**
     * A2 Stage 4K · intenta CONSTRUIR un escalon hacia el objetivo del descenso. Publico para que el ejecutor
     * pueda enrutar aqui la exhaucion de presupuesto SOLO cuando el objetivo exige construccion.
     */
    public boolean construirEscalonHacia(final BlockPos objetivo) {
        if (!objetivoExigeConstruccion(objetivo)) {
            return false;
        }
        return construirEscalonDescendente(objetivo);
    }

    /** ¿El descenso actual exige construir? (salto mayor que la caida segura) */
    private boolean descensoExigeConstruir(final BlockPos centro) {
        if (centro == null) {
            return false;
        }
        int miY = blockPosition().getY();
        int objetivoY = centro.getY();
        int desnivel = miY - objetivoY;
        if (desnivel <= CAIDA_SEGURA_MAXIMA) {
            // A2 Stage 4L: se registra POR QUE no se construye (el objetivo ya esta dentro de la caida segura).
            motivoDeNoConstruir = "objetivo " + centro.toShortString() + " a desnivel " + desnivel
                    + " <= caida segura " + CAIDA_SEGURA_MAXIMA + " (miY=" + miY + ")";
            return false;
        }
        // La celda de aterrizaje candidata mas baja admisible: no puede estar dentro del terreno.
        BlockPos primerEscalon = new BlockPos(blockPosition().getX(), objetivoY + CAIDA_SEGURA_MAXIMA,
                blockPosition().getZ());
        boolean haySoporte = soporteRealYHueco(primerEscalon);
        boolean enterrado = !level().getBlockState(primerEscalon).isAir();
        if (haySoporte && !enterrado) {
            motivoDeNoConstruir = "objetivo " + centro.toShortString() + " desnivel " + desnivel
                    + ": SI hay aterrizaje soportado y transitable en " + primerEscalon.toShortString()
                    + " -> descenso natural, no se construye";
            return false;
        }
        motivoDeConstruir = "objetivo " + centro.toShortString() + " desnivel " + desnivel
                + ": celda " + primerEscalon.toShortString() + (enterrado ? " DENTRO del terreno" : "")
                + (haySoporte ? "" : " sin soporte") + " -> hay que construir";
        return true;
    }

    /** A2 Stage 4L: motivos registrados en la decision de construir (diagnostico). */
    private String motivoDeNoConstruir = "-";
    private String motivoDeConstruir = "-";

    /** Diagnostico de la decision de descenso, para el arnes. */
    public String decisionDescensoDiag() {
        return "construir=[" + motivoDeConstruir + "] noConstruir=[" + motivoDeNoConstruir + "]";
    }

    /**
     * Abre el <b>hueco de delante a la altura de la cabeza</b> (y uno más arriba) siguiendo la regla del muro. Es el
     * camino que ya usaba {@link BreakBlockGoal#breakStepAhead}, sacado aquí para que los dos atravesadores que suben
     * compartan <b>una sola</b> verdad.
     *
     * @param conEscalon si además se pica el <b>suelo de delante</b> (el bloque a la altura de los pies), que es lo que
     *                   convierte el hueco en un <b>escalón</b> al que el asaltante puede subir
     * @return {@code true} si ha picado algo
     */
    private boolean breakStepAheadHacia(BlockPos hacia, boolean conEscalon) {
        BlockPos zPos = blockPosition();
        int sx = Integer.signum(hacia.getX() - zPos.getX());
        int sz = Integer.signum(hacia.getZ() - zPos.getZ());
        int[][] dirs = (sx == 0 && sz == 0)
                ? new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}
                : new int[][]{{sx, 0}, {0, sz}, {sx, sz}};
        for (int[] d : dirs) {
            if (d[0] == 0 && d[1] == 0) {
                continue;
            }
            for (int dist = 1; dist <= 2; dist++) { // a un bloque y a dos: no siempre está pegado a la pared
                boolean roto = false;
                for (int dy = 1; dy <= 2; dy++) {
                    BlockPos p = new BlockPos(zPos.getX() + d[0] * dist, zPos.getY() + dy, zPos.getZ() + d[1] * dist);
                    if (protegidoPorLaAldea(p)) {
                        continue; // dentro de la aldea no se pica (el anillo del muro sí: es la brecha)
                    }
                    if (esTablonDelAsalto(p)) {
                        continue; // los tablones del puente son obra del asedio, no un estorbo (I239)
                    }
                    if (canBreakBlock(level().getBlockState(p))) {
                        roto |= breakBlockAt(p);
                    }
                }
                // Y el suelo de esa columna, que es el ESCALÓN que queda al abrir el hueco: sin esto el asaltante
                // necesita dos rondas (una para el hueco y otra para el escalón) y se queda oscilando delante.
                if (conEscalon) {
                    for (int dy = 0; dy >= -1; dy--) {
                        BlockPos p = new BlockPos(zPos.getX() + d[0] * dist, zPos.getY() + dy,
                                zPos.getZ() + d[1] * dist);
                        if (protegidoPorLaAldea(p)) {
                            continue;
                        }
                        if (esTablonDelAsalto(p)) {
                            continue; // ni el tablón que el puente pone justo ahí, a la altura de los pies (I239)
                        }
                        if (canBreakBlock(level().getBlockState(p))) {
                            roto |= breakBlockAt(p);
                        }
                    }
                }
                if (roto) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * <b>TÚNEL HACIA ABAJO</b>: si el objetivo está <b>por debajo</b> y el asaltante no encuentra la bajada, cava. Usa
     * el taladro que ya existe ({@link #breakBlockTowards}), que es el que lleva su presupuesto
     * ({@link #TUNEL_PRESUPUESTO}) y su espera, y el que <b>respeta la regla del muro</b>: por eso un asaltante
     * <b>dentro</b> de la aldea no se hace un túnel de salida.
     *
     * @return {@code true} si ha cavado
     */
    private boolean cavarHaciaAbajo(BlockPos hacia) {
        if (celdaBloqueadaPorRutaActiva(hacia)) {
            return false;
        }
        if (cavarEspera > 0) {
            return false;
        }
        BlockPos zPos = blockPosition();
        if (hacia.getY() >= zPos.getY() || zPos.getY() - hacia.getY() > 3) {   // I375: ni el objetivo esta debajo, NI SE CAVA MAS DE 3 HACIA ABAJO
            return false; // el objetivo no está por debajo: el túnel hacia abajo no es la herramienta
        }
        BlockPos abajo = zPos.below();
        if (level().getBlockState(abajo).isAir()) {
            return false; // ya hay hueco: esto es caer, no cavar
        }
        if (protegidoPorLaAldea(abajo) || !canBreakBlock(level().getBlockState(abajo))) {
            return false; // dentro de la aldea no se cava (ni lo que no se puede romper)
        }
        if (!breakBlockAt(abajo)) {
            return false;
        }
        particulasDeTrabajo(abajo);
        cavarEspera = CAVAR_ENTRE_BLOQUES;
        DevilRpg.LOGGER.info("[Siege] asaltante #{} CAVA hacia abajo en {} para bajar al objetivo ({})", getId(),
                abajo.toShortString(), hacia.toShortString());
        return true;
    }

    // Configurar atributos personalizados: se usan las bases del perfil (ahora iguales a un zombie normal).
    public static AttributeSupplier.Builder setAttributes() {
        SpawnScaleProfile p = SPAWN_PROFILE;
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, p.baseHealth())
                .add(Attributes.MOVEMENT_SPEED, p.baseSpeed())
                .add(Attributes.ATTACK_DAMAGE, p.baseDamage())
                .add(Attributes.FOLLOW_RANGE, 96.0D); // Cubre los 86 bloques máximos a los que aparece la regla de spawn
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // A2: `Zombie.registerGoals()` instala `HurtByTargetGoal` (venganza al ser herido), que NO filtra por clase:
        // cualquier agresivo que reciba un golpe se pone a perseguir a quien se lo dio, aunque sea otro agresivo.
        // MEDIDO en el asedio (`build\a2n\a2n-20261015-142513`): a t=2620, justo tras el cambio de fase, CUATRO
        // asaltantes (z0, z2, z7, z1) se pusieron a la vez a perseguir a los agresivos #209 y #210 — el patron de
        // una reyerta, no el de una busqueda de objetivo. Se EXCLUYE a los agresivos como represalia, y se conserva
        // la venganza contra todo lo demas (jugadores, aldeanos, golems, animales).
        // OJO: NO se puede quitar DENTRO del bucle: `getAvailableGoals()` es la coleccion viva, y mutarla mientras se
        // recorre provoca NullPointerException (medido: `RUN INVALIDO ... "wrapped" is null` en
        // `a2n-20261015-144525`, que aborto la corrida con 1/8). Se recoge primero y se quita despues.
        java.util.List<Goal> venganzasHeredadas = new java.util.ArrayList<>();
        for (WrappedGoal wrapped : this.targetSelector.getAvailableGoals()) {
            if (wrapped != null && wrapped.getGoal() instanceof HurtByTargetGoal) {
                venganzasHeredadas.add(wrapped.getGoal());
            }
        }
        for (Goal obsoleto : venganzasHeredadas) {
            this.targetSelector.removeGoal(obsoleto);
        }
        this.targetSelector.addGoal(1, (new HurtByTargetGoal(this, AggressiveZombieEntity.class)).setAlertOthers());
        // Zombie.registerGoals() instala ZombieAttackGoal (prioridad 2), MoveThroughVillageGoal (6) y
        // WaterAvoidingRandomStrollGoal (7). Los tres pueden apropiarse de MOVE; esta entidad delega el movimiento
        // a sus goals con planner y destino explícitos.
        this.goalSelector.removeAllGoals(goal -> goal instanceof MeleeAttackGoal
                || goal instanceof MoveThroughVillageGoal
                || goal instanceof WaterAvoidingRandomStrollGoal);
        this.goalSelector.addGoal(1, new FloatGoal(this)); // Flotar en agua
        // Si está atascado en el agua, nada hacia la orilla más cercana (prioridad alta, antes de romper). I235 midió
        // que prioridad 2 empataba con el ZombieAttackGoal heredado: 0 ticks de nado en 60 s. Ese goal se elimina
        // arriba porque PursueTargetGoal controla ahora el combate; mantener prioridad 1 permite que salir del agua
        // siga teniendo precedencia sobre la persecución cuando el atasco ya está confirmado. FloatGoal solo pide JUMP.
        this.goalSelector.addGoal(1, new EscapeWaterGoal(this));
        // Romper el bloque que le estorba cuando está atascado (pero no atacar casas si puede pasar).
        this.goalSelector.addGoal(3, new BreakBlockGoal(this));
        // Y LOS TRES ATRAVESADORES (I212): puente, escalera de bloques y túnel, a velocidad normal. Va justo DESPUES
        // del romper y con la MISMA prioridad (3), sin flags: el romper mira "¿estorba un bloque?" y este mira "¿el
        // objetivo está arriba, abajo, o hay un hueco delante?", y los dos son pasivos (no navegan), asi que no se
        // pisan el camino a nadie (I205).
        this.goalSelector.addGoal(3, new TraverseGoal(this));
        // Combate y flanqueo comparten un solo goal MOVE: la selección de presa tiene prioridad sobre la marcha a la
        // aldea, y no hay un segundo goal que reemplace su ruta por un punto de rodeo no validado.
        this.pursueTargetGoal = new PursueTargetGoal(this);
        this.goalSelector.addGoal(4, this.pursueTargetGoal);
        this.goalSelector.addGoal(6, new FireballAttackGoal(this)); // Lanzar fuego como los Blaze
        // Si no hay objetivo, marchar hacia el centro de la aldea (para no merodear fuera).
        this.goalSelector.addGoal(7, new MoveToVillageCenterGoal(this));
        // Y si tiene un "hogar" (guarida), patrullar su radio.
        this.goalSelector.addGoal(8, new PatrolHomeGoal(this));
        this.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(this, Player.class, true)); // Detectar jugadores   // I379: PRIMERO el jugador (y sus minions, el goal de al lado): es lo que pidio el jugador
        // También ataca a las invocaciones (minions) del jugador, no solo al jugador. Usamos LivingEntity
        // (no TamableAnimal) para cubrir también al oso, que extiende AbstractChestedHorse y no TamableAnimal.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                (target) -> target instanceof ITamableEntity it && it.getOwner() != null));
        // Cazar ANIMALES que estén dentro de su guarida: al morir sobre el sculk, el catalizador de la
        // guarida expande la infección (mecánica vanilla). Solo aplica a los que tienen un hogar (guarida).
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Animal.class, 10, true, false,
                this::isAnimalInsideHome));
        // ALDEANOS: el mundo también es hostil con los asentamientos. Es lo que hace que una horda que marcha
        // a por una aldea sea una amenaza real para sus habitantes (y no solo para el jugador): si el jugador
        // no va a defenderla, los aldeanos caen.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Villager.class, true)); // I165: el PRIMER objetivo de una horda que marcha a la aldea   // I379: los aldeanos DESPUES: solo si no encuentra al jugador ni a sus minions
        // Golems (defensas de la aldea) SOLO si el zombie va a por una aldea: así uno suelto no se pelea con
        // un golem que no le estaba haciendo nada.
        this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, IronGolem.class, 10, true, false,
                (target) -> this.getVillageCenter() != null));
    }

    /**
     * ¿Es una presa válida para el zombie? Sí si es un animal dentro del radio de su guarida... salvo que sea
     * <b>ganado de la granja macabra</b> (etiqueta {@link LairGenerator#LIVESTOCK_TAG}). Ese rebaño es del
     * cultivador: si los demás enemigos de la guarida lo cazan, el cultivador se queda sin nada que criar ni
     * sacrificar y la granja deja de tener sentido. Los animales <b>salvajes</b> que entren en la guarida sí
     * se siguen cazando (así es como la infección se alimenta sola).
     */
    private boolean isAnimalInsideHome(LivingEntity target) {
        BlockPos home = getHomePos();
        if (home == null || getHomeRadius() <= 0) {
            return false;
        }
        if (LairGenerator.isLivestock(target)) {
            return false;
        }
        double r = getHomeRadius();
        return target.distanceToSqr(home.getX() + 0.5D, home.getY() + 0.5D, home.getZ() + 0.5D) <= r * r;
    }

    /**
     * El destino y el "hogar" viajan con el zombie. Importante desde la Iteración 3: un zombie de una horda
     * que marcha a por una aldea <b>no debe perder su destino</b> si el mundo se recarga a mitad del asedio
     * (antes {@code villageCenter} no se guardaba y al recargar se quedaba sin saber a dónde iba).
     */
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (villageCenter != null) {
            tag.put("DevilRpgVillageCenter", NbtUtils.writeBlockPos(villageCenter));
        }
        tag.putBoolean("DevilRpgGoToCenter", goToCenterActive);
        tag.putInt("DevilRpgWorldSiegeIndex", worldSiegeIndex);
        if (homePos != null) {
            tag.put("DevilRpgHomePos", NbtUtils.writeBlockPos(homePos));
            tag.putInt("DevilRpgHomeRadius", homeRadius);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        NbtUtils.readBlockPos(tag, "DevilRpgVillageCenter").ifPresent(pos -> villageCenter = pos);
        // Sin el campo (mobs de partidas viejas) se asume true, que es el valor por defecto de siempre.
        goToCenterActive = !tag.contains("DevilRpgGoToCenter") || tag.getBoolean("DevilRpgGoToCenter");
        worldSiegeIndex = tag.contains("DevilRpgWorldSiegeIndex") ? tag.getInt("DevilRpgWorldSiegeIndex") : -1;
        // AUTO-CURACIÓN: si al cargar el asedio del que formaba parte YA no existe (los asedios no se persisten: se
        // resolvió mientras este zombie estaba descargado, o el mundo se guardó a mitad), se le quita la marca de
        // asediador. Si no, se quedaría para siempre sin poder descartarse por lejanía (ver `removeWhenFarAway`).
        boolean eraAsediador = worldSiegeIndex >= 0;
        if (eraAsediador && !level().isClientSide && level() instanceof ServerLevel serverLevel
                && (!VillageManager.hayAsedio(serverLevel, worldSiegeIndex)
                || !VillageManager.perteneceAAsedioActual(serverLevel, worldSiegeIndex, getUUID()))) {
            // A2 REMEDIACIÓN 2 · o el asedio ya no existe, o este UUID no pertenece a la ola VIGENTE (pudo
            // empezar otro asedio del mismo índice mientras estaba descargado). En ambos casos la campaña es
            // vieja: se suelta el índice, el destino persistido y el goal de marcha.
            worldSiegeIndex = -1;
        }
        // El brillo de asediador es una marca de la CAMPAÑA en curso: si al cargar ya no hay campaña, se le
        // quita. También se limpia el destino/goal persistido, que antes sobrevivía y le hacía marchar solo.
        if (eraAsediador && worldSiegeIndex < 0) {
            setGlowingTag(false);
            goToCenterActive = false;
            villageCenter = null;
        }
        // A2 REMEDIACIÓN 2 · un centro persistido sin índice de asedio es campaña vieja: no debe seguir
        // empujando al zombie hacia una aldea que ya no lo reclama.
        if (worldSiegeIndex < 0) {
            goToCenterActive = false;
            villageCenter = null;
        }
        NbtUtils.readBlockPos(tag, "DevilRpgHomePos").ifPresent(pos -> homePos = pos);
        homeRadius = tag.getInt("DevilRpgHomeRadius");
    }

    public static boolean checkSpawnRules(EntityType<AggressiveZombieEntity> entityType, ServerLevelAccessor world, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        Player nearestPlayer = world.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), SPAWN_PROFILE.maxDistance(), false);

        if (nearestPlayer == null) {
            return false; // No hay jugadores cercanos, no spawnea
        }

        PlayerAuxiliaryCapabilityInterface playerCapability = IGenericCapability.getUnwrappedPlayerCapability(nearestPlayer, PlayerAuxiliaryCapability.INSTANCE);
        Vec3 playerSpawn = playerCapability.getSpawnPoint();
        if (playerSpawn == null) {
            return false; // No se ha registrado el punto de spawn del jugador
        }

        // Calcular distancia real en bloques desde el spawn del jugador
        double distance = Math.sqrt(pos.distSqr(new BlockPos((int) playerSpawn.x, (int) playerSpawn.y, (int) playerSpawn.z)));

        double probability = calculateSpawnProbability(distance);
        double doubleRandom = random.nextDouble();
        DevilRpg.LOGGER.info("Spawn Check => playerSpawn: {} distance: {} probability: {} random: {} spawn? {}", playerSpawn, distance, probability, doubleRandom, doubleRandom < probability);
        return doubleRandom < probability;
    }

    private static double calculateSpawnProbability(double distance) {
        // Curva de probabilidad unificada (zona protegida -> max), igual que la usada por la spawnRule.
        return SPAWN_PROFILE.probability(distance);
    }

    @Override

    public void aiStep() {
        // A2 · el ejecutor propio, si está encendido, es el ÚNICO que mueve. Se le da el tick ANTES de los goals.
        // A2 REMEDIACIÓN 2 · PROPIEDAD ÚNICA: con un objetivo de combate vivo, el movimiento lo lleva el goal
        // de combate (planificador heredado). El ejecutor se suspende para no emitir un segundo `moveTo` ni
        // avanzar waypoints por un desplazamiento que no ordenó. Al perder el objetivo, vuelve a planificar.
        if (com.chipoodle.devilrpg.entity.nav.NavFeature.customNavigation() && !this.level().isClientSide) {
            boolean combate = pursueTargetGoal != null && pursueTargetGoal.estaActivo();
            if (com.chipoodle.devilrpg.entity.nav.MovementOwnership.executorMayMove(true, combate)) {
                this.navExecutor.tick();
            } else if (combate) {
                this.navExecutor.suspendForCombat();
            }
        }
        // I359 · LAS DOS MEDIDAS DEL OBJETIVO, EN EL REGISTRO (lo pidio el jugador). Aqui, cada tick, para verlo tambien
        // cuando NO esta picando (que es justo el caso bueno: el que entra andando). "ENTRO en el recinto" sale UNA vez; el
        // informe de posicion, cada 5 s, para poder medir desde el registro la distancia al centro ((566,566) y (990,990)).
        if (worldSiegeIndex >= 0 && level().getGameTime() - ultimoInformeDePosicion > 100L) {
            // I359 (bis) · EL CHEQUEO VA DENTRO DEL INFORME (cada 5 s), NO CADA TICK: protegidoPorLaAldea llama a
            // cotaParaElRecinto (L619) y no es gratis, asi que medir no puede costarle nada al servidor. La traza de "ENTRO"
            // puede salir hasta 5 s tarde y da igual: para deducirlo del registro es de sobra.
            ultimoInformeDePosicion = level().getGameTime();
            boolean dentroDelRecinto = protegidoPorLaAldea(blockPosition());
            if (dentroDelRecinto && !aviseQueEntro) {
                aviseQueEntro = true;
                DevilRpg.LOGGER.info("[Siege] asaltante #{} de la aldea {} ENTRO en el recinto por {}", getId(), worldSiegeIndex,
                        blockPosition().toShortString());
            }
            DevilRpg.LOGGER.info("[Siege] asaltante #{} de la aldea {} va por {} (y={}) · dentro={} · tunel {}/{}", getId(),
                    worldSiegeIndex, blockPosition().toShortString(), blockPosition().getY(), dentroDelRecinto,
                    tunelRestante, TUNEL_PRESUPUESTO);
        }
        super.aiStep();

        tickTunel();

        if (!attributesAdjusted) {
            adjustAttributesBasedOnSpawnDistance();
            attributesAdjusted = true; // Solo se ejecuta una vez
        }
        if (!equipoAsignado && !level().isClientSide) {
            equiparAlAzar();
            equipoAsignado = true;
        }
    }

    /** Si ya se le ha sorteado el equipo (armas/armaduras) al aparecer. */
    private boolean equipoAsignado;

    /**
     * Algunos zombies agresivos aparecen <b>equipados</b> (arma y armaduras de hierro) y las sueltan al morir con
     * <b>baja probabilidad</b> ({@link #PROBABILIDAD_SOLTAR_EQUIPO}): es una de las fuentes de material de los
     * herreros de la aldea (la otra son los <b>chips de metal</b> que sueltan siempre al morir).
     */
    private void equiparAlAzar() {
        RandomSource random = level().random;
        if (random.nextFloat() >= 0.35F) {
            return; // la mayoría aparecen con las manos vacías
        }
        if (random.nextFloat() < 0.5F) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        } else {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        }
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET}) {
            if (random.nextFloat() < 0.5F) {
                setItemSlot(slot, new ItemStack(switch (slot) {
                    case HEAD -> Items.IRON_HELMET;
                    case CHEST -> Items.IRON_CHESTPLATE;
                    case LEGS -> Items.IRON_LEGGINGS;
                    default -> Items.IRON_BOOTS;
                }));
            }
        }
        // BAJA probabilidad de soltarlo al morir (lo normal es que se pierda), como pidió el jugador.
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!getItemBySlot(slot).isEmpty()) {
                setDropChance(slot, PROBABILIDAD_SOLTAR_EQUIPO);
            }
        }
    }

    /** Probabilidad de que un zombie suelte la pieza de equipo que lleva (baja a propósito). */
    private static final float PROBABILIDAD_SOLTAR_EQUIPO = 0.12F;

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean hitByPlayer) {
        super.dropCustomDeathLoot(level, source, hitByPlayer);
        // CHIPS DE METAL: de aquí salen los lingotes que forjan los herreros (9 pepitas = 1 lingote).
        int pepitas = 1 + level.random.nextInt(2);
        // EL QUE MATA, LOOTEA (lo pidió el jugador: *"así se siente más real el guardia, que es como un jugador que
        // sube de experiencia y lootea"*). Si quien lo ha matado es un aldeano del pueblo —la guardia—, las pepitas
        // van a SU INVENTARIO en el acto, como cuando un jugador recoge lo que mata. Medido el 26-sep-2026: dejándolas en
        // el suelo, las que el recolector no alcanzaba **desaparecían a los 5 minutos** y el almacén no llegaba a
        // juntar las 27 que pide un pico de hierro (`en el ALMACEN=1` corrida tras corrida). Si al aldeano no le
        // caben, se caen al suelo como siempre (nunca se borra nada del pueblo).
        if (source.getEntity() instanceof Villager asesino && !asesino.isBaby()) {
            ItemStack sobra = asesino.getInventory().addItem(new ItemStack(Items.IRON_NUGGET, pepitas));
            if (!sobra.isEmpty()) {
                spawnAtLocation(sobra);
            }
        } else {
            spawnAtLocation(new ItemStack(Items.IRON_NUGGET, pepitas));
        }
        if (level.random.nextFloat() < 0.35F) {
            spawnAtLocation(new ItemStack(Items.ROTTEN_FLESH));
        }
    }

    @Override
    public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);

        if (spawnDistance == 0) { // Solo se calcula al momento del spawn
            Player nearestPlayer = this.level().getNearestPlayer(this, SPAWN_PROFILE.maxDistance());
            if (nearestPlayer != null) {
                PlayerAuxiliaryCapabilityInterface playerCapability = IGenericCapability.getUnwrappedPlayerCapability(nearestPlayer, PlayerAuxiliaryCapability.INSTANCE);
                Vec3 playerSpawn = playerCapability.getAnchorPoint();
                if (playerSpawn == null) {
                    playerSpawn = playerCapability.getSpawnPoint();
                }
                if (playerSpawn != null) {
                    spawnDistance = Math.sqrt(this.blockPosition().distSqr(new BlockPos((int) playerSpawn.x, (int) playerSpawn.y, (int) playerSpawn.z)));
                    spawnThreat = ThreatLevel.current(this.level());
                    // La traza va DETRAS DE UN FLAG de la config (`devilrpg-server.toml`, [logs] logEscaladoDeSpawn),
                    // apagado por defecto: es la herramienta para medir el escalado, no algo del dia a dia. Ademas
                    // solo sale en el SERVIDOR (esto corre tambien en el cliente, que escalaba con OTRA distancia y
                    // sacaba un ataque distinto) y solo cuando la entidad YA esta colocada: el primer `setPos` de la
                    // construccion es (0,0,0) y llenaba el log de "spawned at 0,0,0" (12 lineas por tanda de 3).
                    if (DevilRpgConfig.LOG_ESCALADO_DE_SPAWN && !this.level().isClientSide
                            && (this.getX() != 0.0D || this.getY() != 0.0D || this.getZ() != 0.0D)) {
                        DevilRpg.LOGGER.info("Zombie Spawned at: {} | Player Spawn Point: {} | Distance: {} | Threat: {}", this.blockPosition(), playerSpawn, spawnDistance, String.format("%.2f", spawnThreat));
                    }
                }
            }
        }
    }

    /**
     * Velocidad de un zombi normal del juego (su atributo de vanilla). Es el <b>ancla</b> de los asaltantes.
     */
    private static final double VELOCIDAD_DE_ZOMBI_NORMAL = 0.23D;

    /**
     * <b>Escala efectiva de un asaltante de la PRIMERA aldea</b>, <b>MEDIDA</b> en la partida del jugador (5-oct-2026,
     * traza {@code Attributes Scaled => scaleFactor … MOVEMENT_SPEED}): {@code 0.368 = 0.23 × 1.6}, o sea la escala
     * clavada en el tope. Es el único número que hay que revisar si cambia el perfil de spawn.
     */
    private static final double ESCALA_DE_LA_PRIMERA_ALDEA = 1.6D;

    /**
     * Lo que se le <b>resta</b> a la velocidad escalada para que en la primera aldea ande como un zombi normal.
     * <p>
     * No es un número suelto: se <b>deriva</b> de la velocidad base del perfil y del tope, así que si el perfil cambia
     * el ajuste se recalcula solo. La <b>progresión no se toca</b>: la misma curva, desplazada hacia abajo lo justo
     * para que el primer asaltante ande a la velocidad de vanilla y los de aldeas avanzadas sigan subiendo.
     */
    private static final double AJUSTE_DE_VELOCIDAD_PRIMERA_ALDEA =
            Math.max(0.0D, SPAWN_PROFILE.baseSpeed() * Math.min(ESCALA_DE_LA_PRIMERA_ALDEA, VELOCIDAD_TOPE_DE_ESCALA)
                    - VELOCIDAD_DE_ZOMBI_NORMAL);

    private void adjustAttributesBasedOnSpawnDistance() {
        if (spawnDistance < SPAWN_PROFILE.minDistance()) {
            return; // Si está en la zona de spawn, no cambia atributos
        }

        // Escalado lineal por distancia (con la zona protegida que se encoge con la amenaza), multiplicado
        // por la fuerza que aporta el tiempo (amenaza) al momento del spawn.
        double scaleFactor = SPAWN_PROFILE.scaleFactor(spawnDistance, spawnThreat)
                * (1.0 + spawnThreat * ThreatLevel.maxExtraDifficulty());

        // Aplicar el escalado sobre los valores base del perfil
        Objects.requireNonNull(this.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(SPAWN_PROFILE.baseHealth() * scaleFactor);
        // LA VELOCIDAD **NO** ESCALA COMO LA VIDA NI EL DAÑO (30-sep-2026, lo reportó el jugador: *"son muy lentos
        // caminando inicialmente, aunque ya en niveles más avanzados son tan rápidos como el zombie normal"*).
        // Medido en el perfil: `baseSpeed = 0.071` y el **zombi normal del juego anda a 0.23** — o sea que un asaltante
        // de la primera aldea iba a **un 31 %** de la velocidad de un zombi de vanilla, y solo llegaba a parecer normal
        // cuando el ×4 del escalado lo empujaba a ~0.28. Ahora la base es la del zombi normal y la velocidad escala
        // **poco** (hasta +60 %): un asaltante corre siempre, y el escalado se nota en vida y daño, no en eternizarse.
        // ANCLA DE VELOCIDAD (5-oct-2026, medido en la partida del jugador: en la aldea 0 salian a 0.368, o sea
        // +60 % sobre el zombi normal de 0.23, y se les veia correr de mas). La curva de la progresion es la MISMA;
        // solo se le resta el exceso de la primera aldea. El suelo evita que un perfil mas lento la deje en negativo.
        double velocidad = Math.max(VELOCIDAD_DE_ZOMBI_NORMAL,
                SPAWN_PROFILE.baseSpeed() * Math.min(scaleFactor, VELOCIDAD_TOPE_DE_ESCALA)
                        - AJUSTE_DE_VELOCIDAD_PRIMERA_ALDEA);
        // Y EL TOPE SE APLICA AL VALOR FINAL, NO A LA BASE (5-oct-2026, y esto cierra el pendiente del asaltante de
        // 0.552). MEDIDO con el arnes (`MEDIR_VELOCIDAD`): el juego le pone al CRÍO un modificador de velocidad de
        // ×1,5 —adulto `0,23` → crío `0,345`—, y ese multiplicador se aplica DESPUES de la base. O sea que el tope del
        // mod (base = 0,23 × 1,6 = 0,368) se quedaba corto: un asaltante CRÍO salia a `0,368 × 1,5 = 0,552`, que es
        // EXACTAMENTE el numero que el jugador vio en su registro con el mismo `scaleFactor` que los demas ✓.
        // La base se deriva del valor final que se quiere (`velocidad`), dividiendo por los modificadores que ya tenga
        // el monstruo: asi el tope vale para el crío, para el adulto y para cualquier modificador que ponga el juego.
        var atributoVelocidad = Objects.requireNonNull(this.getAttribute(Attributes.MOVEMENT_SPEED));
        atributoVelocidad.setBaseValue(velocidad);
        double conModificadores = atributoVelocidad.getValue();
        if (conModificadores > velocidad + 1.0E-9D && conModificadores > 0.0D) {
            atributoVelocidad.setBaseValue(velocidad * (velocidad / conModificadores));
        }
        Objects.requireNonNull(this.getAttribute(Attributes.ATTACK_DAMAGE)).setBaseValue(SPAWN_PROFILE.baseDamage() * scaleFactor);

        // Subir la vida ACTUAL al nuevo máximo: al escalar solo se cambiaba el máximo, así que un zombie
        // escalado nacía con la vida base del perfil (9) y el máximo escalado (hasta ~76), o sea HERIDO, y el
        // escalado de vida apenas se notaba en combate. El vex helado ya lo hacía así.
        this.setHealth(this.getMaxHealth());

        // La traza, detras del mismo flag de la config y solo en el servidor (el cliente aplica su propia cuenta y
        // saldria con otros numeros).
        if (DevilRpgConfig.LOG_ESCALADO_DE_SPAWN && !this.level().isClientSide) {
            DevilRpg.LOGGER.info("Attributes Scaled => scaleFactor: {} | DISTANCE: {} | MAX_HEALTH: {} | MOVEMENT_SPEED: {} | ATTACK_DAMAGE: {}",
                    scaleFactor,
                    spawnDistance,
                    Objects.requireNonNull(this.getAttribute(Attributes.MAX_HEALTH)).getValue(),
                    Objects.requireNonNull(this.getAttribute(Attributes.MOVEMENT_SPEED)).getValue(),
                    Objects.requireNonNull(this.getAttribute(Attributes.ATTACK_DAMAGE)).getValue());
        }
    }

    /**
     * La experiencia que suelta el zombie crece con la distancia (y la amenaza), configurable en el perfil.
     * <code>LivingEntity.getExperienceReward(...)</code> usa este valor; aquí se escala según la distancia.
     */
    @Override
    protected int getBaseExperienceReward() {
        return SPAWN_PROFILE.experienceReward(spawnDistance, spawnThreat);
    }

    /**
     * Goal: si el zombie está en el agua y atascado (no progresa hacia su objetivo ni sale del agua),
     * buscar la orilla (bloque de tierra) más cercana y nadar hacia ella para desatascarse. Se activa con
     * prioridad alta, antes que romper o atacar, para que no se quede saltando en el agua fuera de la aldea.
     */
    static class EscapeWaterGoal extends Goal {
        /** Ticks nadando sin avanzar antes de considerar que está atascado y tocarle buscar la orilla. */
        /**
     * Cuántos ticks atascado antes de intentar salir del agua. <b>MEDIDO Y BAJADO (5-oct-2026)</b>: eran <b>40</b> —
     * <b>DOS SEGUNDOS</b> quieto en el agua antes de reaccionar ✗, que es justo lo que el jugador describió (*«cuando
     * están en el agua se quedan ahí y avanzan muy lento»* ✓): no es que naden despacio, es que <b>esperan a estar
     * atascados</b> ✓. Con 8 ticks reaccionan en menos de medio segundo ✓ y el asaltante no se queda plantado ✓.
     */
    private static final int STUCK_TICKS_BEFORE_TRYING = 8;
        /**
         * <b>EL AGUA NO ES UN ATAJO, ES OTRO SITIO POR EL QUE SE ANDA</b> (5-oct-2026, y es el arreglo del "se quedan en el
         * agua y avanzan muy lento" del jugador).
         * <p>
         * Antes esto era un goal de <b>ESCAPE</b>: se rendía a los {@code MAX_ESCAPE_TICKS} = <b>200</b> ticks (10 s) y
         * al rendirse se ponía un <b>castigo de otros 200 ticks</b> ({@code retryCooldown}) sin tocar el agua. Medido en
         * el registro del jugador, eso es exactamente lo que se ve: <b>20 segundos</b> en los que el asaltante ni avanza
         * ni intenta nada. *«Cuando están en el agua se quedan ahí y avanzan muy lento»* ✓.
         * <p>
         * Ahora, mientras esté en el agua, el asaltante <b>empuja hacia la orilla más cercana y hacia arriba</b> —a
         * velocidad de crucero, sin esperas— y la navegación de siempre hace el resto. El tope deja de ser un
         * "rendirse" y pasa a ser un <b>recálculo</b>: si en 10 s no ha salido, vuelve a buscar orilla (por si la que
         * eligió no tenía salida) y <b>sigue nadando</b>, sin castigo.
         */
        private static final int RECALCULAR_CADA_TICKS = 200;
        /** A2 REMEDIACIÓN 2 · escapes de agua por episodio; al agotarlos se retoma la mision. */
        private static final int MAX_INTENTOS_DE_ESCAPE = 4;

        private final AggressiveZombieEntity zombie;
        private BlockPos shore = null;
        /** A2 REMEDIACIÓN 2 · orillas ya pedidas en este episodio, para no repetir la misma. */
        private final java.util.Set<BlockPos> orillasIntentadas = new java.util.HashSet<>();
        private final com.chipoodle.devilrpg.entity.nav.TacticalEscapeBudget escapeBudget =
                new com.chipoodle.devilrpg.entity.nav.TacticalEscapeBudget(MAX_INTENTOS_DE_ESCAPE);
        private boolean escapeAgotado;
        private int stuckTicks = 0;
        private int jumpCooldown = 0;
        private double lastX, lastZ;
        // Seguimiento mientras el goal NO está corriendo, para saber si de verdad está atascado.
        private double trackX = Double.NaN, trackZ = 0;
        private int idleStuckTicks = 0;
        private int escapeTicks = 0;
        // A5.32 · agua atrapada: el movimiento horizontal de una misión no puede aplazar el escape para siempre.
        // 60 ticks (3 s) sin acercarse a la misión es el umbral independiente; se reinicia al salir del agua o al
        // acercarse de verdad a la misión.
        private static final int WATER_DWELL_TICKS = 60;
        private int waterDwellTicks = 0;
        private double bestMissionDistance = Double.MAX_VALUE;
        private boolean waterDwellTracked = false;
        private int recoveryBreaks = 0;

        public EscapeWaterGoal(AggressiveZombieEntity zombie) {
            this.zombie = zombie;
            // Este goal NAVEGA (nada hacia la orilla), así que declara MOVE: los flags controlan la prioridad de
            // `GoalSelector`. En la prueba histórica I235 (9-oct-2026), la prioridad 2 empataba con el
            // `ZombieAttackGoal` heredado y el nado no arrancaba; registerGoals lo retira ahora. La prioridad 1 se
            // conserva para que salir del agua tenga precedencia cuando la navegación detecta un atasco real.
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE));
        }

        /**
         * Solo se activa si de verdad está <b>atascado</b> en el agua (nada y no avanza). Antes se activaba
         * con solo tocar agua (el antiguo {@code isReallyStuck()} devolvía {@code true} siempre), y como tiene
         * prioridad alta eso le bloqueaba TODO lo demás —romper bloques, marchar al centro y atacar—, así que
         * un zombie en el agua se quedaba nadando en el sitio sin hacer nada. Con la aldea flotante rodeada de
         * agua y las oleadas saliendo a 32–40 bloques (en el agua), eso dejaba al asedio entero inútil.
         */
        @Override
        public boolean canUse() {
            if (!zombie.isInWater() || !zombie.isInWaterOrRain()) {
                trackX = Double.NaN;
                idleStuckTicks = 0;
                waterDwellTicks = 0;
                waterDwellTracked = false;
                bestMissionDistance = Double.MAX_VALUE;
                // Episodio nuevo: al salir del agua se repone el presupuesto de escapes.
                escapeAgotado = false;
                escapeBudget.reiniciar();
                orillasIntentadas.clear();
                return false;
            }
            if (escapeAgotado) {
                return false; // no se vuelve a entrar en el bucle de orillas de este episodio
            }
            if (!waterDwellTracked) {
                waterDwellTracked = true;
                waterDwellTicks = 0;
                BlockPos objective = zombie.navExecutor().objective();
                bestMissionDistance = objective == null ? Double.MAX_VALUE
                        : zombie.distanceToSqr(objective.getX() + 0.5D, objective.getY() + 0.5D,
                        objective.getZ() + 0.5D);
            }
            waterDwellTicks++;
            // El movimiento horizontal del goal de misión no reinicia el dwell. Solo lo reinicia un avance
            // real hacia la misión (>2 bloques más cerca) o salir del agua.
            BlockPos objective = zombie.navExecutor().objective();
            if (objective != null && bestMissionDistance < Double.MAX_VALUE) {
                double d = zombie.distanceToSqr(objective.getX() + 0.5D, objective.getY() + 0.5D,
                        objective.getZ() + 0.5D);
                if (d < bestMissionDistance - 4.0D) {
                    bestMissionDistance = d;
                    waterDwellTicks = 0;
                }
            }
            // Seguimiento de atasco general para no activarlo mientras nada de verdad.
            double dx = zombie.getX() - trackX;
            double dz = zombie.getZ() - trackZ;
            if (Double.isNaN(trackX) || dx * dx + dz * dz > 0.01D) {
                trackX = zombie.getX();
                trackZ = zombie.getZ();
                idleStuckTicks = 0;
            } else {
                idleStuckTicks++;
            }
            return idleStuckTicks > STUCK_TICKS_BEFORE_TRYING || waterDwellTicks > WATER_DWELL_TICKS;
        }

        @Override
        public boolean canContinueToUse() {
            // A2 REMEDIACIÓN 2 · finito: si se agotan los escapes, se deja de pedir orillas y el ejecutor
            // retoma la mision. Antes podía recalcular una orilla nueva cada 200 ticks sin tope.
            return !escapeAgotado && zombie.isInWater() && zombie.isInWaterOrRain();
        }

        @Override
        public void start() {
            stuckTicks = 0;
            jumpCooldown = 0;
            escapeTicks = 0;
            recoveryBreaks = 0;
            lastX = zombie.getX();
            lastZ = zombie.getZ();
            solicitarOrilla(findNearestShore());
        }

        /** A2 REMEDIACIÓN 2 · pide una orilla contando el intento; al agotar el presupuesto retoma la mision. */
        private void solicitarOrilla(BlockPos candidata) {
            if (escapeAgotado) {
                return;
            }
            if (!escapeBudget.intentar()) {
                escapeAgotado = true;
                shore = null;
                // No se declara ARRIVED ni se destruye la mision: el ejecutor vuelve a `misionFinal`.
                zombie.navExecutor().clearTacticalObjective();
                DevilRpg.LOGGER.info("[Siege] asaltante #{}: escapes de agua agotados ({}/{});"
                                + " se retoma la mision", zombie.getId(), escapeBudget.intentos(),
                        escapeBudget.maxIntentos());
                return;
            }
            if (candidata == null) {
                shore = null;
                return; // no hay orilla segura en este intento; el siguiente recalculo cuenta otro intento
            }
            shore = candidata;
            orillasIntentadas.add(candidata.immutable());
            // A2 Stage 4Q · AUTORIDAD UNICA DE MOVIMIENTO: si el ejecutor propio esta al mando, este goal
            // SOMETE el objetivo (la orilla) y no navega por su cuenta; si no, se conserva el comportamiento
            // de siempre. No se elimina la funcionalidad de salir del agua.
            if (!zombie.navegarTacticamente(shore)) {
                zombie.getNavigation().moveTo(shore.getX(), shore.getY(), shore.getZ(), 1.2D);
                if (Boolean.getBoolean("n4.trace")) {
                    DevilRpg.LOGGER.info("[N4TRACE] uuid={} shore={} custom=false fallback=vanilla nav={} obj={}",
                            zombie.getUUID(), shore.toShortString(), zombie.navExecutor().state(),
                            zombie.navExecutor().objective());
                }
            } else if (Boolean.getBoolean("n4.trace")) {
                DevilRpg.LOGGER.info("[N4TRACE] uuid={} shore={} custom=true nav={} obj={}",
                        zombie.getUUID(), shore.toShortString(), zombie.navExecutor().state(),
                        zombie.navExecutor().objective());
            }
        }

        @Override
        public void stop() {
            // SIN CASTIGO: al salir del agua se deja de rastrear, pero no se le prohibe volver a nadar (antes se
            // ponía `retryCooldown = MAX_ESCAPE_TICKS`, o sea 10 s más sin poder tocar el agua: la mitad de los 20 s
            // que el jugador veía al asaltante parado en el agua).
            trackX = Double.NaN;
            idleStuckTicks = 0;
            waterDwellTicks = 0;
            waterDwellTracked = false;
            // A2 REMEDIACIÓN 2 · al salir del agua se RETOMA la mision; el guard interno no hace nada si no
            // habia objetivo tactico.
            zombie.navExecutor().clearTacticalObjective();
        }

        @Override
        public void tick() {
            escapeTicks++;
            double dx = zombie.getX() - lastX;
            double dz = zombie.getZ() - lastZ;
            if (dx * dx + dz * dz < 0.01D) {
                stuckTicks++;
            } else {
                stuckTicks = 0;
                lastX = zombie.getX();
                lastZ = zombie.getZ();
            }
            // Si no avanza en 80 ticks (4 s), re-buscar la orilla y re-navegar.
            if (stuckTicks >= 80) {
                solicitarOrilla(findNearestShore());
                stuckTicks = 0;
                lastX = zombie.getX();
                lastZ = zombie.getZ();
            }
            // Y si lleva RECALCULAR_CADA_TICKS sin salir del agua, se vuelve a buscar orilla (la que eligió puede no
            // tener salida). El presupuesto de escapes es finito: no hay bucle indefinido.
            if (escapeTicks >= RECALCULAR_CADA_TICKS) {
                escapeTicks = 0;
                solicitarOrilla(findNearestShore());
            }
            // Empuje hacia la orilla y hacia arriba MIENTRAS ESTÁ EN EL AGUA, a ritmo de nado (cada 6 ticks, que son
            // 0,3 s): es lo que le hace avanzar a velocidad normal y trepar el desnivel de la orilla. Antes era cada 15
            // ticks y además con la mitad del tiempo rendido, y eso es "avanzan muy lento".
            if (zombie.isInWater() && shore != null && --jumpCooldown <= 0) {
                jumpTowardShore();
                jumpCooldown = 6;
            }
            // A5.32 · Stage 2 (break) after the bounded jump has failed. At most 3 blocks per escape attempt.
            if (zombie.isInWater() && shore != null && stuckTicks >= 20 && recoveryBreaks < 3) {
                int rotos = zombie.romperRecuperacionHacia(shore);
                if (rotos > 0) {
                    recoveryBreaks++;
                    zombie.navegarTacticamente(shore);
                    stuckTicks = 0;
                    lastX = zombie.getX();
                    lastZ = zombie.getZ();
                    DevilRpg.LOGGER.info("[Siege] asaltante #{} recuperacion de agua: rompio {} bloque(s) hacia {}"
                                    + " (intento {}/3)", zombie.getId(), rotos, shore.toShortString(), recoveryBreaks);
                }
            }
        }

        /**
         * Empuja al zombie hacia arriba y en la dirección de la orilla para que trepe el desnivel desde el
         * agua. Es deliberadamente simple y robusto: no depende de detectar el bloque exacto, solo acerca
         * el cuerpo a la orilla y lo eleva, para que el pathfinding pueda subir a la tierra.
         */
        private void jumpTowardShore() {
            BlockPos zPos = zombie.blockPosition();
            int sx = Integer.signum(shore.getX() - zPos.getX());
            int sz = Integer.signum(shore.getZ() - zPos.getZ());
            // Empuje horizontal hacia la orilla + empuje vertical para elevarse sobre el agua.
            double vx = sx * 0.4D;
            double vz = sz * 0.4D;
            double vy = 0.5D;
            zombie.setDeltaMovement(vx, vy, vz);
        }

        /** Busca el bloque de tierra (no agua) más cercano dentro de un radio, escaneando en espiral. */
        private BlockPos findNearestShore() {
            BlockPos pos = zombie.blockPosition();
            int r = 0;
            while (r <= 16) {
                for (int x = -r; x <= r; x++) {
                    for (int z = -r; z <= r; z++) {
                        if (Math.abs(x) != r && Math.abs(z) != r) continue; // borde del cuadrado
                        // Se mira el suelo a la altura de sus pies y también uno por encima: en las islas al
                        // nivel del agua el bloque de tierra queda a la MISMA altura que el agua de al lado,
                        // así que mirando solo por debajo no lo encontraría.
                        for (int dy = -1; dy <= 0; dy++) {
                            BlockPos ground = new BlockPos(pos.getX() + x, pos.getY() + dy, pos.getZ() + z);
                            if (isLand(ground)) {
                                BlockPos candidata = ground.above();
                                if (!orillasIntentadas.contains(candidata)) {
                                    return candidata;
                                }
                            }
                        }
                    }
                }
                r++;
            }
            return null;
        }

        private boolean isLand(BlockPos pos) {
            if (!zombie.level().getFluidState(pos).is(FluidTags.WATER)) {
                return zombie.level().getBlockState(pos).isSolid();
            }
            return false;
        }
    }

    /**
     * Goal: romper el obstáculo que le estorba cuando el zombie no logra acercarse a su objetivo, aunque
     * se balancee o rodee (el problema es la deriva/orbitación, no la inmovilidad). Lleva un registro del
     * mejor avance real (distancia al objetivo) y, si tras un periodo no mejoró, rompe el bloque delante.
     * Rompe bloques "débiles" por defecto y obsidiana si su nivel lo permite ({@link #canBreakObsidian()}).
     */
    static class BreakBlockGoal extends Goal {
        /** Tope de acciones de picado sin avanzar; evita cavar indefinidamente desde el mismo sitio. */
        private static final int MAX_BREAK_ACTIONS_WITHOUT_PROGRESS = 24;
        private static final double MIN_EXCAVATION_PROGRESS_SQR = 4.0D;
        /**
     * Cada cuántos ticks se evalúa romper. <b>MEDIDO Y BAJADO (5-oct-2026)</b>: eran <b>60</b> — <b>TRES SEGUNDOS</b>
     * pensando si romper ✗, que es lo que el jugador describió (*«no tarde tanto en romper el muro»*, *«no deben dudar
     * tanto»* ✓). Y en este mismo sitio ya estaba medido con el arnés que, con un muro por delante, el asaltante
     * <b>lo rodea</b> y <b>no rompe ni un bloque en 3 minutos</b> ✗. Con <b>10</b> ticks decide en medio segundo ✓: no
     * duda, rompe lo que le estorba y sigue hacia el centro ✓ (que es su prioridad ✓).
     */
    private static final int BREAK_EVERY_TICKS = 10;
        /** Cuánto tiene que moverse (en bloques, al cuadrado) para contar como que AVANZA: 0,5 bloques. */
        private static final double MOVIMIENTO_QUE_CUENTA = 0.25D;
        private final AggressiveZombieEntity zombie;
        private BlockPos blockToBreak = null;
        /** Dónde estaba el zombie en el tick anterior: es lo que dice si avanza (ver {@link #tick()}). */
        private net.minecraft.world.phys.Vec3 ultimaPos = null;
        private int evalTicks = 0;
        private int fallosConsecutivos = 0;
        private int enfriamientoDeFallo = 0;
        private LivingEntity objetivoDeExcavacion;
        /** A2 FASE 7 · ultima celda de rotura (centro de la aldea cuando se marcha, objetivo cuando se pelea). */
        private BlockPos objetivoDeRoturaCache;
        private net.minecraft.world.phys.Vec3 anclaDeExcavacion;
        private int accionesDePicadoSinAvance;
        private boolean excavacionPausada;

        public BreakBlockGoal(AggressiveZombieEntity zombie) {
            this.zombie = zombie;
            // A PROPÓSITO sin flags: este goal es un observador pasivo que solo rompe bloques cuando detecta
            // que el zombie no progresa. NO navega ni mira, así que no debe pedir MOVE/LOOK; si los pidiera,
            // como se registra en prioridad 3, bloquearía a la persecución mientras haya objetivo.
        }

        @Override
        public boolean canUse() {
            // A2 FASE 7 · TAMBIEN DURANTE LA MARCHA AL CENTRO.
            // Antes este goal solo existia con un objetivo de COMBATE. Un asaltante que marchaba al centro con
            // un muro delante no tenia objetivo vivo y, por tanto, NINGUNA ruta de brecha: se quedaba empujando
            // la pared. Ahora el objetivo de rotura puede ser el centro de la aldea.
            if (zombie.getTarget() != null && zombie.getTarget().isAlive()) {
                return true;
            }
            return zombie.isGoToCenterActive() && zombie.getVillageCenter() != null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        /** A2 FASE 7 · a que celda se abre paso: al objetivo de combate o, si no hay, al centro del asedio. */
        private BlockPos objetivoDeRotura() {
            LivingEntity target = zombie.getTarget();
            if (target != null && target.isAlive()) {
                return target.blockPosition();
            }
            if (zombie.isGoToCenterActive() && zombie.getVillageCenter() != null) {
                return zombie.getVillageCenter();
            }
            return null;
        }

        @Override
        public void start() {
            ultimaPos = zombie.position();
            evalTicks = 0;
            blockToBreak = null;
            fallosConsecutivos = 0;
            enfriamientoDeFallo = 0;
            objetivoDeExcavacion = zombie.getTarget();
            objetivoDeRoturaCache = null;
            anclaDeExcavacion = zombie.position();
            accionesDePicadoSinAvance = 0;
            excavacionPausada = false;
        }

        /**
         * Abrirse paso cuando el zombie <b>no avanza</b>.
         * <p>
         * <b>El progreso se mide por dónde está el ZOMBIE</b> (¿se ha movido 0,5 bloques desde el tick anterior?), no
         * por su distancia al objetivo. Estaba medido con la distancia y era el fallo que dejaba el asedio fuera: con
         * un muro de piedra de 15 bloques delante y el objetivo al otro lado, el asaltante lo <b>RODEA</b>, y mientras
         * rodea la distancia al objetivo sigue bajando poco a poco, así que el detector de atasco <b>no se disparaba
         * nunca</b> (medido con el arnés: 3 minutos sin romper un solo bloque y el muro intacto).
         * <p>
         * <b>Y SOLO SE ROMPE FUERA DEL RECINTO</b> (5-oct-2026). Los dos sitios donde este goal elige qué picar
         * —{@link #blockingBlockAhead()} y {@link #breakStepAhead(Entity)}— preguntan antes a
         * {@link AggressiveZombieEntity#protegidoPorLaAldea}, que es donde vive la regla del jugador (*"solo el muro
         * perimetral es rompible; una vez dentro ya no puede romper nada"*). Antes este goal rompía <b>sin comprobar
         * nada</b>: era la mitad que faltaba de I89, y un asaltante que ya había entrado seguía levantando el pueblo.
         */
        @Override
        public void tick() {
            BlockPos hacia = objetivoDeRotura();
            LivingEntity target = zombie.getTarget();
            if (hacia == null) {
                return;
            }
            net.minecraft.world.phys.Vec3 posicion = zombie.position();
            if (objetivoDeExcavacion != target || !hacia.equals(objetivoDeRoturaCache)) {
                objetivoDeExcavacion = target;
                objetivoDeRoturaCache = hacia.immutable();
                anclaDeExcavacion = posicion;
                accionesDePicadoSinAvance = 0;
                excavacionPausada = false;
            } else if (anclaDeExcavacion != null
                    && posicion.distanceToSqr(anclaDeExcavacion) > MIN_EXCAVATION_PROGRESS_SQR) {
                anclaDeExcavacion = posicion;
                accionesDePicadoSinAvance = 0;
                excavacionPausada = false;
            }
            if (excavacionPausada) {
                ultimaPos = posicion;
                evalTicks = 0;
                return;
            }
            if (enfriamientoDeFallo > 0) {
                enfriamientoDeFallo--;
                ultimaPos = posicion;
                return;
            }
            boolean seMueve = ultimaPos != null && zombie.position().distanceToSqr(ultimaPos) > MOVIMIENTO_QUE_CUENTA;
            ultimaPos = posicion;
            if (seMueve) {
                evalTicks = 0;
                return;
            }
            // Pegado al objetivo no se rompe NADA: ahí lo que toca es pelear (y lo de dentro de la aldea está vetado,
            // así que picar alrededor del jugador solo levantaría el pueblo).
            if (zombie.distanceToSqr(hacia.getX() + 0.5D, hacia.getY() + 0.5D, hacia.getZ() + 0.5D) < 9.0D) {
                evalTicks = 0;
                return;
            }
            // Sin moverse durante el periodo: está chocando u orbitando. A abrirse paso.
            if (++evalTicks >= BREAK_EVERY_TICKS) {
                boolean rompio;
                if (target == null) {
                    // A2 FASE 7 · MARCHA: solo se ataca el ANILLO REAL DEL MURO (brecha del asedio). No se
                    // levanta terreno arbitrario fuera del blanco del asedio.
                    rompio = breakBrechaAhead(hacia);
                } else {
                    boolean targetAbove = hacia.getY() > zombie.getY() + 1.0D;
                    if (targetAbove) {
                        // El objetivo está ARRIBA: un túnel a su propia altura no le sirve para subir. Rompe un
                        // ESCALÓN: la columna de delante a la altura de la cabeza (y una más arriba), de modo que
                        // el obstáculo quede en un bloque de alto que SÍ puede saltar.
                        rompio = breakStepAhead(hacia);
                    } else {
                        blockToBreak = blockingBlockAhead();
                        rompio = blockToBreak != null && breakBlock(blockToBreak);
                    }
                }
                if (rompio) {
                    fallosConsecutivos = 0;
                    if (++accionesDePicadoSinAvance >= MAX_BREAK_ACTIONS_WITHOUT_PROGRESS) {
                        excavacionPausada = true;
                    }
                    zombie.pathPlanner.terrainChanged();
                    // A2 REMEDIACIÓN 2 · la brecha cambia el mundo: ademas del planificador heredado, se
                    // invalida la ruta/cache del ejecutor propio para que no siga una arista obsoleta.
                    zombie.navExecutor().terrainChanged();
                } else {
                    fallosConsecutivos++;
                    enfriamientoDeFallo = Math.min(200, 20 << Math.min(fallosConsecutivos, 3));
                }
                evalTicks = 0;
            }
        }

        /**
         * Abre un <b>escalón</b> hacia el objetivo cuando este está por encima: rompe los bloques de la
         * columna de delante a la altura de la cabeza y uno más arriba. Así un muro de 2–3 bloques queda
         * convertido en un escalón de 1 bloque, que el zombie puede saltar y luego repetir para seguir subiendo.
         */
        private boolean breakStepAhead(Entity target) {
            return target != null && breakStepAhead(target.blockPosition());
        }

        private boolean breakStepAhead(BlockPos hacia) {
            BlockPos zPos = zombie.blockPosition();
            int sx = Integer.signum(hacia.getX() - zPos.getX());
            int sz = Integer.signum(hacia.getZ() - zPos.getZ());
            // Columnas candidatas: primero las que van hacia el objetivo, y si está justo encima, las cuatro.
            int[][] dirs = (sx == 0 && sz == 0)
                    ? new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}
                    : new int[][]{{sx, 0}, {0, sz}, {sx, sz}};
            for (int[] d : dirs) {
                if (d[0] == 0 && d[1] == 0) continue;
                int px = zPos.getX() + d[0];
                int pz = zPos.getZ() + d[1];
                boolean broke = false;
                for (int dy = 1; dy <= 2; dy++) { // altura de la cabeza y uno más arriba
                    BlockPos p = new BlockPos(px, zPos.getY() + dy, pz);
                    if (zombie.protegidoPorLaAldea(p)) {
                        continue; // dentro de la aldea no se pica (la muralla sí: es la brecha)
                    }
                    // NI LOS TABLONES QUE HA TENDIDO ÉL (I239): el puente sube un bloque por tablón, así que los de
                    // arriba caen justo a la altura de la cabeza y este camino se los llevaba por delante.
                    if (esTablonDelAsalto(p)) {
                        continue;
                    }
                    if (canBreak(zombie.level().getBlockState(p))) {
                        broke |= zombie.breakBlockAt(p);
                    }
                }
                if (broke) {
                    return true;
                }
            }
            return false;
        }

        /**
         * A2 FASE 7 · <b>BRECHA DE LA MARCHA</b>: abre paso SOLO en el anillo real del muro generado.
         * <p>
         * Antes, un asaltante sin objetivo de combate no tenia ningun camino de rotura: {@code BreakBlockGoal}
         * exigia {@code getTarget() != null}. Ahora la marcha al centro puede abrir la brecha, pero con dos
         * limites deliberados: (1) la celda tiene que pertenecer al anillo del muro ({@code esLaMuralla});
         * (2) se gasta el MISMO presupuesto de tunel ({@code tunelRestante}/{@code tunelEspera}) que las demas
         * acciones de rotura. Asi no se destruye terreno arbitrario ni se cava sin fin.
         */
        private boolean breakBrechaAhead(BlockPos hacia) {
            if (zombie.tunelRestante <= 0 || zombie.tunelEspera > 0) {
                return false;
            }
            BlockPos zPos = zombie.blockPosition();
            int cota = zombie.cotaParaElRecinto();
            int sx = Integer.signum(hacia.getX() - zPos.getX());
            int sz = Integer.signum(hacia.getZ() - zPos.getZ());
            int[][] dirs = (sx == 0 && sz == 0)
                    ? new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}
                    : new int[][]{{sx, 0}, {0, sz}, {sx, sz}};
            for (int[] d : dirs) {
                if (d[0] == 0 && d[1] == 0) continue;
                for (int dist = 1; dist <= 2; dist++) {
                    int px = zPos.getX() + d[0] * dist;
                    int pz = zPos.getZ() + d[1] * dist;
                    boolean roto = false;
                    for (int dy = 0; dy <= 2; dy++) {
                        // A2 REMEDIACIÓN 2 · el presupuesto se comprueba POR BLOQUE, no solo al entrar:
                        // una columna de 3 no puede dejar `tunelRestante` en negativo.
                        if (zombie.tunelRestante <= 0) {
                            break;
                        }
                        BlockPos p = new BlockPos(px, zPos.getY() + dy, pz);
                        if (!zombie.esLaMuralla(p, cota)) {
                            continue; // fuera del anillo del muro: no es una brecha del asedio
                        }
                        if (zombie.protegidoPorLaAldea(p)) {
                            continue; // la capa/celda no es muralla real: no se toca
                        }
                        if (esTablonDelAsalto(p)) {
                            continue;
                        }
                        if (canBreak(zombie.level().getBlockState(p)) && zombie.breakBlockAt(p)) {
                            zombie.tunelRestante--;
                            roto = true;
                        }
                    }
                    if (roto) {
                        zombie.tunelEspera = TUNEL_ENTRE_BLOQUES;
                        zombie.particulasDeTrabajo(new BlockPos(px, zPos.getY(), pz));
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Busca el bloque sólido rompible que más probablemente estorba el paso: mira posiciones alrededor
         * (a la altura del cuerpo y uno arriba, en las 4 direcciones) priorizando la ruta hacia el objetivo.
         */
        private BlockPos blockingBlockAhead() {
            Entity target = zombie.getTarget();
            if (target == null) return null;
            BlockPos zPos = zombie.blockPosition();
            BlockPos best = null;
            double bestScore = Double.MAX_VALUE;
            int sx = Integer.signum((int) Math.floor(target.getX()) - zPos.getX());
            int sz = Integer.signum((int) Math.floor(target.getZ()) - zPos.getZ());
            for (int dy = 0; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        BlockPos candidate = new BlockPos(zPos.getX() + dx, zPos.getY() + dy, zPos.getZ() + dz);
                        if (zombie.protegidoPorLaAldea(candidate)) {
                            continue; // la obra del pueblo no se toca (la muralla sí: es la brecha del asedio)
                        }
                        BlockState bs = zombie.level().getBlockState(candidate);
                        // ¿ESTORBA? Se pregunta por la FORMA DE COLISIÓN, no por `isSolid()`: `isSolid()` deja fuera la
                        // valla —su forma no es un cubo— y la valla es justo lo que cierra las parcelas y la aldea.
                        // Medido con el arnés: el asaltante se quedó de bruces contra una valla de roble, sin picarla,
                        // con el objetivo (el jugador de pega) al otro lado.
                        if (!bs.isAir() && !bs.getCollisionShape(zombie.level(), candidate).isEmpty() && canBreak(bs)) {
                            // PERO NO SE PICA LO QUE ESTÁ EN EL AIRE (I239, medido el 9-oct-2026). Un bloque a la
                            // altura de los pies (o uno más) con AIRE DEBAJO no es terreno que estorbe: es un TABLÓN
                            // DEL PUENTE —los tiende `puentearHacia` justo así, sobre el agujero— y el romper y el
                            // puente se estaban peleando: medido con el arnés, de los bloques que picaba, 45-62 eran
                            // `cobblestone` EN EL ANILLO DEL FOSO (r=10..11), o sea los tablones que acababa de
                            // tender él mismo. Sin esta regla el puente se destruye solo.
                            if (zombie.level().getBlockState(candidate.below()).isAir()) {
                                continue;
                            }
                            // Y TAMPOCO LOS SUYOS, aunque el tablón de abajo les haga de suelo (el puente sube un
                            // bloque por tablón, así que a partir del segundo SÍ tienen suelo debajo).
                            if (esTablonDelAsalto(candidate)) {
                                continue;
                            }
                            double score = Math.abs(dx - sx) + Math.abs(dz - sz) + dy * 0.5D;
                            if (score < bestScore) {
                                bestScore = score;
                                best = candidate;
                            }
                        }
                    }
                }
            }
            return best;
        }

        private boolean canBreak(BlockState state) {
            return zombie.canBreakBlock(state);
        }

        private boolean breakBlock(BlockPos pos) {
            boolean broke = zombie.breakBlockAt(pos);
            // Abrir SIEMPRE el hueco de 2 de alto (cuerpo + cabeza). Antes solo se rompía el de arriba si el
            // objetivo estaba más alto, así que contra una valla de 2 bloques el zombie picaba el de abajo,
            // seguía sin caber y necesitaba otra ronda entera (3 s más) para el de arriba.
            // Los dos de arriba pasan por la MISMA regla: si la celda de arriba ya es de dentro de la aldea (el
            // borde de la brecha, a la altura de la cabeza), no se toca, que un hueco abierto hacia dentro es
            // justo lo que el jugador veta (*"una vez dentro ya no puede romper nada"*).
            BlockPos above = pos.above();
            if (!zombie.protegidoPorLaAldea(above) && zombie.canBreakBlock(zombie.level().getBlockState(above))) {
                broke |= zombie.breakBlockAt(above);
            }
            // Y si el objetivo está todavía más arriba, uno más: así el hueco queda en escalón de subida.
            Entity target = zombie.getTarget();
            if (target != null && target.getY() > zombie.getY() + 1.0D) {
                BlockPos higher = above.above();
                if (!zombie.protegidoPorLaAldea(higher)
                        && zombie.canBreakBlock(zombie.level().getBlockState(higher))) {
                    broke |= zombie.breakBlockAt(higher);
                }
            }
            return broke;
        }
    }

    /**
     * <b>LOS TRES ATRAVESADORES</b> (I212): cuando el asaltante <b>no avanza</b> hacia su objetivo, elige la
     * herramienta que le toca y la usa <b>en el mismo tick</b>, sin dudar:
     * <ol>
     *   <li><b>PUENTE</b> — si lo que hay delante es un hueco (o agua), tiende un tablón y pasa
     *       ({@link AggressiveZombieEntity#puentearHacia});</li>
     *   <li><b>ESCALERA DE BLOQUES</b> — si el objetivo está <b>arriba</b>, abre el hueco de delante y se apila un
     *       escalón bajo los pies ({@link AggressiveZombieEntity#apilarBloqueParaSubir});</li>
     *   <li><b>TÚNEL</b> — si el objetivo está <b>abajo</b> y no hay bajada, cava
     *       ({@link AggressiveZombieEntity#cavarHaciaAbajo}).</li>
     * </ol>
     * Lo pidió el jugador: *«no deben dudar tanto para ocupar estas herramientas»* ✓, y las tres van a <b>velocidad
     * normal</b> (medio segundo por bloque, no los tres segundos que tenía el romper antes de I209).
     * <p>
     * Es un goal <b>pasivo</b>, como {@link BreakBlockGoal}: <b>sin flags</b> a propósito, porque no navega —el camino
     * lo sigue llevando su goal de siempre mientras esto le quita el estorbo—. Comparte con él el detector de atasco
     * (¿se ha movido 0,5 bloques desde el tick anterior?) y <b>las dos reglas de la casa</b>: la del muro
     * ({@link AggressiveZombieEntity#protegidoPorLaAldea}: el anillo se pica, dentro de la aldea no) y la de no pisar
     * el camino de otro goal (I205).
     */
    static class TraverseGoal extends Goal {
        /** Comprueba el progreso cada medio segundo, igual que el ritmo original de traversal. */
        private static final int VENTANA_DE_MOVIMIENTO = 10;
        /** Medio bloque de progreso durante la ventana basta para dejar que la ruta siga. */
        private static final double DESPLAZAMIENTO_MINIMO_SQR = 0.25D;
        private static final int VENTANAS_PARADO_ANTES_DE_ACTUAR = 1;
        private final AggressiveZombieEntity zombie;
        private net.minecraft.world.phys.Vec3 ultimaPos = null;
        private int evalTicks;
        private int ventanasParado;
        private int enfriamiento;
        private int fallosConsecutivos;

        public TraverseGoal(AggressiveZombieEntity zombie) {
            this.zombie = zombie;
            // A PROPÓSITO sin flags (igual que BreakBlockGoal): no navega ni mira, solo aparta el estorbo.
        }

        @Override
        public boolean canUse() {
            // Sirve a los DOS caminos del asaltante, a propósito: el que va a por un objetivo (pelea) y el que marcha
            // al centro de la aldea (asedio). Si solo mirara el objetivo, un asaltante que marcha al centro —el caso
            // de la ola cuando aún no ha visto a nadie— se quedaría sin puente, sin escalera y sin túnel.
            return objetivoDeLaMarcha() != null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            ultimaPos = zombie.position();
            evalTicks = 0;
            ventanasParado = 0;
            enfriamiento = 0;
            fallosConsecutivos = 0;
        }

        /** La celda a la que va: la de lo que ataca o, si no ataca a nadie, la del centro de su aldea. */
        private BlockPos objetivoDeLaMarcha() {
            Entity target = zombie.getTarget();
            if (target != null && target.isAlive()) {
                return target.blockPosition();
            }
            BlockPos centro = zombie.getVillageCenter();
            if (centro == null || zombie.getHomePos() != null) {
                return null;
            }
            return centro;
        }

        @Override
        public void tick() {
            BlockPos hacia = objetivoDeLaMarcha();
            if (hacia == null) {
                return;
            }
            if (zombie.distanceToSqr(hacia.getX() + 0.5D, hacia.getY() + 0.5D, hacia.getZ() + 0.5D) < 9.0D) {
                ultimaPos = zombie.position();
                evalTicks = 0;
                ventanasParado = 0;
                return; // pegado al objetivo: aquí lo que toca es pelear
            }
            if (enfriamiento > 0) {
                enfriamiento--;
                ultimaPos = zombie.position();
                return;
            }
            if (++evalTicks < VENTANA_DE_MOVIMIENTO) {
                return;
            }
            evalTicks = 0;
            net.minecraft.world.phys.Vec3 posicion = zombie.position();
            if (ultimaPos != null && posicion.distanceToSqr(ultimaPos) > DESPLAZAMIENTO_MINIMO_SQR) {
                ultimaPos = posicion;
                ventanasParado = 0;
                fallosConsecutivos = 0;
                return;
            }
            ultimaPos = posicion;
            if (++ventanasParado < VENTANAS_PARADO_ANTES_DE_ACTUAR) {
                return;
            }
            ventanasParado = 0;
            // LA HERRAMIENTA SE ELIGE POR SITUACIÓN, no por un orden fijo (medido en la corrida 36): con el orden
            // fijo (puente → escalera → túnel), un asaltante que tenía una cueva debajo y su objetivo abajo se pasaba
            // la corrida APILANDO escalones hacia arriba —12 cavadas frente a 1 escalón— por culpa del orden. Ahora:
            // objetivo ABAJO → se cava; objetivo ARRIBA → escalera de bloques; si no, un hueco delante → puente.
            // Y si la herramienta que le toca no puede (porque está dentro de la aldea, o le queda presupuesto),
            // se prueba la siguiente, que es lo que hace que nunca se quede sin hacer nada.
            boolean algo = false;
            // I361 · LA MISMA TOLERANCIA AL BAJAR QUE AL SUBIR (medido en su registro: CAVA hacia abajo 43, 38, 29, 22... -lo DOMINANTE- y 0 entradas; el objetivo es la victima, L1801, y si esta UN bloque mas baja se ponia a cavar hacia el suelo del pueblo en vez de atravesar el muro). Al subir ya habia tolerancia de 1 (y+1, abajo); al bajar no habia ninguna.
            if (hacia.getY() < zombie.blockPosition().getY() - 1) {
                algo = zombie.cavarHaciaAbajo(hacia);
            } else if (hacia.getY() > zombie.blockPosition().getY() + 1) {
                algo = zombie.apilarBloqueParaSubir(hacia);
            }
            if (!algo) {
                // I374 · EL JUEGO DEL ASEDIO, COMO LO PIDIO EL JUGADOR: a la altura del bloqueo hay DOS maneras de sortearlo
                // (ROMPER el muro con el tunel de frente, o TREPAR con una escalera de bloques) y, cuando valen las dos,
                // se ELIGE AL AZAR; si solo vale una, se usa esa. Antes iba en orden fijo (puente -> escalera), asi que
                // siempre hacia lo mismo. Basta con probarlas en orden aleatorio: la que pueda, entra.
                boolean primeroRomper = zombie.primeroRomperDecidido();
                for (int intento = 0; intento < 2 && !algo; intento++) {
                    boolean romperAhora = (intento == 0) == primeroRomper;
                    algo = romperAhora ? zombie.puentearHacia(hacia) : zombie.apilarBloqueParaSubir(hacia);
                }
            }
            if (!algo) {
                algo = zombie.cavarHaciaAbajo(hacia);
            }
            if (algo) {
                fallosConsecutivos = 0;
                // The block helpers already enforce their own 10/20-tick success cooldown. Do not add another
                // 40 ticks here or the effective wall traversal rate drops to one action every 50 ticks.
                enfriamiento = 0;
                zombie.pathPlanner.terrainChanged();
                // A2 REMEDIACIÓN 2 · el atravesador también cambia el mundo; la ruta global del ejecutor
                // no puede sobrevivir a ese cambio.
                zombie.navExecutor().terrainChanged();
            } else {
                // A helper that is still cooling down is not a failed terrain action. Wait only for its cadence;
                // back off longer only when every available helper was ready and none could change the terrain.
                int cooldownRestante = Math.max(zombie.puenteEspera,
                        Math.max(zombie.apilarEspera, zombie.cavarEspera));
                if (cooldownRestante > 0) {
                    enfriamiento = Math.max(0, cooldownRestante - VENTANA_DE_MOVIMIENTO);
                } else {
                    fallosConsecutivos++;
                    enfriamiento = Math.min(160, 20 << Math.min(fallosConsecutivos, 3));
                }
            }
        }
    }

    /**
     * Elige el centro de la aldea solo cuando no hay objetivo de combate. La planificación y el traversal físico
     * están separados: este goal elige el destino y el planner compartido sigue rutas validadas.
     */
    static class MoveToVillageCenterGoal extends Goal {
        private static final double ARRIVE_DIST = 3.0D * 3.0D;
        private static final int TICKS_BEFORE_RECOVERY = 80;
        private final AggressiveZombieEntity zombie;
        private int ticksWithoutRoute;

        MoveToVillageCenterGoal(AggressiveZombieEntity zombie) {
            this.zombie = zombie;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!zombie.isGoToCenterActive() || zombie.getHomePos() != null || zombie.getTarget() != null) {
                return false;
            }
            BlockPos center = zombie.getVillageCenter();
            return center != null && distSqr(center) > ARRIVE_DIST;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            zombie.pathPlanner.reset();
            ticksWithoutRoute = 0;
        }

        @Override
        public void stop() {
            zombie.pathPlanner.reset();
        }

        @Override
        public void tick() {
            BlockPos center = zombie.getVillageCenter();
            if (center == null) {
                return;
            }
            // A2 · con la navegación propia encendida, este goal SOLO envía el objetivo: el movimiento lo ejecuta
            // NavExecutor (dueño único). Así los goals dejan de competir por MOVE.
            // Y el objetivo NO es el centro: el centro es PROTECTED y el planificador nunca lo encola (medido:
            // metaEncolada=false -> 0/8). Se envía el punto de APROXIMACIÓN, por fuera del muro, en el rumbo del
            // propio asaltante.
            if (com.chipoodle.devilrpg.entity.nav.NavFeature.customNavigation()) {
                // A2 · CADENA de etapas a radios decrecientes. `setObjectiveChain` es IDEMPOTENTE: si el contenido no
                // ha cambiado, no toca nada. Eso corrige la regresion medida en `a2n-20261015-212315`, donde el
                // envio por tick reiniciaba las etapas (plans=89, moves=0-3).
                java.util.List<BlockPos> cadena = zombie.cadenaDeAproximacion(center);
                if (!cadena.isEmpty()) {
                    zombie.navExecutor().setObjectiveChain(cadena);
                }
                return;
            }
            AggressiveZombiePathPlanner.Result result = zombie.pathPlanner.navigateTo(center, 1.0D);
            if (result == AggressiveZombiePathPlanner.Result.STALLED
                    || (result == AggressiveZombiePathPlanner.Result.NO_ROUTE
                    && ++ticksWithoutRoute >= TICKS_BEFORE_RECOVERY)) {
                zombie.recoverNavigation(center);
                ticksWithoutRoute = 0;
            } else if (result != AggressiveZombiePathPlanner.Result.NO_ROUTE) {
                ticksWithoutRoute = 0;
            }
        }

        private double distSqr(BlockPos center) {
            return zombie.distanceToSqr(center.getX(), center.getY(), center.getZ());
        }
    }
    /**
     * Reúne la persecución y el flanqueo opcional en el único goal de movimiento de combate. El objetivo determina
     * el destino; el planner compartido valida cada ruta antes de que el zombie la siga.
     */
    static class PursueTargetGoal extends Goal {
        private static final double ATTACK_RANGE_SQR = 4.0D;
        private static final double FLANK_RANGE_SQR = 36.0D;
        private static final double FLANK_RADIUS = 3.5D;
        private static final int DESTINATION_REFRESH_TICKS = 20;
        private static final int TICKS_BEFORE_RECOVERY = 80;
        private final AggressiveZombieEntity zombie;
        private LivingEntity selectedTarget;
        private BlockPos selectedDestination;
        private int destinationRefreshTicks;
        private int attackTicks;
        private int ticksWithoutRoute;
        private boolean inAttackRange;
        /** A2 REMEDIACIÓN 2 · refleja si ESTE goal posee MOVE ahora mismo (no solo si hay target). */
        private boolean activo;

        public boolean estaActivo() {
            return activo;
        }

        PursueTargetGoal(AggressiveZombieEntity zombie) {
            this.zombie = zombie;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = zombie.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            activo = true;
            zombie.pathPlanner.reset();
            selectedTarget = null;
            selectedDestination = null;
            destinationRefreshTicks = 0;
            attackTicks = 0;
            ticksWithoutRoute = 0;
            inAttackRange = false;
        }

        @Override
        public void stop() {
            activo = false;
            zombie.pathPlanner.reset();
            selectedTarget = null;
            selectedDestination = null;
            ticksWithoutRoute = 0;
            inAttackRange = false;
        }

        @Override
        public void tick() {
            LivingEntity target = zombie.getTarget();
            if (target == null || !target.isAlive()) {
                return;
            }
            if (selectedTarget != target) {
                zombie.pathPlanner.reset();
                selectedTarget = target;
                selectedDestination = null;
                destinationRefreshTicks = 0;
                ticksWithoutRoute = 0;
                inAttackRange = false;
            }
            zombie.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (zombie.distanceToSqr(target) <= ATTACK_RANGE_SQR) {
                if (!inAttackRange) {
                    zombie.pathPlanner.reset();
                    inAttackRange = true;
                }
                if (--attackTicks <= 0) {
                    zombie.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                    zombie.doHurtTarget(target);
                    attackTicks = 20;
                }
                return;
            }
            inAttackRange = false;

            if (selectedDestination == null || --destinationRefreshTicks <= 0) {
                selectedDestination = chooseDestination(target);
                destinationRefreshTicks = DESTINATION_REFRESH_TICKS;
            }
            AggressiveZombiePathPlanner.Result result = zombie.pathPlanner.navigateTo(selectedDestination, 1.2D);
            if (result == AggressiveZombiePathPlanner.Result.STALLED
                    || (result == AggressiveZombiePathPlanner.Result.NO_ROUTE
                    && ++ticksWithoutRoute >= TICKS_BEFORE_RECOVERY)) {
                zombie.recoverNavigation(selectedDestination);
                ticksWithoutRoute = 0;
            } else if (result != AggressiveZombiePathPlanner.Result.NO_ROUTE) {
                ticksWithoutRoute = 0;
            }
        }

        private BlockPos chooseDestination(LivingEntity target) {
            BlockPos targetPos = target.blockPosition();
            if (zombie.distanceToSqr(target) <= FLANK_RANGE_SQR) {
                return targetPos;
            }
            java.util.List<AggressiveZombieEntity> pack = zombie.level().getEntitiesOfClass(
                    AggressiveZombieEntity.class, zombie.getBoundingBox().inflate(12.0D),
                    other -> other == zombie || (other.isAlive() && other.getTarget() == target));
            if (pack.size() < 2) {
                return targetPos;
            }
            pack.sort(java.util.Comparator.comparing(AggressiveZombieEntity::getUUID));
            int slot = pack.indexOf(zombie);
            double angle = Math.PI * 2.0D * Math.max(0, slot) / pack.size();
            int x = (int) Math.floor(target.getX() + Math.cos(angle) * FLANK_RADIUS);
            int z = (int) Math.floor(target.getZ() + Math.sin(angle) * FLANK_RADIUS);
            return new BlockPos(x, targetPos.getY(), z);
        }
    }
    /**
     * Goal: si el zombie tiene un <b>hogar</b> con radio (p. ej. el núcleo de su guarida), patrulla esa
     * zona: si se aleja más allá del radio vuelve hacia el hogar, y si está dentro y sin objetivo de ataque
     * deambula por los alrededores. Así los enemigos de una guarida no se quedan pegados al centro ni se
     * pierden por el mundo.
     */
    static class PatrolHomeGoal extends Goal {
        private final AggressiveZombieEntity zombie;
        private static final double SPEED = 1.0D;
        private static final int WANDER_INTERVAL_TICKS = 60; // cada 3 s elige un nuevo punto de ronda
        private int wanderTicks = 0;
        private double wanderX, wanderZ;

        public PatrolHomeGoal(AggressiveZombieEntity zombie) {
            this.zombie = zombie;
            // Navega (vuelve al hogar o ronda): declara MOVE/LOOK. Es el goal de menor prioridad (8), así que
            // con los flags cualquier goal de combate puede interrumpirlo, que es lo que se quiere.
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            BlockPos home = zombie.getHomePos();
            if (home == null || zombie.getHomeRadius() <= 0) {
                return false;
            }
            // Fuera del radio -> siempre vuelve. Dentro -> patrulla solo si no está atacando.
            return outsideRadius(home) || zombie.getTarget() == null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            wanderTicks = 0;
        }

        @Override
        public void tick() {
            BlockPos home = zombie.getHomePos();
            if (home == null) {
                return;
            }
            // 1) Si se alejó demasiado, volver al hogar.
            if (outsideRadius(home)) {
                // A2 Stage 4Q · AUTORIDAD UNICA: con el ejecutor propio al mando se SOMETE el objetivo (el hogar)
                // y no se navega aqui; si no, comportamiento de siempre.
                if (!zombie.navegarConEjecutorPropio(home)) {
                    zombie.getNavigation().moveTo(home.getX(), home.getY(), home.getZ(), SPEED);
                }
                return;
            }
            // 2) Si tiene objetivo, el ataque manda (este goal no interfiere).
            if (zombie.getTarget() != null) {
                return;
            }
            // 3) Sin objetivo: rondar un punto aleatorio dentro del radio del hogar.
            if (--wanderTicks <= 0 || zombie.getNavigation().isDone()) {
                java.util.Random random = new java.util.Random();
                double angle = random.nextDouble() * Math.PI * 2.0;
                double dist = random.nextDouble() * zombie.getHomeRadius();
                wanderX = home.getX() + Math.cos(angle) * dist;
                wanderZ = home.getZ() + Math.sin(angle) * dist;
                wanderTicks = WANDER_INTERVAL_TICKS;
                zombie.getNavigation().moveTo(wanderX, home.getY(), wanderZ, SPEED);
            }
        }

        /** ¿Está el zombie más allá del radio de patrulla de su hogar? */
        private boolean outsideRadius(BlockPos home) {
            double r = zombie.getHomeRadius();
            return zombie.distanceToSqr(home.getX() + 0.5D, home.getY() + 0.5D, home.getZ() + 0.5D) > r * r;
        }
    }

    // Clase interna para el comportamiento de lanzar fuego
    static class FireballAttackGoal extends Goal {
        /** Distancia MÍNIMA: si está pegado, que use el ataque cuerpo a cuerpo. */
        private static final double MIN_FIREBALL_RANGE = 3.0D;
        /** Distancia MÁXIMA al objetivo para lanzar fuego. */
        private static final double MAX_FIREBALL_RANGE = 16.0D;
        /** Reintento corto cuando no puede disparar, para hacerlo en cuanto el objetivo esté a tiro. */
        private static final int OUT_OF_RANGE_RETRY_TICKS = 20;
        /** Tiempo entre ataques cuando sí dispara. */
        private static final int ATTACK_INTERVAL_TICKS = 240;

        private final AggressiveZombieEntity zombie;
        private int attackTimer;

        public FireballAttackGoal(AggressiveZombieEntity zombie) {
            this.zombie = zombie;
            // A PROPÓSITO sin flags: es un ataque a distancia instantáneo que NO navega ni mira (dispara
            // usando posiciones y se apoya en el SmallFireball). Si pidiera MOVE/LOOK bloquearía el
            // movimiento del zombie mientras tenga objetivo, que es justo lo contrario de lo que se busca.
        }

        @Override
        public boolean canUse() {
            return zombie.getTarget() != null && zombie.getTarget().isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            // Sin esto, el Goal.canContinueToUse() por defecto (true) lo dejaba "corriendo" para siempre
            // incluso después de morir el objetivo.
            return canUse();
        }

        /**
         * ¿Está el objetivo a tiro? Hay que estar entre {@link #MIN_FIREBALL_RANGE} y
         * {@link #MAX_FIREBALL_RANGE} <b>y con línea de visión</b>. Antes no se comprobaba nada: el zombie
         * lanzaba bolas de fuego a cualquier distancia y a través de las paredes (el {@code d0 = 4.0} del código
         * original solo era la dispersión aleatoria del disparo, no un alcance).
         */
        private boolean isInFireballRange(Entity target) {
            double distanceSqr = zombie.distanceToSqr(target);
            if (distanceSqr < MIN_FIREBALL_RANGE * MIN_FIREBALL_RANGE
                    || distanceSqr > MAX_FIREBALL_RANGE * MAX_FIREBALL_RANGE) {
                return false;
            }
            return zombie.getSensing().hasLineOfSight(target);
        }

        @Override
        public void tick() {
            Entity target = zombie.getTarget();
            if (target == null) return;

            if (--attackTimer > 0) return;

            if (!isInFireballRange(target)) {
                // Reintenta pronto: así dispara en cuanto el objetivo entre en rango o deje de estar tras un
                // muro, en vez de esperar los 240 ticks completos sin haber disparado.
                attackTimer = OUT_OF_RANGE_RETRY_TICKS;
                return;
            }

            double d0 = 4.0D; // dispersión aleatoria del disparo
            double d1 = target.getX() - zombie.getX();
            double d2 = target.getY(0.5D) - zombie.getY(0.5D);
            double d3 = target.getZ() - zombie.getZ();
            SmallFireball fireball = new ZombieFireball(zombie.level(), zombie, new Vec3(d1 + zombie.getRandom().nextGaussian() * d0, d2, d3 + zombie.getRandom().nextGaussian() * d0));
            fireball.setPos(fireball.getX(), zombie.getY(0.5D) + 0.5D, fireball.getZ());
            zombie.level().addFreshEntity(fireball);
            attackTimer = ATTACK_INTERVAL_TICKS; // Tiempo entre ataques
        }
    }

}
