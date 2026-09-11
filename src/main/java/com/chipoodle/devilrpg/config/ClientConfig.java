package com.chipoodle.devilrpg.config;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * For configuration settings that change the behaviour of code on the LOGICAL
 * CLIENT. This can be moved to an inner class of ExampleModConfig, but is
 * separate because of personal preference and to keep the code organised
 *
 * @author Cadiboo
 */
final class ClientConfig {

    final ModConfigSpec.BooleanValue clientBoolean;
    final ModConfigSpec.ConfigValue<List<String>> clientStringList;
    final ModConfigSpec.EnumValue<DyeColor> clientDyeColorEnum;

    final ModConfigSpec.BooleanValue modelTranslucency;
    final ModConfigSpec.DoubleValue modelScale;

    /** Fondo del árbol de habilidades elegido con los botones (nombre de archivo; vacío = por defecto). */
    final ModConfigSpec.ConfigValue<String> skillBackground;
    /** Skin de widget de los nodos elegido con los botones (nombre de archivo; vacío = por defecto). */
    final ModConfigSpec.ConfigValue<String> skillWidgetSkin;

    ClientConfig(final ModConfigSpec.Builder builder) {
        builder.push("general");
        clientBoolean = builder
                .comment("An example boolean in the client config")
                .translation(DevilRpg.MODID + ".config.clientBoolean")
                .define("clientBoolean", true);
        clientStringList = builder
                .comment("An example list of Strings in the client config")
                .translation(DevilRpg.MODID + ".config.clientStringList")
                .define("clientStringList", new ArrayList<>());
        clientDyeColorEnum = builder
                .comment("An example DyeColor enum in the client config")
                .translation(DevilRpg.MODID + ".config.clientDyeColorEnum")
                .defineEnum("clientDyeColorEnum", DyeColor.WHITE);

        modelTranslucency = builder
                .comment("If the model should be rendered translucent")
                .translation(DevilRpg.MODID + ".config.modelTranslucency")
                .define("modelTranslucency", true);
        modelScale = builder
                .comment("The scale to render the model at")
                .translation(DevilRpg.MODID + ".config.modelScale")
                .defineInRange("modelScale", 0.0625F, 0.0001F, 100F);
        builder.pop();

        // Apariencia del árbol de habilidades. Lo escriben los botones de la propia pantalla de skills.
        builder.push("skills_ui");
        skillBackground = builder
                .comment("Fondo del arbol de habilidades (nombre del archivo de textures/gui/mandalas).",
                        "Vacio = el fondo por defecto. Lo cambian los botones <- -> de la pantalla de skills.")
                .translation(DevilRpg.MODID + ".config.skillBackground")
                .define("skillBackground", "");
        skillWidgetSkin = builder
                .comment("Skin de widget de los nodos del arbol (nombre del archivo de textures/gui/skill/widget).",
                        "Vacio = el skin por defecto. Lo cambian los botones <- -> y 'Def' de la pantalla de skills.")
                .translation(DevilRpg.MODID + ".config.skillWidgetSkin")
                .define("skillWidgetSkin", "");
        builder.pop();
    }

}
