package com.chipoodle.devilrpg.client.render.entity.renderer;

import com.chipoodle.devilrpg.entity.FrostBall;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Renderiza el FrostBall como la bola de hielo lanzada (usa el modelo del item de nieve), en vez de
 * un craneo de Wither con textura faltante (que se veia morado/negro).
 */
@OnlyIn(Dist.CLIENT)
public class FrostBallRenderer extends ThrownItemRenderer<FrostBall> {

    public FrostBallRenderer(EntityRendererProvider.Context context) {
        // Renderiza el item de nieve (bola de hielo) que se lanza.
        super(context, Items.SNOWBALL);
    }
}
