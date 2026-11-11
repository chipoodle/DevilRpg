package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A2 · <b>Clasificación de obstáculos</b> de la navegación propia.
 * <p>
 * Sustituye la pregunta difusa «¿hay ruta?» de {@code PathNavigation} por una decisión <b>explícita</b> sobre cada
 * celda, que es lo que permite elegir una transición de terreno concreta (pisar, subir un escalón, saltar, bajar,
 * romper, puentear) en vez de reintentar la misma ruta rechazada.
 * <p>
 * Reglas de la casa que se conservan: <b>nada de romper ni construir dentro del recinto de la aldea</b>; esa
 * decisión la toma quien inyecta el predicado ({@link Policies}), no esta clase, para que la política siga viviendo
 * junto al resto de invariantes de la aldea.
 */
public final class NavObstruction {

    /** Qué se puede hacer con una celda concreta. */
    public enum Kind {
        /** Aire con suelo firme debajo: se camina. */
        PASSABLE,
        /** Bloque a la altura de los pies con hueco encima: se sube (1 bloque). */
        STEP_UP,
        /** Dos bloques de alto con hueco encima: requiere salto. */
        JUMP_UP,
        /** No hay suelo debajo: caída. Solo aceptable si es poco profunda o hay agua. */
        DROP,
        /** Es un bloque rompible y la política lo permite. */
        BREAKABLE,
        /** Es un bloque que la política protege (p. ej. el recinto de la aldea): NO se toca. */
        PROTECTED,
        /** No es transitable ni rompible: hay que rodear. */
        BLOCKED
    }

    /** Políticas inyectables: así la protección de la aldea no se duplica dentro del planificador. */
    public interface Policies {
        /** ¿Esta celda es intocable (aldea, estructuras protegidas)? */
        boolean isProtected(BlockPos pos);

        /** ¿Se permite romper aquí? */
        boolean canBreak(BlockPos pos);

        /** ¿Se permite colocar un bloque de apoyo aquí? */
        boolean canPlace(BlockPos pos);

        /** Altura máxima de caída aceptable sin agua. */
        int maxSafeDrop();

        /** Profundidad mínima de agua que hace segura una caída. */
        int waterDepthForSafeDrop();
    }

    private NavObstruction() {
    }

    /** AABB de una celda, desplazada una fracción para no tocar las paredes laterales. */
    private static AABB cellBox(BlockPos pos) {
        return new AABB(pos.getX() + 0.15D, pos.getY(), pos.getZ() + 0.15D,
                pos.getX() + 0.85D, pos.getY() + 1.0D, pos.getZ() + 0.85D);
    }

    /**
     * A2 diagnóstico · instantánea ACOTADA (una línea) de una celda y sus seis vecinos ortogonales. Solo lectura:
     * no cambia ninguna semántica de {@link #classify}. Sirve para ver POR QUÉ una celda no llega a encolarse.
     */
    public static String snapshot(CollisionGetter level, Mob mob, BlockPos center, Policies policies) {
        StringBuilder sb = new StringBuilder();
        sb.append("celda=").append(center.toShortString())
                .append(" kind=").append(classify(level, mob, center, policies))
                .append(" fits=").append(fits(level, mob, center))
                .append(" soporte=").append(hasSupport(level, center))
                .append(" cabezaLibre1=").append(headClearOneUp(level, mob, center));
        int[][] vecinos = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {0, 0, 1}, {0, 0, -1}};
        sb.append(" | vecinos:");
        for (int[] v : vecinos) {
            BlockPos n = v.length == 2 ? center.offset(v[0], 0, v[1]) : center.offset(v[0], v[1], v[2]);
            sb.append(' ').append(n.toShortString())
                    .append('=').append(classify(level, mob, n, policies))
                    .append("/f").append(fits(level, mob, n) ? 1 : 0)
                    .append("/s").append(hasSupport(level, n) ? 1 : 0);
        }
        return sb.toString();
    }

    private static AABB upperBox(BlockPos pos) {
        return new AABB(pos.getX() + 0.15D, pos.getY() + 1.0D, pos.getZ() + 0.15D,
                pos.getX() + 0.85D, pos.getY() + 2.0D, pos.getZ() + 0.85D);
    }

    /** ¿Cabe el cuerpo (2 bloques) en esta celda sin colisionar? */
    public static boolean fits(CollisionGetter level, Mob mob, BlockPos pos) {
        return level.getBlockCollisions(mob, cellBox(pos)).iterator().hasNext() == false
                && level.getBlockCollisions(mob, upperBox(pos)).iterator().hasNext() == false;
    }

    /** ¿Hay colisión a la altura de los pies? */
    public static boolean blockedAtFeet(CollisionGetter level, Mob mob, BlockPos pos) {
        return level.getBlockCollisions(mob, cellBox(pos)).iterator().hasNext();
    }

    /** ¿Hay hueco para la cabeza si se sube un bloque? */
    public static boolean headClearOneUp(CollisionGetter level, Mob mob, BlockPos pos) {
        return level.getBlockCollisions(mob, upperBox(pos)).iterator().hasNext() == false;
    }

    /** ¿Hay un bloque con forma sólida justo debajo (suelo firme)? */
    public static boolean hasSupport(BlockGetter level, BlockPos pos) {
        return hasSolidFace(level, pos.below());
    }

    /**
     * A2 Stage 6E · <b>¿el bloque de esa celda ofrece una CARA SÓLIDA REAL?</b> No es aire y su forma de colisión no
     * es vacía. Es la comprobación geométrica que hace válida una colocación <b>adherida a una cara</b>, que es la
     * regla real de Minecraft, y es el ÚNICO sitio donde vive: así el ejecutor (que decide el candidato) y la
     * primitiva de colocación (que lo ejecuta) no pueden discrepar.
     * <p>
     * Motivo medido (`a2n-20261016-170218`): la primitiva exigía suelo firme JUSTO DEBAJO, así que en una cara
     * vertical la única celda colocable era la que pisa el suelo de la llanura, 5 bloques por debajo del borde
     * (&gt; caída segura) y el descenso no podía empezar.
     */
    public static boolean hasSolidFace(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && !state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * A2 Stage 4V · CAIDA REAL de una celda: cuantos bloques hay que bajar hasta encontrar suelo firme.
     * <p>
     * Motivo medido (`a2n-20261016-075956`): el planificador aceptaba `DROP` con coste positivo SIEMPRE, sin mirar
     * la altura. Por eso una caida de 6 bloques entraba en la ruta como transito normal y barato (`coste=1.8`) y
     * NUNCA pasaba por la rama de construccion, que exige `cost < 0`. La invariante de caida segura existe en
     * `Policies.maxSafeDrop()`, pero el planificador no la aplicaba. Esto la hace medible.
     */
    public static int caidaReal(BlockGetter level, BlockPos pos, int maximo) {
        for (int i = 1; i <= maximo; i++) {
            if (hasSupport(level, pos.below(i))) {
                return i - 1;
            }
        }
        return maximo + 1; // mas profundo que el maximo medido: se considera insegura
    }

    /** Profundidad de agua por debajo de la celda (0 si no hay agua). */
    public static int waterDepthBelow(BlockGetter level, BlockPos pos) {
        int depth = 0;
        for (int i = 1; i <= 8; i++) {
            if (level.getFluidState(pos.below(i)).isEmpty()) {
                break;
            }
            depth++;
        }
        return depth;
    }

    /**
     * Clasifica la celda para un mob concreto. El orden importa: primero lo intocable, luego lo que cabe, luego lo
     * que se puede salvar subiendo/saltando, luego el hueco (caída) y por último romper.
     */
    public static Kind classify(CollisionGetter level, Mob mob, BlockPos pos, Policies policies) {
        if (policies.isProtected(pos)) {
            return Kind.PROTECTED;
        }
        // ¿Cabe de pie?
        if (fits(level, mob, pos)) {
            if (hasSupport(level, pos)) {
                return Kind.PASSABLE;
            }
            // Sin suelo: caída. Se mide lo que hay debajo.
            int depth = 0;
            for (int i = 1; i <= policies.maxSafeDrop() + 2; i++) {
                BlockPos below = pos.below(i);
                if (hasSupport(level, below)) {
                    break;
                }
                depth++;
            }
            if (waterDepthBelow(level, pos) >= policies.waterDepthForSafeDrop()) {
                return Kind.DROP;
            }
            return depth <= policies.maxSafeDrop() ? Kind.DROP : Kind.BLOCKED;
        }
        // No cabe: ¿es un escalón de 1?
        if (blockedAtFeet(level, mob, pos) && headClearOneUp(level, mob, pos)
                && fits(level, mob, pos.above()) && hasSupport(level, pos.above())) {
            return Kind.STEP_UP;
        }
        // ¿Un salto de 2?
        if (fits(level, mob, pos.above(2)) && hasSupport(level, pos.above(2))) {
            return Kind.JUMP_UP;
        }
        // ¿Romper?
        if (policies.canBreak(pos)) {
            return Kind.BREAKABLE;
        }
        return Kind.BLOCKED;
    }
}
