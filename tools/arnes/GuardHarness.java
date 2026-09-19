package com.chipoodle.devilrpg.debug;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.goal.VillagerGuardGoal;
import com.chipoodle.devilrpg.survival.ObjectiveTargets;
import com.chipoodle.devilrpg.world.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Locale;
import java.util.Random;

/**
 * ARNES TEMPORAL DE DIAGNOSTICO (no se queda en el mod).
 * <p>
 * Arranca un servidor headless con la partida del jugador copiada en {@code run/world}, fuerza los chunks de la aldea
 * 2, mete un jugador de pega en la plaza (el latido del pueblo necesita jugador cerca) y deja correr el latido de
 * verdad ({@code VillageManager.manageNearby}: reparte oficios, alista a la guardia y le pone sus goals), volcando en
 * el log lo que hace cada guardia.
 */
@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.GAME)
public class GuardHarness {

    private static final BlockPos CENTRO = new BlockPos(1414, 120, 1414);
    private static final int INDICE = 2;
    /**
     * ¿Se siembra el almacén con <b>pociones de agua ya embotelladas</b>? Para medir el <b>VIAJE AL AGUA</b> del
     * clérigo tiene que estar en {@code false}: si el almacén ya tiene botellas de agua, las usa y <b>nunca</b> coge
     * las de cristal (ver {@code VillagerClericGoal.trabajar}, paso 4), así que el viaje a la orilla no se mide.
     * En {@code true} se mide la cadena de la poción sin el paseo (era como estaba antes de esta ronda).
     */
    private static final boolean SEMBRAR_AGUA_EMBOTELLADA = false;
    private static boolean listo = false;
    private static int ticks = 0;
    /** Cuantas veces se ha visto a un granjero SUBIDO a la valla de su bancal (el bug que se mide). */
    private static int subidasALaValla = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();
        FakePlayer pega;
        if (!listo) {
            listo = true;
            pega = preparar(level, event.getServer());
        } else {
            pega = FakePlayerFactory.getMinecraft(level);
        }
        ticks++;
        // A los 15 s (chunks ya cargados) se le deja al almacen lo que el pueblo NO puede fabricar, para medir la
        // cadena del CLERIGO: verruga del Nether, polvo de blaze y BOTELLAS DE CRISTAL (para que tenga que ir al agua
        // a llenarlas: ver SEMBRAR_AGUA_EMBOTELLADA); y el botin que ya barre el recolector (pepitas de oro,
        // zanahorias, ojos de arana) para la zanahoria dorada.
        if (ticks == 600) {
            com.chipoodle.devilrpg.world.VillageStorage.guardar(level, CENTRO,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHER_WART, 4));
            com.chipoodle.devilrpg.world.VillageStorage.guardar(level, CENTRO,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BLAZE_POWDER, 4));
            if (SEMBRAR_AGUA_EMBOTELLADA) {
                var agua = net.minecraft.world.item.alchemy.PotionContents.createItemStack(
                        net.minecraft.world.item.Items.POTION, net.minecraft.world.item.alchemy.Potions.WATER);
                agua.setCount(3);
                com.chipoodle.devilrpg.world.VillageStorage.guardar(level, CENTRO, agua);
            }
            com.chipoodle.devilrpg.world.VillageStorage.guardar(level, CENTRO,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_NUGGET, 16));
            com.chipoodle.devilrpg.world.VillageStorage.guardar(level, CENTRO,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CARROT, 2));
            com.chipoodle.devilrpg.world.VillageStorage.guardar(level, CENTRO,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE, 3));
            com.chipoodle.devilrpg.world.VillageStorage.guardar(level, CENTRO,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SPIDER_EYE, 2));
            // Se CONFIRMA lo que hay de verdad en el cofre (si el chunk no estaba listo, `guardar` no pone nada).
            DevilRpg.LOGGER.info("[Arnes] almacen sembrado (agua embotellada={}): verruga={} blaze={} agua={} cristal={}"
                            + " pepitas={} zanahorias={} ojos={}", SEMBRAR_AGUA_EMBOTELLADA,
                    cuenta(level, net.minecraft.world.item.Items.NETHER_WART),
                    cuenta(level, net.minecraft.world.item.Items.BLAZE_POWDER),
                    cuenta(level, net.minecraft.world.item.Items.POTION),
                    cuenta(level, net.minecraft.world.item.Items.GLASS_BOTTLE),
                    cuenta(level, net.minecraft.world.item.Items.GOLD_NUGGET),
                    cuenta(level, net.minecraft.world.item.Items.CARROT),
                    cuenta(level, net.minecraft.world.item.Items.SPIDER_EYE));
        }
        // Los bichos que YA venian en el guardado dentro del recinto BLOQUEAN el latido del pueblo
        // (`hayEnemigosDentro`): sin esto el reparto de oficios y la guardia ni se tocan. Se barren cada segundo.
        if (ticks % 20 == 0) {
            for (net.minecraft.world.entity.Mob m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                    new AABB(CENTRO).inflate(140))) {
                if (m instanceof net.minecraft.world.entity.monster.Monster) {
                    m.discard();
                }
            }
        }
        // El latido de la aldea, tal cual lo llama el tick del jugador (con el ancla del objetivo 2).
        if (pega != null) {
            pega.moveTo(CENTRO.getX() + 0.5D, CENTRO.getY(), CENTRO.getZ() + 0.5D);
            VillageManager.manageNearby(level, pega, ancla(), INDICE);
        }
        if (ticks % 20 == 0) {
            volcar(level);
        }
        // EL PORCHE DE LA TABERNA (migracion 63): se mide la columna `bx-1`, la que queda ENTRE el toldo (bx-2) y la
        // pared de la taberna (bx). A los 10 s el latido ya migro la aldea, asi que esto es "despues".
        if (ticks == 200) {
            volcarPorche(level, "DESPUES");
        }
    }

    private static FakePlayer preparar(ServerLevel level, net.minecraft.server.MinecraftServer server) {
        int cx = CENTRO.getX() >> 4;
        int cz = CENTRO.getZ() >> 4;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                level.setChunkForced(cx + dx, cz + dz, true);
            }
        }
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        // Sin bichos: la ronda se mide sola (el combate va antes que la ronda y los guardias se morian peleando).
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setDayTime(6000L);
        for (net.minecraft.world.entity.Mob m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                new AABB(CENTRO).inflate(160))) {
            if (m instanceof net.minecraft.world.entity.monster.Monster) {
                m.discard();
            }
        }
        FakePlayer pega = FakePlayerFactory.getMinecraft(level);
        pega.moveTo(CENTRO.getX() + 0.5D, CENTRO.getY(), CENTRO.getZ() + 0.5D);
        DevilRpg.LOGGER.info("[Arnes] ALDEA 2 centro={} aldeanos={} gameTime={} ancla={}", CENTRO,
                level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(96)).size(), level.getGameTime(),
                ancla());
        return pega;
    }

    /** El ancla del jugador que hace que el objetivo 2 caiga en el centro de la aldea (misma cuenta que el mod). */
    private static Vec3 ancla() {
        Random rnd = new Random(0x5DEECE66DL + INDICE);
        double distancia = 800 + INDICE * 600 + rnd.nextDouble() * 400;
        double rad = Math.toRadians(45.0D);
        return new Vec3(CENTRO.getX() - Math.cos(rad) * distancia, CENTRO.getY(),
                CENTRO.getZ() - Math.sin(rad) * distancia);
    }

    /** Cuántas cosas de ese tipo hay en el almacén (para confirmar el sembrado). */
    private static int cuenta(ServerLevel level, net.minecraft.world.item.Item item) {
        return com.chipoodle.devilrpg.world.VillageStorage.cuenta(level, CENTRO, s -> s.is(item));
    }

    // --- LA GRANJA: que bancal trabaja cada granjero y si se sube a la valla ----------------------------------
    // FARM_PLOTS y PLOT_WIDTH son privados en el generador: se copian aqui para la medida (el arnes es temporal).
    private static final int[][] PARCELAS = {{-30, 14}, {10, 4}, {-28, 34}};
    private static final int ANCHO_PARCELA = 9;

    /** El indice del bancal en el que esta ese aldeano (mirando el rectangulo de su valla), o -1 si esta fuera. */
    private static int bancalDe(ServerLevel level, Villager v) {
        int cota = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
        if (Math.abs(v.getY() - cota) > 4) {
            return -1;
        }
        for (int i = 0; i < PARCELAS.length; i++) {
            int x0 = CENTRO.getX() + PARCELAS[i][0];
            int z0 = CENTRO.getZ() + PARCELAS[i][1];
            if (v.getX() >= x0 - 1 && v.getX() <= x0 + ANCHO_PARCELA
                    && v.getZ() >= z0 - 1 && v.getZ() <= z0 + ANCHO_PARCELA) {
                return i;
            }
        }
        return -1;
    }

    /** ¿Esta SUBIDO a la valla del bancal? (de pie sobre una valla o una compuerta: el bloque de debajo es eso) */
    private static boolean subidoALaValla(ServerLevel level, Villager v) {
        var debajo = level.getBlockState(v.blockPosition().below());
        return debajo.is(net.minecraft.world.level.block.Blocks.OAK_FENCE)
                || debajo.is(net.minecraft.world.level.block.Blocks.OAK_FENCE_GATE);
    }

    /** El compostero del bancal `i` de la aldea medida (misma cuenta que el generador: ver la migracion 64). */
    private static BlockPos composteroDelBancal(ServerLevel level, int i) {
        int cota = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
        return new BlockPos(CENTRO.getX() + PARCELAS[i][0] - 3, cota, CENTRO.getZ() + PARCELAS[i][1]);
    }

    /**
     * El <b>porche de la taberna</b>: la columna {@code bx-1}, que es la que queda <b>entre</b> el toldo
     * ({@code bx-2}) y la <b>pared</b> de la taberna ({@code bx}). Si esas celdas estan vacias, el techito no
     * conecta con el edificio (el bug que reporto el jugador). Se mira, por celda: el escalon del alero pegado al
     * muro, su tablon de soffito debajo y que la pared de al lado sea solida.
     */
    private static void volcarPorche(ServerLevel level, String etiqueta) {
        if (!com.chipoodle.devilrpg.world.VillageGenerator.tabernaConstruida(level, CENTRO)) {
            DevilRpg.LOGGER.info("[Arnes] PORCHE {}: no hay taberna construida en {}", etiqueta, CENTRO);
            return;
        }
        int nivel = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
        BlockPos base = com.chipoodle.devilrpg.world.VillageGenerator.baseDeLaTaberna(CENTRO);
        int bx = base.getX();
        int bz = base.getZ();
        int pz = 7;          // TABERNA_PUERTA (privada en el generador: se copia aqui para la medida)
        int yToldo = nivel + 5 - 2;    // TABERNA_PISO2 = 5
        int yDentro = nivel + 5 - 1;
        int pegadas = 0;
        for (int dz = pz - 3; dz <= pz + 3; dz++) {
            var escalon = level.getBlockState(new BlockPos(bx - 1, yDentro, bz + dz));
            var tablon = level.getBlockState(new BlockPos(bx - 1, yToldo, bz + dz));
            var pared = level.getBlockState(new BlockPos(bx, yDentro, bz + dz));
            boolean pegado = escalon.getBlock() instanceof net.minecraft.world.level.block.StairBlock
                    && tablon.is(net.minecraft.world.level.block.Blocks.DARK_OAK_PLANKS)
                    && !pared.isAir();
            if (pegado) {
                pegadas++;
            }
            DevilRpg.LOGGER.info("[Arnes] PORCHE {} dz{}: dx-1 escalon={} tablon={} pared={} -> {}",
                    etiqueta, dz, escalon.getBlock(), tablon.getBlock(), pared.getBlock(),
                    pegado ? "PEGADO A LA PARED" : "HUECO");
        }
        DevilRpg.LOGGER.info("[Arnes] PORCHE {}: celdas del toldo PEGADAS a la pared: {}/7 (base {}, cota {})",
                etiqueta, pegadas, base, nivel);
    }

    private static void volcar(ServerLevel level) {
        java.util.Map<String, Integer> censo = new java.util.TreeMap<>();
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(96))) {
            censo.merge(v.isBaby() ? "CRIA" : str(v.getVillagerData().getProfession()), 1, Integer::sum);
            boolean esClerigo = !v.isBaby()
                    && v.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.CLERIC;
            // LA GRANJA (lo que se mide en esta ronda): se sigue a los GRANJEROS, con su bancal y si van subidos a la
            // valla (el bug: trepaban por el compostero pegado a ella).
            boolean esGranjero = !v.isBaby()
                    && v.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.FARMER;
            if (esGranjero) {
                int bancal = bancalDe(level, v);
                boolean valla = subidoALaValla(level, v);
                if (valla) {
                    subidasALaValla++;
                }
                WalkTarget wtg = v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
                var puesto = v.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
                DevilRpg.LOGGER.info("[Arnes] t={} {} GRANJERO bancal={} valla={} puesto={} pos=({},{},{}) destino={}"
                                + " etiqueta={}",
                        level.getGameTime(), v.getUUID().toString().substring(0, 8), bancal, valla ? "SI" : "no",
                        puesto == null ? "SIN PUESTO" : puesto.pos().toShortString(),
                        fmt(v.getX()), fmt(v.getY()), fmt(v.getZ()),
                        wtg == null ? "SIN DESTINO" : wtg.getTarget().currentBlockPosition().toShortString(),
                        v.getCustomName() == null ? "-" : v.getCustomName().getString().replace("\n", " | "));
                continue;
            }
            if (!v.isBaby() && !VillagerGuardGoal.esGuardia(v) && !esClerigo) {
                continue; // de los adultos solo se sigue a la guardia, al CLERIGO y a los GRANJEROS
            }
            WalkTarget wt = v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            DevilRpg.LOGGER.info("[Arnes] t={} {} pos=({},{},{}) destino={} oficio={} trabajo={} puesto={} etiqueta={}",
                    level.getGameTime(), v.getUUID().toString().substring(0, 8), fmt(v.getX()), fmt(v.getY()),
                    fmt(v.getZ()),
                    wt == null ? "SIN DESTINO" : wt.getTarget().currentBlockPosition().toShortString(),
                    str(v.getVillagerData().getProfession()), v.getBrain().isActive(Activity.WORK),
                    v.getPersistentData().getInt(VillageManager.GUARD_INDEX_TAG),
                    v.getCustomName() == null ? "-" : v.getCustomName().getString().replace("\n", " | "));
        }
        // EL RESUMEN DE LA GRANJA: cuantos granjeros hay en cada bancal (el reparto) y cuantas veces se les ha visto
        // subidos a la valla desde que arranco el arnes.
        StringBuilder bancales = new StringBuilder();
        for (int i = 0; i < PARCELAS.length; i++) {
            int enEl = 0;
            for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(96))) {
                if (!v.isBaby() && v.getVillagerData().getProfession()
                        == net.minecraft.world.entity.npc.VillagerProfession.FARMER && bancalDe(level, v) == i) {
                    enEl++;
                }
            }
            BlockPos comp = composteroDelBancal(level, i);
            boolean hayCompostero = level.getBlockState(comp).is(net.minecraft.world.level.block.Blocks.COMPOSTER);
            bancales.append(" bancal").append(i).append("=granjeros:").append(enEl)
                    .append("/compostero:").append(hayCompostero ? "SI" : "NO");
        }
        DevilRpg.LOGGER.info("[Arnes] t={} GRANJA {} · subidas a la valla (acumulado): {}",
                level.getGameTime(), bancales, subidasALaValla);
        DevilRpg.LOGGER.info("[Arnes] t={} CENSO {}", level.getGameTime(), censo);
    }

    private static String str(Object o) {
        return String.valueOf(o).replace("minecraft:", "");
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.2f", d);
    }
}
