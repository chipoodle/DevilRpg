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
 * Mide la <b>despedida de invocaciones con un palo</b>: tiene que irse con el <b>BOTON SECUNDARIO</b> (click derecho
 * sobre ella, el palo en cualquier mano) y el <b>golpe (boton izquierdo) tiene que quedarse COMO ESTABA</b> (pegarle a
 * una invocacion tuya no le hace nada).
 * <p>
 * Invoca un lobo de alma DE VERDAD, lo mete en la lista del jugador y prueba los cinco casos. Al terminar, para el
 * servidor solo (la medida queda en el log).
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

        // 1) BOTON SECUNDARIO con el palo en la MANO PRINCIPAL + invocacion TUYA: se va.
        SoulWolf conPalo = invocar(level, jugador, true);
        anotar(cap, jugador, conPalo);
        conLasManos(jugador, new ItemStack(Items.STICK), ItemStack.EMPTY);
        jugador.interactOn(conPalo, InteractionHand.MAIN_HAND);
        informe("CASO 1 secundario + palo en la principal + mia", conPalo, cap, true);

        // 2) BOTON SECUNDARIO con el palo en la MANO SECUNDARIA + invocacion TUYA: tambien se va.
        SoulWolf conPaloManoMala = invocar(level, jugador, true);
        anotar(cap, jugador, conPaloManoMala);
        conLasManos(jugador, ItemStack.EMPTY, new ItemStack(Items.STICK));
        jugador.interactOn(conPaloManoMala, InteractionHand.OFF_HAND);
        informe("CASO 2 secundario + palo en la secundaria + mia", conPaloManoMala, cap, true);

        // 3) BOTON SECUNDARIO SIN palo (espada) + invocacion TUYA: NO se despide.
        SoulWolf conEspada = invocar(level, jugador, true);
        conLasManos(jugador, new ItemStack(Items.IRON_SWORD), ItemStack.EMPTY);
        jugador.interactOn(conEspada, InteractionHand.MAIN_HAND);
        informe("CASO 3 secundario + espada + mia", conEspada, cap, false);

        // 4) BOTON SECUNDARIO con palo + invocacion DE OTRO: NO se toca.
        SoulWolf ajena = invocar(level, jugador, false);
        conLasManos(jugador, new ItemStack(Items.STICK), ItemStack.EMPTY);
        jugador.interactOn(ajena, InteractionHand.MAIN_HAND);
        informe("CASO 4 secundario + palo + ajena", ajena, cap, false);

        // 5) BOTON IZQUIERDO (golpe) con palo + invocacion TUYA: tiene que quedarse COMO ESTABA (no se despide).
        SoulWolf golpeada = invocar(level, jugador, true);
        anotar(cap, jugador, golpeada);
        conLasManos(jugador, new ItemStack(Items.STICK), ItemStack.EMPTY);
        jugador.attack(golpeada);
        informe("CASO 5 izquierdo (golpe) + palo + mia", golpeada, cap, false);

        for (SoulWolf lobo : new SoulWolf[]{conPalo, conPaloManoMala, conEspada, ajena, golpeada}) {
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

    private static void conLasManos(FakePlayer jugador, ItemStack principal, ItemStack secundaria) {
        jugador.setItemInHand(InteractionHand.MAIN_HAND, principal);
        jugador.setItemInHand(InteractionHand.OFF_HAND, secundaria);
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
            lobo.setOwnerUUID(UUID.randomUUID()); // de otro jugador (para el caso 4)
        }
        lobo.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(lobo);
        return lobo;
    }
}
