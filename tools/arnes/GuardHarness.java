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
    /**
     * ¿Se mide el <b>SUEÑO Y LAS CAMAS</b> (noche fija) o el <b>TRABAJO Y EL COMBUSTIBLE</b> (día fijo)? En noche, el
     * arnés pasa el VIGILANTE DE CAMAS cada segundo y se salta las siembras de trabajo (que ensucian el log y mueven
     * al pueblo de sitio). Ver `volcarCamas`.
     */
    private static final boolean MEDIR_NOCHE = true;
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
        if (!MEDIR_NOCHE && ticks == 600) {
            // --- TERCERA MEDIDA: LA REMESA INICIAL DE MADERA ---------------------------------------------------
            // Se VACIA el almacen entero (como el de una aldea recien fundada, que nace sin nada dentro): en la
            // siguiente pasada del latido el pueblo tiene que meter su remesa inicial de 128 troncos, UNA vez.
            var caja = com.chipoodle.devilrpg.world.VillageStorage.almacen(level, CENTRO);
            int sacados = 0;
            if (caja != null) {
                for (int i = 0; i < caja.getContainerSize(); i++) {
                    if (!caja.getItem(i).isEmpty()) {
                        sacados++;
                    }
                    caja.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                }
                caja.setChanged();
            }
            DevilRpg.LOGGER.info("[Arnes] REMESA: almacen vaciado ({} pila(s) fuera, {} troncos antes): en la"
                    + " siguiente pasada del latido tiene que entrar la remesa inicial",
                    sacados, com.chipoodle.devilrpg.world.VillageStorage.cuentaLena(level, CENTRO));
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
        // SIN MUERTES POR VEJEZ EN LA MEDIDA: el mod le pone a cada aldeano su fecha de nacimiento
        // (`DevilRpgVillagerBorn`) y a los 3 dias de juego (una hora de servidor) muere de viejo. En una corrida
        // larga eso repuebla la aldea a mitad de la medida (UUID nuevos y aldeanos sin cama recien llegados), asi
        // que aqui se les REJUVENECE: la medida del sueno no se ensucia con el relevo generacional.
        if (ticks % 200 == 0) {
            for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(140))) {
                v.getPersistentData().putLong("DevilRpgVillagerBorn", level.getGameTime());
            }
        }
        // El latido de la aldea, tal cual lo llama el tick del jugador (con el ancla del objetivo 2).
        if (pega != null) {
            pega.moveTo(CENTRO.getX() + 0.5D, CENTRO.getY(), CENTRO.getZ() + 0.5D);
            VillageManager.manageNearby(level, pega, ancla(), INDICE);
        }
        if (MEDIR_NOCHE) {
            // EL VIGILANTE DE CAMAS, cada segundo (la transicion se canta sola cuando el HOME desaparece o se reclama).
            if (ticks % 20 == 0) {
                volcarCamas(level);
            }
            // Y A RESOLUCION DE TICK: al perderse la cama se imprime como estaba EN EL TICK ANTERIOR, que es lo que
            // dice quien la borro (ver `vigilarCamasCadaTick`).
            vigilarCamasCadaTick(level);
            // Y EL QUE ESTA DENTRO DE UN BANCAL: su destino, sus goals activos y el estado de las compuertas.
            if (ticks % 40 == 0) {
                vigilarGranjerosEnElBancal(level);
            }
            // Y EL QUE NO TIENE CAMA: sondeo de rutas a las celdas que importan (donde se corta el camino).
            if (ticks % 100 == 0) {
                sondarRutasDelSinCama(level);
            }
        } else {
            if (ticks == 400 || ticks == 1400) {
                volcarObjetos(level);
                volcarCamas(level);
            }
            if (ticks % 20 == 0) {
                volcar(level);
            }
            if (ticks % 40 == 0) {
                volcarCombustible(level);
            }
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
        // LA HORA SE FIJA SEGUN LO QUE SE MIDA: NOCHE (18000 = medianoche) para el sueño y las camas, DIA (6000 =
        // mediodia) para el trabajo y el combustible (la cocina y la fragua son faenas de dia).
        level.setDayTime(MEDIR_NOCHE ? 18000L : 6000L);
        // Sin bichos: la ronda se mide sola (el combate va antes que la ronda y los guardias se morian peleando).
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
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

    /**
     * EL COMBUSTIBLE (lo que se mide en esta ronda): cuanta <b>lena</b> queda en el almacen (y si se queda clavada en
     * la reserva), cuanta <b>carne cruda</b> queda en la despensa, y que hacen el <b>cocinero</b> (ahumador) y los
     * <b>herreros</b> (fundicion): su posicion, cuanta lena llevan encima, su destino y su etiqueta.
     */
    private static void volcarCombustible(ServerLevel level) {
        int lena = com.chipoodle.devilrpg.world.VillageStorage.cuentaLena(level, CENTRO);
        var despensa = com.chipoodle.devilrpg.world.VillagePantry.despensa(level, CENTRO);
        int crudo = com.chipoodle.devilrpg.world.VillagePantry.contar(despensa,
                com.chipoodle.devilrpg.world.VillagePantry::sePuedeCocinar);
        StringBuilder linea = new StringBuilder();
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(64))) {
            if (v.isBaby()) {
                continue;
            }
            var prof = v.getVillagerData().getProfession();
            boolean cocinero = prof == net.minecraft.world.entity.npc.VillagerProfession.BUTCHER;
            boolean herrero = prof == net.minecraft.world.entity.npc.VillagerProfession.WEAPONSMITH
                    || prof == net.minecraft.world.entity.npc.VillagerProfession.TOOLSMITH;
            if (!cocinero && !herrero) {
                continue;
            }
            int encima = 0;
            for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
                var s = v.getInventory().getItem(i);
                if (com.chipoodle.devilrpg.world.VillageStorage.esLena(s)) {
                    encima += s.getCount();
                }
            }
            var wt = v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            linea.append("\n    ").append(cocinero ? "COCINERO" : "HERRERO").append(' ')
                    .append(v.getUUID().toString().substring(0, 8))
                    .append(" pos=").append(v.blockPosition().toShortString())
                    .append(" lenaEncima=").append(encima)
                    .append(" destino=").append(wt == null ? "SIN DESTINO"
                            : wt.getTarget().currentBlockPosition().toShortString())
                    .append(" etiqueta=").append(v.getCustomName() == null ? "-"
                            : v.getCustomName().getString().replace("\n", " | "));
        }
        DevilRpg.LOGGER.info("[Arnes] t={} COMBUSTIBLE: lenaEnAlmacen={} (reserva={}) carneCrudaEnDespensa={}{}",
                level.getGameTime(), lena, com.chipoodle.devilrpg.world.VillageStorage.RESERVA_LENA, crudo, linea);
    }

    /**
     * La ruta <b>con detalle</b>: con `accuracy` 1 y 2, cuantos nodos tiene, si de verdad <b>ALCANZA</b>
     * (`canReach`, que es lo que exige vanilla en `AcquirePoi`) y <b>donde acaba</b> (y a que distancia del destino).
     * Es lo que distingue "hay ruta" de "la ruta llega": una cama es un bloque al que no se puede subir, asi que la
     * ruta termina AL LADO y `canReach` puede decir que no.
     */
    private static String rutaDetallada(Villager v, BlockPos destino) {
        StringBuilder sb = new StringBuilder();
        for (int acc : new int[]{1, 2}) {
            try {
                var camino = v.getNavigation().createPath(destino, acc);
                if (camino == null) {
                    sb.append(" a").append(acc).append("=NO(nula)");
                    continue;
                }
                var fin = camino.getEndNode();
                double d = fin == null ? -1.0D : Math.sqrt(fin.asBlockPos().distSqr(destino));
                sb.append(" a").append(acc).append('=').append(camino.getNodeCount()).append("n alcance=")
                        .append(camino.canReach() ? "SI" : "NO").append(" fin=")
                        .append(fin == null ? "?" : fin.asBlockPos().toShortString())
                        .append(" dFin=").append(fmt(d));
            } catch (RuntimeException e) {
                sb.append(" a").append(acc).append("=ERROR:").append(e.getClass().getSimpleName());
            }
        }
        return sb.toString().trim();
    }

    /** ¿Encuentra el aldeano camino hasta esa celda? ("SI"/"NO"/"?"): es `PathNavigation.createPath`. */    private static String ruta(ServerLevel level, Villager v, BlockPos destino) {
        try {
            var camino = v.getNavigation().createPath(destino, 1);
            if (camino == null) {
                return "NO(nulo)";
            }
            return camino.getNodeCount() > 0 ? "SI(" + camino.getNodeCount() + ")" : "NO(vacio)";
        } catch (RuntimeException e) {
            return "ERROR:" + e.getClass().getSimpleName();
        }
    }

    /**
     * LAS CAMAS: aldeano por aldeano, si tiene cama en la memoria del cerebro, si esta durmiendo y que actividad tiene
     * activa (REST/WORK/MEET), con la hora del mundo. Es lo que mide "por que dice Sin cama si sobran camas".
     * <p>
     * Y EL VIGILANTE: se guarda la cama de cada aldeano de la pasada anterior, y cuando CAMBIA se canta la transicion
     * con el estado de la cama vieja, que es lo que dice QUIEN la borra. La sospecha (codigo de vanilla,
     * {@code ValidateNearbyPoi}, que el aldeano lleva registrado para {@code HOME}):
     * <pre>
     *   if (!poiManager.exists(pos, HOME))            memory.erase();   // (a) la cama ya no esta
     *   else if (bedIsOccupied(level, pos, entity))    memory.erase();   // (b) OCCUPIED y el no duerme
     * </pre>
     * Asi que al perderla se imprime: el bloque que hay, si el POI existe, la propiedad OCCUPIED de la cama, quien
     * duerme en ella (aldeano o jugador), quien mas la tiene en el cerebro y a que distancia estaba el aldeano.
     */
    private static final java.util.Map<String, String> camaAnterior = new java.util.HashMap<>();

    private static void volcarCamas(ServerLevel level) {
        DevilRpg.LOGGER.info("[Arnes] CAMAS: dayTime={} (franja {})", level.getDayTime() % 24000,
                level.getDayTime() % 24000 >= 12000 ? "DESCANSO" : "dia");
        // EL RESUMEN (el criterio de "arreglado"): cuantos adultos tienen cama, cuantos COMPARTEN cama (dos aldeanos
        // con la misma cama: la mitad de la misma cama o la misma casilla) y quien se queda SIN cama.
        int adultos = 0;
        int conCama = 0;
        java.util.Map<String, java.util.List<String>> porCama = new java.util.TreeMap<>();
        java.util.List<String> sinCama = new java.util.ArrayList<>();
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(64))) {
            if (v.isBaby()) {
                continue;
            }
            String uuid = v.getUUID().toString().substring(0, 8);
            var home = v.getBrain().getMemory(MemoryModuleType.HOME);
            String cama = home.map(g -> g.pos().toShortString()).orElse("NINGUNA");
            String existe = "";
            if (home.isPresent()) {
                BlockPos p = home.get().pos();
                boolean poi = level.getPoiManager().getType(p).isPresent();
                String bloque = level.getBlockState(p).getBlock().toString()
                        .replace("Block{minecraft:", "").replace("}", "");
                existe = " poi=" + (poi ? "SI" : "NO") + " bloque=" + bloque + " " + estadoDeLaCama(level, p, v);
            }
            String anterior = camaAnterior.put(uuid, cama);
            adultos++;
            if (home.isPresent()) {
                conCama++;
                porCama.computeIfAbsent(claveDeLaCama(level, home.get().pos()), k -> new java.util.ArrayList<>())
                        .add(uuid);
            } else {
                sinCama.add(uuid + "(" + str(v.getVillagerData().getProfession()) + ")");
            }
            if (anterior != null && !anterior.equals(cama)) {
                if ("NINGUNA".equals(cama)) {
                    DevilRpg.LOGGER.info("[Arnes] CAMA PERDIDA {} prof={} tenia={} -> SIN CAMA · {} · pos={}",
                            uuid, str(v.getVillagerData().getProfession()), anterior,
                            diagnosticoDeLaCama(level, anterior, v), v.blockPosition().toShortString());
                } else {
                    DevilRpg.LOGGER.info("[Arnes] CAMA RECLAMADA {} prof={} {} -> {} · {}",
                            uuid, str(v.getVillagerData().getProfession()),
                            "NINGUNA".equals(anterior) ? "SIN CAMA" : anterior, cama,
                            diagnosticoDeLaCama(level, cama, v));
                }
            }
            DevilRpg.LOGGER.info("[Arnes] CAMA {} prof={} home={}{} durmiendo={} REST={} WORK={} MEET={} pos={}",
                    uuid, str(v.getVillagerData().getProfession()),
                    cama, existe, v.isSleeping(),
                    v.getBrain().isActive(Activity.REST), v.getBrain().isActive(Activity.WORK),
                    v.getBrain().isActive(Activity.MEET), v.blockPosition().toShortString());
        }
        if (ticks % 200 == 0) {
            StringBuilder compartidas = new StringBuilder();
            int ok = 0;
            for (var entrada : porCama.entrySet()) {
                if (entrada.getValue().size() > 1) {
                    compartidas.append(" [").append(entrada.getKey()).append(": ")
                            .append(String.join("+", entrada.getValue())).append(']');
                } else {
                    ok++;
                }
            }
            DevilRpg.LOGGER.info("[Arnes] CAMAS RESUMEN: adultos={} conCama={} (camas distintas ocupadas={})"
                            + " COMPARTIDAS={}{} SIN CAMA={}{}",
                    adultos, conCama, ok, porCama.size() - ok, compartidas, sinCama.size(),
                    sinCama.isEmpty() ? "" : " " + String.join(" ", sinCama));
            // Y POR QUE NO LE DAN CAMA: para el primer aldeano sin cama, las 8 camas libres mas cercanas con el
            // motivo por el que la reclamacion las descarta (o la acepta).
            for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(64))) {
                if (v.isBaby() || v.getBrain().hasMemoryValue(MemoryModuleType.HOME)) {
                    continue;
                }
                DevilRpg.LOGGER.info("[Arnes] SIN CAMA {} {} pos={} (dist a la plaza {})", uuid8(v),
                        str(v.getVillagerData().getProfession()), v.blockPosition().toShortString(),
                        fmt(Math.sqrt(v.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(CENTRO)))));
                var poi = level.getPoiManager();
                poi.findAllClosestFirstWithType(h -> h.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME),
                                p -> true, v.blockPosition(), 48,
                                net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE)
                        .limit(8)
                        .forEach(par -> {
                            BlockPos p = par.getSecond();
                            var est = level.getBlockState(p);
                            String bloque = est.getBlock().toString().replace("Block{minecraft:", "")
                                    .replace("}", "");
                            boolean ocupada = est.hasProperty(net.minecraft.world.level.block.BedBlock.OCCUPIED)
                                    && est.getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED);
                            String pareja = "?";
                            if (est.hasProperty(net.minecraft.world.level.block.BedBlock.PART)
                                    && est.hasProperty(net.minecraft.world.level.block.BedBlock.FACING)) {
                                var hacia = est.getValue(net.minecraft.world.level.block.BedBlock.PART)
                                        == net.minecraft.world.level.block.state.properties.BedPart.FOOT
                                        ? est.getValue(net.minecraft.world.level.block.BedBlock.FACING)
                                        : est.getValue(net.minecraft.world.level.block.BedBlock.FACING).getOpposite();
                                var pe = level.getBlockState(p.relative(hacia));
                                boolean peOcupada = pe.hasProperty(net.minecraft.world.level.block.BedBlock.OCCUPIED)
                                        && pe.getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED);
                                pareja = pe.getBlock().toString().replace("Block{minecraft:", "").replace("}", "")
                                        + " ocupada=" + peOcupada;
                            }
                            DevilRpg.LOGGER.info("[Arnes]   CAMA CANDIDATA {} bloque={} ocupada={} pareja=[{}] {}"
                                            + " dist={}", p.toShortString(), bloque, ocupada, pareja,
                                    rutaDetallada(v, p),
                                    fmt(Math.sqrt(v.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(p)))));
                        });
                break; // con uno basta para ver el motivo
            }
        }
    }

    private static String uuid8(Villager v) {
        return v.getUUID().toString().substring(0, 8);
    }

    /**
     * EL QUE ESTA DENTRO DE UN BANCAL (de noche): se imprime su posicion exacta, su cama, el destino de su cerebro
     * ({@code WALK_TARGET}), los <b>goals que tiene corriendo</b> (es lo que dice si la pierna de salir del bancal del
     * goal del granjero esta activa) y el <b>estado de las cuatro compuertas</b> de su bancal (abierta/cerrada), que
     * es lo que decide si puede salir.
     */
    private static void vigilarGranjerosEnElBancal(ServerLevel level) {
        int cota = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(96))) {
            if (v.isBaby()) {
                continue;
            }
            int dentro = -1;
            for (int i = 0; i < com.chipoodle.devilrpg.world.VillageGenerator.parcelasDeGranja(); i++) {
                if (com.chipoodle.devilrpg.world.VillageGenerator.estaDentroDeLaParcela(CENTRO, i, cota,
                        v.blockPosition())) {
                    dentro = i;
                }
            }
            if (dentro < 0) {
                continue;
            }
            StringBuilder goals = new StringBuilder();
            for (net.minecraft.world.entity.ai.goal.WrappedGoal w : v.goalSelector.getAvailableGoals()) {
                if (w.isRunning()) {
                    goals.append(w.getGoal().getClass().getSimpleName()).append(' ');
                }
            }
            var wt = v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            var home = v.getBrain().getMemory(MemoryModuleType.HOME);
            StringBuilder portones = new StringBuilder();
            for (BlockPos p : com.chipoodle.devilrpg.world.VillageGenerator.portonesDeLaParcela(CENTRO, dentro, cota)) {
                var est = level.getBlockState(p);
                boolean abierta = est.hasProperty(net.minecraft.world.level.block.FenceGateBlock.OPEN)
                        && est.getValue(net.minecraft.world.level.block.FenceGateBlock.OPEN);
                portones.append(' ').append(p.toShortString()).append(abierta ? "=ABIERTA" : "=cerrada");
            }
            DevilRpg.LOGGER.info("[Arnes] EN-BANCAL {} {} dentro={} pos=({},{},{}) durmiendo={} REST={} home={}"
                            + " destino={} goals=[{}] compuertas:{}", uuid8(v),
                    str(v.getVillagerData().getProfession()), dentro, fmt(v.getX()), fmt(v.getY()), fmt(v.getZ()),
                    v.isSleeping(), v.getBrain().isActive(Activity.REST),
                    home.map(g -> g.pos().toShortString()).orElse("NINGUNA"),
                    wt == null ? "SIN DESTINO" : wt.getTarget().currentBlockPosition().toShortString(),
                    goals.toString().trim(), portones);
        }
    }

    /** La cama que tenia cada aldeano en el tick ANTERIOR, y como estaba (para el vigilante de cada tick). */
    private static final java.util.Map<String, String> camaTickAnterior = new java.util.HashMap<>();
    private static final java.util.Map<String, String> estadoTickAnterior = new java.util.HashMap<>();

    /**
     * <b>VIGILANTE A RESOLUCIÓN DE TICK</b>: se llama cada tick de servidor y solo escribe cuando la cama de un
     * aldeano <b>cambia</b>. Al <b>PERDERLA</b> imprime cómo estaba la cama <b>en el tick anterior</b>, que es lo que
     * identifica al culpable: vanilla ({@code ValidateNearbyPoi}) borra el {@code HOME} si el <b>POI ya no está</b> o
     * si la cama está <b>{@code OCCUPIED}</b> y el aldeano <b>no</b> está durmiendo (y solo mira a ≤16 bloques).
     */
    private static void vigilarCamasCadaTick(ServerLevel level) {
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(64))) {
            if (v.isBaby()) {
                continue;
            }
            String uuid = uuid8(v);
            var home = v.getBrain().getMemory(MemoryModuleType.HOME);
            String cama = home.map(g -> g.pos().toShortString()).orElse("NINGUNA");
            String estado = home.map(g -> "poi=" + (level.getPoiManager().getType(g.pos()).isPresent() ? "SI" : "NO")
                    + " " + estadoDeLaCama(level, g.pos(), v)).orElse("-");
            String antes = camaTickAnterior.put(uuid, cama);
            String estadoAntes = estadoTickAnterior.put(uuid, estado);
            if (antes == null || antes.equals(cama)) {
                continue;
            }
            if ("NINGUNA".equals(cama)) {
                DevilRpg.LOGGER.info("[Arnes] PERDIDA-TICK {} {} tenia={} · EN EL TICK ANTERIOR: {} · pos={}"
                                + " durmiendo={} REST={} dist={} · MEMORIAS QUE LE QUEDAN={} · ACTIVIDADES={}", uuid,
                        str(v.getVillagerData().getProfession()), antes, estadoAntes,
                        v.blockPosition().toShortString(), v.isSleeping(), v.getBrain().isActive(Activity.REST),
                        fmt(Math.sqrt(v.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(
                                posDe(antes))))),
                        memoriasPresentes(v), v.getBrain().getActiveActivities());
            } else {
                DevilRpg.LOGGER.info("[Arnes] RECLAMADA-TICK {} {} {} -> {} · {}", uuid,
                        str(v.getVillagerData().getProfession()),
                        "NINGUNA".equals(antes) ? "SIN CAMA" : antes, cama, estado);
            }
        }
    }

    /** Las memorias que le QUEDAN al aldeano (solo las que tienen valor): dice si el borrado es de HOME o de todo. */
    private static String memoriasPresentes(Villager v) {
        StringBuilder sb = new StringBuilder();
        for (var entrada : v.getBrain().getMemories().entrySet()) {
            if (entrada.getValue().isPresent()) {
                String nombre = String.valueOf(entrada.getKey()).replace("minecraft:", "");
                String valor = String.valueOf(entrada.getValue().get());
                if (valor.length() > 40) {
                    valor = valor.substring(0, 40) + "...";
                }
                sb.append(nombre).append('=').append(valor).append(" | ");
            }
        }
        return sb.length() == 0 ? "(ninguna)" : sb.toString().trim();
    }

    /**
     * SONDA DE RUTAS DEL ALDEANO SIN CAMA (temporal): desde su posicion, prueba la ruta a las celdas que importan
     * (su cama, la compuerta de la habitacion, el pasillo, el interior) para ver DONDE se corta el camino. Imprime
     * tambien el bloque que tiene debajo y a los lados, que es lo que suele explicar el corte.
     */
    private static void sondarRutasDelSinCama(ServerLevel level) {
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(96))) {
            if (v.isBaby() || v.getBrain().hasMemoryValue(MemoryModuleType.HOME)) {
                continue;
            }
            BlockPos p = v.blockPosition();
            DevilRpg.LOGGER.info("[Arnes] SONDA {} {} pos={} debajo={} suelo={} norte={} sur={} este={} oeste={}",
                    uuid8(v), str(v.getVillagerData().getProfession()), p.toShortString(),
                    level.getBlockState(p.below()).getBlock(), level.getBlockState(p).getBlock(),
                    level.getBlockState(p.north()).getBlock(), level.getBlockState(p.south()).getBlock(),
                    level.getBlockState(p.east()).getBlock(), level.getBlockState(p.west()).getBlock());
            for (BlockPos destino : new BlockPos[]{
                    new BlockPos(1442, 120, 1401),   // fuera, al oeste de la compuerta
                    new BlockPos(1443, 120, 1401),   // la compuerta (abierta)
                    new BlockPos(1444, 120, 1401),   // justo dentro
                    new BlockPos(1445, 120, 1401),   // dentro, 2
                    new BlockPos(1446, 120, 1401),   // dentro, 3
                    new BlockPos(1447, 120, 1401),   // dentro, 4 (la pared norte esta al este de aqui)
                    new BlockPos(1447, 120, 1403),   // dentro, bajando
                    new BlockPos(1447, 120, 1405),   // dentro, abajo
                    new BlockPos(1450, 120, 1405),   // junto a la cama
                    new BlockPos(1452, 120, 1404)} ) { // al lado de la cabecera
                DevilRpg.LOGGER.info("[Arnes]   RUTA a {} -> {}", destino.toShortString(),
                        rutaDetallada(v, destino));
            }
            BlockPos compuerta = new BlockPos(1443, 120, 1401);
            var est = level.getBlockState(compuerta);
            DevilRpg.LOGGER.info("[Arnes]   COMPUERTA {}: bloque={} colision={} arriba={} abajo={}",
                    compuerta.toShortString(), est.getBlock(),
                    est.getCollisionShape(level, compuerta).isEmpty() ? "vacia" : "NO vacia",
                    level.getBlockState(compuerta.above()).getBlock(),
                    level.getBlockState(compuerta.below()).getBlock());
            break;
        }
    }

    /** La posición de un texto "x, y, z" (el que imprime `BlockPos.toShortString`). */    private static BlockPos posDe(String texto) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(-?\\d+), ?(-?\\d+), ?(-?\\d+)").matcher(texto);
        if (!m.find()) {
            return CENTRO;
        }
        return new BlockPos(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
    }

    /**
     * La clave de la <b>cama entera</b> (las dos mitades son dos POIs distintos): así dos aldeanos que tienen una
     * mitad cada uno cuentan como cama COMPARTIDA, que es justo el caso que vanilla castiga borrándole el HOME al que
     * no duerme.
     */
    private static String claveDeLaCama(ServerLevel level, BlockPos p) {
        var estado = level.getBlockState(p);
        BlockPos pareja = null;
        if (estado.hasProperty(net.minecraft.world.level.block.BedBlock.PART)
                && estado.hasProperty(net.minecraft.world.level.block.BedBlock.FACING)) {
            var hacia = estado.getValue(net.minecraft.world.level.block.BedBlock.PART)
                    == net.minecraft.world.level.block.state.properties.BedPart.FOOT
                    ? estado.getValue(net.minecraft.world.level.block.BedBlock.FACING)
                    : estado.getValue(net.minecraft.world.level.block.BedBlock.FACING).getOpposite();
            pareja = p.relative(hacia);
        }
        if (pareja == null) {
            return p.toShortString();
        }
        String a = p.toShortString();
        String b = pareja.toShortString();
        return a.compareTo(b) <= 0 ? a + "+" + b : b + "+" + a;
    }

    /** Lo que se puede leer de una cama: OCCUPIED, quien duerme en ella y quien mas la tiene reclamada. */
    private static String estadoDeLaCama(ServerLevel level, BlockPos p, Villager dueno) {
        var estado = level.getBlockState(p);
        String ocupada = estado.hasProperty(net.minecraft.world.level.block.BedBlock.OCCUPIED)
                ? String.valueOf(estado.getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED)) : "?";
        StringBuilder quien = new StringBuilder();
        for (net.minecraft.world.entity.LivingEntity e : level.getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class, new AABB(p).inflate(3.0))) {
            if (e.isSleeping() && e.blockPosition().closerToCenterThan(net.minecraft.world.phys.Vec3.atCenterOf(p), 3.0)) {
                quien.append(e == dueno ? " EL MISMO" : " " + e.getType().toShortString());
            }
        }
        return "OCCUPIED=" + ocupada + " durmiendoEnElla=[" + quien.toString().trim() + "]";
    }

    /** El parte de la cama que se acaba de perder (o de la que se acaba de reclamar): bloque, POI, dueno y distancia. */
    private static String diagnosticoDeLaCama(ServerLevel level, String posTexto, Villager v) {
        BlockPos p = null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(-?\\d+), ?(-?\\d+), ?(-?\\d+)").matcher(posTexto);
        if (m.find()) {
            p = new BlockPos(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        }
        if (p == null) {
            return "no se pudo leer la posicion '" + posTexto + "'";
        }
        boolean poi = level.getPoiManager().getType(p).isPresent();
        String bloque = level.getBlockState(p).getBlock().toString().replace("Block{minecraft:", "").replace("}", "");
        StringBuilder duenos = new StringBuilder();
        for (Villager otro : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(64))) {
            var suya = otro.getBrain().getMemory(MemoryModuleType.HOME);
            if (suya.isPresent() && suya.get().pos().equals(p)) {
                duenos.append(otro == v ? " EL" : " " + otro.getUUID().toString().substring(0, 8));
            }
            // ¿La OTRA MITAD de la cama (la pareja de bloques) es de alguien? Vanilla cuenta cada mitad como un POI
            // HOME, asi que dos aldeanos pueden acabar "compartiendo" cama y al que no duerme le borra el HOME.
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos mitad = p.relative(d);
                if (level.getBlockState(mitad).is(net.minecraft.tags.BlockTags.BEDS)
                        && suya.isPresent() && suya.get().pos().equals(mitad)) {
                    duenos.append(" [otra mitad: ").append(otro.getUUID().toString().substring(0, 8)).append(']');
                }
            }
        }
        double dist = Math.sqrt(v.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(p)));
        return "poi=" + (poi ? "SI" : "NO") + " bloque=" + bloque + " " + estadoDeLaCama(level, p, v)
                + " duennos=[" + duenos.toString().trim() + "] dist=" + fmt(dist) + " (vanilla borra si dist<=16 y"
                + " la cama esta ocupada o el POI no existe)";
    }

    /** Los objetos tirados por el pueblo (a eso va el recolector) y los que estén en la taberna, sobre todo arriba. */
    private static void volcarObjetos(ServerLevel level) {
        BlockPos taberna = com.chipoodle.devilrpg.world.VillageGenerator.baseDeLaTaberna(CENTRO);
        int cota = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
        int total = 0;
        int enLaTaberna = 0;
        int arriba = 0;
        for (net.minecraft.world.entity.item.ItemEntity it : level.getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, new AABB(CENTRO).inflate(140))) {
            total++;
            BlockPos p = it.blockPosition();
            if (Math.abs(p.getX() - taberna.getX()) <= 20 && Math.abs(p.getZ() - taberna.getZ()) <= 16) {
                enLaTaberna++;
                if (p.getY() > cota + 6) {
                    arriba++;
                    DevilRpg.LOGGER.info("[Arnes] OBJETO arriba en la taberna: {} x{} en {}", 
                            it.getItem().getHoverName().getString(), it.getItem().getCount(), p.toShortString());
                }
            }
        }
        DevilRpg.LOGGER.info("[Arnes] OBJETOS en el pueblo: {} (en la taberna: {}, de esos por encima del forjado: {})",
                total, enLaTaberna, arriba);
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
            // EL RECOLECTOR (holgazan): que goal tiene ACTIVO y a donde va, que es lo que hay que medir ahora (el
            // jugador lo vio "de charla en el 3er piso sin hacer nada").
            boolean esRecolector = !v.isBaby()
                    && v.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.NITWIT;
            if (esRecolector) {
                StringBuilder activos = new StringBuilder();
                for (net.minecraft.world.entity.ai.goal.WrappedGoal w : v.goalSelector.getAvailableGoals()) {
                    if (w.isRunning()) {
                        activos.append(w.getGoal().getClass().getSimpleName()).append(' ');
                    }
                }
                WalkTarget wtr = v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
                // ¿ENCUENTRA CAMINO? Es la pregunta de esta ronda: se queda clavado en el desvan con el almacen como
                // destino, asi que se prueban las rutas al almacen, a la plaza y al hueco del desvan.
                int cotaRec = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
                String rutaAlmacen = ruta(level, v, new BlockPos(1461, 121, 1434));
                String rutaPlaza = ruta(level, v, new BlockPos(CENTRO.getX(), cotaRec, CENTRO.getZ()));
                String rutaHueco = ruta(level, v, new BlockPos(1442, 131, 1438));
                DevilRpg.LOGGER.info("[Arnes] t={} {} RECOLECTOR pos=({},{},{}) activos=[{}] destino={} rutas[almacen={}"
                                + " plaza={} huecoDesvan={}] etiqueta={}",
                        level.getGameTime(), v.getUUID().toString().substring(0, 8), fmt(v.getX()), fmt(v.getY()),
                        fmt(v.getZ()), activos.toString().trim(),
                        wtr == null ? "SIN DESTINO" : wtr.getTarget().currentBlockPosition().toShortString(),
                        rutaAlmacen, rutaPlaza, rutaHueco,
                        v.getCustomName() == null ? "-" : v.getCustomName().getString().replace("\n", " | "));
                continue;
            }
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
