package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.blockentity.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Holds a list of all our {@link BlockEntityType}s. Suppliers that create
 * BlockEntityTypes are added to the DeferredRegister. The DeferredRegister is
 * then added to our mod event bus in our constructor.
 *
 * @author Cadiboo
 */
public final class ModEntityBlocks {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DevilRpg.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SoulVineBlockEntity>> SOUL_VINE_ENTITY_BLOCK = BLOCK_ENTITIES.register("soulvineentityblock",
            () -> BlockEntityType.Builder.of(SoulVineBlockEntity::new, ModBlocks.SOUL_VINE_BLOCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SoulShieldVineBlockEntity>> SOUL_SHIELD_VINE_ENTITY_BLOCK = BLOCK_ENTITIES.register("soulshieldvineentityblock",
            () -> BlockEntityType.Builder.of(SoulShieldVineBlockEntity::new, ModBlocks.SOUL_SHIELD_VINE_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SoulLichenBlockEntity>> SOUL_LICHEN_ENTITY_BLOCK = BLOCK_ENTITIES.register("soullichenentityblock",
            () -> BlockEntityType.Builder.of(SoulLichenBlockEntity::new, ModBlocks.SOUL_LICHEN_BLOCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SoulMinerVineBlockEntity>> SOUL_MINER_VINE_ENTITY_BLOCK = BLOCK_ENTITIES.register("soulminervineentityblock",
            () -> BlockEntityType.Builder.of(SoulMinerVineBlockEntity::new, ModBlocks.SOUL_MINER_VINE_BLOCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ManaBerryBlockEntity>> MANA_BERRY_ENTITY_BLOCK = BLOCK_ENTITIES.register("manaberrybushentityblock",
            () -> BlockEntityType.Builder.of(ManaBerryBlockEntity::new, ModBlocks.MANA_BERRY_BUSH_BLOCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BloomingSanctuaryBlockEntity>> BLOOMING_SANCTUARY_ENTITY_BLOCK = BLOCK_ENTITIES.register("bloomingsanctuaryentityblock",
            () -> BlockEntityType.Builder.of(BloomingSanctuaryBlockEntity::new, ModBlocks.BLOOMING_SANCTUARY_BLOCK.get()).build(null));
}
