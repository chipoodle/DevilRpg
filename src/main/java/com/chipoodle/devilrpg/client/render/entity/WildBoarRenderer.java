
package com.chipoodle.devilrpg.client.render.entity;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.layer.WildBoarSaddleLayer;
import com.chipoodle.devilrpg.entity.wildboar.WildBoarEntity;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.model.PigModel;
import net.minecraft.util.ResourceLocation;

/**
 * Handles rendering all WildBoar Entities.
 * The render method is called once each frame for every visible WildBoar.
 * <p>
 * We use a PigModel in our renderer and simply change it's texture.
 *
 * @author Cadiboo
 */
public class WildBoarRenderer extends MobRenderer<WildBoarEntity, PigModel<WildBoarEntity>> {

	private static final ResourceLocation WILD_BOAR_TEXTURE = new ResourceLocation(DevilRpg.MODID, "textures/entity/wild_boar/wild_boar.png");

	public WildBoarRenderer(final EntityRendererManager manager) {
		super(manager, new PigModel<>(), 0.7F);
		this.addLayer(new WildBoarSaddleLayer(this));
	}

	@Override
	public ResourceLocation getEntityTexture(final WildBoarEntity entity) {
		return WILD_BOAR_TEXTURE;
	}

}
