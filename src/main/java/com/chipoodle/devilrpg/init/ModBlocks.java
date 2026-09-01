package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.block.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Holds a list of all our {@link Block}s. Suppliers that create Blocks are
 * added to the DeferredRegister. The DeferredRegister is then added to our mod
 * event bus in our constructor.
 *
 * @author Cadiboo
 */
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, DevilRpg.MODID);
    public static final DeferredHolder<Block, SoulVineBlock> SOUL_VINE_BLOCK = BLOCKS.register("soulvine", () -> new SoulVineBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .randomTicks()
                    .noCollission()
                    .instabreak()
                    .sound(SoundType.VINE)
    ));
    public static final DeferredHolder<Block, SoulMinerVineBlock> SOUL_MINER_VINE_BLOCK = BLOCKS.register("soulminervine", () -> new SoulMinerVineBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .randomTicks()
                    .noCollission()
                    .instabreak()
                    .sound(SoundType.VINE)
    ));

    public static final DeferredHolder<Block, SoulShieldVineBlock> SOUL_SHIELD_VINE_BLOCK = BLOCKS.register("soulshieldvine", () -> new SoulShieldVineBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .randomTicks()
                    .dynamicShape().strength(1.5F)
                    .noOcclusion()
                    .sound(SoundType.VINE)
    ));

    public static final DeferredHolder<Block, SoulLichenBlock> SOUL_LICHEN_BLOCK = BLOCKS.register("soullichen", () -> new SoulLichenBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.GLOW_LICHEN).lightLevel(SoulLichenBlock.emission(7)).randomTicks()
    ));

    public static final DeferredHolder<Block, ManaBerryBushBlock> MANA_BERRY_BUSH_BLOCK = BLOCKS.register("mana_berry_bush", () -> new ManaBerryBushBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .randomTicks()
                    .noCollission()
                    .instabreak()
                    .sound(SoundType.SWEET_BERRY_BUSH)
    ));

    public static final DeferredHolder<Block, BloomingSanctuaryBlock> BLOOMING_SANCTUARY_BLOCK = BLOCKS.register("bloomingsanctuary", () -> new BloomingSanctuaryBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .randomTicks()
                    .dynamicShape().strength(1.5F)
                    .noOcclusion()
                    .sound(SoundType.VINE)
    ));

    public static final DeferredHolder<Block, UpwardSporeBlossomBlock> UPWARD_SPORE_BLOSSOM_BLOCK = BLOCKS.register("upward_spore_blossom", () -> new UpwardSporeBlossomBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.SPORE_BLOSSOM).randomTicks()
    ));
}
