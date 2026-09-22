package com.chipoodle.devilrpg.item;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.survival.ObjectiveTargets;
import com.chipoodle.devilrpg.survival.VillageNames;
import com.chipoodle.devilrpg.world.VillageManager;
import com.chipoodle.devilrpg.world.VillageSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <b>EL DIARIO DEL INVOCADO</b>: el cuaderno donde el jugador apunta las aldeas que ha descubierto —solo las que ha
 * <b>entrado</b> de verdad— con su nombre, sus coordenadas, su estado y a qué distancia y hacia dónde le caen.
 * <p>
 * Y es un <b>LIBRO DE VERDAD</b>: un libro escrito de los del juego, así que al usarlo se abre la <b>interfaz de
 * libro</b> normal (con sus páginas, su título y su autor) en vez de escupir el texto por el chat. Lo pidió el
 * jugador: *"El libro del invocado debe ser un libro que pueda leer, es decir que abra la interfaz de libro que tiene
 * el juego, no que cuando le dé click aparezca en el chat lo que dice; eso no se ve natural"*.
 * <p>
 * Como el contenido depende del estado de las aldeas (que cambia), las páginas se <b>reescriben</b> cada vez que se
 * abre (ver {@code CommonForgeInteractionEventSubscriber.onAbrirElDiario}) y también al <b>salvar una aldea</b>
 * ({@link #actualizarSiLoTiene}), así que el libro nunca enseña algo viejo. Se reconoce por una marca en sus datos
 * ({@link #MARCA}), no por su nombre, para que renombrarlo no rompa nada.
 */
public final class DiarioDelInvocado {

    /** Título del libro (lo que se ve en el lomo y arriba en la pantalla del libro). */
    public static final String TITULO = "Diario del Invocado";
    /** Autor que sale en la pantalla del libro. */
    public static final String AUTOR = "Los clérigos";
    /** Marca en los datos del objeto: así se sabe que ese libro escrito es NUESTRO diario. */
    private static final String MARCA = "diario_del_invocado";
    /** Líneas por página (una página de libro del juego son 14; se dejan un par de aire). */
    private static final int LINEAS_POR_PAGINA = 12;

    private DiarioDelInvocado() {
    }

    /** ¿Ese objeto es el Diario del Invocado? (libro escrito + nuestra marca en los datos) */
    public static boolean esElDiario(ItemStack stack) {
        return stack.is(Items.WRITTEN_BOOK)
                && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains(MARCA);
    }

    /** Un diario nuevo, ya escrito con lo que el jugador sabe ahora mismo. */
    public static ItemStack crear(ServerPlayer player) {
        ItemStack libro = new ItemStack(Items.WRITTEN_BOOK);
        actualizar(libro, player);
        return libro;
    }

    /** Reescribe las páginas del libro con lo que el jugador sabe AHORA (nombre, coordenadas, estado y rumbo). */
    public static void actualizar(ItemStack libro, ServerPlayer player) {
        List<Component> lineas = lineas(player);
        List<Filterable<Component>> paginas = new ArrayList<>();
        MutableComponent pagina = Component.literal("");
        int enLaPagina = 0;
        for (Component linea : lineas) {
            if (enLaPagina == LINEAS_POR_PAGINA) {
                paginas.add(Filterable.passThrough(pagina));
                pagina = Component.literal("");
                enLaPagina = 0;
            }
            pagina.append(linea).append("\n");
            enLaPagina++;
        }
        paginas.add(Filterable.passThrough(pagina));
        libro.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(Filterable.passThrough(TITULO), AUTOR, 0, paginas, true));
        libro.set(DataComponents.CUSTOM_DATA, CustomData.of(marca()));
        libro.set(DataComponents.CUSTOM_NAME, Component.literal(TITULO).withStyle(ChatFormatting.GOLD));
    }

    /**
     * Pone al día el diario si el jugador lo lleva encima (si no lo lleva, no se hace nada). Lo llama el pueblo al
     * <b>salvarse una aldea</b>: el jugador pidió que eso se refleje en el libro.
     */
    public static void actualizarSiLoTiene(ServerPlayer player) {
        boolean tocado = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (esElDiario(stack)) {
                actualizar(stack, player);
                tocado = true;
            }
        }
        if (tocado) {
            player.inventoryMenu.broadcastChanges(); // que el cliente vea el libro al día
        }
    }

    /**
     * Manda al cliente el hueco de la mano: hace falta porque el libro se reescribe en el SERVIDOR y el cliente es
     * quien abre la pantalla con <b>su</b> copia del objeto; sin esto, se abriría con las páginas viejas.
     */
    public static void sincronizarLaMano(ServerPlayer player) {
        int hueco = 36 + player.getInventory().selected;
        player.connection.send(new ClientboundContainerSetSlotPacket(player.inventoryMenu.containerId,
                player.inventoryMenu.getStateId(), hueco,
                player.getInventory().getItem(player.getInventory().selected)));
    }

    private static net.minecraft.nbt.CompoundTag marca() {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putBoolean(MARCA, true);
        return tag;
    }

    /**
     * Las líneas del diario, ya montadas (nombre, coordenadas, estado, distancia y rumbo de cada aldea descubierta).
     * Es {@code public static} a propósito: el <b>arnés</b> las mide tal cual salen —sin abrir el juego— y el libro
     * escribe exactamente estas mismas, así que lo que se mide es lo que el jugador lee.
     */
    public static List<Component> lineas(ServerPlayer player) {
        List<Component> lineas = new ArrayList<>();
        PlayerAuxiliaryCapabilityInterface aux =
                IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
        if (aux == null) {
            return lineas;
        }
        var level = player.serverLevel();
        List<Integer> visitadas = new ArrayList<>(aux.getAldeasVisitadas());
        Collections.sort(visitadas);
        if (visitadas.isEmpty()) {
            lineas.add(Component.literal("Todavía no has entrado en ninguna aldea. La runa de la piedra de invocación"
                    + " marca la primera."));
            return lineas;
        }
        VillageSavedData saved = VillageSavedData.get(level);
        lineas.add(Component.literal("Aldeas que has descubierto:").withStyle(ChatFormatting.GOLD));
        for (int i : visitadas) {
            BlockPos centro = VillageManager.centroDe(level, i);
            String coords = centro != null
                    ? "(" + centro.getX() + ", " + centro.getZ() + ")"
                    : "(sin plano guardado)";
            // El estado lo dice VillageManager.estadoDeLaAldea: la regla en un solo sitio (Diario y arnés).
            String estado = VillageManager.estadoDeLaAldea(level, i);
            String rumbo = "";
            if (centro != null) {
                double dx = centro.getX() + 0.5D - player.getX();
                double dz = centro.getZ() + 0.5D - player.getZ();
                int metros = (int) Math.sqrt(dx * dx + dz * dz);
                rumbo = " · a " + metros + " m hacia el "
                        + ObjectiveTargets.direccionHacia(player.blockPosition(), centro);
            }
            ChatFormatting color = saved.isFallen(i) ? ChatFormatting.DARK_RED : ChatFormatting.YELLOW;
            lineas.add(Component.literal(VillageNames.nombre(i)).withStyle(color, ChatFormatting.BOLD));
            lineas.add(Component.literal(coords + " — " + estado + rumbo));
        }
        lineas.add(Component.literal("(" + visitadas.size() + " aldea(s); la barra de aldea te guía a la que toca)"));
        return lineas;
    }

    /** Aviso en el log de que el jugador ha abierto el libro (para poder seguirle la pista en el log). */
    public static void registrarLaConsulta(ServerPlayer player) {
        DevilRpg.LOGGER.info("[Diario] {} ha abierto el Diario del Invocado", player.getName().getString());
    }
}
