package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class ModBlockTags extends BlockTagsProvider {
    /*public ModBlockTags(DataGenerator generatorIn,  @Nullable ExistingFileHelper existingFileHelper) {

        //super(generatorIn,DevilRpg.MODID , existingFileHelper);
    }*/

    public ModBlockTags(PackOutput output,
                        CompletableFuture<HolderLookup.Provider> lookupProvider,
                        String modId,
                        @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, modId, existingFileHelper);
    }

    @Override
    public void addTags(HolderLookup.@NotNull Provider provider) {
        DevilRpg.LOGGER.info("-----------------------> Adding tags");
        this.tag(BlockTags.CLIMBABLE).add(ModBlocks.SOUL_VINE_BLOCK.get());
        this.tag(BlockTags.ICE).add(ModBlocks.SOUL_VINE_BLOCK.get());
        DevilRpg.LOGGER.info("done.");
    }

   /* @Override
    protected Path getPath(ResourceLocation p_200431_1_) {
        return this.generator.getOutputFolder().resolve("data/" + p_200431_1_.getNamespace() + "/tags/block/" + p_200431_1_.getPath() + ".json");
    }*/
}
