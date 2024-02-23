package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.blockentity.SoulLichenBlockEntity;
import com.chipoodle.devilrpg.blockentity.SoulVineBlockEntity;
import net.minecraft.world.level.block.Block;
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
public final class ModEntityBlocks {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, DevilRpg.MODID);

    public static final RegistryObject<BlockEntityType<SoulVineBlockEntity>> SOUL_VINE_ENTITY_BLOCK = BLOCK_ENTITIES.register("soulvineentityblock",
            () -> BlockEntityType.Builder.of(SoulVineBlockEntity::new, ModBlocks.SOUL_VINE_BLOCK.get()).build(null));
    public static final RegistryObject<BlockEntityType<SoulLichenBlockEntity>> SOUL_LICHEN_ENTITY_BLOCK = BLOCK_ENTITIES.register("soullichenentityblock",
            () -> BlockEntityType.Builder.of(SoulLichenBlockEntity::new, ModBlocks.SOUL_LICHEN_BLOCK.get()).build(null));
}
