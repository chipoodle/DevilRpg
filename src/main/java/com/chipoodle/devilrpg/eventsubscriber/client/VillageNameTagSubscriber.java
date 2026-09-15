package com.chipoodle.devilrpg.eventsubscriber.client;

import com.chipoodle.devilrpg.DevilRpg;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;
import org.joml.Matrix4f;

/**
 * Dibuja las etiquetas de <b>varias líneas</b> de los aldeanos de la aldea (nombre y oficio arriba, lo que está
 * haciendo debajo).
 * <p>
 * Hace falta porque la etiqueta de nombre de vanilla se dibuja con {@code Font.drawInBatch(Component, ...)}, que
 * <b>no parte las líneas</b>: el salto de línea salía como un glifo raro en medio del texto (el "LF" que veía el
 * jugador). Aquí se intercepta el evento de la etiqueta, se le dice al juego que <b>no</b> la dibuje y se pintan las
 * líneas una debajo de otra, centradas, imitando el dibujo de vanilla (una pasada de fondo y otra del texto).
 */
@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class VillageNameTagSubscriber {

    /** Color con el que vanilla pinta la pasada de fondo de la etiqueta (negro translúcido). */
    private static final int FONDO = 553648127;

    private VillageNameTagSubscriber() {
    }

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        Entity entity = event.getEntity();
        Component contenido = event.getContent();
        if (!(entity instanceof Villager) || contenido == null) {
            return;
        }
        String texto = contenido.getString();
        if (!texto.contains("\n")) {
            return; // no es nuestra etiqueta (una sola línea): que la pinte el juego como siempre
        }
        // El juego no sabe pintar esto: se le quita y lo pintamos nosotros línea a línea.
        event.setCanRender(TriState.FALSE);
        dibujar(entity, texto.split("\n"), event.getPoseStack(), event.getMultiBufferSource(),
                event.getPackedLight(), event.getPartialTick());
    }

    /** Pinta las líneas centradas, una debajo de otra, en el mismo sitio donde vanilla pone la etiqueta. */
    private static void dibujar(Entity entity, String[] lineas, PoseStack pose, MultiBufferSource buffer,
                                int luz, float parcial) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 anclaje = entity.getAttachments()
                .getNullable(EntityAttachment.NAME_TAG, 0, entity.getViewYRot(parcial));
        if (anclaje == null) {
            return;
        }
        Font fuente = minecraft.font;
        pose.pushPose();
        pose.translate(anclaje.x, anclaje.y + 0.5D, anclaje.z);
        pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(0.025F, -0.025F, 0.025F);
        Matrix4f matriz = pose.last().pose();
        float fondo = minecraft.options.getBackgroundOpacity(0.25F);
        int colorFondo = (int) (fondo * 255.0F) << 24;
        // La primera línea va arriba del todo: se sube media etiqueta para que el conjunto quede centrado.
        float y = -(lineas.length - 1) * fuente.lineHeight / 2.0F;
        for (String linea : lineas) {
            Component texto = Component.literal(linea);
            float x = -fuente.width(texto) / 2.0F;
            fuente.drawInBatch(texto, x, y, FONDO, false, matriz, buffer,
                    Font.DisplayMode.SEE_THROUGH, colorFondo, luz);
            fuente.drawInBatch(texto, x, y, -1, false, matriz, buffer,
                    Font.DisplayMode.NORMAL, 0, luz);
            y += fuente.lineHeight;
        }
        pose.popPose();
    }
}
