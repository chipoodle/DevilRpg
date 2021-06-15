package com.chipoodle.devilrpg.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.block.DispenserBlock;
import net.minecraft.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.dispenser.IBlockSource;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Direction;
import net.minecraftforge.common.util.Lazy;
import net.minecraftforge.common.util.NonNullSupplier;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import net.minecraft.item.Item.Properties;

/**
 * Exists to work around a limitation with Spawn Eggs:
 * Spawn Eggs require an EntityType, but EntityTypes are created AFTER Items.
 * Therefore it is "impossible" for modded spawn eggs to exist.
 * This class gets around it by passing "null" to the SpawnEggItem constructor
 * and doing the initialisation after registry events have finished firing.
 * <p>
 * TODO: Remove once Forge adds this stuff in itself.
 *
 * @author Cadiboo
 */
public class ModdedSpawnEggItem extends SpawnEggItem {

	protected static final List<ModdedSpawnEggItem> UNADDED_EGGS = new ArrayList<>();
	private final Lazy<? extends EntityType<?>> entityTypeSupplier;

	public ModdedSpawnEggItem(final NonNullSupplier<? extends EntityType<?>> entityTypeSupplier, final int primaryColorIn, final int secondaryColorIn, final Properties builder) {
		super(null, primaryColorIn, secondaryColorIn, builder);
		this.entityTypeSupplier = Lazy.of(entityTypeSupplier::get);
		UNADDED_EGGS.add(this);
	}

	public ModdedSpawnEggItem(final RegistryObject<? extends EntityType<?>> entityTypeSupplier, final int primaryColorIn, final int secondaryColorIn, final Properties builder) {
		super(null, primaryColorIn, secondaryColorIn, builder);
		this.entityTypeSupplier = Lazy.of(entityTypeSupplier);
		UNADDED_EGGS.add(this);
	}

	/**
	 * Adds all the supplier based spawn eggs to vanilla's map and registers an
	 * IDispenseItemBehavior for each of them as normal spawn eggs have one
	 * registered for each of them during {@link net.minecraft.dispenser.IDispenseItemBehavior#init()}
	 * but supplier based ones won't have had their EntityTypes created yet.
	 */
	public static void initUnaddedEggs() {
		final Map<EntityType<?>, SpawnEggItem> EGGS = ObfuscationReflectionHelper.getPrivateValue(SpawnEggItem.class, null, "BY_ID");
		DefaultDispenseItemBehavior defaultDispenseItemBehavior = new DefaultDispenseItemBehavior() {
            @Override
			public ItemStack execute(IBlockSource source, ItemStack stack) {
            	
				EntityType<?> entitytype = ((SpawnEggItem) stack.getItem()).getType(stack.getTag());
				//////////////Direction direction = source.getBlockState().get(DispenserBlock.FACING);
				//////////////entitytype.spawn(source.getLevel(), stack, null, source.getPos().offset(direction), SpawnReason.DISPENSER, direction != Direction.UP, false);
				stack.shrink(1);
				return stack;
			}
		};
                UNADDED_EGGS.stream().map((egg) -> {
                    EGGS.put(egg.getType(null), egg);
                return egg;
            }).forEachOrdered((egg) -> {
                DispenserBlock.registerBehavior(egg, defaultDispenseItemBehavior);
                // ItemColors for each spawn egg don't need to be registered because this method is called before ItemColors is created
            });
		UNADDED_EGGS.clear();
	}

	@Override
	public EntityType<?> getType(@Nullable final CompoundNBT nbt) {
		return entityTypeSupplier.get();
	}

}
