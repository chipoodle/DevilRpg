package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

import javax.annotation.Nullable;
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
    public void addTags(HolderLookup.Provider provider) {
        DevilRpg.LOGGER.info("-----------------------> Adding tags");
        this.tag(BlockTags.CLIMBABLE).add(ModBlocks.SOUL_VINE_BLOCK.get());
        DevilRpg.LOGGER.info("done.");
    }

   /* @Override
    protected Path getPath(ResourceLocation p_200431_1_) {
        return this.generator.getOutputFolder().resolve("data/" + p_200431_1_.getNamespace() + "/tags/block/" + p_200431_1_.getPath() + ".json");
    }*/


}
