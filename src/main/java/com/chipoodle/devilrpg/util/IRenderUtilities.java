package com.chipoodle.devilrpg.util;

import com.google.common.base.Functions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

public interface IRenderUtilities {
    DyeColor[] reds = {DyeColor.BLACK, DyeColor.RED, DyeColor.ORANGE};
    DyeColor[] blues = {DyeColor.BLACK, DyeColor.BLUE, DyeColor.LIGHT_BLUE};
    Map<DyeColor, float[]> DYE_TO_RGB = Arrays.stream(DyeColor.values()).collect(Collectors.toMap(Functions.identity(), dye -> createColor(dye)));

    Map<DyeColor, float[]> DYE_TO_RGB_RED = Arrays.stream(reds).collect(Collectors.toMap(Functions.identity(), dye -> createColor(dye)));

    Map<DyeColor, float[]> DYE_TO_RGB_BLUE = Arrays.stream(blues).collect(Collectors.toMap(Functions.identity(), dye -> createColor(dye)));

	
	/*public default Map<DyeColor, float[]> getRedishColors() {
		return Arrays.stream(reds).collect(
						Collectors.toMap(Functions.identity(),dye->createColor(dye)));
	}*/

    static float[] createColor(DyeColor dyeColorIn) {
        if (dyeColorIn == DyeColor.WHITE) {
            return new float[]{0.9019608F, 0.9019608F, 0.9019608F};
        } else {
            float[] afloat = dyeColorIn.getTextureDiffuseColors();
            float f = 0.75F;
            return new float[]{afloat[0] * 0.75F, afloat[1] * 0.75F, afloat[2] * 0.75F};
        }
    }

    @OnlyIn(Dist.CLIENT)
    static float[] getDyeRgb(DyeColor dyeColor) {
        return DYE_TO_RGB.get(dyeColor);
    }

    static <T extends LivingEntity> float[] groovyRed(T entitylivingbaseIn, float partialTicks) {
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
        float[] returnFloat = {f, f1, f2};
        return returnFloat;
    }

    static <T extends LivingEntity> float[] groovyBlue(T entitylivingbaseIn, float partialTicks) {
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
        float[] returnFloat = {f, f1, f2};
        return returnFloat;
    }

    static void spawnLingeringCloud(Level Level, LivingEntity living, float radius, int puntosAsignados) {
        Collection<MobEffectInstance> collection = living.getActiveEffects();
        if (!collection.isEmpty()) {
            AreaEffectCloud areaEffectCloud = new AreaEffectCloud(Level, living.getX(), living.getY(), living.getZ());
            areaEffectCloud.setRadius(1 + radius * puntosAsignados);
            areaEffectCloud.setRadiusOnUse(-0.5F);
            areaEffectCloud.setWaitTime(10);
            areaEffectCloud.setDuration(areaEffectCloud.getDuration() / 2);
            areaEffectCloud.setRadiusPerTick(-areaEffectCloud.getRadius() / (float) areaEffectCloud.getDuration());

            for (MobEffectInstance effectinstance : collection) {
                areaEffectCloud.addEffect(new MobEffectInstance(effectinstance));
            }

            Level.addFreshEntity(areaEffectCloud);
        }

    }

    static void customDeadParticles(Level Level, RandomSource rand, LivingEntity living) {

        for (int i = 0; i < 7; ++i) {
            double d0 = rand.nextGaussian() * 0.02D;
            double d1 = rand.nextGaussian() * 0.02D;
            double d2 = rand.nextGaussian() * 0.02D;
            Level.addParticle(ParticleTypes.END_ROD, living.getRandomX(1.0D), living.getRandomY() + 0.5D, living.getRandomZ(1.0D), d0, d1, d2);
        }
    }

    static void customParticles(Level Level, RandomSource rand, LivingEntity living, ParticleOptions particle) {

        for (int i = 0; i < 7; ++i) {
            double d0 = rand.nextGaussian() * 0.02D;
            double d1 = rand.nextGaussian() * 0.02D;
            double d2 = rand.nextGaussian() * 0.02D;
            Level.addParticle(particle, living.getRandomX(1.0D), living.getRandomY() + 1.5D, living.getRandomZ(1.0D), d0, d1, d2);
        }
    }

    static void rotationParticles(Level Level, RandomSource rand, LivingEntity living, ParticleOptions particle, int numberOfParticles, double distanciaDesdeElCentro) {
        double x;
        double y;
        double z;
        int ageInTicks = 20;
        float f = (float) (ageInTicks * 0.43);
        for (int j = 0; j < numberOfParticles; ++j) {
            double d0 = rand.nextGaussian() * 0.02D;
            double d1 = rand.nextGaussian() * 0.02D;
            double d2 = rand.nextGaussian() * 0.02D;
            y = living.getRandomY() + 0.6D + j * 0.1;
            //this.blazeSticks[j].yRot = f;
            // this.blazeSticks[j].rotateAngleX = MathHelper.sin(f)*0.3f;
            x = -Mth.sin(f) * distanciaDesdeElCentro + living.getRandomX(0.9D);
            z = -Mth.cos(f) * distanciaDesdeElCentro + living.getRandomZ(0.9D);
            f += (float) (Math.PI * 2 / numberOfParticles);
            Level.addParticle(particle, x, y, z, d0, d1, d2);
        }
    }

    default <T extends LivingEntity> float[] groovy(T entitylivingbaseIn, float partialTicks) {
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
        float[] returnFloat = {f, f1, f2};
        return returnFloat;
    }

    static void spawnBlockingParticles(Player player, ParticleOptions particleTypes) {
        double radius = 1.5; // Radio de la esfera
        int numCircles = 6;
        double playerX = player.getX();
        double playerY = player.getY();
        double playerZ = player.getZ();

        for (int i = 0; i < numCircles; i++) {
            double phi = (i / (double) numCircles) * Math.PI; // Ángulo en el plano XY
            for (int j = 0; j < numCircles; j++) {
                double theta = (j / (double) numCircles) * 2 * Math.PI; // Ángulo alrededor del eje Y

                // Coordenadas esféricas a cartesianas
                double offsetX = radius * Math.sin(phi) * Math.cos(theta);
                double offsetY = radius * Math.sin(phi) * Math.sin(theta);
                double offsetZ = radius * Math.cos(phi);

                // Ajusta las coordenadas según la posición del jugador
                double spawnX = playerX + offsetX;
                double spawnY = playerY + offsetY+1.5;
                double spawnZ = playerZ + offsetZ;

                // Genera la partícula en la posición calculada
                player.level.addParticle(particleTypes, spawnX, spawnY, spawnZ, 0.0, 0.0, 0.0);
            }
        }
    }
}
