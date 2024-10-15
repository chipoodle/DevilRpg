package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.block.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
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
            Block.Properties.of(Material.LEAVES, MaterialColor.COLOR_GREEN)
                    .randomTicks()
                    .noCollission()
                    .instabreak()
                    .sound(SoundType.VINE)
    ));
    public static final RegistryObject<SoulMinerVineBlock> SOUL_MINER_VINE_BLOCK = BLOCKS.register("soulminervine", () -> new SoulMinerVineBlock(
            Block. Properties.of(Material.LEAVES, MaterialColor.COLOR_GREEN)
                    .randomTicks()
                    .noCollission()
                    .instabreak()
                    .sound(SoundType.VINE)
    ));

    public static final RegistryObject<SoulShieldVineBlock> SOUL_SHIELD_VINE_BLOCK = BLOCKS.register("soulshieldvine", () -> new SoulShieldVineBlock(
            Block. Properties.of(Material.LEAVES, MaterialColor.COLOR_GREEN)
                    .randomTicks()
                    .dynamicShape().strength(1.5F)
                    .noOcclusion()
                    .sound(SoundType.VINE)
    ));

    public static final RegistryObject<SoulLichenBlock> SOUL_LICHEN_BLOCK = BLOCKS.register("soullichen", () -> new SoulLichenBlock(
            Block.Properties.copy(Blocks.GLOW_LICHEN).lightLevel(SoulLichenBlock.emission(7)).randomTicks()
    ));

    public static final RegistryObject<ManaBerryBushBlock> MANA_BERRY_BUSH_BLOCK = BLOCKS.register("mana_berry_bush", () -> new ManaBerryBushBlock(
            Block.Properties.of(Material.PLANT, MaterialColor.COLOR_GREEN)
                    .randomTicks()
                    .noCollission()
                    .instabreak()
                    .sound(SoundType.SWEET_BERRY_BUSH)
    ));
}
