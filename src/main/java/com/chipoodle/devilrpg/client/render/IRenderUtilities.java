package com.chipoodle.devilrpg.client.render;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import com.chipoodle.devilrpg.entity.SoulWolfEntity;
import com.google.common.base.Functions;

import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.DyeColor;
import net.minecraft.particles.BasicParticleType;
import net.minecraft.particles.BlockParticleData;
import net.minecraft.particles.ParticleType;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public interface IRenderUtilities {
	DyeColor[] reds = {DyeColor.BLACK,DyeColor.RED,DyeColor.ORANGE};
	DyeColor[] blues = {DyeColor.BLACK, DyeColor.BLUE,DyeColor.LIGHT_BLUE};
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
			float[] afloat = dyeColorIn.getTextureDiffuseColors();
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
		int i = entitylivingbaseIn.tickCount / 25 + entitylivingbaseIn.getId();
		int j = DyeColor.values().length;
		int k = i % j;
		int l = (i + 1) % j;
		float f3 = ((float) (entitylivingbaseIn.tickCount % 25) + partialTicks) / 25.0F;
		float[] afloat1 = getDyeRgb(DyeColor.byId(k));
		float[] afloat2 = getDyeRgb(DyeColor.byId(l));
		f = afloat1[0] * (1.0F - f3) + afloat2[0] * f3;
		f1 = afloat1[1] * (1.0F - f3) + afloat2[1] * f3;
		f2 = afloat1[2] * (1.0F - f3) + afloat2[2] * f3;
		float[] returnFloat = { f, f1, f2 };
		return returnFloat;
	}

	public static <T extends LivingEntity> float[] groovyRed(T entitylivingbaseIn, float partialTicks) {
		float f;
		float f1;
		float f2;
		// int i1 = 25;
		int i = entitylivingbaseIn.tickCount / 25 + entitylivingbaseIn.getId();
		int j = reds.length;
		int k = i % j;
		int l = (i + 1) % j;
		float f3 = ((float) (entitylivingbaseIn.tickCount % 25) + partialTicks) / 25.0F;
		float[] afloat1 = DYE_TO_RGB_RED.get(reds[k]);
		float[] afloat2 = DYE_TO_RGB_RED.get(reds[l]);
		f = afloat1[0] * (1.0F - f3) + afloat2[0] * f3;
		f1 = afloat1[1] * (1.0F - f3) + afloat2[1] * f3;
		f2 = afloat1[2] * (1.0F - f3) + afloat2[2] * f3;
		float[] returnFloat = { f, f1, f2 };
		return returnFloat;
	}
	
	public static <T extends LivingEntity> float[] groovyBlue(T entitylivingbaseIn, float partialTicks) {
		float f;
		float f1;
		float f2;
		// int i1 = 25;
		int i = entitylivingbaseIn.tickCount / 25 + entitylivingbaseIn.getId();
		int j = blues.length;
		int k = i % j;
		int l = (i + 1) % j;
		float f3 = ((float) (entitylivingbaseIn.tickCount % 25) + partialTicks) / 25.0F;
		float[] afloat1 = DYE_TO_RGB_BLUE.get(blues[k]);
		float[] afloat2 = DYE_TO_RGB_BLUE.get(blues[l]);
		f = afloat1[0] * (1.0F - f3) + afloat2[0] * f3;
		f1 = afloat1[1] * (1.0F - f3) + afloat2[1] * f3;
		f2 = afloat1[2] * (1.0F - f3) + afloat2[2] * f3;
		float[] returnFloat = { f, f1, f2 };
		return returnFloat;
	}
	
	public static void spawnLingeringCloud(World world, LivingEntity living,float radius, int puntosAsignados) {
		Collection<EffectInstance> collection = living.getActiveEffects();
		if (!collection.isEmpty()) {
			AreaEffectCloudEntity areaeffectcloudentity = new AreaEffectCloudEntity(world, living.getX(),
					living.getY(), living.getZ());
			areaeffectcloudentity.setRadius(1 + radius * puntosAsignados);
			areaeffectcloudentity.setRadiusOnUse(-0.5F);
			areaeffectcloudentity.setWaitTime(10);
			areaeffectcloudentity.setDuration(areaeffectcloudentity.getDuration() / 2);
			areaeffectcloudentity
					.setRadiusPerTick(-areaeffectcloudentity.getRadius() / (float) areaeffectcloudentity.getDuration());

			for (EffectInstance effectinstance : collection) {
				areaeffectcloudentity.addEffect(new EffectInstance(effectinstance));
			}

			world.addFreshEntity(areaeffectcloudentity);
		}

	}

	public static void customDeadParticles(World world, Random rand, LivingEntity living) {

		for (int i = 0; i < 7; ++i) {
			double d0 = rand.nextGaussian() * 0.02D;
			double d1 = rand.nextGaussian() * 0.02D;
			double d2 = rand.nextGaussian() * 0.02D;
			world.addParticle(ParticleTypes.END_ROD, living.getRandomX(1.0D), living.getRandomY() + 0.5D,living.getRandomZ(1.0D), d0, d1, d2);
		}
	}
	
	public static void customParticles(World world, Random rand, LivingEntity living, BasicParticleType particle) {

		for (int i = 0; i < 7; ++i) {
			double d0 = rand.nextGaussian() * 0.02D;
			double d1 = rand.nextGaussian() * 0.02D;
			double d2 = rand.nextGaussian() * 0.02D;
			world.addParticle(particle, living.getRandomX(1.0D), living.getRandomY() + 1.5D,living.getRandomZ(1.0D), d0, d1, d2);
		}
	}
	public static void rotationParticles(World world, Random rand, LivingEntity living, BasicParticleType particle, int numberOfParticles, double distanciaDesdeElCentro) {
		double x;
		double y;
		double z;
		int ageInTicks = 20;
		float f = (float) (ageInTicks * 0.43);
		for (int j = 0; j < numberOfParticles; ++j) {
			double d0 = rand.nextGaussian() * 0.02D;
			double d1 = rand.nextGaussian() * 0.02D;
			double d2 = rand.nextGaussian() * 0.02D;
			y = living.getRandomY()+0.6D;
			//this.blazeSticks[j].yRot = f;
			// this.blazeSticks[j].rotateAngleX = MathHelper.sin(f)*0.3f;
			x = -MathHelper.sin(f) * distanciaDesdeElCentro+living.getRandomX(0.9D);
			z = -MathHelper.cos(f) * distanciaDesdeElCentro+living.getRandomZ(0.9D);
			f += Math.PI * 2 / numberOfParticles;
			world.addParticle(particle, x, y,z, d0, d1, d2);
		}
	}
}
