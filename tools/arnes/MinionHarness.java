package com.chipoodle.devilrpg.debug;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityInterface;
import com.chipoodle.devilrpg.entity.SoulWolf;
import com.chipoodle.devilrpg.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Objects;
import java.util.UUID;

/**
 * ARNES TEMPORAL DE DIAGNOSTICO (no se queda en el mod).
 * <p>
 * Mide la <b>despedida de invocaciones con un palo</b> (click izquierdo con palo sobre una invocacion TUYA): se
 * invoca un lobo de alma de verdad, se le mete en la lista del jugador, se le da un palo al jugador y se le pega; y se
 * comprueban los tres casos que NO tienen que despedir nada (espada, invocacion de otro, mano vacia).
 * <p>
 * Al terminar, para el servidor solo (la medida queda en el log).
 */
@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.GAME)
public class MinionHarness {

    private static int ticks = 0;
    private static boolean hecho = false;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ticks++;
        if (hecho || ticks < 60) {
            return; // 3 s: el mundo ya esta cargado
        }
        hecho = true;
        probar(event.getServer());
        event.getServer().halt(false); // el arnes no se queda corriendo: se mide y se para
    }

    private static void probar(MinecraftServer server) {
        ServerLevel level = server.overworld();
        FakePlayer jugador = FakePlayerFactory.getMinecraft(level);
        PlayerMinionCapabilityInterface cap =
                IGenericCapability.getUnwrappedPlayerCapability(jugador, PlayerMinionCapability.INSTANCE);

        // 1) PALO + INVOCACION TUYA: tiene que irse del mundo Y de la lista.
        SoulWolf mia = invocar(level, jugador, true);
        anotar(cap, jugador, mia);
        conLaMano(jugador, new ItemStack(Items.STICK));
        jugador.attack(mia);
        informe("CASO 1 palo + mia", mia, cap, true);

        // 2) ESPADA + INVOCACION TUYA: NO se despide (se pelea como siempre).
        SoulWolf conEspada = invocar(level, jugador, true);
        conLaMano(jugador, new ItemStack(Items.IRON_SWORD));
        jugador.attack(conEspada);
        informe("CASO 2 espada + mia", conEspada, cap, false);

        // 3) PALO + INVOCACION DE OTRO: NO se toca.
        SoulWolf ajena = invocar(level, jugador, false);
        conLaMano(jugador, new ItemStack(Items.STICK));
        jugador.attack(ajena);
        informe("CASO 3 palo + ajena", ajena, cap, false);

        // 4) PALO EN LA MANO SECUNDARIA (mano principal vacia) + INVOCACION TUYA: NO se despide (solo la principal).
        SoulWolf secundaria = invocar(level, jugador, true);
        anotar(cap, jugador, secundaria);
        conLaMano(jugador, ItemStack.EMPTY);
        jugador.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK));
        jugador.attack(secundaria);
        informe("CASO 4 palo solo en la secundaria + mia", secundaria, cap, false);

        // Limpieza de la prueba (no se queda nada por el mundo).
        for (SoulWolf lobo : new SoulWolf[]{conEspada, ajena, secundaria, mia}) {
            if (lobo != null && lobo.isAlive()) {
                lobo.discard();
            }
        }
        DevilRpg.LOGGER.info("[ArnesPalo] fin de la medida");
    }

    private static void informe(String caso, SoulWolf lobo, PlayerMinionCapabilityInterface cap, boolean debeIrse) {
        boolean enElMundo = lobo != null && lobo.isAlive() && !lobo.isRemoved();
        boolean enLaLista = lobo != null && cap.getSoulWolfMinions().contains(lobo.getUUID());
        boolean correcto = debeIrse ? (!enElMundo && !enLaLista) : enElMundo;
        DevilRpg.LOGGER.info("[ArnesPalo] {}: se espera {} -> enElMundo={} enLaLista={} => {}",
                caso, debeIrse ? "QUE SE VAYA" : "QUE SE QUEDE", enElMundo ? "SI" : "NO", enLaLista ? "SI" : "NO",
                correcto ? "OK" : "FALLO");
    }

    private static void conLaMano(FakePlayer jugador, ItemStack mano) {
        jugador.setItemInHand(InteractionHand.MAIN_HAND, mano);
        jugador.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    }

    private static void anotar(PlayerMinionCapabilityInterface cap, FakePlayer dueno, SoulWolf lobo) {
        cap.getSoulWolfMinions().offer(lobo.getUUID());
        cap.setSoulWolfMinions(cap.getSoulWolfMinions(), dueno);
    }

    private static SoulWolf invocar(ServerLevel level, FakePlayer dueno, boolean mio) {
        BlockPos pos = level.getSharedSpawnPos().above(2);
        SoulWolf lobo = ModEntities.SOUL_WOLF.get().create(level, null, pos, MobSpawnType.MOB_SUMMONED, true, true);
        Objects.requireNonNull(lobo);
        if (mio) {
            lobo.tame(dueno); // como la invocacion de verdad: dueño y tameado
        } else {
            lobo.setOwnerUUID(UUID.randomUUID()); // de otro jugador (para el caso 3)
        }
        lobo.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(lobo);
        return lobo;
    }
}
