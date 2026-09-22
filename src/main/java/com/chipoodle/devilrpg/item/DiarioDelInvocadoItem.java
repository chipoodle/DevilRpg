package com.chipoodle.devilrpg.item;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.survival.ObjectiveTargets;
import com.chipoodle.devilrpg.survival.VillageNames;
import com.chipoodle.devilrpg.world.VillageManager;
import com.chipoodle.devilrpg.world.VillageSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <b>DIARIO DEL INVOCADO</b>: el cuaderno donde el jugador apunta las <b>aldeas que ha descubierto</b> —solo las que
 * ha <b>entrado</b> de verdad— con su <b>nombre</b>, sus <b>coordenadas</b> y su <b>estado</b> (viva, sellada o en
 * ruinas), más a qué distancia y hacia dónde le caen.
 * <p>
 * Lo pidió el jugador: *"Estaría bien que la piedra de invocación [dé] un libro o algo que vaya guardando las aldeas
 * descubiertas (sólo las que uno ya haya entrado) junto con su estatus y sus coordenadas"*, y de hecho lo necesitaba
 * para algo muy concreto: *"el problema es que no tengo las coordenadas para poder regresar"*.
 * <p>
 * Es de <b>solo lectura</b> y no se gasta: la lista se arma en el momento de usarlo (el descubrimiento vive en la
 * capability del jugador y el estado en {@link VillageSavedData}), así que nunca se queda desfasado — no hay que
 * "actualizarlo". Se obtiene de la <b>piedra de invocación</b> (su primera lectura) y también está en la pestaña del
 * mod para quien lo pierda.
 */
public class DiarioDelInvocadoItem extends Item {

    public DiarioDelInvocadoItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            mostrarElDiario(serverLevel, serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    /** Escribe en el chat lo que el jugador lleva apuntado, aldea por aldea. */
    private void mostrarElDiario(ServerLevel level, ServerPlayer player) {
        PlayerAuxiliaryCapabilityInterface aux =
                IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
        if (aux == null) {
            return;
        }
        List<Integer> visitadas = new ArrayList<>(aux.getAldeasVisitadas());
        Collections.sort(visitadas);
        player.displayClientMessage(Component.literal("— Diario del Invocado —").withStyle(ChatFormatting.GOLD), false);
        if (visitadas.isEmpty()) {
            player.displayClientMessage(Component.literal(
                    "Todavía no has entrado en ninguna aldea. La runa de la piedra de invocación marca la primera."),
                    false);
            return;
        }
        VillageSavedData saved = VillageSavedData.get(level);
        for (int i : visitadas) {
            BlockPos centro = VillageManager.centroDe(level, i);
            String coords = centro != null
                    ? "(" + centro.getX() + ", " + centro.getZ() + ")"
                    : "(sin plano guardado)";
            String estado;
            if (saved.isFallen(i)) {
                estado = "EN RUINAS";
            } else if (saved.isSiegeResolved(i)) {
                estado = "a salvo, con el sello puesto";
            } else {
                estado = "viva, sin socorrer";
            }
            String rumbo = "";
            if (centro != null) {
                double dx = centro.getX() + 0.5D - player.getX();
                double dz = centro.getZ() + 0.5D - player.getZ();
                int metros = (int) Math.sqrt(dx * dx + dz * dz);
                rumbo = " · a " + metros + " m hacia el " + ObjectiveTargets.direccionHacia(player.blockPosition(), centro);
            }
            ChatFormatting color = saved.isFallen(i) ? ChatFormatting.DARK_RED : ChatFormatting.YELLOW;
            player.displayClientMessage(Component.literal(VillageNames.nombre(i) + "  " + coords + " — " + estado + rumbo)
                    .withStyle(color), false);
        }
        player.displayClientMessage(Component.literal(
                "(" + visitadas.size() + " aldea(s) apuntada(s); la barra de aldea te guía a la que toca ahora)"),
                false);
    }
}
