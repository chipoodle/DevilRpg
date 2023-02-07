package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.block.Blocks;
import net.minecraft.data.BlockTagsProvider;
import net.minecraft.data.DataGenerator;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.common.data.ForgeBlockTagsProvider;

import javax.annotation.Nullable;
import java.nio.file.Path;

public class ModBlockTags extends BlockTagsProvider {
    public ModBlockTags(DataGenerator generatorIn,  @Nullable ExistingFileHelper existingFileHelper) {
        super(generatorIn,DevilRpg.MODID , existingFileHelper);
    }

    @Override
    public void addTags() {
        this.tag(BlockTags.CLIMBABLE).add( ModBlocks.SOUL_VINE_BLOCK.get());
    }

    @Override
    protected Path getPath(ResourceLocation p_200431_1_) {
        return this.generator.getOutputFolder().resolve("data/" + p_200431_1_.getNamespace() + "/tags/blocks/" + p_200431_1_.getPath() + ".json");
    }

}
