package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

public class SkillDisplayInfo {
    private final Component title;
    private final Component description;
    private final ItemStack icon;
    private final ResourceLocation background;
    private final ResourceLocation image;
    private final SkillFrameType frame;
    private final boolean showToast;
    private final boolean announceToChat;
    private final boolean hidden;
    private float x;
    private float y;

    public SkillDisplayInfo(ItemStack icon, Component title, Component description, @Nullable ResourceLocation background, @Nullable ResourceLocation image, SkillFrameType frame, boolean showToast, boolean announceToChat, boolean hidden) {
        this.title = title;
        this.description = description;
        this.icon = icon;
        this.background = background;
        this.image = image;
        this.frame = frame;
        this.showToast = showToast;
        this.announceToChat = announceToChat;
        this.hidden = hidden;
    }

    public static SkillDisplayInfo deserialize(JsonObject object) {
        Component itextcomponent = Component.Serializer.fromJson(object.get("title"));
        Component itextcomponent1 = Component.Serializer.fromJson(object.get("description"));
        if (itextcomponent != null && itextcomponent1 != null) {
            ItemStack itemstack = deserializeIcon(GsonHelper.getAsJsonObject(object, "icon"));
            ResourceLocation background = object.has("background") ? new ResourceLocation(GsonHelper.getAsString(object, "background")) : null;
            ResourceLocation image = object.has("image") ? new ResourceLocation(GsonHelper.getAsString(object, "image")) : null;
            SkillFrameType frametype = object.has("frame") ? SkillFrameType.byName(GsonHelper.getAsString(object, "frame")) : SkillFrameType.TASK;
            boolean flag = GsonHelper.getAsBoolean(object, "show_toast", true);
            boolean flag1 = GsonHelper.getAsBoolean(object, "announce_to_chat", true);
            boolean flag2 = GsonHelper.getAsBoolean(object, "hidden", false);
            return new SkillDisplayInfo(itemstack, itextcomponent, itextcomponent1, background, image, frametype, flag, flag1, flag2);
        } else {
            throw new JsonSyntaxException("Both title and description must be set");
        }
    }

    private static ItemStack deserializeIcon(JsonObject object) {
        if (!object.has("item")) {
            throw new JsonSyntaxException("Unsupported icon type, currently only items are supported (add 'item' key)");
        } else {
            Item item = GsonHelper.getAsItem(object, "item");
            if (object.has("data")) {
                throw new JsonParseException("Disallowed data tag found");
            } else {
                ItemStack itemstack = new ItemStack(item);
                if (object.has("nbt")) {
                    try {
                        CompoundTag compoundnbt = TagParser.parseTag(GsonHelper.convertToString(object.get("nbt"), "nbt"));
                        itemstack.setTag(compoundnbt);
                    } catch (CommandSyntaxException commandsyntaxexception) {
                        throw new JsonSyntaxException("Invalid nbt tag: " + commandsyntaxexception.getMessage());
                    }
                }

                return itemstack;
            }
        }
    }

    public static SkillDisplayInfo read(FriendlyByteBuf buf) {
        Component itextcomponent = buf.readComponent();
        Component itextcomponent1 = buf.readComponent();
        ItemStack itemstack = buf.readItem();
        SkillFrameType frametype = buf.readEnum(SkillFrameType.class);
        int i = buf.readInt();
        ResourceLocation background = (i & 1) != 0 ? buf.readResourceLocation() : null;
        //TODO: Revisar si esta conversión con el bit es correcta o se tiene que recorrer, después del background
        ResourceLocation image = (i & 2) != 0 ? buf.readResourceLocation() : null;
        boolean flag = (i & 4) != 0;
        boolean flag1 = (i & 8) != 0;
        SkillDisplayInfo displayinfo = new SkillDisplayInfo(itemstack, itextcomponent, itextcomponent1, background, image, frametype, flag, false, flag1);
        displayinfo.setPosition(buf.readFloat(), buf.readFloat());
        return displayinfo;
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public Component getTitle() {
        return this.title;
    }

    public Component getDescription() {
        return this.description;
    }

    @OnlyIn(Dist.CLIENT)
    public ItemStack getIcon() {
        return this.icon;
    }

    @Nullable
    @OnlyIn(Dist.CLIENT)
    public ResourceLocation getBackground() {
        return this.background;
    }

    @Nullable
    @OnlyIn(Dist.CLIENT)
    public ResourceLocation getImage() {
        return this.image;
    }

    public SkillFrameType getFrame() {
        return this.frame;
    }

    @OnlyIn(Dist.CLIENT)
    public float getX() {
        return this.x;
    }

    @OnlyIn(Dist.CLIENT)
    public float getY() {
        return this.y;
    }

    @OnlyIn(Dist.CLIENT)
    public boolean shouldShowToast() {
        return this.showToast;
    }

    public boolean shouldAnnounceToChat() {
        return this.announceToChat;
    }

    public boolean isHidden() {
        return this.hidden;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeComponent(this.title);
        buf.writeComponent(this.description);
        buf.writeItem(this.icon);
        buf.writeEnum(this.frame);
        int i = 0;
        if (this.background != null) {
            i |= 1;
        }
        if (this.image != null) {
            i |= 2;
        }

        if (this.showToast) {
            i |= 4;
        }

        if (this.hidden) {
            i |= 8;
        }

        buf.writeInt(i);
        if (this.background != null) {
            buf.writeResourceLocation(this.background);
        }
        if (this.image != null) {
            buf.writeResourceLocation(this.image);
        }

        buf.writeFloat(this.x);
        buf.writeFloat(this.y);
    }

    public JsonElement serialize() {
        JsonObject jsonobject = new JsonObject();
        jsonobject.add("icon", this.serializeIcon());
        jsonobject.add("title", Component.Serializer.toJsonTree(this.title));
        jsonobject.add("description", Component.Serializer.toJsonTree(this.description));
        jsonobject.addProperty("frame", this.frame.getName());
        jsonobject.addProperty("show_toast", this.showToast);
        jsonobject.addProperty("announce_to_chat", this.announceToChat);
        jsonobject.addProperty("hidden", this.hidden);
        if (this.background != null) {
            jsonobject.addProperty("background", this.background.toString());
        }
        if (this.image != null) {
            jsonobject.addProperty("image", this.image.toString());
        }

        return jsonobject;
    }

    private JsonObject serializeIcon() {
        JsonObject jsonobject = new JsonObject();
        jsonobject.addProperty("item", BuiltInRegistries.ITEM.getKey(this.icon.getItem()).toString());
        if (this.icon.hasTag()) {
            jsonobject.addProperty("nbt", this.icon.getTag().toString());
        }

        return jsonobject;
    }
}

