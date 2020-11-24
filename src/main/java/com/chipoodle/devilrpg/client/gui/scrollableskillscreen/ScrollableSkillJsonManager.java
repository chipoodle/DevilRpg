package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import java.util.Collection;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.client.resources.JsonReloadListener;
import net.minecraft.profiler.IProfiler;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.ResourceLocation;

public class ScrollableSkillJsonManager extends JsonReloadListener {
		   private static final Logger LOGGER = LogManager.getLogger();
		   private static final Gson GSON = (new GsonBuilder()).create();
		   private ScrollableSkillList advancementList = new ScrollableSkillList();
		  // private final LootPredicateManager lootPredicateManager;

		   public ScrollableSkillJsonManager(/*LootPredicateManager lootPredicateManager*/) {
		      super(GSON, "skills");
		      //this.lootPredicateManager = lootPredicateManager;
		   }

		   protected void apply(Map<ResourceLocation, JsonElement> objectIn, IResourceManager resourceManagerIn, IProfiler profilerIn) {
		      Map<ResourceLocation, SkillElement.Builder> map = Maps.newHashMap();
		      objectIn.forEach((conditions, advancement) -> {
		         try {
		            JsonObject jsonobject = JSONUtils.getJsonObject(advancement, "advancement");
		            SkillElement.Builder advancement$builder = SkillElement.Builder.deserialize(jsonobject);
		            if (advancement$builder == null) {
		                LOGGER.debug("Skipping loading advancement {} as it's conditions were not met", conditions);
		                return;
		            }
		            map.put(conditions, advancement$builder);
		         } catch (IllegalArgumentException | JsonParseException jsonparseexception) {
		            LOGGER.error("Parsing error loading custom advancement {}: {}", conditions, jsonparseexception.getMessage());
		         }

		      });
		      ScrollableSkillList advancementlist = new ScrollableSkillList();
		      advancementlist.loadSkills(map);

		      for(SkillElement advancement : advancementlist.getRoots()) {
		         if (advancement.getDisplay() != null) {
		            ScrollableSkillTreeNode.layout(advancement);
		         }
		      }

		      this.advancementList = advancementlist;
		   }

		   @Nullable
		   public SkillElement getAdvancement(ResourceLocation id) {
		      return this.advancementList.getAdvancement(id);
		   }

		   public Collection<SkillElement> getAllAdvancements() {
		      return this.advancementList.getAll();
		   }
}
