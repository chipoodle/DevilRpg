package com.chipoodle.devilrpg.client.render;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import com.google.common.base.Functions;
import com.google.common.collect.Maps;

import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.item.DyeColor;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.EffectInstance;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public interface IRenderUtilities {
	DyeColor[] reds = {DyeColor.RED,DyeColor.BROWN};
	DyeColor[] blues = {DyeColor.BLUE,DyeColor.LIGHT_BLUE};
	Map<DyeColor, float[]> DYE_TO_RGB = 
			Arrays.stream(DyeColor.values()).collect(
					Collectors.toMap(Functions.identity(),dye->createColor(dye)));
	
	Map<DyeColor, float[]> DYE_TO_RGB_RED = 
			Arrays.stream(reds).collect(
					Collectors.toMap(Functions.identity(),dye->createColor(dye)));
	
	Map<DyeColor, float[]> DYE_TO_RGB_BLUE = 
			Arrays.stream(blues).collect(
					Collectors.toMap(Functions.identity(),dye->createColor(dye)));

	
	/*public default Map<DyeColor, float[]> getRedishColors() {
		return Arrays.stream(reds).collect(
						Collectors.toMap(Functions.identity(),dye->createColor(dye)));
	}*/
	
	public static float[] createColor(DyeColor dyeColorIn) {
		if (dyeColorIn == DyeColor.WHITE) {
			return new float[] { 0.9019608F, 0.9019608F, 0.9019608F };
		} else {
			float[] afloat = dyeColorIn.getColorComponentValues();
			float f = 0.75F;
			return new float[] { afloat[0] * 0.75F, afloat[1] * 0.75F, afloat[2] * 0.75F };
		}
	}

	@OnlyIn(Dist.CLIENT)
	public static float[] getDyeRgb(DyeColor dyeColor) {
		return DYE_TO_RGB.get(dyeColor);
	}
	
	public default <T extends LivingEntity> float[] groovy(T entitylivingbaseIn, float partialTicks) {
		float f;
		float f1;
		float f2;
		// int i1 = 25;
		int i = entitylivingbaseIn.ticksExisted / 25 + entitylivingbaseIn.getEntityId();
		int j = DyeColor.values().length;
		int k = i % j;
		int l = (i + 1) % j;
		float f3 = ((float) (entitylivingbaseIn.ticksExisted % 25) + partialTicks) / 25.0F;
		float[] afloat1 = getDyeRgb(DyeColor.byId(k));
		float[] afloat2 = getDyeRgb(DyeColor.byId(l));
		f = afloat1[0] * (1.0F - f3) + afloat2[0] * f3;
		f1 = afloat1[1] * (1.0F - f3) + afloat2[1] * f3;
		f2 = afloat1[2] * (1.0F - f3) + afloat2[2] * f3;
		float[] returnFloat = { f, f1, f2 };
		return returnFloat;
	}

	public default <T extends LivingEntity> float[] groovyRed(T entitylivingbaseIn, float partialTicks) {
		float f;
		float f1;
		float f2;
		// int i1 = 25;
		int i = entitylivingbaseIn.ticksExisted / 25 + entitylivingbaseIn.getEntityId();
		int j = reds.length;
		int k = i % j;
		int l = (i + 1) % j;
		float f3 = ((float) (entitylivingbaseIn.ticksExisted % 25) + partialTicks) / 25.0F;
		float[] afloat1 = DYE_TO_RGB_RED.get(reds[k]);
		float[] afloat2 = DYE_TO_RGB_RED.get(reds[l]);
		f = afloat1[0] * (1.0F - f3) + afloat2[0] * f3;
		f1 = afloat1[1] * (1.0F - f3) + afloat2[1] * f3;
		f2 = afloat1[2] * (1.0F - f3) + afloat2[2] * f3;
		float[] returnFloat = { f, f1, f2 };
		return returnFloat;
	}
	
	public default <T extends LivingEntity> float[] groovyBlue(T entitylivingbaseIn, float partialTicks) {
		float f;
		float f1;
		float f2;
		// int i1 = 25;
		int i = entitylivingbaseIn.ticksExisted / 25 + entitylivingbaseIn.getEntityId();
		int j = blues.length;
		int k = i % j;
		int l = (i + 1) % j;
		float f3 = ((float) (entitylivingbaseIn.ticksExisted % 25) + partialTicks) / 25.0F;
		float[] afloat1 = DYE_TO_RGB_BLUE.get(blues[k]);
		float[] afloat2 = DYE_TO_RGB_BLUE.get(blues[l]);
		f = afloat1[0] * (1.0F - f3) + afloat2[0] * f3;
		f1 = afloat1[1] * (1.0F - f3) + afloat2[1] * f3;
		f2 = afloat1[2] * (1.0F - f3) + afloat2[2] * f3;
		float[] returnFloat = { f, f1, f2 };
		return returnFloat;
	}

	public default void spawnLingeringCloud(World world, LivingEntity living) {
		Collection<EffectInstance> collection = living.getActivePotionEffects();
		if (!collection.isEmpty()) {
			AreaEffectCloudEntity areaeffectcloudentity = new AreaEffectCloudEntity(world, living.getPosX(),
					living.getPosY(), living.getPosZ());
			areaeffectcloudentity.setRadius(2.5F);
			areaeffectcloudentity.setRadiusOnUse(-0.5F);
			areaeffectcloudentity.setWaitTime(10);
			areaeffectcloudentity.setDuration(areaeffectcloudentity.getDuration() / 2);
			areaeffectcloudentity
					.setRadiusPerTick(-areaeffectcloudentity.getRadius() / (float) areaeffectcloudentity.getDuration());

			for (EffectInstance effectinstance : collection) {
				areaeffectcloudentity.addEffect(new EffectInstance(effectinstance));
			}

			world.addEntity(areaeffectcloudentity);
		}

	}

	public default void customDeadParticles(World world, Random rand, LivingEntity living) {

		for (int i = 0; i < 7; ++i) {
			double d0 = rand.nextGaussian() * 0.02D;
			double d1 = rand.nextGaussian() * 0.02D;
			double d2 = rand.nextGaussian() * 0.02D;
			world.addParticle(ParticleTypes.END_ROD, living.getPosXRandom(1.0D), living.getPosYRandom() + 0.5D,
					living.getPosZRandom(1.0D), d0, d1, d2);
		}
	}
}
