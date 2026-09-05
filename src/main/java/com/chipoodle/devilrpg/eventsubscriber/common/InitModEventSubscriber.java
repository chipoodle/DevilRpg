package com.chipoodle.devilrpg.eventsubscriber.common;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.config.ConfigHelper;
import com.chipoodle.devilrpg.config.ConfigHolder;
import com.chipoodle.devilrpg.entity.*;
import com.chipoodle.devilrpg.init.ModBlocks;
import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.init.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Supplier;

import static com.chipoodle.devilrpg.init.ModBlocks.BLOCKS;
import static com.chipoodle.devilrpg.init.ModItems.*;


/**
 * Subscribe to events from the MOD EventBus that should be handled on both
 * PHYSICAL sides in this class
 *
 * @author Cadiboo
 */
@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class InitModEventSubscriber {


    @SubscribeEvent
    public static void initEntityAttributes(EntityAttributeCreationEvent event) {
        DevilRpg.LOGGER.info("----------------------->InitModEventSubscriber.initEntityAttributes()");
        event.put(ModEntities.SOUL_WOLF.get(), SoulWolf.setAttributes().build());
        event.put(ModEntities.SOUL_BEAR.get(), SoulBear.setAttributes().build());
        //event.put(ModEntities.WISP.get(), SoulWisp.setAttributes().build());
        event.put(ModEntities.WISP_HEALTH.get(), SoulWispHealth.setAttributes().build());
        event.put(ModEntities.WISP_ARCHER.get(), SoulWispArcher.setAttributes().build());
        event.put(ModEntities.WISP_CHOPPER.get(), SoulWispChopper.setAttributes().build());
        event.put(ModEntities.WISP_FORESTER.get(), SoulWispForester.setAttributes().build());
        event.put(ModEntities.SUNFLOWER_SHULKER.get(), SunflowerShulker.createAttributes().build());
        event.put(ModEntities.EXPLODING_SPORE_BULLET.get(),ExplodingSporeBullet.createAttributes().build());
        event.put(ModEntities.AGGRESSIVE_ZOMBIE.get(), AggressiveZombieEntity.setAttributes().build());

    }

    @SubscribeEvent
    public static void updateEntityAttributes(EntityAttributeModificationEvent event) {
        DevilRpg.LOGGER.info("----------------------->InitModEventSubscriber.updateEntityAttributes()");
        /*if (!event.has(EntityType.CREEPER, EXAMPLE_ATTRIBUTE.get())) {
            event.add(EntityType.CREEPER,
                    EXAMPLE_ATTRIBUTE.get() // Applies new attribute to creeper
            );
        }*/
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        DevilRpg.LOGGER.info("----------------------->InitModEventSubscriber.onCommonSetup()");

        event.enqueueWork(() -> {
            try {
                // Obtener el bloque de fuego
                FireBlock fireBlock = (FireBlock) Blocks.FIRE;

                // Obtener el método privado setFlammable mediante reflexión
                Method setFlammableMethod = FireBlock.class.getDeclaredMethod("setFlammable", Block.class, int.class, int.class);

                // Hacer el método accesible
                setFlammableMethod.setAccessible(true);

                // Invocar el método con los parámetros correctos
                setFlammableMethod.invoke(fireBlock, ModBlocks.SOUL_VINE_BLOCK.get(), 60, 100);
                setFlammableMethod.invoke(fireBlock, ModBlocks.SOUL_MINER_VINE_BLOCK.get(), 60, 100);
                setFlammableMethod.invoke(fireBlock, ModBlocks.SOUL_SHIELD_VINE_BLOCK.get(), 60, 100);
                setFlammableMethod.invoke(fireBlock, ModBlocks.BLOOMING_SANCTUARY_BLOCK.get(), 60, 100);
                setFlammableMethod.invoke(fireBlock, ModBlocks.UPWARD_SPORE_BLOSSOM_BLOCK.get(), 60, 100);

                DevilRpg.LOGGER.info("onCommonSetup FMLCommonSetupEvent SOUL_VINE_BLOCK, SOUL_MINER_VINE_BLOCK, SOUL_SHIELD_VINE_BLOCK, BLOOMING_SANCTUARY_BLOCK are now flammables.");
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                DevilRpg.LOGGER.error("Error making SOUL_VINE_BLOCK, SOUL_MINER_VINE_BLOCK, SOUL_SHIELD_VINE_BLOCK flammables", e);
            }
        });

        //event.enqueueWork(ModEntities::registerSpawnPlacements);
    }

    /**
     * This method will be called by Forge when a config changes.
     *
     * @param event
     */
    @SubscribeEvent
    public static void onModConfigEvent(final ModConfigEvent.Loading event) {
        DevilRpg.LOGGER.info("----------------------->InitModEventSubscriber.onModConfigEvent()");
        final ModConfig config = event.getConfig();
        // Rebake the configs when they change
        if (config.getSpec() == ConfigHolder.CLIENT_SPEC) {
            ConfigHelper.bakeClient(config);
            DevilRpg.LOGGER.debug("Baked client config");
        } else if (config.getSpec() == ConfigHolder.SERVER_SPEC) {
            ConfigHelper.bakeServer(config);
            DevilRpg.LOGGER.debug("Baked server config");
        }
    }

    /**
     * This method will be called by Forge when it is time for the mod to register
     * its Items. This method will always be called after the Block registry method.
     *
     * @param event
     */
    @SubscribeEvent
    public static void onRegisterItems(final RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.ITEM)) {
            DevilRpg.LOGGER.info("----------------------->InitModEventSubscriber.onRegisterItems()");
            BLOCKS.getEntries().forEach((blockRegistryObject) -> {
                Block block = blockRegistryObject.get();
                Item.Properties properties = new Item.Properties();
                Supplier<Item> blockItemFactory = () -> new BlockItem(block, properties);
                event.register(Registries.ITEM, blockRegistryObject.getId(), blockItemFactory);
            });
        }
    }

    /**
     * event that allows an item can be added to a CreativeModeTab via CreativeModeTabEvent$BuildContents on the mod event bus. An item(s) can be added without any additional configurations via #accept.
     *
     * @param event
     */
    /*@SubscribeEvent
    public static void onCreativeModeTabEvent$BuildContents(final CreativeModeTabEvent.BuildContents event) {
        if (event.getTab() == CreativeModeTabs.COMBAT) {
            event.accept(PORTAL_ITEM);
        }

    }*/

    // Creative tab is registered in ModCreativeTabs via the vanilla CREATIVE_MODE_TAB registry.

    @SubscribeEvent
    public static void onSpawnPlacementRegister(RegisterSpawnPlacementsEvent event) {
        DevilRpg.LOGGER.info("----------------------->InitModEventSubscriber.onSpawnPlacementRegister()");
        // El spawn natural de vanilla esta DESACTIVADO para las entidades custom: ahora las
        // spawnea el CustomSpawner (CustomSpawnerTickHandler) segun sus reglas propias.
        // Si mas adelante quieres que alguna entidad vuelva a spawnear de forma natural,
        // descomenta el registro correspondiente:
        /*
        event.register(
                ModEntities.AGGRESSIVE_ZOMBIE.get(), // Tu entidad registrada
                SpawnPlacementTypes.ON_GROUND, // Tipo de spawn (en el suelo)
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, // Altura del spawn
                AggressiveZombieEntity::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE // Para una entidad modded nueva: REPLACE setea el placement (AND solo combina con uno ya existente)
        );
        */
    }

}
