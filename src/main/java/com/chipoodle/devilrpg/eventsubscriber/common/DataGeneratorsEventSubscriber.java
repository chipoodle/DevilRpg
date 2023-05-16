package com.chipoodle.devilrpg.eventsubscriber.common;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.init.ModBlockTags;
import net.minecraft.data.DataGenerator;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(modid = DevilRpg.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DataGeneratorsEventSubscriber {
    private DataGeneratorsEventSubscriber() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DevilRpg.LOGGER.info("----------------------->DataGeneratorsEventSubscriber.GatherDataEvent()");

        DataGenerator gen = event.getGenerator();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        ModBlockTags blockTags = new ModBlockTags(
                event.getGenerator().getPackOutput(),
                event.getLookupProvider(),
                DevilRpg.MODID,
                existingFileHelper);

        /* ModBlockTags blockTags = new ModBlockTags(gen, existingFileHelper);*/
        gen.addProvider(true, blockTags);
    }

}
