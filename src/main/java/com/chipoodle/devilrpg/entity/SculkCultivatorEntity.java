package com.chipoodle.devilrpg.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.Comparator;
import java.util.List;

/**
 * <b>Cultivador del sculk</b>: un no-muerto que aparece SOLO en las guaridas (mismas reglas de escalado que
 * el {@link AggressiveZombieEntity}, del que hereda) y se dedica a <b>cultivar la infección</b>:
 * <ul>
 *   <li><b>Cría una granja macabra</b>: alimenta a los animales de la guarida para que se apareen.</li>
 *   <li><b>Sacrifica</b> parte del ganado sobre el sculk cuando ya hay bastantes, alimentando al
 *       catalizador (que expande la infección).</li>
 *   <li><b>Extrae sculk</b> del terreno ya infectado y lo condensa en <b>catalizadores nuevos</b>, que
 *       coloca en los bordes del sculk para que la infección siga creciendo.</li>
 * </ul>
 * Si lo dejas vivo, la guarida se extiende sola.
 */
public class SculkCultivatorEntity extends AggressiveZombieEntity {

    /** Radio en el que el cultivador trabaja (respecto al núcleo de su guarida). */
    private static final int WORK_RADIUS = 24;
    /** Bloques de sculk necesarios para "condensar" un catalizador nuevo. */
    private static final int SCULK_PER_CATALYST = 24;
    /** Máximo de catalizadores que mantiene por guarida. */
    private static final int MAX_CATALYSTS = 6;
    /** Animales en el corral a partir de los cuales empieza a sacrificar. */
    private static final int SACRIFICE_THRESHOLD = 5;

    public SculkCultivatorEntity(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    /** Mismas reglas (perfil y escalado) que el zombie agresivo. */
    public static AttributeSupplier.Builder setAttributes() {
        return AggressiveZombieEntity.setAttributes();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals(); // hereda romper bloques, manada, patrulla, etc.
        // Tareas de cultivador (prioridad alta: es su "trabajo").
        this.goalSelector.addGoal(2, new SacrificeGoal(this));
        this.goalSelector.addGoal(3, new BreedAnimalsGoal(this));
        this.goalSelector.addGoal(4, new PlantCatalystGoal(this));
    }

    private BlockPos workCenter() {
        BlockPos home = getHomePos();
        return home != null ? home : blockPosition();
    }

    private int workRadius() {
        int r = getHomeRadius();
        return r > 0 ? r : WORK_RADIUS;
    }

    /** Cuenta bloques de la infección (sculk, venas y catalizadores) alrededor del centro de trabajo. */
    private int countSculk() {
        BlockPos c = workCenter();
        int r = workRadius();
        int count = 0;
        for (int x = -r; x <= r; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -r; z <= r; z++) {
                    var state = level().getBlockState(c.offset(x, y, z));
                    if (state.is(Blocks.SCULK) || state.is(Blocks.SCULK_VEIN) || state.is(Blocks.SCULK_CATALYST)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private int countCatalysts() {
        BlockPos c = workCenter();
        int r = workRadius();
        int count = 0;
        for (int x = -r; x <= r; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -r; z <= r; z++) {
                    if (level().getBlockState(c.offset(x, y, z)).is(Blocks.SCULK_CATALYST)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** Animales vivos dentro del radio de trabajo (el "ganado" de la granja). */
    private List<Animal> nearbyAnimals() {
        BlockPos c = workCenter();
        int r = workRadius();
        return level().getEntitiesOfClass(Animal.class,
                new net.minecraft.world.phys.AABB(c).inflate(r),
                a -> a.isAlive() && a.distanceToSqr(c.getX() + 0.5D, c.getY() + 0.5D, c.getZ() + 0.5D) <= (double) r * r);
    }

    /**
     * Goal: cuando el ganado crece lo suficiente, sacrifica un animal sobre el sculk (alimenta al
     * catalizador, que expande la infección).
     */
    static class SacrificeGoal extends Goal {
        private final SculkCultivatorEntity cult;
        private Animal victim = null;

        SacrificeGoal(SculkCultivatorEntity cult) {
            this.cult = cult;
        }

        @Override
        public boolean canUse() {
            if (cult.getTarget() != null) return false; // si está peleando, eso primero
            List<Animal> animals = cult.nearbyAnimals();
            if (animals.size() < SACRIFICE_THRESHOLD) return false;
            victim = animals.stream()
                    .min(Comparator.comparingDouble(a -> a.distanceToSqr(cult)))
                    .orElse(null);
            return victim != null;
        }

        @Override
        public boolean canContinueToUse() {
            return victim != null && victim.isAlive() && cult.getTarget() == null;
        }

        @Override
        public void tick() {
            if (victim == null) return;
            double dist = cult.distanceToSqr(victim);
            if (dist > 4.0D) {
                cult.getNavigation().moveTo(victim, 1.0D);
            } else {
                // Sacrificio: muere sobre el sculk y alimenta al catalizador.
                victim.hurt(cult.damageSources().mobAttack(cult), Float.MAX_VALUE);
                victim = null;
            }
        }

        @Override
        public void stop() {
            victim = null;
        }
    }

    /**
     * Goal: alimentar al ganado para que se aparee (cría macabra). Pone en "modo amor" a los animales
     * cercanos para que se reproduzcan entre ellos.
     */
    static class BreedAnimalsGoal extends Goal {
        private final SculkCultivatorEntity cult;
        private Animal target = null;
        private int cooldown = 0;

        BreedAnimalsGoal(SculkCultivatorEntity cult) {
            this.cult = cult;
        }

        @Override
        public boolean canUse() {
            if (cult.getTarget() != null || --cooldown > 0) return false;
            List<Animal> animals = cult.nearbyAnimals();
            // Solo cría si hay poco ganado (deja de criar cuando ya hay muchos, para no saturar).
            if (animals.size() >= SACRIFICE_THRESHOLD + 4) return false;
            target = animals.stream()
                    .filter(a -> !a.isInLove() && a.canFallInLove())
                    .min(Comparator.comparingDouble(a -> a.distanceToSqr(cult)))
                    .orElse(null);
            return target != null;
        }

        @Override
        public boolean canContinueToUse() {
            return target != null && target.isAlive();
        }

        @Override
        public void tick() {
            if (target == null) return;
            if (cult.distanceToSqr(target) > 4.0D) {
                cult.getNavigation().moveTo(target, 1.0D);
            } else {
                target.setInLove(null); // alimentado: entrará en celo y se apareará
                cooldown = 60;
                target = null;
            }
        }

        @Override
        public void stop() {
            target = null;
        }
    }

    /**
     * Goal: "extraer" sculk del terreno ya infectado y condensarlo en un catalizador nuevo, colocándolo en
     * el borde de la infección (un lugar estratégico) para que siga creciendo.
     */
    static class PlantCatalystGoal extends Goal {
        private final SculkCultivatorEntity cult;
        private BlockPos spot = null;
        private int cooldown = 0;

        PlantCatalystGoal(SculkCultivatorEntity cult) {
            this.cult = cult;
        }

        @Override
        public boolean canUse() {
            if (cult.getTarget() != null || --cooldown > 0) return false;
            // Suficiente infección acumulada y hueco para más catalizadores.
            if (cult.countSculk() < SCULK_PER_CATALYST * 2) return false;
            if (cult.countCatalysts() >= MAX_CATALYSTS) return false;
            spot = findEdgeSpot();
            return spot != null;
        }

        @Override
        public boolean canContinueToUse() {
            return spot != null && cult.getTarget() == null;
        }

        @Override
        public void tick() {
            if (spot == null) return;
            if (cult.distanceToSqr(spot.getX() + 0.5D, spot.getY() + 0.5D, spot.getZ() + 0.5D) > 4.0D) {
                cult.getNavigation().moveTo(spot.getX(), spot.getY(), spot.getZ(), 1.0D);
                return;
            }
            // "Extrae" sculk cercano (lo consume) y coloca el catalizador nuevo.
            consumeNearbySculk(spot);
            cult.level().setBlock(spot, Blocks.SCULK_CATALYST.defaultBlockState(), 3);
            cooldown = 200;
            spot = null;
        }

        @Override
        public void stop() {
            spot = null;
        }

        /** Busca un borde de la infección: suelo sólido con aire encima, junto a sculk y sin catalizador. */
        private BlockPos findEdgeSpot() {
            BlockPos c = cult.workCenter();
            int r = cult.workRadius();
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos ground = new BlockPos(c.getX() + x, c.getY() - 1, c.getZ() + z);
                    BlockPos above = ground.above();
                    if (!cult.level().getBlockState(above).isAir()) continue;
                    if (!cult.level().getBlockState(ground).isSolid()) continue;
                    if (cult.level().getBlockState(ground).is(Blocks.SCULK_CATALYST)) continue;
                    // Debe estar junto a sculk (en el borde de la infección).
                    if (touchesSculk(above)) {
                        return above;
                    }
                }
            }
            return null;
        }

        private boolean touchesSculk(BlockPos pos) {
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                if (cult.level().getBlockState(pos.relative(d)).is(Blocks.SCULK)) {
                    return true;
                }
            }
            return false;
        }

        /** Consume (convierte en aire) algunos bloques de sculk alrededor, como "material extraído". */
        private void consumeNearbySculk(BlockPos center) {
            int consumed = 0;
            for (int x = -3; x <= 3 && consumed < 6; x++) {
                for (int z = -3; z <= 3 && consumed < 6; z++) {
                    BlockPos p = center.offset(x, -1, z);
                    if (cult.level().getBlockState(p).is(Blocks.SCULK)) {
                        cult.level().setBlock(p, Blocks.COARSE_DIRT.defaultBlockState(), 3);
                        consumed++;
                    }
                }
            }
        }
    }
}
