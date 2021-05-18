package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class SkillDisplayInfo {
	   private final ITextComponent title;
	   private final ITextComponent description;
	   private final ItemStack icon;
	   private final ResourceLocation background;
	   private final ResourceLocation image;
	   private final ScrollableSkillFrameType frame;
	   private final boolean showToast;
	   private final boolean announceToChat;
	   private final boolean hidden;
	   private float x;
	   private float y;

	   public SkillDisplayInfo(ItemStack icon, ITextComponent title, ITextComponent description, @Nullable ResourceLocation background,@Nullable ResourceLocation image, ScrollableSkillFrameType frame, boolean showToast, boolean announceToChat, boolean hidden) {
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

	   public void setPosition(float x, float y) {
	      this.x = x;
	      this.y = y;
	   }

	   public ITextComponent getTitle() {
	      return this.title;
	   }

	   public ITextComponent getDescription() {
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

	   public ScrollableSkillFrameType getFrame() {
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

	   public static SkillDisplayInfo deserialize(JsonObject object) {
	      ITextComponent itextcomponent = ITextComponent.Serializer.getComponentFromJson(object.get("title"));
	      ITextComponent itextcomponent1 = ITextComponent.Serializer.getComponentFromJson(object.get("description"));
	      if (itextcomponent != null && itextcomponent1 != null) {
	         ItemStack itemstack = deserializeIcon(JSONUtils.getJsonObject(object, "icon"));
	         ResourceLocation background = object.has("background") ? new ResourceLocation(JSONUtils.getString(object, "background")) : null;
	         ResourceLocation image = object.has("image") ? new ResourceLocation(JSONUtils.getString(object, "image")) : null;
	         ScrollableSkillFrameType frametype = object.has("frame") ? ScrollableSkillFrameType.byName(JSONUtils.getString(object, "frame")) : ScrollableSkillFrameType.TASK;
	         boolean flag = JSONUtils.getBoolean(object, "show_toast", true);
	         boolean flag1 = JSONUtils.getBoolean(object, "announce_to_chat", true);
	         boolean flag2 = JSONUtils.getBoolean(object, "hidden", false);
	         return new SkillDisplayInfo(itemstack, itextcomponent, itextcomponent1, background,image, frametype, flag, flag1, flag2);
	      } else {
	         throw new JsonSyntaxException("Both title and description must be set");
	      }
	   }

	   private static ItemStack deserializeIcon(JsonObject object) {
	      if (!object.has("item")) {
	         throw new JsonSyntaxException("Unsupported icon type, currently only items are supported (add 'item' key)");
	      } else {
	         Item item = JSONUtils.getItem(object, "item");
	         if (object.has("data")) {
	            throw new JsonParseException("Disallowed data tag found");
	         } else {
	            ItemStack itemstack = new ItemStack(item);
	            if (object.has("nbt")) {
	               try {
	                  CompoundNBT compoundnbt = JsonToNBT.getTagFromJson(JSONUtils.getString(object.get("nbt"), "nbt"));
	                  itemstack.setTag(compoundnbt);
	               } catch (CommandSyntaxException commandsyntaxexception) {
	                  throw new JsonSyntaxException("Invalid nbt tag: " + commandsyntaxexception.getMessage());
	               }
	            }

	            return itemstack;
	         }
	      }
	   }

	   public void write(PacketBuffer buf) {
	      buf.writeTextComponent(this.title);
	      buf.writeTextComponent(this.description);
	      buf.writeItemStack(this.icon);
	      buf.writeEnumValue(this.frame);
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

	   public static SkillDisplayInfo read(PacketBuffer buf) {
	      ITextComponent itextcomponent = buf.readTextComponent();
	      ITextComponent itextcomponent1 = buf.readTextComponent();
	      ItemStack itemstack = buf.readItemStack();
	      ScrollableSkillFrameType frametype = buf.readEnumValue(ScrollableSkillFrameType.class);
	      int i = buf.readInt();
	      ResourceLocation background = (i & 1) != 0 ? buf.readResourceLocation() : null;
	      //TODO: Revisar si esta conversión con el bit es correcta o se tiene que recorrer, después del background
	      ResourceLocation image = (i & 2) != 0 ? buf.readResourceLocation() : null;
	      boolean flag = (i & 4) != 0;
	      boolean flag1 = (i & 8) != 0;
	      SkillDisplayInfo displayinfo = new SkillDisplayInfo(itemstack, itextcomponent, itextcomponent1, background,image, frametype, flag, false, flag1);
	      displayinfo.setPosition(buf.readFloat(), buf.readFloat());
	      return displayinfo;
	   }

	   public JsonElement serialize() {
	      JsonObject jsonobject = new JsonObject();
	      jsonobject.add("icon", this.serializeIcon());
	      jsonobject.add("title", ITextComponent.Serializer.toJsonTree(this.title));
	      jsonobject.add("description", ITextComponent.Serializer.toJsonTree(this.description));
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
	      jsonobject.addProperty("item", Registry.ITEM.getKey(this.icon.getItem()).toString());
	      if (this.icon.hasTag()) {
	         jsonobject.addProperty("nbt", this.icon.getTag().toString());
	      }

	      return jsonobject;
	   }
	}

