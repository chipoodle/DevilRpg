package com.chipoodle.devilrpg.client.render.entity.layer;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.model.SoulWispModel;
import com.chipoodle.devilrpg.client.render.entity.model.SoulWispModelHeart;
import com.chipoodle.devilrpg.entity.SoulWispEntity;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.IEntityRenderer;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.client.renderer.entity.layers.EnergyLayer;
import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
//public class SoulWispGelLayer<T extends SoulWispEntity> extends EnergyLayer<T, PlayerModel<T>> {
//public class SoulWispGelLayer<T extends SoulWispEntity> extends EnergyLayer<T, SoulWispModel<T>> {
public class SoulWispGelLayer<T extends SoulWispEntity> extends GhostEnergyLayer<T, SoulWispModelHeart<T>> {
	private final SoulWispModel<T> wispModel = new SoulWispModel<T>();
	private static final ResourceLocation WISP_GEL = new ResourceLocation(DevilRpg.MODID + ":textures/entity/soul/soulgelghost.png");
	//private static final ResourceLocation WISP_GEL = new ResourceLocation(DevilRpg.MODID + ":textures/entity/soul/soulgel.png");
	//private static final ResourceLocation WISP_WINGS = new ResourceLocation(DevilRpg.MODID + ":textures/entity/flyingwisp/wings.png");
	private IEntityRenderer<T, SoulWispModelHeart<T>> entityRenderer;
	private float gelMovementFactor;

	//private WingsModel<SoulWispEntity> wingsModel = new WingsModel<SoulWispEntity>();
	
	public SoulWispGelLayer(IEntityRenderer<T, SoulWispModelHeart<T>> p_i50947_1_, float gelMovementFactor) {
		super(p_i50947_1_);
		entityRenderer = p_i50947_1_;
		this.gelMovementFactor = gelMovementFactor;
	}
	
	public void render(MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn, T entitylivingbaseIn,
			float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw,
			float headPitch) {
			
		//renderWings(matrixStackIn, bufferIn, packedLightIn, entitylivingbaseIn, limbSwing, limbSwingAmount, ageInTicks,
		//		netHeadYaw, headPitch);
		
		groovyMethod(matrixStackIn, bufferIn, packedLightIn, entitylivingbaseIn, partialTicks);
		
		float x = (float) (entitylivingbaseIn.getX() -  entitylivingbaseIn.getOwner().getX());
		float z = (float) (entitylivingbaseIn.getZ() -  entitylivingbaseIn.getOwner().getZ());
		
		/*String message1 = String.format("Z: %f ", -0.62 - (z*0.01));
		entitylivingbaseIn.getOwner().sendMessage(new StringTextComponent(message1));*/
		
		
		
		//matrixStackIn.translate(0.84,0.3423*2,-0.62 - (z*0.01));
		//Quaternion q = new Quaternion(45,0,45,true);
		//matrixStackIn.rotate(q);
		//matrixStackIn.translate(0.5f,0.0f,0.0f);
		
		
		//matrixStackIn.scale(1.4f, 1.4f, 1.4f);
		//matrixStackIn.translate(0.0f, -0.32f, 0.0f);
		
		
		super.render(matrixStackIn, bufferIn, packedLightIn, entitylivingbaseIn, limbSwing, limbSwingAmount,partialTicks, ageInTicks, netHeadYaw, headPitch);
	}

	/*private void renderWings(MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn,
			T entitylivingbaseIn, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch) {
		matrixStackIn.push();	
		matrixStackIn.scale(4.5f, 4.5f, 4.5f);
		matrixStackIn.translate(0.0f, -1.1f, 0.0f);
		IVertexBuilder ivertexbuilder = bufferIn.getBuffer(this.wingsModel.getRenderType(WISP_WINGS));
		this.wingsModel.render(matrixStackIn, ivertexbuilder, packedLightIn, OverlayTexture.DEFAULT_LIGHT, 1.0F,1.0F, 1.0F, 1.0F);
		this.wingsModel.render(entitylivingbaseIn, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
		matrixStackIn.pop();
	}*/
	
	public ResourceLocation getEntityTexture(SoulWispEntity entity) {
		return WISP_GEL;
	}

	protected float xOffset(float p_225634_1_) {
		return p_225634_1_ * gelMovementFactor;
	}

	protected ResourceLocation getTextureLocation() {
		return WISP_GEL;
	}

	protected EntityModel<T> model() {
		return wispModel;
	}
	
	private void groovyMethod(MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn,
			T entitylivingbaseIn, float partialTicks) {
		if (!entitylivingbaseIn.isInvisible()) {
			IVertexBuilder ivertexbuilder = entitylivingbaseIn.getBuffer(bufferIn,
					entityRenderer.getTextureLocation(entitylivingbaseIn));
			float[] rgbArray = entitylivingbaseIn.groovyBlue(entitylivingbaseIn, partialTicks);
			entityRenderer.getModel().renderToBuffer(matrixStackIn, ivertexbuilder, packedLightIn,
					LivingRenderer.getOverlayCoords(entitylivingbaseIn, 0.1F), rgbArray[0], rgbArray[1], rgbArray[2], 1.0F);
		}
	}
}