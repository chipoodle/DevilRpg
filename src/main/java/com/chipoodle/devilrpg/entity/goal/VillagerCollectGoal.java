package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.world.VillageGenerator;
import com.chipoodle.devilrpg.world.VillageManager;
import com.chipoodle.devilrpg.world.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Goal del <b>constructor recolector</b>: además de reparar la aldea, recoge del suelo las cosas del pueblo
 * (semillas, trigo, abono, troncos, tablones, cuero, lana, metales...) y las guarda en el <b>cofre del almacén</b>.
 * <p>
 * Solo recoge una <b>lista blanca</b> de cosas del pueblo: lo que tú tires (armas, comida tuya, minerales raros) se
 * queda en el suelo para que lo puedas recuperar.
 */
public class VillagerCollectGoal extends Goal {

    /** Distancia a la que recoge el objeto del suelo. */
    private static final double REACH = 2.5D;
    /** Items que lleva encima antes de ir a descargar al almacén. */
    private static final int LLEVAR_MAX = 8;
    private static final int REST_TICKS = 20;
    private static final int IDLE_REST_TICKS = 100;
    private static final int STUCK_LIMIT = 120;
    /**
     * Radio alrededor del centro donde recoge (no se va por el mundo a por cosas). Derivado del radio de la valla:
     * con el recinto agrandado (36) un radio fijo de 40 dejaba los objetos del borde del pueblo sin recoger.
     */
    private static final double RADIO = VillageGenerator.FENCE_RADIUS + 6.0D;
    /** Solo se recogen objetos que lleven un rato en el suelo (5 s): así no le quita a nadie lo que acaba de soltar. */
    private static final int EDAD_MINIMA = 100;

    private final Villager villager;
    private final BlockPos center;
    private final int objectiveIndex;
    @Nullable
    private ItemEntity objetivo;
    @Nullable
    private BlockPos destino;
    private int restTicks;
    private int stuckTicks;

    public VillagerCollectGoal(Villager villager, BlockPos center, int objectiveIndex) {
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
        // El RECOLECTOR es el aldeano SIN OFICIO (holgazán): el goal solo se le engancha a él. OJO: antes se exigía
        // la marca de OBRERO, que el recolector NUNCA tiene (los obreros se eligen entre los demás oficios), así que
        // esta comprobación dejaba al recolector sin hacer nada NUNCA: los objetos del pueblo se quedaban tirados por
        // el suelo (visto en el guardado: 155 objetos en una aldea).
        if (villager.getVillagerData().getProfession() != net.minecraft.world.entity.npc.VillagerProfession.NITWIT) {
            return false;
        }
        if (VillageManager.isVillageUnderAttack(level, objectiveIndex)) {
            return false;
        }
        // De noche, a dormir: ni recoge ni se queda andando por el pueblo.
        if (VillageManager.estaDescansando(villager)) {
            return false;
        }
        // Distancia HORIZONTAL al centro (la Y del centro no cuenta: la aldea es un recinto en el plano XZ).
        if (distanciaHorizontalAlCentro() > RADIO * RADIO) {
            return false;
        }
        // Con las manos llenas, al almacén (a su punto de apoyo: el cofre es sólido y no se navega hacia él).
        if (cuantosLleva() >= LLEVAR_MAX) {
            destino = VillageStorage.puntoDeApoyo(level, center);
            objetivo = null;
            return true;
        }
        objetivo = buscarObjeto(level);
        if (objetivo == null) {
            restTicks = IDLE_REST_TICKS;
            return false;
        }
        destino = null;
        return true;
    }

    @Override
    public void start() {
        stuckTicks = 0;
        ir();
    }

    @Override
    public boolean canContinueToUse() {
        if (villager.isBaby() || stuckTicks >= STUCK_LIMIT || VillageManager.estaDescansando(villager)) {
            return false;
        }
        if (objetivo != null) {
            return objetivo.isAlive();
        }
        return destino != null;
    }

    @Override
    public void tick() {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }
        if (objetivo != null) {
            if (!objetivo.isAlive()) {
                objetivo = null;
                return;
            }
            BlockPos p = objetivo.blockPosition();
            villager.getLookControl().setLookAt(objetivo);
            if (villager.distanceToSqr(objetivo) > REACH * REACH) {
                // Se le manda POR EL CEREBRO, en cada tick: si se navega a mano, el cerebro del aldeano lo manda a
                // otra parte y se va sin recogerlo.
                VillageManager.caminarHacia(villager, p, 0.6F);
                stuckTicks++;
                return;
            }
            VillageManager.parar(villager);
            villager.swing(InteractionHand.MAIN_HAND);
            VillageManager.ponerActividad(villager, "Recogiendo");
            ItemStack stack = objetivo.getItem().copy();
            int antes = stack.getCount();
            ItemStack resto = guardarEnInventario(stack);
            int cogidos = antes - resto.getCount();
            if (cogidos <= 0) {
                objetivo = null; // no le cabe: lo deja en el suelo
                return;
            }
            objetivo.getItem().shrink(cogidos);
            level.playSound(null, p, net.minecraft.sounds.SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.4F, 1.0F);
            if (objetivo.getItem().isEmpty()) {
                objetivo.discard();
            }
            objetivo = null;
            restTicks = REST_TICKS;
            return;
        }
        if (destino != null) {
            villager.getLookControl().setLookAt(destino.getX() + 0.5D, destino.getY() + 0.5D, destino.getZ() + 0.5D);
            if (villager.distanceToSqr(destino.getX() + 0.5D, destino.getY() + 0.5D, destino.getZ() + 0.5D)
                    > VillageStorage.ALCANCE_ALMACEN * VillageStorage.ALCANCE_ALMACEN) {
                VillageManager.caminarHacia(villager, destino, 0.6F);
                stuckTicks++;
                return;
            }
            VillageManager.parar(villager);
            VillageManager.ponerActividad(villager, "Guardando en el almacen");
            descargar(level);
            destino = null;
            restTicks = REST_TICKS;
        }
    }

    @Override
    public void stop() {
        objetivo = null;
        destino = null;
        restTicks = REST_TICKS;
        VillageManager.parar(villager);
    }

    /**
     * Distancia <b>horizontal</b> (en el plano XZ) al centro de la aldea, al cuadrado. La Y no se mira a propósito:
     * el centro puede traer cualquier Y (la del spawn del jugador) y mirarla dejaba al aldeano fuera de su propio
     * pueblo.
     */
    private double distanciaHorizontalAlCentro() {
        double dx = villager.getX() - center.getX();
        double dz = villager.getZ() - center.getZ();
        return dx * dx + dz * dz;
    }

    private void ir() {
        if (objetivo != null) {
            VillageManager.caminarHacia(villager, objetivo.blockPosition(), 0.6F);
            return;
        }
        if (destino != null) {
            VillageManager.caminarHacia(villager, destino, 0.6F);
        }
    }

    /** Deja en el almacén todo lo que lleve de la lista blanca. */
    private void descargar(ServerLevel level) {
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.isEmpty() || !esDelPueblo(s)) {
                continue;
            }
            ItemStack resto = VillageStorage.guardar(level, center, s.copy());
            villager.getInventory().setItem(i, resto);
        }
    }

    @Nullable
    private ItemEntity buscarObjeto(ServerLevel level) {
        ItemEntity mejor = null;
        double mejorDist = 24.0D * 24.0D;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(center).inflate(RADIO))) {
            if (!item.isAlive() || item.getItem().isEmpty()) {
                continue;
            }
            if (item.tickCount < EDAD_MINIMA) {
                continue; // recién soltado: se le deja un margen a quien lo soltó
            }
            if (!esDelPueblo(item.getItem())) {
                continue;
            }
            double d = item.distanceToSqr(villager);
            if (d < mejorDist) {
                mejorDist = d;
                mejor = item;
            }
        }
        return mejor;
    }

    /** Lista blanca: cosas del pueblo. Lo demás (tu equipo, tus minerales) no se toca. */
    public static boolean esDelPueblo(ItemStack s) {
        return s.is(Items.WHEAT) || s.is(Items.WHEAT_SEEDS) || s.is(Items.BEETROOT_SEEDS) || s.is(Items.BONE_MEAL)
                || s.is(Items.STICK) || s.is(Items.STRING) || s.is(Items.LEATHER) || s.is(Items.FEATHER)
                || s.is(Items.COAL) || s.is(Items.CHARCOAL) || s.is(Items.IRON_INGOT) || s.is(Items.COPPER_INGOT)
                || s.is(Items.GOLD_INGOT) || s.is(Items.CARROT) || s.is(Items.POTATO)
                || s.is(Blocks.OAK_LOG.asItem()) || s.is(Blocks.OAK_PLANKS.asItem())
                || s.is(Blocks.OAK_SAPLING.asItem()) || s.getDescriptionId().contains("sapling")
                || s.getDescriptionId().contains("_log") || s.getDescriptionId().contains("_wool")
                || s.getDescriptionId().contains("_seeds");
    }

    private int cuantosLleva() {
        int n = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (!s.isEmpty() && esDelPueblo(s)) {
                n += s.getCount();
            }
        }
        return n;
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
