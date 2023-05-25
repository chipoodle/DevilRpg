package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.block.SoulVineBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
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
    public static final RegistryObject<SoulVineBlock> SOUL_VINE_BLOCK = BLOCKS.register("soulvine", () -> new SoulVineBlock(
            //Block.Properties.copy(Blocks.VINE).lightLevel((state) -> 15)
            //BlockBehaviour.Properties.of(Material.DECORATION, MaterialColor.SAND).noCollission().sound(SoundType.SCAFFOLDING).dynamicShape()
            Block.Properties.copy(Blocks.WEEPING_VINES)
            //BlockBehaviour.Properties.of(Material.PLANT).noCollission().instabreak().sound(SoundType.WEEPING_VINES)
            //.randomTicks().noCollission().strength(0.2F).sound(SoundType.VINE)
    ));
}
