package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.block.Block;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DataSerializerEntry;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Holds a list of all our {@link net.minecraft.block.BlockState}s. Suppliers that create Blocks are
 * added to the DeferredRegister. The DeferredRegister is then added to our mod
 * event bus in our constructor. When the Block Registry Event is fired by Forge
 * and it is time for the mod to register its Blocks, our Blocks are created and
 * registered by the DeferredRegister. The Block Registry Event will always be
 * called before the Item registry is filled. Note: This supports registry
 * overrides.
 *
 * @author Cadiboo
 */
public final class ModBlockStates {

    public static final DeferredRegister<DataSerializerEntry> BLOCK_STATES = DeferredRegister.create(ForgeRegistries.DATA_SERIALIZERS, DevilRpg.MODID);


}
