package com.chipoodle.devilrpg.block;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>COMANDO DE MEDIDA DE LOS PORTONES</b> (`/porton escanear [radio]`), 6-oct-2026.
 * <p>
 * Existe porque el jugador reportó que el portón <b>no se ve ni se comporta como una pieza</b> (*«son más de 3 bloques,
 * parece como si fueran varias puertas y cuando le doy click no se abre»*) y desde fuera no hay forma de saber qué hay
 * puesto de verdad: se puede estar viendo un portón viejo de una versión anterior, o uno a medio construir. Esto lo
 * <b>mide en su propia partida</b> y lo imprime en el chat y en el registro, sin adivinar nada.
 * <p>
 * Informa, por cada portón que encuentre: <b>cuántas celdas tiene</b> (tienen que ser <b>9</b>: 3 de ancho por 3 de
 * alto), su orientación, si las celdas están <b>completas</b>, cuántas tienen la marca de <b>juntura</b>, y sobre todo
 * si <b>están abiertas o cerradas</b> — que es lo que dice si el golpe de click está llegando al bloque.
 */
@EventBusSubscriber(modid = DevilRpg.MODID)
public final class PortonDiagnosticoCommand {

    private PortonDiagnosticoCommand() {
    }

    @SubscribeEvent
    public static void registrar(final RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("porton")
                .then(Commands.literal("escanear")
                        .executes(c -> escanear(c, 8))
                        .then(Commands.argument("radio", IntegerArgumentType.integer(1, 32))
                                .executes(c -> escanear(c, IntegerArgumentType.getInteger(c, "radio")))))
                .then(Commands.literal("abrir")
                        .executes(c -> abatirTodos(c, true)))
                .then(Commands.literal("cerrar")
                        .executes(c -> abatirTodos(c, false))));
    }

    private static int escanear(CommandContext<CommandSourceStack> ctx, int radio) {
        CommandSourceStack fuente = ctx.getSource();
        ServerLevel nivel = fuente.getLevel();
        BlockPos centro = BlockPos.containing(fuente.getPosition());
        AABB caja = new AABB(centro).inflate(radio);

        // Se agrupan las celdas por portón: por su orientación y por su columna (la celda de ABAJO de cada hoja).
        Map<String, List<BlockPos>> portones = new LinkedHashMap<>();
        int celdas = 0;
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(caja.getMinPosition()),
                BlockPos.containing(caja.getMaxPosition()))) {
            BlockState s = nivel.getBlockState(p);
            if (!(s.getBlock() instanceof DoubleGateBlock)) {
                continue;
            }
            celdas++;
            // La celda de abajo de su columna y su orientación: eso identifica el portón.
            BlockPos abajo = p.below(s.getValue(DoubleGateBlock.LAYER).indice());
            String clave = s.getValue(DoubleGateBlock.FACING) + "@" + abajo.toShortString();
            portones.computeIfAbsent(clave, k -> new ArrayList<>()).add(p.immutable());
        }

        decir(fuente, "--- PORTONES en " + radio + " bloques: " + portones.size() + " (celdas de portón: " + celdas + ")",
                ChatFormatting.GOLD);
        if (portones.isEmpty()) {
            decir(fuente, "No hay ningún bloque de portón (devilrpg:porton_doble) cerca.", ChatFormatting.RED);
            return 0;
        }
        for (Map.Entry<String, List<BlockPos>> e : portones.entrySet()) {
            List<BlockPos> lista = e.getValue();
            int abiertas = 0;
            int junturas = 0;
            for (BlockPos p : lista) {
                BlockState s = nivel.getBlockState(p);
                if (s.getValue(DoubleGateBlock.OPEN)) {
                    abiertas++;
                }
                if (s.getValue(DoubleGateBlock.JUNTURA)) {
                    junturas++;
                }
            }
            // La caja que ocupan: dice el ANCHO y el ALTO reales (tienen que ser 3 y 3).
            int minX = lista.stream().mapToInt(BlockPos::getX).min().orElse(0);
            int maxX = lista.stream().mapToInt(BlockPos::getX).max().orElse(0);
            int minY = lista.stream().mapToInt(BlockPos::getY).min().orElse(0);
            int maxY = lista.stream().mapToInt(BlockPos::getY).max().orElse(0);
            int minZ = lista.stream().mapToInt(BlockPos::getZ).min().orElse(0);
            int maxZ = lista.stream().mapToInt(BlockPos::getZ).max().orElse(0);
            boolean completo = lista.size() == 9;
            decir(fuente, String.format("%s | celdas=%d %s | ancho=%d alto=%d fondo=%d | abiertas=%d/%d | junturas=%d",
                    e.getKey(), lista.size(), completo ? "(COMPLETO)" : "(INCOMPLETO, deberian ser 9)",
                    Math.max(maxX - minX + 1, maxZ - minZ + 1), maxY - minY + 1,
                    Math.min(maxX - minX + 1, maxZ - minZ + 1), abiertas, lista.size(), junturas),
                    completo ? ChatFormatting.GREEN : ChatFormatting.RED);
        }
        decir(fuente, "Si el porton sale INCOMPLETO o con ancho/alto distinto de 3x3, ese es el fallo. Si sale "
                + "COMPLETO y abiertas=0, el click no esta llegando al bloque.", ChatFormatting.YELLOW);
        return portones.size();
    }

    /** Abre o cierra a mano todos los portones de alrededor (para probar sin depender del click). */
    private static int abatirTodos(CommandContext<CommandSourceStack> ctx, boolean abrir) {
        CommandSourceStack fuente = ctx.getSource();
        ServerLevel nivel = fuente.getLevel();
        BlockPos centro = BlockPos.containing(fuente.getPosition());
        int tocados = 0;
        for (BlockPos p : BlockPos.betweenClosed(centro.offset(-12, -6, -12), centro.offset(12, 6, 12))) {
            BlockState s = nivel.getBlockState(p);
            if (s.getBlock() instanceof DoubleGateBlock && s.getValue(DoubleGateBlock.OPEN) != abrir
                    && s.getValue(DoubleGateBlock.LAYER) == DoubleGateBlock.PanelLayer.LOW) {
                DoubleGateBlock.abatir(nivel, p, s, abrir);
                tocados++;
            }
        }
        decir(fuente, (abrir ? "Abiertos" : "Cerrados") + " " + tocados + " porton(es) a mano.", ChatFormatting.GOLD);
        return tocados;
    }

    private static void decir(CommandSourceStack fuente, String texto, ChatFormatting color) {
        fuente.sendSuccess(() -> Component.literal(texto).withStyle(color), false);
        DevilRpg.LOGGER.info("[Porton] {}", texto);
    }
}
