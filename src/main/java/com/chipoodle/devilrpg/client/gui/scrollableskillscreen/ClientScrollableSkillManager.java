package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.chipoodle.devilrpg.DevilRpg;
import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientScrollableSkillManager {
	private static final Logger LOGGER = LogManager.getLogger();
	private final Minecraft mc;
	private final ScrollableSkillList scrollableSkillList = new ScrollableSkillList();
	private final Map<SkillElement, ScrollableSkillProgress> advancementToProgress = Maps.newHashMap();
	@Nullable
	private ClientScrollableSkillManager.IListener listener;
	@Nullable
	private SkillElement selectedTab;

	public ClientScrollableSkillManager(Minecraft p_i47380_1_) {
		this.mc = p_i47380_1_;
	}

	public void buildSkillTrees() {
		ResourceLocation resourcefile = new ResourceLocation(DevilRpg.MODID , "skills/root_complete.json");
		InputStream inputStream = null;
		Map<ResourceLocation, SkillElement.Builder> skillelementMap = new HashMap<ResourceLocation, SkillElement.Builder>();

		try {
			inputStream = Minecraft.getInstance().getResourceManager().getResource(resourcefile).getInputStream();
		} catch (IOException e1) {
			DevilRpg.LOGGER.error("Ocurrió un error con buildSkillTrees()", e1);
		}
		String text = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines()
				.collect(Collectors.joining("\n"));

		DevilRpg.LOGGER.info("text: "+text);
		JsonParser parser = new JsonParser();
		JsonElement jsonRootElement = parser.parse(text);

		JsonArray asJsonArray = jsonRootElement.getAsJsonArray();
		asJsonArray.forEach(jsonElement -> {
			JsonObject jsonObject = jsonElement.getAsJsonObject();
			SkillElement.Builder skill$builder = SkillElement.Builder.deserialize(jsonObject);
			if (skill$builder != null) {
				skillelementMap.put(skill$builder.getId(), skill$builder);
			}
		});
		scrollableSkillList.loadSkills(skillelementMap);
	}

	public void read(ScrollableSkillInfoPacket packetIn) {
		if (packetIn.isFirstSync()) {
			this.scrollableSkillList.clear();
			this.advancementToProgress.clear();
		}

		this.scrollableSkillList.removeAll(packetIn.getAdvancementsToRemove());
		this.scrollableSkillList.loadSkills(packetIn.getAdvancementsToAdd());

		for (Entry<ResourceLocation, ScrollableSkillProgress> entry : packetIn.getProgressUpdates().entrySet()) {
			SkillElement skillElement = this.scrollableSkillList.getAdvancement(entry.getKey());
			if (skillElement != null) {
				ScrollableSkillProgress advancementprogress = entry.getValue();
				this.advancementToProgress.put(skillElement, advancementprogress);
				if (this.listener != null) {
					this.listener.onUpdateAdvancementProgress(skillElement, advancementprogress);
				}

				if (!packetIn.isFirstSync() && advancementprogress.isDone() && skillElement.getDisplay() != null
						&& skillElement.getDisplay().shouldShowToast()) {
					this.mc.getToastGui().add(new SkillToast(skillElement));
				}
			} else {
				LOGGER.warn("Server informed client about progress for unknown advancement {}", entry.getKey());
			}
		}

	}

	public ScrollableSkillList getAdvancementList() {
		return this.scrollableSkillList;
	}

	public void setSelectedTab(@Nullable SkillElement advancementIn, boolean tellServer) {
		ClientPlayNetHandler clientplaynethandler = this.mc.getConnection();
		if (clientplaynethandler != null && advancementIn != null && tellServer) {
			// clientplaynethandler.sendPacket(CSeenAdvancementsPacket.openedTab(advancementIn));
		}

		if (this.selectedTab != advancementIn) {
			this.selectedTab = advancementIn;
			if (this.listener != null) {
				this.listener.setSelectedTab(advancementIn);
			}
		}

	}

	public void setListener(@Nullable ClientScrollableSkillManager.IListener listenerIn) {
		this.listener = listenerIn;
		this.scrollableSkillList.setListener(listenerIn);
		if (listenerIn != null) {

			for (Entry<SkillElement, ScrollableSkillProgress> entry : this.advancementToProgress.entrySet()) {
				listenerIn.onUpdateAdvancementProgress(entry.getKey(), entry.getValue());
			}
			listenerIn.setSelectedTab(this.selectedTab);
		}

	}

	@OnlyIn(Dist.CLIENT)
	public interface IListener extends ScrollableSkillList.IListener {
		void onUpdateAdvancementProgress(SkillElement advancementIn, ScrollableSkillProgress progress);

		void setSelectedTab(@Nullable SkillElement advancementIn);
	}
}
