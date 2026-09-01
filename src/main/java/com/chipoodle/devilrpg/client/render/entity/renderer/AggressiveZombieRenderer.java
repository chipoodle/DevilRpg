package com.chipoodle.devilrpg.client.render.entity.renderer;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;
import org.jetbrains.annotations.NotNull;

public class AggressiveZombieRenderer extends ZombieRenderer {
    private static final ResourceLocation TEXTURE =  ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "textures/entity/aggressive_zombie/aggressive_zombie.png");

    public AggressiveZombieRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull Zombie zombie) {
        return TEXTURE;
    }

}