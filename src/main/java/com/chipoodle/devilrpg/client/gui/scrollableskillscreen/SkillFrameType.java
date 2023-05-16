package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public enum SkillFrameType {
    TASK("task", 0, ChatFormatting.GREEN),
    CHALLENGE("challenge", 26, ChatFormatting.DARK_PURPLE),
    GOAL("goal", 52, ChatFormatting.GREEN);

    private final String name;
    private final int icon;
    private final ChatFormatting format;
    private final Component displayName;

    SkillFrameType(String nameIn, int iconIn, ChatFormatting formatIn) {
        this.name = nameIn;
        this.icon = iconIn;
        this.format = formatIn;
        this.displayName = Component.translatable("skills.toast." + nameIn);
    }

    public static SkillFrameType byName(String nameIn) {
        for (SkillFrameType frametype : values()) {
            if (frametype.name.equals(nameIn)) {
                return frametype;
            }
        }

        throw new IllegalArgumentException("Unknown frame type '" + nameIn + "'");
    }

    public String getName() {
        return this.name;
    }

    @OnlyIn(Dist.CLIENT)
    public int getIcon() {
        return this.icon;
    }

    public ChatFormatting getFormat() {
        return this.format;
    }

    @OnlyIn(Dist.CLIENT)
    public Component getDisplayName() {
        return this.displayName;
    }
}
