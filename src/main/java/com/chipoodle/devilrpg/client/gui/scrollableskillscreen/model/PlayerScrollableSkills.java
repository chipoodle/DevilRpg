package com.chipoodle.devilrpg.client.gui.scrollableskillscreen.model;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.SkillElement;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.SkillProgress;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.reflect.TypeToken;
import com.mojang.datafixers.DataFixer;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.ResourceLocation;

public class PlayerScrollableSkills {
	private static final Logger LOGGER = LogManager.getLogger();
	/*private static final Gson GSON = (new GsonBuilder())
			.registerTypeAdapter(SkillProgress.class, new SkillProgress.Serializer())
			.registerTypeAdapter(ResourceLocation.class, new ResourceLocation.Serializer()).setPrettyPrinting()
			.create();*/
	private static final TypeToken<Map<ResourceLocation, SkillProgress>> MAP_TOKEN = new TypeToken<Map<ResourceLocation, SkillProgress>>() {
	};
	private final DataFixer dataFixer;
	private final PlayerList playerList;
	private final File progressFile;
	private final Map<SkillElement, SkillProgress> progress = Maps.newLinkedHashMap();
	private final Set<SkillElement> visible = Sets.newLinkedHashSet();
	private final Set<SkillElement> visibilityChanged = Sets.newLinkedHashSet();
	private final Set<SkillElement> progressChanged = Sets.newLinkedHashSet();
	private ServerPlayerEntity player;
	@Nullable
	private SkillElement lastSelectedTab;
	private boolean isFirstPacket = true;

	public PlayerScrollableSkills(DataFixer dataFixer, PlayerList playerList, ScrollableSkillJsonManager advancementManager, File progressFile, ServerPlayerEntity player) {
		this.dataFixer = dataFixer;
		this.playerList = playerList;
		this.progressFile = progressFile;
		this.player = player;
		//this.deserialize(advancementManager);
		DevilRpg.LOGGER.info("|-----"+ this);
	}

	public void setPlayer(ServerPlayerEntity player) {
		this.player = player;
	}

	public void dispose() {
		/*
		 * for (ICriterionTrigger<?> icriteriontrigger : CriteriaTriggers.getAll()) {
		 * icriteriontrigger.removeAllListeners(this); }
		 */

	}

	public void reset(ScrollableSkillJsonManager manager) {
		this.dispose();
		this.progress.clear();
		this.visible.clear();
		this.visibilityChanged.clear();
		this.progressChanged.clear();
		this.isFirstPacket = true;
		this.lastSelectedTab = null;
		//this.deserialize(manager);
	}

	private void registerAchievementListeners(ScrollableSkillJsonManager manager) {
		for (SkillElement advancement : manager.getAllAdvancements()) {
			this.registerListeners(advancement);
		}

	}

	private void ensureAllVisible() {
		List<SkillElement> list = Lists.newArrayList();

		for (Entry<SkillElement, SkillProgress> entry : this.progress.entrySet()) {
			if (entry.getValue().isDone()) {
				list.add(entry.getKey());
				this.progressChanged.add(entry.getKey());
			}
		}

		for (SkillElement advancement : list) {
			this.ensureVisibility(advancement);
		}

	}

	private void unlockDefaultAdvancements(ScrollableSkillJsonManager manager) {
		for (SkillElement advancement : manager.getAllAdvancements()) {
			//this.grantCriterion(advancement, "");
			advancement.getRewards().apply(this.player);
		}

	}

	/*private void deserialize(ScrollableSkillJsonManager manager) {
		if (this.progressFile.isFile()) {
			try (JsonReader jsonreader = new JsonReader(
					new StringReader(Files.toString(this.progressFile, StandardCharsets.UTF_8)))) {
				jsonreader.setLenient(false);
				Dynamic<JsonElement> dynamic = new Dynamic<>(JsonOps.INSTANCE, Streams.parse(jsonreader));
				if (!dynamic.get("DataVersion").asNumber().result().isPresent()) {
					dynamic = dynamic.set("DataVersion", dynamic.createInt(1343));
				}

				dynamic = this.dataFixer.update(DefaultTypeReferences.ADVANCEMENTS.getType(), dynamic,
						dynamic.get("DataVersion").asInt(0), SharedConstants.getCurrentVersion().getWorldVersion());
				dynamic = dynamic.remove("DataVersion");
				Map<ResourceLocation, SkillProgress> map = GSON.getAdapter(MAP_TOKEN)
						.fromJsonTree(dynamic.getValue());
				if (map == null) {
					throw new JsonParseException("Found null for advancements");
				}

				Stream<Entry<ResourceLocation, SkillProgress>> stream = map.entrySet().stream()
						.sorted(Comparator.comparing(Entry::getValue));

				for (Entry<ResourceLocation, SkillProgress> entry : stream.collect(Collectors.toList())) {
					SkillElement advancement = manager.getAdvancement(entry.getKey());
					if (advancement == null) {
						LOGGER.warn("Ignored advancement '{}' in progress file {} - it doesn't exist anymore?",
								entry.getKey(), this.progressFile);
					} else {
						this.startProgress(advancement, entry.getValue());
					}
				}
			} catch (JsonParseException jsonparseexception) {
				LOGGER.error("Couldn't parse player advancements in {}", this.progressFile, jsonparseexception);
			} catch (IOException ioexception) {
				LOGGER.error("Couldn't access player advancements in {}", this.progressFile, ioexception);
			}
		}

		this.unlockDefaultAdvancements(manager);

		if (net.minecraftforge.common.ForgeConfig.SERVER.fixAdvancementLoading.get())
			ScrollableSkillLoadFix.loadVisibility(this, this.visible, this.visibilityChanged, this.progress,
					this.progressChanged, this::shouldBeVisible);
		else
			this.ensureAllVisible();
		this.registerAchievementListeners(manager);
	}*/

	/*public void save() {
		Map<ResourceLocation, SkillProgress> map = Maps.newHashMap();

		for (Entry<SkillElement, SkillProgress> entry : this.progress.entrySet()) {
			SkillProgress advancementprogress = entry.getValue();
			if (advancementprogress.hasProgress()) {
				map.put(entry.getKey().getId(), advancementprogress);
			}
		}

		if (this.progressFile.getParentFile() != null) {
			this.progressFile.getParentFile().mkdirs();
		}

		JsonElement jsonelement = GSON.toJsonTree(map);
		jsonelement.getAsJsonObject().addProperty("DataVersion", SharedConstants.getCurrentVersion().getWorldVersion());

		try (OutputStream outputstream = new FileOutputStream(this.progressFile);
				Writer writer = new OutputStreamWriter(outputstream, Charsets.UTF_8.newEncoder());) {
			GSON.toJson(jsonelement, writer);
		} catch (IOException ioexception) {
			LOGGER.error("Couldn't save player advancements to {}", this.progressFile, ioexception);
		}

	}*/

	/*public boolean grantCriterion(SkillElement advancementIn, String criterionKey) {
		// Forge: don't grant advancements for fake players
		if (this.player instanceof net.minecraftforge.common.util.FakePlayer)
			return false;
		boolean flag = false;
		SkillProgress advancementprogress = this.getProgress(advancementIn);
		boolean flag1 = advancementprogress.isDone();
		if (advancementprogress.grantCriterion(criterionKey)) {
			this.unregisterListeners(advancementIn);
			this.progressChanged.add(advancementIn);
			flag = true;
			if (!flag1 && advancementprogress.isDone()) {
				advancementIn.getRewards().apply(this.player);
				if (advancementIn.getDisplay() != null && advancementIn.getDisplay().shouldAnnounceToChat()
						&& this.player.level.getGameRules().getBoolean(GameRules.RULE_ANNOUNCE_ADVANCEMENTS)) {
					this.playerList.broadcastMessage(
							new TranslationTextComponent(
									"chat.type.advancement." + advancementIn.getDisplay().getFrame().getName(),
									this.player.getDisplayName(), advancementIn.getDisplayText()),
							ChatType.SYSTEM, Util.NIL_UUID);
				}
				// net.minecraftforge.common.ForgeHooks.onAdvancement(this.player,
				// advancementIn);
			}
		}

		if (advancementprogress.isDone()) {
			this.ensureVisibility(advancementIn);
		}

		return flag;
	}*/

	public boolean revokeCriterion(SkillElement advancementIn, String criterionKey) {
		boolean flag = false;
		SkillProgress advancementprogress = this.getProgress(advancementIn);
		if (advancementprogress.revokeCriterion(criterionKey)) {
			this.registerListeners(advancementIn);
			this.progressChanged.add(advancementIn);
			flag = true;
		}

		if (!advancementprogress.hasProgress()) {
			this.ensureVisibility(advancementIn);
		}

		return flag;
	}

	private void registerListeners(SkillElement advancementIn) {
		SkillProgress advancementprogress = this.getProgress(advancementIn);
		/*
		 * if (!advancementprogress.isDone()) { for (Entry<String, Criterion> entry :
		 * advancementIn.getCriteria().entrySet()) { CriterionProgress criterionprogress
		 * = advancementprogress.getCriterionProgress(entry.getKey()); if
		 * (criterionprogress != null && !criterionprogress.isObtained()) {
		 * ICriterionInstance icriterioninstance =
		 * entry.getValue().getCriterionInstance(); if (icriterioninstance != null) {
		 * ICriterionTrigger<ICriterionInstance> icriteriontrigger = CriteriaTriggers
		 * .get(icriterioninstance.getId()); if (icriteriontrigger != null) {
		 * icriteriontrigger.addListener(this, new
		 * ICriterionTrigger.Listener<>(icriterioninstance, advancementIn,
		 * entry.getKey())); } } } }
		 * 
		 * }
		 */
	}

	private void unregisterListeners(SkillElement advancementIn) {
		SkillProgress advancementprogress = this.getProgress(advancementIn);

		/*
		 * for (Entry<String, Criterion> entry : advancementIn.getCriteria().entrySet())
		 * { CriterionProgress criterionprogress =
		 * advancementprogress.getCriterionProgress(entry.getKey()); if
		 * (criterionprogress != null && (criterionprogress.isObtained() ||
		 * advancementprogress.isDone())) { ICriterionInstance icriterioninstance =
		 * entry.getValue().getCriterionInstance(); if (icriterioninstance != null) {
		 * ICriterionTrigger<ICriterionInstance> icriteriontrigger = CriteriaTriggers
		 * .get(icriterioninstance.getId()); if (icriteriontrigger != null) {
		 * icriteriontrigger.removeListener(this, new
		 * ICriterionTrigger.Listener<>(icriterioninstance, advancementIn,
		 * entry.getKey())); } } } }
		 */

	}

	public void flushDirty(ServerPlayerEntity serverPlayer) {
		if (this.isFirstPacket || !this.visibilityChanged.isEmpty() || !this.progressChanged.isEmpty()) {
			Map<ResourceLocation, SkillProgress> map = Maps.newHashMap();
			Set<SkillElement> set = Sets.newLinkedHashSet();
			Set<ResourceLocation> set1 = Sets.newLinkedHashSet();

			for (SkillElement advancement : this.progressChanged) {
				if (this.visible.contains(advancement)) {
					map.put(advancement.getId(), this.progress.get(advancement));
				}
			}

			for (SkillElement advancement1 : this.visibilityChanged) {
				if (this.visible.contains(advancement1)) {
					set.add(advancement1);
				} else {
					set1.add(advancement1.getId());
				}
			}

			if (this.isFirstPacket || !map.isEmpty() || !set.isEmpty() || !set1.isEmpty()) {
				serverPlayer.connection.send(new ScrollableSkillInfoPacket(this.isFirstPacket, set, set1, map));
				this.visibilityChanged.clear();
				this.progressChanged.clear();
			}
		}

		this.isFirstPacket = false;
	}

	public void setSelectedTab(@Nullable SkillElement advancementIn) {
		SkillElement advancement = this.lastSelectedTab;
		if (advancementIn != null && advancementIn.getParent() == null && advancementIn.getDisplay() != null) {
			this.lastSelectedTab = advancementIn;
		} else {
			this.lastSelectedTab = null;
		}

		if (advancement != this.lastSelectedTab) {
			/*
			 * this.player.connection.sendPacket(new SSelectAdvancementsTabPacket(
			 * this.lastSelectedTab == null ? null : this.lastSelectedTab.getId()));
			 */
		}

	}

	public SkillProgress getProgress(SkillElement advancementIn) {
		SkillProgress advancementprogress = this.progress.get(advancementIn);
		if (advancementprogress == null) {
			//advancementprogress = new SkillProgress();
			this.startProgress(advancementIn, advancementprogress);
		}

		return advancementprogress;
	}

	private void startProgress(SkillElement advancementIn, SkillProgress progress) {
		//progress.update(advancementIn.getCriteria(), advancementIn.getRequirements());
		this.progress.put(advancementIn, progress);
	}

	private void ensureVisibility(SkillElement advancementIn) {
		boolean flag = this.shouldBeVisible(advancementIn);
		boolean flag1 = this.visible.contains(advancementIn);
		if (flag && !flag1) {
			this.visible.add(advancementIn);
			this.visibilityChanged.add(advancementIn);
			if (this.progress.containsKey(advancementIn)) {
				this.progressChanged.add(advancementIn);
			}
		} else if (!flag && flag1) {
			this.visible.remove(advancementIn);
			this.visibilityChanged.add(advancementIn);
		}

		if (flag != flag1 && advancementIn.getParent() != null) {
			this.ensureVisibility(advancementIn.getParent());
		}

		for (SkillElement advancement : advancementIn.getChildren()) {
			this.ensureVisibility(advancement);
		}

	}

	private boolean shouldBeVisible(SkillElement advancement) {
		for (int i = 0; advancement != null && i <= 2; ++i) {
			if (i == 0 && this.hasCompletedChildrenOrSelf(advancement)) {
				return true;
			}

			if (advancement.getDisplay() == null) {
				return false;
			}

			SkillProgress advancementprogress = this.getProgress(advancement);
			if (advancementprogress.isDone()) {
				return true;
			}

			if (advancement.getDisplay().isHidden()) {
				return false;
			}

			advancement = advancement.getParent();
		}

		return false;
	}

	private boolean hasCompletedChildrenOrSelf(SkillElement advancementIn) {
		SkillProgress advancementprogress = this.getProgress(advancementIn);
		if (advancementprogress.isDone()) {
			return true;
		} else {
			for (SkillElement advancement : advancementIn.getChildren()) {
				if (this.hasCompletedChildrenOrSelf(advancement)) {
					return true;
				}
			}

			return false;
		}
	}

	@Override
	public String toString() {
		return "PlayerScrollableSkills [dataFixer=" + dataFixer + ", playerList=" + playerList + ", progressFile="
				+ progressFile + ", progress=" + progress + ", visible=" + visible + ", visibilityChanged="
				+ visibilityChanged + ", progressChanged=" + progressChanged + ", player=" + player
				+ ", lastSelectedTab=" + lastSelectedTab + ", isFirstPacket=" + isFirstPacket + "]";
	}
	
	
}
