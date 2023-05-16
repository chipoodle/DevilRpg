package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.block.SoulVineBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Holds a list of all our {@link Block}s. Suppliers that create Blocks are
 * added to the DeferredRegister. The DeferredRegister is then added to our mod
 * event bus in our constructor. When the Block Registry Event is fired by Forge
 * and it is time for the mod to register its Blocks, our Blocks are created and
 * registered by the DeferredRegister. The Block Registry Event will always be
 * called before the Item registry is filled. Note: This supports registry
 * overrides.
 *
 * @author Cadiboo
 */
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, DevilRpg.MODID);
    // This block has the ROCK material, meaning it needs at least a wooden pickaxe to break it. It is very similar to Iron Ore
    // public static final RegistryObject<Block> EXAMPLE_ORE = BLOCKS.register("example_ore", () -> new Block(Block.Properties.of(Material.STONE).strength(3.0F, 3.0F)));
    // This block has the IRON material, meaning it needs at least a stone pickaxe to break it. It is very similar to the Iron Block
    // public static final RegistryObject<Block> EXAMPLE_BLOCK = BLOCKS.register("example_block", () -> new Block(Block.Properties.of(Material.METAL, MaterialColor.METAL).strength(5.0F, 6.0F).sound(SoundType.METAL)));

    public static final RegistryObject<SoulVineBlock> SOUL_VINE_BLOCK = BLOCKS.register("soulvine", () -> new SoulVineBlock(
            //Block.Properties.copy(Blocks.VINE).lightLevel((state) -> 15)
            Block.Properties.copy(Blocks.VINE)
            //.randomTicks().noCollission().strength(0.2F).sound(SoundType.VINE)
    ));

    /*public static final TagKey<Block> SOULVINE_TAG = BlockTags.create(new ResourceLocation(DevilRpg.MODID,"climbable"));*/
}
