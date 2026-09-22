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
    private static final boolean MEDIR_NOCHE = false;
    /**
     * ¿Se mide el <b>CIERRE DE PUERTAS</b>? Pone el mundo de <b>día</b> (los aldeanos se levantan y salen: cruzan
     * puertas), se salta las siembras y volca cada 2 s las <b>puertas de madera abiertas</b> del pueblo. Es lo que
     * pide el jugador: *"los aldeanos cuando vayan a dormir tienen que cerrar la puerta porque todas la dejan
     * abierta"*.
     */
    private static final boolean MEDIR_PUERTAS = false;
    /**
     * <b>¿Se mete un bicho DENTRO de la aldea y se deja ahí?</b> Es la reproducción de la queja del jugador
     * (*"Mauricio sigue sin ir a buscar cama y hay varias en la taberna"*): con un monstruo dentro del recinto,
     * `manageNearby` corta el latido entero (`hayEnemigosDentro`), y con él se quedaba sin hacer TODO lo que va
     * detrás, camas incluidas. Se usa un <b>aldeano-zombi</b> a propósito: es un {@code Monster} (cuenta para
     * `hayEnemigosDentro`) pero el sello lo deja en paz —`expulsarHostilesDeLaAldea` no lo toca, para no cortar una
     * curación en marcha—, así que el "bicho dentro" se mantiene toda la corrida sin que lo expulsen en el primer
     * latido. Va con `NoAI` (no pelea ni anda) e invulnerable.
     */
    private static final boolean BICHO_DENTRO = false;
    /**
     * ¿Se mide la <b>COCINA</b> (lo reportó el jugador: *"el cocinero está cocinando FUERA de la taberna, esto no
     * debe ser así, debe estar adentro"*)? Pone el mundo de <b>día</b> (de noche el cocinero se acuesta), viste el
     * almacén con <b>leña</b> y la despensa con <b>carne cruda</b> —que es lo que hace que el cocinero trabaje— y
     * volca cada 2 s dónde está, a qué distancia del ahumador, si lo <b>VE</b> (rayo de colisión) y qué ruta tiene a su
     * casilla de la cocina. Es lo que distingue "cocina dentro" de "cocina a través de la pared".
     */
    private static final boolean MEDIR_COCINA = false;
    /** ¿Se mide LA HUERTA (lo que hay tirado en los bancales y el zurron de cada granjero)? Ver igilarLaHuerta. */
    private static final boolean MEDIR_HUERTA = false;
    /**
     * ¿Se mide <b>LA MILICIA Y EL SANADOR</b> (I62/I64)? Deja el mundo de DÍA, suelta <b>dos zombis flojos</b> dentro
     * de la aldea (para que la guardia pelee, mate y suba de nivel) y <b>hiere</b> a tres guardias al 35 % de su vida
     * (para que el clérigo tenga a quién curar). Cada segundo vuelca la vida, el NIVEL y las MATANZAS de cada guardia
     * y lo que está haciendo el clérigo.
     */
    private static final boolean MEDIR_MILICIA = false;
    /** ¿Se mide LA CASA DEL HUECO (la pared a la que le faltaba un bloque y el cofre de al lado)? Ver olcarLaParedYElCofre. */
    private static final boolean MEDIR_HUECO_CASA = false;
    /**
     * ¿Se miden LAS ALDEAS CON NOMBRE, EL REVELADO Y EL DIARIO DEL INVOCADO (I87)? Es un modo de <b>solo
     * lectura</b> (no siembra, no barre, no cambia la hora): deja correr el latido con el jugador de pega y vuelca
     * cada 10 s qué aldeas tiene <b>descubiertas</b> (`aldeasVisitadas`) y <b>reveladas</b> (`aldeasReveladas`), el
     * nombre y el estado de cada una, qué dibujaría la <b>barra de aldea</b>, el <b>rumbo</b> a la siguiente y las
     * <b>líneas del Diario</b> tal cual las leería el jugador.
     * <p>
     * Lo que se busca: (1) que un guardado <b>viejo</b> (sin los campos nuevos en la capability) cargue sin
     * reventar; (2) que la <b>siembra</b> apunte las aldeas que esa partida ya resolvió (`[Village] Diario del
     * Invocado sembrado para …`); (3) que la barra quede <b>OCULTA</b> mientras no haya revelado ni visitado nada; y
     * (4) que el Diario liste esas aldeas con nombre, coordenadas, estado y rumbo.
     */
    private static final boolean MEDIR_ALDEAS = false;
    /**
     * ¿Se mide EL ASEDIO CLÁSICO DE PRINCIPIO A FIN, para ver al <b>clérigo revelando la siguiente al vencer</b>
     * (I87)? Es la única parte del revelado que no se puede medir por método suelto: hace falta un asedio de verdad.
     * <p>
     * Cómo lo monta: a los 15 s arranca el asedio del objetivo <b>3</b> (una aldea que NO existe todavía: se genera
     * ahí mismo, así que su asedio está sin resolver — las otras tres de esa partida ya lo están) y deja al jugador
     * de pega <b>dentro</b> de esa aldea (si no, el reloj se queda EN PAUSA, I86). Cuando la ola sale (90 s de
     * margen), se la deja pelear 10 s y <b>se limpia desde el arnés</b>: la milicia recién nacida de una aldea a
     * ~2.000 bloques no puede con una ola escalada, y lo que se mide aquí es la <b>resolución</b> (salvada), no el
     * combate.
     * <p>
     * Lo que se busca en el log: `[Village] Aldea 3 salvada: revelada la aldea 4 a … (hacia el …)` y, en el volcado
     * del arnés, `revelada(4)=true` con la barra en `Aldea  (… m)`.
     * <p>
     * <b>MEDIDO (22-sep-2026): con un jugador de pega esto NO llega a la resolución, y se sabe por qué.</b> El reloj
     * del asedio solo corre con el jugador del asedio <b>en la lista del servidor</b> (I86), y un {@code FakePlayer}
     * <b>no está en ella</b>: `distanciaAlCentro` devuelve {@code MAX_VALUE}, el asedio queda EN PAUSA y la ola nunca
     * sale. Las dos corridas lo enseñan: `hayAsedio(3)=true` toda la corrida, `agresivos=0` siempre y
     * `revelada(4)=false`. O sea: este modo <b>sí</b> mide que el asedio existe, que el estado de la aldea es
     * `en asedio` (y que el Diario lo enseña así) y que <b>sin jugador de verdad el reloj no corre</b>; para ver al
     * clérigo revelar al vencer hace falta <b>jugar el asedio</b> (o un cliente conectado).
     * <p>
     * De paso, la primera corrida destapó un problema del <b>montaje</b>: el jugador de pega tampoco carga chunks, así
     * que la aldea 3 se descargaba (`aldeanos3=11` y diez segundos después `0`). Se fuerzan los chunks de esa aldea al
     * arrancar el asedio y con eso los 11 aldeanos se mantienen.
     */
    private static final boolean MEDIR_ASEDIO_VIVO = false;
    /** Dónde se planta el bicho (relativo a la plaza): dentro del recinto (radio 62) y a la altura del pueblo. */
    private static final BlockPos BICHO_EN = new BlockPos(6, 0, 6);
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
        if (!MEDIR_NOCHE && !MEDIR_PUERTAS && !MEDIR_COCINA && !MEDIR_ALDEAS && ticks == 600) {
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
        // OJO: en la medida de LA MILICIA **no se barre**, porque los bichos que hay dentro son los que se acaban de
        // sembrar para que la guardia pelee (medido: con el barrido, el zombi desaparecia en el mismo segundo, la
        // guardia se quedaba con la etiqueta "Atacando" un instante y volvia a su ronda, y no habia ni una muerte).
        if (ticks % 20 == 0 && !MEDIR_MILICIA) {
            if (BICHO_DENTRO) {
                // ...pero para medir EL BUG DEL LATIDO CORTADO hay que dejar UNO dentro a proposito.
                mantenerBichoDentro(level);
            } else {
                for (net.minecraft.world.entity.Mob m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                        new AABB(CENTRO).inflate(140))) {
                    if (m instanceof net.minecraft.world.entity.monster.Monster) {
                        m.discard();
                    }
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
        if (MEDIR_ALDEAS) {
            // LAS ALDEAS CON NOMBRE Y EL DIARIO (I87): solo lectura, cada 10 s.
            if (ticks % 200 == 0) {
                volcarAldeas(level, pega);
            }
        }
        if (MEDIR_PUERTAS) {
            // LAS PUERTAS, cada 2 s: los aldeanos estan de dia (se levantan y salen) y cruzan puertas: se cuenta
            // cuantas de madera quedan ABIERTAS en el pueblo. Lo que se busca es que BAJE (las cierran al pasar).
            if (ticks % 40 == 0) {
                volcarPuertas(level);
            }
        } else if (MEDIR_ASEDIO_VIVO) {
            medirElAsedioVivo(level, pega);
        } else if (MEDIR_COCINA) {
            // LA COCINA, cada 2 s: donde esta el cocinero, si VE el ahumador y si tiene ruta a su casilla.
            if (ticks == 400) {
                sembrarLaCocina(level);
            }
            if (ticks % 40 == 0) {
                vigilarCocinero(level);
            }
        } else if (MEDIR_NOCHE) {
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
        } else if (MEDIR_MILICIA) {
            // LA MILICIA Y EL SANADOR (I62/I64): a los 60 s (chunks cargados, oficios repartidos y guardia alistada:
            // a los 30 s TODAVIA no hay guardias y la siembra se quedaba sin heridos, medido) se siembra la pelea, y a
            // partir de ahí se vuelca cada segundo quién pelea, quién sube de nivel y a quién cura el clérigo.
            if (ticks == 3000) {
                sembrarLaMilicia(level);
            }
            if (ticks == 3040) {
                golpearConLaGuardia(level); // 2 s despues: las entidades recien anadidas ya estan en el nivel
            }
            if (ticks % 20 == 0) {
                volcarLaMilicia(level);
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
            // LA HUERTA (lo reporto el jugador: "los granjeros estan dejando muchos vegetales en el suelo cuando
            // cosechan"): lo que hay TIRADO en cada bancal y el zurron de cada granjero (con sus huecos libres).
            if (MEDIR_HUERTA && ticks % 40 == 0) {
                vigilarLaHuerta(level);
            }
        }
        // LA CASA DEL HUECO (el reporte del jugador con captura): las dos celdas de la pared que le faltaban, sus
        // vecinas y el COFRE de al lado con lo que tiene dentro. Cada 2 s, para ver el antes (aire) y el después
        // (adoquín) en la MISMA corrida y comprobar que el cofre no se toca.
        if (MEDIR_HUECO_CASA && ticks % 40 == 0) {
            volcarLaParedYElCofre(level);
        }
        // EL PORCHE DE LA TABERNA (migracion 63): se mide la columna `bx-1`, la que queda ENTRE el toldo (bx-2) y la
        // pared de la taberna (bx). A los 10 s el latido ya migro la aldea, asi que esto es "despues".
        if (ticks == 200) {
            volcarPorche(level, "DESPUES");
        }
    }

    /** El bicho de la medida (el aldeano-zombi que se deja dentro de la aldea): se reutiliza, no se duplica. */
    private static net.minecraft.world.entity.monster.ZombieVillager bicho = null;

    /**
     * <b>Mantiene UN bicho dentro de la aldea</b> para medir el latido cortado: si no está (lo barrió otra cosa, se
     * descargó el chunk...), se vuelve a plantar; y si está, se le deja clavado en su celda (sin IA no se mueve, pero
     * un empujón lo saca del recinto y entonces la medida dejaría de ser "con un bicho dentro").
     */
    private static void mantenerBichoDentro(ServerLevel level) {
        int cota = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
        BlockPos donde = CENTRO.offset(BICHO_EN.getX(), cota - CENTRO.getY(), BICHO_EN.getZ());
        if (bicho == null || bicho.isRemoved() || !bicho.isAlive()) {
            bicho = net.minecraft.world.entity.EntityType.ZOMBIE_VILLAGER.create(level);
            if (bicho == null) {
                return;
            }
            bicho.moveTo(donde.getX() + 0.5D, donde.getY(), donde.getZ() + 0.5D, 0.0F, 0.0F);
            bicho.setNoAi(true);
            bicho.setInvulnerable(true);
            bicho.setPersistenceRequired();
            level.addFreshEntity(bicho);
            DevilRpg.LOGGER.info("[Arnes] BICHO DENTRO: plantado un aldeano-zombi en {} (cota {})", donde, cota);
            return;
        }
        bicho.moveTo(donde.getX() + 0.5D, donde.getY(), donde.getZ() + 0.5D, bicho.getYRot(), bicho.getXRot());
    }

    /**
     * <b>¿Está el latido del pueblo cortado?</b> Se cuenta lo mismo que mira el mod
     * (`hayEnemigosDentro`: monstruos dentro del recinto en XZ y a la altura del pueblo) y se imprime junto al
     * censo de camas, para poder decir en la misma línea "hay bicho dentro" y "a este no le han dado cama".
     */
    private static String estadoDelRecinto(ServerLevel level) {
        int cota = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
        int dentro = 0;
        for (net.minecraft.world.entity.monster.Monster m : level.getEntitiesOfClass(
                net.minecraft.world.entity.monster.Monster.class,
                new AABB(CENTRO).inflate(com.chipoodle.devilrpg.world.VillageGenerator.FENCE_RADIUS))) {
            if (VillageManager.dentroDelRecinto(cota, CENTRO, m,
                    com.chipoodle.devilrpg.world.VillageGenerator.FENCE_RADIUS)) {
                dentro++;
            }
        }
        return dentro == 0 ? "NO (el latido corre entero)" : "SI (" + dentro + " monstruo(s): latido cortado)";
    }

    /**
     * <b>LA PELEA Y EL SANADOR, SEMBRADOS A MANO</b> (I62/I64): dos zombis <b>flojos</b> dentro de la aldea —para que
     * la guardia pelee y mate— y tres guardias <b>heridos</b> al 35 % de su vida, para que el clérigo tenga pacientes
     * sin depender de que un zombi acierte. Los zombis pegan poco a propósito (1 de daño): la medida es de la
     * guardia, no de una masacre de aldeanos.
     */
    private static void sembrarLaMilicia(ServerLevel level) {
        int cota = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
        // Primero los guardias: se hiere a tres y se apunta dónde está el primero, que es donde se sueltan los
        // zombis (a 3 bloques y SIN IA: la guardia los ve, los mata y sube de nivel sin que ellos maten a nadie).
        Villager primero = null;
        int heridos = 0;
        int guardias = 0;
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(140))) {
            if (!com.chipoodle.devilrpg.entity.goal.VillagerGuardGoal.esGuardia(v)) {
                continue;
            }
            guardias++;
            if (primero == null) {
                primero = v;
            }
            if (heridos < 3) {
                v.setHealth(Math.max(1.0F, v.getMaxHealth() * 0.35F));
                heridos++;
            }
        }
        BlockPos junto = primero == null ? CENTRO.offset(14, 0, 6) : primero.blockPosition().offset(3, 0, 0);
        int zombis = 0;
        for (int i = 0; i < 4; i++) {
            BlockPos donde = junto.offset(i % 2, 0, i / 2);
            net.minecraft.world.entity.monster.Zombie z = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
            if (z == null) {
                continue;
            }
            z.moveTo(donde.getX() + 0.5D, donde.getY(), donde.getZ() + 0.5D, 0.0F, 0.0F);
            z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(4.0D);
            z.setHealth(4.0F);
            z.setNoAi(true); // no pelean: la medida es de la guardia (un zombi con IA podia matar a un aldeano)
            z.setPersistenceRequired();
            level.addFreshEntity(z);
            zombis++;
        }
        DevilRpg.LOGGER.info("[Arnes] MILICIA: {} zombi(s) flojo(s) junto a {} y {} de {} guardia(s) herido(s) al 35 %"
                + " (el clerigo tiene que ir a curarlos)", zombis,
                primero == null ? "la plaza" : nombreCorto(primero), heridos, guardias);
    }

    /**
     * <b>LOS GOLPES, ATRIBUIDOS A LA GUARDIA</b> (I62): la pelea de verdad —que el goal de la guardia ataque a un
     * monstruo— ya está medida aparte; lo que aquí se mide es el <b>enganche de la muerte</b>: el daño va con
     * {@code mobAttack(guardia)}, que es exactamente lo que produce el juego cuando el aldeano pega, así que la muerte
     * pasa por {@code LivingDeathEvent} con el aldeano como dueño del daño.
     * <p>
     * Va <b>en un tick posterior</b> al sembrado a propósito: una entidad recién añadida al nivel no aparece todavía
     * en las consultas (`getEntitiesOfClass`) hasta que el mundo da un tick, así que haciéndolo en el mismo tick el
     * barrido no encontraba a los zombis y no había ni un golpe (medido: la línea de la siembra salía y la de los
     * golpes no).
     */
    private static void golpearConLaGuardia(ServerLevel level) {
        Villager guardia = null;
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(140))) {
            if (com.chipoodle.devilrpg.entity.goal.VillagerGuardGoal.esGuardia(v)) {
                guardia = v;
                break;
            }
        }
        int golpes = 0;
        if (guardia != null) {
            for (net.minecraft.world.entity.Mob m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                    new AABB(CENTRO).inflate(48))) {
                if (m instanceof net.minecraft.world.entity.monster.Monster) {
                    m.hurt(level.damageSources().mobAttack(guardia), 100.0F);
                    golpes++;
                }
            }
        }
        DevilRpg.LOGGER.info("[Arnes] MILICIA: {} golpe(s) mortal(es) de {} (matanzas={}, nv={})",
                golpes, guardia == null ? "nadie" : nombreCorto(guardia),
                guardia == null ? 0 : VillageManager.matanzasDeGuardia(guardia),
                guardia == null ? 0 : VillageManager.nivelDeGuardia(guardia));
    }

    /**
     * Cada segundo: <b>cada guardia</b> (vida, NIVEL, matanzas y etiqueta) y <b>el clérigo</b> (su etiqueta y el
     * herido más cercano, con su vida y a cuántos bloques está). Es la medida de I62 (la milicia aprende) y de I64
     * (el clérigo sana).
     */
    private static void volcarLaMilicia(ServerLevel level) {
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(140))) {
            if (!com.chipoodle.devilrpg.entity.goal.VillagerGuardGoal.esGuardia(v)) {
                continue;
            }
            // OJO: la ETIQUETA (que lleva la actividad) va la ULTIMA y sin saltos de linea: el nombre del aldeano es
            // "Nombre (Oficio)\nActividad" y con el `\n` en medio el log partia la linea y no se veia la medida.
            DevilRpg.LOGGER.info("[Arnes] GUARDIA {} nv={} matanzas={} vida={}/{} pos={} | etiqueta: {}",
                    nombreCorto(v), VillageManager.nivelDeGuardia(v), VillageManager.matanzasDeGuardia(v),
                    redondo(v.getHealth()), v.getMaxHealth(), v.blockPosition().toShortString(), etiquetaDe(v));
        }
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(140))) {
            if (v.isBaby() || v.getVillagerData().getProfession()
                    != net.minecraft.world.entity.npc.VillagerProfession.CLERIC) {
                continue;
            }
            double mejor = Double.MAX_VALUE;
            String quien = "ninguno";
            for (Villager otro : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(140))) {
                if (otro == v || !otro.isAlive() || otro.getHealth() >= otro.getMaxHealth() * 0.75D) {
                    continue;
                }
                double d = v.distanceToSqr(otro);
                if (d < mejor) {
                    mejor = d;
                    quien = nombreCorto(otro) + " (" + redondo(otro.getHealth()) + "/" + otro.getMaxHealth()
                            + " a " + redondo((float) Math.sqrt(d)) + " bloques)";
                }
            }
            DevilRpg.LOGGER.info("[Arnes] CLERIGO {} vida={}/{} pos={} | herido mas cercano: {} | etiqueta: {}",
                    nombreCorto(v), redondo(v.getHealth()), v.getMaxHealth(),
                    v.blockPosition().toShortString(), quien, etiquetaDe(v));
        }
    }

    /** El nombre del aldeano sin el oficio detrás (lo que va antes del paréntesis). */
    private static String nombreCorto(Villager v) {
        String nombre = v.getName().getString();
        int parentesis = nombre.indexOf(" (");
        return parentesis > 0 ? nombre.substring(0, parentesis) : nombre;
    }

    private static double redondo(float valor) {
        return Math.round(valor * 10.0F) / 10.0D;
    }

    /** La etiqueta que el jugador ve sobre la cabeza del aldeano (nombre y, debajo, lo que está haciendo). */
    private static String etiquetaDe(Villager v) {
        return v.getCustomName() == null ? "(sin etiqueta)" : v.getCustomName().getString().replace("\n", " / ");
    }

    /** Leña al almacén y carne cruda a la despensa: sin eso el cocinero no tiene nada que cocinar (ni con qué quemar). */
    private static void sembrarLaCocina(ServerLevel level) {        var resto = com.chipoodle.devilrpg.world.VillageStorage.guardar(level, CENTRO,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG, 40));
        var despensa = com.chipoodle.devilrpg.world.VillagePantry.despensa(level, CENTRO);
        int carnes = 0;
        for (int i = 0; i < 32; i++) {
            if (com.chipoodle.devilrpg.world.VillagePantry.guardar(despensa,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BEEF, 1)).isEmpty()) {
                carnes++;
            }
        }
        DevilRpg.LOGGER.info("[Arnes] COCINA: sembrados 40 troncos (sobraron {}) y {} carnes crudas en la despensa",
                resto.isEmpty() ? 0 : resto.getCount(), carnes);
    }

    /**
     * <b>¿DÓNDE COCINA EL COCINERO?</b> Se imprime su posición, la distancia a la <b>casilla de la cocina</b> y al
     * <b>ahumador</b>, si <b>VE</b> el ahumador (rayo de colisión: si está fuera del comedor, el rayo choca con la
     * pared) y su <b>ruta</b> a la casilla de la cocina (nodos, si alcanza y dónde acaba). Es la diferencia entre
     * "cocina dentro de la taberna" y "cocina a través de la pared" (el bug que reportó el jugador).
     */
    private static void vigilarCocinero(ServerLevel level) {
        int cota = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
        BlockPos ahumador = com.chipoodle.devilrpg.world.VillageGenerator.puestoDelCocinero(level, CENTRO);
        BlockPos casilla = new BlockPos(ahumador.getX(), ahumador.getY(), ahumador.getZ() - 1);
        BlockPos base = com.chipoodle.devilrpg.world.VillageGenerator.baseDeLaTaberna(CENTRO);
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(RADIO_CENSO))) {
            if (v.isBaby()
                    || v.getVillagerData().getProfession() != net.minecraft.world.entity.npc.VillagerProfession.BUTCHER) {
                continue;
            }
            Vec3 ojo = new Vec3(v.getX(), v.getY() + 1.0D, v.getZ());
            Vec3 meta = Vec3.atCenterOf(ahumador);
            var choque = level.clip(new net.minecraft.world.level.ClipContext(ojo, meta,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, v));
            boolean ve = choque.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                    || level.getBlockState(choque.getBlockPos()).is(net.minecraft.world.level.block.Blocks.SMOKER);
            boolean dentro = v.getX() >= base.getX() + 1 && v.getX() <= base.getX() + 7
                    && v.getZ() >= base.getZ() + 1 && v.getZ() <= base.getZ() + 12
                    && Math.abs(v.getY() - cota) < 2.0D;
            StringBuilder goals = new StringBuilder();
            for (net.minecraft.world.entity.ai.goal.WrappedGoal w : v.goalSelector.getAvailableGoals()) {
                if (w.isRunning()) {
                    goals.append(w.getGoal().getClass().getSimpleName()).append(' ');
                }
            }
            var wt = v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            DevilRpg.LOGGER.info("[Arnes] COCINERO pos={} dentroDeLaTaberna={} VEelAhumador={} dCasilla={} dAhumador={}"
                            + " destino={} goals=[{}] etiqueta={} · casilla={} ruta: {}",
                    v.blockPosition().toShortString(), dentro ? "SI" : "NO", ve ? "SI" : "NO",
                    fmt(Math.sqrt(v.distanceToSqr(casilla.getX() + 0.5D, casilla.getY() + 0.5D, casilla.getZ() + 0.5D))),
                    fmt(Math.sqrt(v.distanceToSqr(meta.x, meta.y, meta.z))),
                    wt == null ? "SIN DESTINO" : wt.getTarget().currentBlockPosition().toShortString(),
                    goals.toString().trim(),
                    v.getCustomName() == null ? "-" : v.getCustomName().getString().replace("\n", " | "),
                    casilla.toShortString(), rutaDetallada(v, casilla));
        }
    }

    /**
     * <b>LA PARED QUE LE FALTABA A LA CASA Y EL COFRE DE AL LADO</b> (reporte del jugador: *"¿qué ves de extraño en
     * esta casa? ¡si le falta completarse a la pared! corrígelo y checa que el cofre no estorbe"*). Imprime el bloque
     * de las DOS celdas del hueco (`1427,120,1392` y `1427,121,1392`), el de sus vecinas (la ventana de al lado y el
     * poste de la esquina) y el <b>cofre</b> de `1428,120,1392` con sus objetos: así se ve en el log el antes (aire)
     * y el después (adoquín) y que el cofre sigue donde estaba y con lo suyo.
     */
    private static void volcarLaParedYElCofre(ServerLevel level) {
        StringBuilder sb = new StringBuilder();
        for (BlockPos p : new BlockPos[]{new BlockPos(1427, 120, 1392), new BlockPos(1427, 121, 1392),
                new BlockPos(1427, 120, 1391), new BlockPos(1427, 121, 1391), new BlockPos(1427, 120, 1393),
                new BlockPos(1428, 120, 1392)}) {
            String nombre = level.getBlockState(p).getBlock().toString()
                    .replace("Block{minecraft:", "").replace("}", "");
            sb.append(' ').append(p.toShortString()).append('=').append(nombre);
        }
        StringBuilder cofre = new StringBuilder();
        if (level.getBlockEntity(new BlockPos(1428, 120, 1392)) instanceof net.minecraft.world.Container c) {
            cofre.append("cofre[").append(c.getContainerSize()).append(" huecos]");
            for (int i = 0; i < c.getContainerSize(); i++) {
                var s = c.getItem(i);
                if (!s.isEmpty()) {
                    cofre.append(' ').append(i).append(':').append(s.getCount()).append('x')
                            .append(s.getItem().toString().replace("Item{minecraft:", "").replace("}", ""));
                }
            }
        } else {
            cofre.append("SIN COFRE (o no es un contenedor)");
        }
        DevilRpg.LOGGER.info("[Arnes] CASA hueco:{} · {}", sb, cofre);
    }

    /**
     * <b>¿QUÉ HAY TIRADO EN LA HUERTA Y QUÉ LLEVA EL GRANJERO ENCIMA?</b> (lo reportó el jugador: *"los granjeros están
     * dejando muchos vegetales en el suelo cuando cosechan"*). Por bancal, imprime cada objeto del suelo (qué es,
     * cuántos, su edad en ticks y la celda) y, por granjero, su posición, el bancal en el que está, su etiqueta y su
     * <b>zurrón</b> (hueco a hueco, con los huecos libres): es lo que dice si lo que se cae es por el TOPE de semillas,
     * por no caberle o porque nadie lo recoge.
     */
    private static void vigilarLaHuerta(ServerLevel level) {
        int cota = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
        for (int i = 0; i < com.chipoodle.devilrpg.world.VillageGenerator.parcelasDeGranja(); i++) {
            StringBuilder dentro = new StringBuilder();
            int cuantos = 0;
            for (net.minecraft.world.entity.item.ItemEntity it : level.getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class, new AABB(CENTRO).inflate(140))) {
                if (!com.chipoodle.devilrpg.world.VillageGenerator.estaDentroDeLaParcela(CENTRO, i, cota,
                        it.blockPosition())) {
                    continue;
                }
                cuantos++;
                dentro.append(' ').append(it.getItem().getHoverName().getString()).append('x')
                        .append(it.getItem().getCount()).append("@").append(it.blockPosition().toShortString())
                        .append("(edad ").append(it.tickCount).append(')');
            }
            DevilRpg.LOGGER.info("[Arnes] HUERTA bancal {}: {} objeto(s) en el suelo:{}", i, cuantos, dentro);
        }
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(RADIO_CENSO))) {
            if (v.isBaby()
                    || v.getVillagerData().getProfession() != net.minecraft.world.entity.npc.VillagerProfession.FARMER) {
                continue;
            }
            StringBuilder zurron = new StringBuilder();
            int libres = 0;
            for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
                var s = v.getInventory().getItem(i);
                if (s.isEmpty()) {
                    libres++;
                } else {
                    zurron.append(' ').append(i).append(':').append(s.getCount()).append('x')
                            .append(s.getHoverName().getString());
                }
            }
            int bancal = -1;
            for (int i = 0; i < com.chipoodle.devilrpg.world.VillageGenerator.parcelasDeGranja(); i++) {
                if (com.chipoodle.devilrpg.world.VillageGenerator.estaDentroDeLaParcela(CENTRO, i, cota,
                        v.blockPosition())) {
                    bancal = i;
                }
            }
            DevilRpg.LOGGER.info("[Arnes] GRANJERO {} nombre={} pos={} bancal={} huecosLibres={}/{} zurron:{} etiqueta={}",
                    uuid8(v), v.getCustomName() == null ? "-" : v.getCustomName().getString().replace("\n", " | "),
                    v.blockPosition().toShortString(), bancal, libres, v.getInventory().getContainerSize(),
                    zurron, v.getCustomName() == null ? "-" : "");
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
        // mediodia) para el trabajo, el combustible y el CIERRE DE PUERTAS (los aldeanos tienen que salir y cruzar).
        level.setDayTime(MEDIR_NOCHE && !MEDIR_PUERTAS ? 18000L : 6000L);
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

    /** Se hace UNA vez (el escenario de los revelados), no en cada volcado. */
    private static boolean escenarioDeReveladosHecho = false;

    /**
     * LOS TRES TEXTOS DE LA BARRA y LOS DOS REVELADOS, medidos de verdad (I87):
     * <ol>
     *   <li>los tres textos que decide {@code VillageBarText} (oculta / sin nombre / con nombre);</li>
     *   <li>el <b>clérigo</b> al vencer el asedio: revela la SIGUIENTE y dice quién habla (nombre de un clérigo real
     *       del pueblo, de los que el arnés tiene censados);</li>
     *   <li>la <b>piedra de invocación</b>: revela el objetivo actual;</li>
     *   <li>y que los dos son <b>idempotentes</b> (repetirlos no cambia nada).</li>
     * </ol>
     * Se llama al objetivo 3 (que NO está en el guardado) y al 5, para que ninguno venga ya revelado por la siembra.
     */
    private static void probarLosRevelados(ServerLevel level, FakePlayer pega,
                                           com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface aux) {
        // EL JUGADOR DE PEGA NO TRAE ANCLA (el arnés se la pasa por parámetro al latido, no vive en su capability):
        // se la ponemos, que es lo que hace el mod al entrar al mundo. Sin ancla, la PIEDRA no puede calcular dónde
        // cae la aldea y no revela nada — lo cazó la primera corrida de esta medida ("PIEDRA despues:
        // revelada(5)=false"), y por eso la piedra ahora deja un WARN en el log cuando le pasa.
        aux.setAnchorPoint(ancla(), pega);
        aux.setSpawnPoint(ancla(), pega);
        DevilRpg.LOGGER.info("[Arnes] ancla del jugador de pega puesta en la capability: {}", ancla());

        DevilRpg.LOGGER.info("[Arnes] BARRA oculta      -> {}",
                String.valueOf(com.chipoodle.devilrpg.survival.VillageBarText.texto(3, false, false, 1234, "->")));
        DevilRpg.LOGGER.info("[Arnes] BARRA revelada    -> {}",
                com.chipoodle.devilrpg.survival.VillageBarText.texto(3, false, true, 1234, "->"));
        DevilRpg.LOGGER.info("[Arnes] BARRA descubierta -> {}",
                com.chipoodle.devilrpg.survival.VillageBarText.texto(3, true, true, 12, "arriba"));

        // LA TABLA DE NOMBRES ENTERA (la del jugador, con su lore): verifica de una vez el ORDEN y, sobre todo, la
        // CODIFICACION — estas lineas llevan los acentos (Raíz, Rocío, Florumbría, Páramo, Lúgubria…) y se comprueban
        // luego a nivel de bytes en el log, que es lo unico que distingue un acento bien puesto de un destrozo.
        for (int i = 0; i < com.chipoodle.devilrpg.survival.VillageNames.cuantos() + 2; i++) {
            DevilRpg.LOGGER.info("[Arnes] NOMBRE aldea {} = \"{}\"", i,
                    com.chipoodle.devilrpg.survival.VillageNames.nombre(i));
        }

        // 1) EL CLERIGO AL VENCER EL ASEDIO de la aldea 2: tiene que revelar la 3 y hablar con nombre propio.
        aux.setObjectiveIndex(3, pega);
        DevilRpg.LOGGER.info("[Arnes] CLERIGO antes: revelada(3)={}", aux.isAldeaRevelada(3));
        try {
            VillageManager.elClerigoSenalaLaSiguiente(level, INDICE, CENTRO, pega);
        } catch (Exception e) {
            DevilRpg.LOGGER.warn("[Arnes] CLERIGO: el aviso al jugador de pega fallo ({})", e.toString());
        }
        DevilRpg.LOGGER.info("[Arnes] CLERIGO despues: revelada(3)={} barra=\"{}\"", aux.isAldeaRevelada(3),
                com.chipoodle.devilrpg.survival.VillageBarText.texto(3, aux.isAldeaVisitada(3),
                        aux.isAldeaRevelada(3), 1234, "->"));
        try {
            VillageManager.elClerigoSenalaLaSiguiente(level, INDICE, CENTRO, pega);
        } catch (Exception e) {
            DevilRpg.LOGGER.warn("[Arnes] CLERIGO (2a vez): {}", e.toString());
        }
        DevilRpg.LOGGER.info("[Arnes] CLERIGO otra vez: revelada(3)={} (idempotente)", aux.isAldeaRevelada(3));

        // 2) LA PIEDRA DE INVOCACION, con un objetivo que no venga revelado (el 5).
        aux.setObjectiveIndex(5, pega);
        DevilRpg.LOGGER.info("[Arnes] PIEDRA antes: revelada(5)={}", aux.isAldeaRevelada(5));
        try {
            com.chipoodle.devilrpg.block.LoreStoneBlock.revelarLaAldeaDeLaPiedra(pega);
        } catch (Exception e) {
            DevilRpg.LOGGER.warn("[Arnes] PIEDRA: el mensaje al jugador de pega fallo ({})", e.toString());
        }
        DevilRpg.LOGGER.info("[Arnes] PIEDRA despues: revelada(5)={} barra=\"{}\"", aux.isAldeaRevelada(5),
                com.chipoodle.devilrpg.survival.VillageBarText.texto(5, aux.isAldeaVisitada(5),
                        aux.isAldeaRevelada(5), 1234, "->"));
        // Y EL CASO CONTRARIO: la piedra con un objetivo que YA está revelado solo confirma (no cambia nada).
        try {
            com.chipoodle.devilrpg.block.LoreStoneBlock.revelarLaAldeaDeLaPiedra(pega);
        } catch (Exception e) {
            DevilRpg.LOGGER.warn("[Arnes] PIEDRA (2a vez): {}", e.toString());
        }
        aux.setObjectiveIndex(0, pega);
        DevilRpg.LOGGER.info("[Arnes] el escenario de revelados termina; el Diario NO cambia (solo lo visitado): {}",
                aux.getAldeasVisitadas());
    }

    /**
     * LA ETIQUETA ES SOLO DE LOS ALDEANOS DEL MOD (lo pidió el jugador: *"las villas normales (vanilla) tienen a sus
     * aldeanos con las mismas etiquetas que la villa de mi mod… eso sólo es para los aldeanos del mod"*). Se mide:
     * <ol>
     *   <li>cuántos aldeanos de la aldea 2 están marcados como del pueblo y cuántos llevan etiqueta;</li>
     *   <li>que a uno al que se le QUITA la marca (como un aldeano de vanilla) se le borre la etiqueta y no se le
     *       vuelva a poner, y que al devolvérsela la recupere;</li>
     *   <li>el estado de los aldeanos que andan LEJOS de las aldeas del mod (aldeas de vanilla) y qué pasa cuando el
     *       jugador pasa a su lado: la etiqueta que el mod les hubiera puesto tiene que desaparecer.</li>
     * </ol>
     */
    private static void probarLasEtiquetas(ServerLevel level, FakePlayer pega) {
        var cerca = level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(96.0D));
        int marcados = 0;
        int etiquetados = 0;
        for (Villager v : cerca) {
            if (VillageManager.esDelPueblo(v)) {
                marcados++;
            }
            if (v.getCustomName() != null) {
                etiquetados++;
            }
        }
        DevilRpg.LOGGER.info("[Arnes] ETIQUETAS aldea 2: {} aldeano(s), {} del pueblo (marcados), {} con etiqueta",
                cerca.size(), marcados, etiquetados);
        if (!cerca.isEmpty()) {
            Villager uno = cerca.get(0);
            DevilRpg.LOGGER.info("[Arnes] ETIQUETAS ejemplo ANTES: delPueblo={} etiqueta=\"{}\"",
                    VillageManager.esDelPueblo(uno), etiquetaDe(uno));
            uno.getPersistentData().putBoolean(VillageManager.DEL_PUEBLO_TAG, false);
            VillageManager.refrescarEtiquetas(level, pega);
            DevilRpg.LOGGER.info("[Arnes] ETIQUETAS ejemplo SIN la marca: delPueblo={} etiqueta=\"{}\" (tiene que"
                    + " quedar vacia)", VillageManager.esDelPueblo(uno), etiquetaDe(uno));
            uno.getPersistentData().putBoolean(VillageManager.DEL_PUEBLO_TAG, true);
            uno.getPersistentData().putLong("DevilRpgActividadTick", 0L);
            uno.getPersistentData().putBoolean(VillageManager.ACTIVIDAD_TAG, false);
            VillageManager.refrescarEtiquetas(level, pega);
            DevilRpg.LOGGER.info("[Arnes] ETIQUETAS ejemplo CON la marca: delPueblo={} etiqueta=\"{}\"",
                    VillageManager.esDelPueblo(uno), etiquetaDe(uno));
        }
        // Y LOS DE FUERA (aldeas de vanilla): cuántos hay, cuántos llevan etiqueta del mod (de antes del arreglo) y
        // qué pasa cuando el jugador se pone a su lado y se refrescan las etiquetas.
        Villager fuera = null;
        int cuantos = 0;
        int fueraEtiquetados = 0;
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(4000.0D))) {
            boolean deAlguna = false;
            for (int i = 0; i <= 3; i++) {
                BlockPos c = VillageManager.centroDe(level, i);
                if (c != null && c.distSqr(v.blockPosition()) < 200.0D * 200.0D) {
                    deAlguna = true;
                    break;
                }
            }
            if (deAlguna) {
                continue;
            }
            cuantos++;
            if (v.getCustomName() != null) {
                fueraEtiquetados++;
                if (fuera == null) {
                    fuera = v;
                }
            }
        }
        DevilRpg.LOGGER.info("[Arnes] ETIQUETAS FUERA de las aldeas del mod: {} aldeano(s), {} con etiqueta del mod"
                + " (de antes del arreglo)", cuantos, fueraEtiquetados);
        if (fuera != null) {
            DevilRpg.LOGGER.info("[Arnes] ETIQUETAS uno de fuera ANTES: pos={} delPueblo={} etiqueta=\"{}\"",
                    fuera.blockPosition(), VillageManager.esDelPueblo(fuera), etiquetaDe(fuera));
            pega.moveTo(fuera.getX(), fuera.getY(), fuera.getZ());
            VillageManager.refrescarEtiquetas(level, pega);
            DevilRpg.LOGGER.info("[Arnes] ETIQUETAS uno de fuera DESPUES de pasar el jugador: delPueblo={} etiqueta="
                    + "\"{}\" (tiene que quedar vacia)", VillageManager.esDelPueblo(fuera), etiquetaDe(fuera));
            pega.moveTo(CENTRO.getX() + 0.5D, CENTRO.getY(), CENTRO.getZ() + 0.5D);
        }
    }

    /** Ticks que lleva la ola a la vista (para limpiarla a los 10 s) y dónde está la aldea del asedio. */
    private static int ticksDeOlaVista = 0;
    private static BlockPos centroDelAsedio = null;

    /** EL ASEDIO DE VERDAD (I87): ver {@link #MEDIR_ASEDIO_VIVO}. */
    private static void medirElAsedioVivo(ServerLevel level, FakePlayer pega) {
        if (ticks == 300) {
            centroDelAsedio = com.chipoodle.devilrpg.survival.ObjectiveTargets.targetOf(ancla(), 3);
            // FORZAR LOS CHUNKS DE LA ALDEA 3: el jugador de pega NO carga chunks (no es un jugador de verdad) y sin
            // ellos la aldea se descarga a los pocos segundos. Medido en la primera corrida de este modo:
            // `aldeanos3=11` al generarse y `0` diez segundos después, con `agresivos=0` — la ola no llegaba a
            // spawnear porque su trozo de mundo no estaba cargado. Se fuerzan los mismos 13x13 que ya se fuerzan en
            // la aldea 2 en `preparar`.
            int cx3 = centroDelAsedio.getX() >> 4;
            int cz3 = centroDelAsedio.getZ() >> 4;
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    level.setChunkForced(cx3 + dx, cz3 + dz, true);
                }
            }
            com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface aux =
                    com.chipoodle.devilrpg.capability.IGenericCapability.getUnwrappedPlayerCapability(
                            pega, com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability.INSTANCE);
            if (aux != null) {
                aux.setAnchorPoint(ancla(), pega);
                aux.setSpawnPoint(ancla(), pega);
                // El asedio de la 3 tiene que ser "el actual", o el clérigo no revela: el aviso es para el jugador
                // que lo está viviendo.
                aux.setObjectiveIndex(3, pega);
            }
            DevilRpg.LOGGER.info("[Arnes] ASEDIO: arrancando el asedio de la aldea 3 en {} (se genera ahora; las"
                    + " otras tres de esa partida ya estan resueltas)", centroDelAsedio);
            VillageManager.start(level, pega, 3, centroDelAsedio);
            DevilRpg.LOGGER.info("[Arnes] ASEDIO: arrancado, hayAsedio(3)={}", VillageManager.hayAsedio(level, 3));
        }
        if (centroDelAsedio == null) {
            return;
        }
        // El jugador de pega, DENTRO de la aldea 3: si no, el reloj del asedio se queda EN PAUSA (I86) y no se
        // resolveria nunca. Y se gestiona hasta el objetivo 3, que es el que se esta asediando.
        pega.moveTo(centroDelAsedio.getX() + 0.5D, centroDelAsedio.getY() + 1.0D, centroDelAsedio.getZ() + 0.5D);
        VillageManager.manageNearby(level, pega, ancla(), 3);
        int bichos = level.getEntitiesOfClass(com.chipoodle.devilrpg.entity.AggressiveZombieEntity.class,
                new AABB(centroDelAsedio).inflate(150.0D)).size();
        if (bichos > 0) {
            ticksDeOlaVista++;
        }
        if (ticks % 100 == 0) {
            com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface aux =
                    com.chipoodle.devilrpg.capability.IGenericCapability.getUnwrappedPlayerCapability(
                            pega, com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability.INSTANCE);
            DevilRpg.LOGGER.info("[Arnes] ASEDIO t={} agresivos={} hayAsedio(3)={} revelada(4)={} aldeanos3={}",
                    ticks, bichos, VillageManager.hayAsedio(level, 3),
                    aux != null && aux.isAldeaRevelada(4),
                    level.getEntitiesOfClass(Villager.class, new AABB(centroDelAsedio).inflate(96.0D)).size());
        }
        // A LOS 10 s DE VER LA OLA, SE LIMPIA DESDE EL ARNES (ver el javadoc de MEDIR_ASEDIO_VIVO).
        if (bichos > 0 && ticksDeOlaVista == 200) {
            DevilRpg.LOGGER.info("[Arnes] ASEDIO: limpiando la ola ({} agresivo(s)) desde el arnes: el asedio tiene"
                    + " que resolverse SALVADO y el clerigo revelar la aldea 4", bichos);
            for (com.chipoodle.devilrpg.entity.AggressiveZombieEntity z
                    : level.getEntitiesOfClass(com.chipoodle.devilrpg.entity.AggressiveZombieEntity.class,
                    new AABB(centroDelAsedio).inflate(150.0D))) {
                z.hurt(level.damageSources().generic(), Float.MAX_VALUE);
            }
        }
    }

    /**
     * Vuelca el estado del descubrimiento y el revelado (I87), lo que dibujaría la barra de aldea, el rumbo a la
     * siguiente y las LÍNEAS DEL DIARIO tal cual las manda el objeto al usarlo.
     */
    private static void volcarAldeas(ServerLevel level, FakePlayer pega) {
        com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface aux =
                com.chipoodle.devilrpg.capability.IGenericCapability.getUnwrappedPlayerCapability(
                        pega, com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability.INSTANCE);
        if (aux == null) {
            DevilRpg.LOGGER.info("[Arnes] ALDEAS: el jugador de pega no tiene capability (no se puede medir)");
            return;
        }
        int indice = aux.getObjectiveIndex();
        DevilRpg.LOGGER.info("[Arnes] ALDEAS indice={} visitadas={} reveladas={}",
                indice, aux.getAldeasVisitadas(), aux.getAldeasReveladas());
        if (!escenarioDeReveladosHecho) {
            escenarioDeReveladosHecho = true;
            probarLosRevelados(level, pega, aux);
            probarLasEtiquetas(level, pega);
        }
        for (int i = 0; i <= indice; i++) {
            DevilRpg.LOGGER.info("[Arnes] ALDEA {} nombre=\"{}\" visitada={} revelada={} centro={} estado=\"{}\"",
                    i, com.chipoodle.devilrpg.survival.VillageNames.nombre(i),
                    aux.isAldeaVisitada(i), aux.isAldeaRevelada(i), VillageManager.centroDe(level, i),
                    VillageManager.estadoDeLaAldea(level, i));
        }
        // LO QUE DIBUJARIA LA BARRA DE ALDEA: la aldea ACTUAL, la siguiente y una que NO esté en el guardado (el caso
        // que importa de verdad: la que viene DESPUÉS de una que cayó tiene que salir OCULTA hasta que la piedra o un
        // clérigo la revelen — es lo que pidió el jugador).
        for (int i : new int[]{indice, indice + 1, indice + 3}) {
            boolean vis = aux.isAldeaVisitada(i);
            boolean rev = aux.isAldeaRevelada(i);
            DevilRpg.LOGGER.info("[Arnes] BARRA DE ALDEA (aldea {}): {}", i, !vis && !rev
                    ? "OCULTA (ni direccion ni nombre: hay que leer la piedra o ganar un asedio)"
                    : (vis
                            ? "con NOMBRE: \"" + com.chipoodle.devilrpg.survival.VillageNames.nombre(i) + "\""
                            : "sin nombre: \"Aldea\""));
        }
        BlockPos siguiente = com.chipoodle.devilrpg.survival.ObjectiveTargets.targetOf(ancla(), indice + 1);
        DevilRpg.LOGGER.info("[Arnes] RUMBO a la aldea {}: {}", indice + 1,
                com.chipoodle.devilrpg.survival.ObjectiveTargets.direccionHacia(CENTRO, siguiente));
        for (net.minecraft.network.chat.Component linea
                : com.chipoodle.devilrpg.item.DiarioDelInvocadoItem.lineasDelDiario(level, pega)) {
            DevilRpg.LOGGER.info("[Arnes] DIARIO: {}", linea.getString());
        }
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
    /**
     * Radio del censo de camas: el MOD reparte camas a los aldeanos dentro de {@code FALLEN_CHECK_RADIUS}
     * ({@code FENCE_RADIUS + 44} = 106), así que el arnés tiene que censar lo mismo. Con 64 (lo que medía antes) los
     * aldeanos que están más lejos de la plaza —justo los que se quedan sin cama— no salían en el recuento y el
     * resumen decía "SIN CAMA=0" con el jugador viendo "Sin cama" encima de Mauricio.
     */
    private static final double RADIO_CENSO = com.chipoodle.devilrpg.world.VillageGenerator.FENCE_RADIUS + 44.0;

    private static void volcarCamas(ServerLevel level) {
        DevilRpg.LOGGER.info("[Arnes] CAMAS: dayTime={} (franja {}) · UN BICHO DENTRO: {}",
                level.getDayTime() % 24000, level.getDayTime() % 24000 >= 12000 ? "DESCANSO" : "dia",
                estadoDelRecinto(level));
        // EL RESUMEN (el criterio de "arreglado"): cuantos aldeanos tienen cama, cuantos COMPARTEN cama (dos aldeanos
        // con la misma cama: la mitad de la misma cama o la misma casilla) y quien se queda SIN cama. SE CENSAN
        // TAMBIEN LAS CRIAS: el reparto las incluia en el debe (Mauricio y Leoncio eran crias con "Sin cama" encima) y
        // con el censo solo de adultos el resumen decia "SIN CAMA=0" con el jugador viendo lo contrario.
        int adultos = 0;
        int crias = 0;
        int conCama = 0;
        int durmiendo = 0;
        java.util.Map<String, java.util.List<String>> porCama = new java.util.TreeMap<>();
        java.util.List<String> sinCama = new java.util.ArrayList<>();
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(RADIO_CENSO))) {
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
            if (v.isBaby()) {
                crias++;
            } else {
                adultos++;
            }
            if (v.isSleeping()) {
                durmiendo++;
            }
            if (home.isPresent()) {
                conCama++;
                porCama.computeIfAbsent(claveDeLaCama(level, home.get().pos()), k -> new java.util.ArrayList<>())
                        .add(uuid);
            } else {
                sinCama.add(uuid + "(" + str(v.getVillagerData().getProfession())
                        + (v.isBaby() ? ",cria" : "") + ")");
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
            DevilRpg.LOGGER.info("[Arnes] CAMA {} nombre={} prof={} home={}{} durmiendo={} REST={} WORK={} MEET={} pos={}",
                    uuid, v.getCustomName() == null ? "-" : v.getCustomName().getString().replace("\n", " | "),
                    str(v.getVillagerData().getProfession()),
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
            DevilRpg.LOGGER.info("[Arnes] CAMAS RESUMEN: aldeanos={} (adultos={} crias={}) conCama={} (camas distintas"
                            + " ocupadas={}) COMPARTIDAS={}{} SIN CAMA={}{} DURMIENDO={} · UN BICHO DENTRO: {}",
                    adultos + crias, adultos, crias, conCama, ok, porCama.size() - ok, compartidas, sinCama.size(),
                    sinCama.isEmpty() ? "" : " " + String.join(" ", sinCama), durmiendo, estadoDelRecinto(level));
            // Y POR QUE NO LE DAN CAMA: para el primer aldeano sin cama, las 8 camas libres mas cercanas con el
            // motivo por el que la reclamacion las descarta (o la acepta).
            for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(RADIO_CENSO))) {
                if (v.getBrain().hasMemoryValue(MemoryModuleType.HOME)) {
                    continue;
                }
                DevilRpg.LOGGER.info("[Arnes] SIN CAMA {} {} nombre={} pos={} (dist a la plaza {})", uuid8(v),
                        str(v.getVillagerData().getProfession()),
                        v.getCustomName() == null ? "-" : v.getCustomName().getString().replace("\n", " | "),
                        v.blockPosition().toShortString(),
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
            // LAS PUERTAS ABIERTAS DEL PUEBLO (lo pidio el jugador: "los aldeanos cuando vayan a dormir tienen que
            // cerrar la puerta porque todas la dejan abierta").
            volcarPuertas(level);
        }
    }

    private static String uuid8(Villager v) {
        return v.getUUID().toString().substring(0, 8);
    }

    /**
     * LAS PUERTAS DE MADERA ABIERTAS del pueblo (la mitad de abajo de cada una). Lo que se busca con el arreglo es que
     * el numero <b>BAJE</b>: los aldeanos cierran la que cruzan (`VillagerDoorGoal`). Las que estan abiertas y nadie
     * cruza no se tocan (pueden ser del jugador), asi que el numero no tiene por que llegar a cero.
     */
    private static void volcarPuertas(ServerLevel level) {
        StringBuilder abiertas = new StringBuilder();
        int cuantas = 0;
        int atrapados = 0;
        java.util.Set<Long> ahora = new java.util.HashSet<>();
        for (BlockPos q : BlockPos.betweenClosed(CENTRO.offset(-56, -8, -56), CENTRO.offset(56, 12, 56))) {
            var est = level.getBlockState(q);
            if (!est.is(net.minecraft.tags.BlockTags.WOODEN_DOORS)) {
                continue;
            }
            boolean abajo = est.getValue(net.minecraft.world.level.block.DoorBlock.HALF)
                    == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER;
            if (!est.getValue(net.minecraft.world.level.block.DoorBlock.OPEN)) {
                // PUERTA CERRADA CON ALGUIEN DENTRO: es el bug que reportó el jugador ("un aldeano lo movió y empezó a
                // caminar erráticamente"): el aldeano queda atrapado contra la caja de colisión de la puerta cerrada.
                // OJO con el criterio: NO vale "la caja de la entidad TOCA la celda" (un aldeano en la celda de al
                // lado roza la puerta con el hombro y daría un falso positivo): se cuenta el CENTRO de la entidad
                // DENTRO de la celda de la puerta, que es estar de verdad en el hueco.
                if (abajo) {
                    for (net.minecraft.world.entity.Entity e : level.getEntitiesOfClass(
                            net.minecraft.world.entity.Entity.class, new AABB(q).inflate(1.0D, 0.5D, 1.0D))) {
                        var p = e.position();
                        boolean dentro = p.x >= q.getX() && p.x < q.getX() + 1.0D
                                && p.z >= q.getZ() && p.z < q.getZ() + 1.0D
                                && p.y >= q.getY() - 0.2D && p.y <= q.getY() + 2.0D;
                        if (dentro) {
                            atrapados++;
                            DevilRpg.LOGGER.info("[Arnes] PUERTA CERRADA CON ALGUIEN DENTRO en {}: {} pos=({},{},{})"
                                            + " velocidad={}",
                                    q.toShortString(), e.getType().toShortString(), fmt(p.x), fmt(p.y), fmt(p.z),
                                    fmt(e.getDeltaMovement().horizontalDistance()));
                        }
                    }
                }
                continue;
            }
            if (abajo) {
                cuantas++;
                ahora.add(q.asLong());
                if (cuantas <= 8) {
                    abiertas.append(' ').append(q.toShortString());
                }
            }
        }
        // Y LAS QUE SE HAN CERRADO desde el barrido anterior, con el aldeano que tenia al lado: es la prueba de que
        // las cierra el pueblo (`VillagerDoorGoal`).
        for (long antes : puertasAbiertasAnteriores) {
            if (ahora.contains(antes)) {
                continue;
            }
            BlockPos p = BlockPos.of(antes);
            StringBuilder quien = new StringBuilder();
            for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(p).inflate(4.0))) {
                quien.append(' ').append(uuid8(v));
                // Y SU ETIQUETA: el que la ha cerrado la tiene puesta ("Cerrando la puerta": la escribe el goal), asi
                // que esto dice QUIEN la cerro y no solo quien andaba cerca.
                if (v.getCustomName() != null) {
                    quien.append('(').append(v.getCustomName().getString().replace("\n", " | ")).append(')');
                }
            }
            DevilRpg.LOGGER.info("[Arnes] PUERTA CERRADA en {} (aldeano(s) al lado:{})", p.toShortString(), quien);
        }
        puertasAbiertasAnteriores = ahora;
        DevilRpg.LOGGER.info("[Arnes] PUERTAS DE MADERA ABIERTAS en el pueblo: {}{} (cerradas CON alguien dentro: {})",
                cuantas, abiertas, atrapados);
    }

    /** Las puertas abiertas del barrido anterior (para cantar las que se cierran). */
    private static java.util.Set<Long> puertasAbiertasAnteriores = new java.util.HashSet<>();

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
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(RADIO_CENSO))) {
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

