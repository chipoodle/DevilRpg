package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.block.SoulVineBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
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
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, DevilRpg.MODID);
    // This block has the ROCK material, meaning it needs at least a wooden pickaxe to break it. It is very similar to Iron Ore
    // public static final RegistryObject<Block> EXAMPLE_ORE = BLOCKS.register("example_ore", () -> new Block(Block.Properties.of(Material.STONE).strength(3.0F, 3.0F)));
    // This block has the IRON material, meaning it needs at least a stone pickaxe to break it. It is very similar to the Iron Block
    // public static final RegistryObject<Block> EXAMPLE_BLOCK = BLOCKS.register("example_block", () -> new Block(Block.Properties.of(Material.METAL, MaterialColor.METAL).strength(5.0F, 6.0F).sound(SoundType.METAL)));

    public static final RegistryObject<SoundEvent> METAL_SWORD_SOUND = SOUND_EVENTS.register("metal_sword_sound", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(DevilRpg.MODID, "metal_sword_sound")));

    /*public static final TagKey<Block> SOULVINE_TAG = BlockTags.create(new ResourceLocation(DevilRpg.MODID,"climbable"));*/
}
