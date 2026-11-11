package com.chipoodle.devilrpg.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.AABB;

/**
 * Coordinador compartido de agricultura, aislado por aldea.
 *
 * <p>Nivel 1 de la arquitectura: reparte parcelas entre los granjeros y evita
 * dos dueños de la misma parcela. No ejecuta trabajo, no mueve aldeanos y no
 * sustituye al goal individual. La asignación se guarda en los datos del
 * aldeano ({@value #PLOT_TAG}); la identidad de la parcela es su índice estable
 * de {@link VillageGenerator}, no una coordenada suelta.</p>
 */
public final class VillageFarmCoordinator {

    private static final String PLOT_TAG = "DevilRpgFarmPlot";
    private static final double RADIO = 160.0D;

    private VillageFarmCoordinator() {
    }

    /**
     * Devuelve la parcela asignada a este granjero, creándola/reclamándola si
     * hace falta. {@code -1} si la aldea no tiene parcelas.
     */
    public static int parcelaAsignada(ServerLevel level, BlockPos center, Villager villager) {
        int total = VillageGenerator.parcelasDeGranja();
        if (total <= 0) {
            return -1;
        }
        VillageSavedData saved = VillageSavedData.get(level);
        int guardada = villager.getPersistentData().getInt(PLOT_TAG);
        if (villager.getPersistentData().contains(PLOT_TAG)
                && esValida(guardada, total)
                && sinConflicto(level, center, villager, guardada)
                && saved.claimFarmPlot(center, guardada, villager.getUUID())) {
            return guardada;
        }
        int delPuesto = parcelaDelPuesto(level, center, villager);
        if (delPuesto >= 0 && sinConflicto(level, center, villager, delPuesto)
                && saved.claimFarmPlot(center, delPuesto, villager.getUUID())) {
            villager.getPersistentData().putInt(PLOT_TAG, delPuesto);
            return delPuesto;
        }
        for (int i = 0; i < total; i++) {
            if (sinConflicto(level, center, villager, i)
                    && saved.claimFarmPlot(center, i, villager.getUUID())) {
                villager.getPersistentData().putInt(PLOT_TAG, i);
                return i;
            }
        }
        // Sin parcela libre: NO se inventa un duplicado ni se roba la de otro.
        return -1;
    }

    /** Libera la asignación confirmada (muerte/cambio de oficio/parcela inválida). */
    public static void liberar(ServerLevel level, BlockPos center, Villager villager) {
        if (villager.getPersistentData().contains(PLOT_TAG)) {
            int plot = villager.getPersistentData().getInt(PLOT_TAG);
            VillageSavedData.get(level).releaseFarmPlot(center, plot, villager.getUUID());
            villager.getPersistentData().remove(PLOT_TAG);
        }
    }

    private static boolean esValida(int plot, int total) {
        return plot >= 0 && plot < total;
    }

    private static boolean sinConflicto(ServerLevel level, BlockPos center, Villager self, int plot) {
        java.util.UUID autoritativo = VillageSavedData.get(level).getFarmOwner(center, plot);
        if (autoritativo != null && !autoritativo.equals(self.getUUID())) {
            return false; // aunque el dueño esté descargado
        }
        for (Villager other : level.getEntitiesOfClass(Villager.class,
                new AABB(center).inflate(RADIO))) {
            if (other == self || other.isBaby()
                    || other.getVillagerData().getProfession() != VillagerProfession.FARMER) {
                continue;
            }
            if (other.getPersistentData().contains(PLOT_TAG)
                    && other.getPersistentData().getInt(PLOT_TAG) == plot) {
                return false;
            }
        }
        return true;
    }

    private static int parcelaDelPuesto(ServerLevel level, BlockPos center, Villager villager) {
        var puesto = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (puesto.isEmpty()) {
            return -1;
        }
        BlockPos p = puesto.get().pos();
        int cota = VillageGenerator.cotaDeLaPlaza(level, center);
        for (int i = 0; i < VillageGenerator.parcelasDeGranja(); i++) {
            BlockPos comp = VillageGenerator.composteroDeLaParcela(center, i, cota);
            if (p.getZ() == comp.getZ() && Math.abs(p.getX() - comp.getX()) <= 1
                    && Math.abs(p.getY() - cota) <= 2) {
                return i;
            }
        }
        return -1;
    }
}
