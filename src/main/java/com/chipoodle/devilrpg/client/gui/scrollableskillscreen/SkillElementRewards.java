package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import java.util.List;

import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.command.FunctionObject;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootContext;
import net.minecraft.loot.LootParameterSets;
import net.minecraft.loot.LootParameters;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;

public class SkillElementRewards {
	public static final SkillElementRewards EMPTY = new SkillElementRewards(0, new ResourceLocation[0],
			new ResourceLocation[0], FunctionObject.CacheableFunction.NONE);
	private final int experience;
	private final ResourceLocation[] loot;
	private final ResourceLocation[] recipes;
	private final FunctionObject.CacheableFunction function;

	public SkillElementRewards(int experience, ResourceLocation[] loot, ResourceLocation[] recipes, FunctionObject.CacheableFunction function) {
	      this.experience = experience;
	      this.loot = loot;
	      this.recipes = recipes;
	      this.function = function;
	   }

	public void apply(ServerPlayerEntity player) {
		player.giveExperiencePoints(this.experience);
		LootContext lootcontext = (new LootContext.Builder(player.getLevel()))
				.withParameter(LootParameters.THIS_ENTITY, player)
				.withParameter(LootParameters.ORIGIN, player.position())
				.withRandom(player.getRandom())
				.withLuck(player.getLuck())
				.create(LootParameterSets.ADVANCEMENT_REWARD); // FORGE: luck to LootContext
		boolean flag = false;

		for (ResourceLocation resourcelocation : this.loot) {
			for (ItemStack itemstack : player.server.getLootTables().get(resourcelocation)
					.getRandomItems(lootcontext)) {
				if (player.addItem(itemstack)) {
					player.level.playSound(null, player.getX(), player.getY(), player.getZ(),
							SoundEvents.ITEM_PICKUP, SoundCategory.PLAYERS, 0.2F,
							((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F);
					flag = true;
				} else {
					ItemEntity itementity = player.drop(itemstack, false);
					if (itementity != null) {
						itementity.setNoPickUpDelay();
						itementity.setOwner(player.getUUID());
					}
				}
			}
		}

		if (flag) {
			player.inventoryMenu.broadcastChanges();
		}

		if (this.recipes.length > 0) {
			player.awardRecipesByKey(this.recipes);
		}

		MinecraftServer minecraftserver = player.server;
		this.function.get(minecraftserver.getFunctions()).ifPresent((commandFunction) -> {
			minecraftserver.getFunctions().execute(commandFunction,
					player.createCommandSourceStack().withSuppressedOutput().withPermission(2));
		});
	}

	public String toString() {
		return "";/*"SkillElementRewards{experience=" + this.experience + ", loot=" + Arrays.toString((Object[]) this.loot)
				+ ", recipes=" + Arrays.toString((Object[]) this.recipes) + ", function=" + this.function + '}';*/
	}

	public JsonElement serialize() {
		if (this == EMPTY) {
			return JsonNull.INSTANCE;
		} else {
			JsonObject jsonobject = new JsonObject();
			if (this.experience != 0) {
				jsonobject.addProperty("experience", this.experience);
			}

			if (this.loot.length > 0) {
				JsonArray jsonarray = new JsonArray();

				for (ResourceLocation resourcelocation : this.loot) {
					jsonarray.add(resourcelocation.toString());
				}

				jsonobject.add("loot", jsonarray);
			}

			if (this.recipes.length > 0) {
				JsonArray jsonarray1 = new JsonArray();

				for (ResourceLocation resourcelocation1 : this.recipes) {
					jsonarray1.add(resourcelocation1.toString());
				}

				jsonobject.add("recipes", jsonarray1);
			}

			if (this.function.getId() != null) {
				jsonobject.addProperty("function", this.function.getId().toString());
			}

			return jsonobject;
		}
	}

	public static SkillElementRewards deserializeRewards(JsonObject json) throws JsonParseException {
		int i = JSONUtils.getAsInt(json, "experience", 0);
		JsonArray jsonarray = JSONUtils.getAsJsonArray(json, "loot", new JsonArray());
		ResourceLocation[] aresourcelocation = new ResourceLocation[jsonarray.size()];

		for (int j = 0; j < aresourcelocation.length; ++j) {
			aresourcelocation[j] = new ResourceLocation(JSONUtils.convertToString(jsonarray.get(j), "loot[" + j + "]"));
		}

		JsonArray jsonarray1 = JSONUtils.getAsJsonArray(json, "recipes", new JsonArray());
		ResourceLocation[] aresourcelocation1 = new ResourceLocation[jsonarray1.size()];

		for (int k = 0; k < aresourcelocation1.length; ++k) {
			aresourcelocation1[k] = new ResourceLocation(JSONUtils.convertToString(jsonarray1.get(k), "recipes[" + k + "]"));
		}

		FunctionObject.CacheableFunction functionobject$cacheablefunction;
		if (json.has("function")) {
			functionobject$cacheablefunction = new FunctionObject.CacheableFunction(
					new ResourceLocation(JSONUtils.getAsString(json, "function")));
		} else {
			functionobject$cacheablefunction = FunctionObject.CacheableFunction.NONE;
		}

		return new SkillElementRewards(i, aresourcelocation, aresourcelocation1, functionobject$cacheablefunction);
	}

	public static class Builder {
		private int experience;
		private final List<ResourceLocation> loot = Lists.newArrayList();
		private final List<ResourceLocation> recipes = Lists.newArrayList();
		@Nullable
		private ResourceLocation function;

		/**
		 * Creates a new builder with the given amount of experience as a reward
		 */
		public static SkillElementRewards.Builder experience(int experienceIn) {
			return (new SkillElementRewards.Builder()).addExperience(experienceIn);
		}

		/**
		 * Adds the given amount of experience. (Not a direct setter)
		 */
		public SkillElementRewards.Builder addExperience(int experienceIn) {
			this.experience += experienceIn;
			return this;
		}

		/**
		 * Creates a new builder with the given recipe as a reward.
		 */
		public static SkillElementRewards.Builder recipe(ResourceLocation recipeIn) {
			return (new SkillElementRewards.Builder()).addRecipe(recipeIn);
		}

		/**
		 * Adds the given recipe to the rewards.
		 */
		public SkillElementRewards.Builder addRecipe(ResourceLocation recipeIn) {
			this.recipes.add(recipeIn);
			return this;
		}

		public SkillElementRewards build() {
			return new SkillElementRewards(this.experience, this.loot.toArray(new ResourceLocation[0]),
					this.recipes.toArray(new ResourceLocation[0]),
					this.function == null ? FunctionObject.CacheableFunction.NONE
							: new FunctionObject.CacheableFunction(this.function));
		}
	}
}
