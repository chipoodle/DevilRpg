package com.chipoodle.devilrpg.client.render.entity.renderer;

import com.chipoodle.devilrpg.entity.IceSpear;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Dibuja la lanza de hielo del wisp arquero con el item del proyectil (el icono de la habilidad,
 * {@code ice-spear.png}), más grande que la bola de hielo y a plena luz, para que se distinga bien del
 * disparo normal.
 */
@OnlyIn(Dist.CLIENT)
public class IceSpearRenderer extends ThrownItemRenderer<IceSpear> {

    /** Escala del proyectil: 1.0 es el tamaño de un item normal (la bola de hielo). */
    private static final float SCALE = 2.2F;

    public IceSpearRenderer(EntityRendererProvider.Context context) {
        // fullBright = true: la lanza se ve iluminada aunque esté de noche o en cueva.
        super(context, SCALE, true);
    }
}
