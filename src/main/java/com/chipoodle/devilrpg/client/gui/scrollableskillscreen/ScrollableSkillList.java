package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.base.Functions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class ScrollableSkillList {

	private static final Logger LOGGER = LogManager.getLogger();
	private final Map<ResourceLocation, SkillElement> skillElementMap = Maps.newHashMap();
	private final Set<SkillElement> roots = Sets.newLinkedHashSet();
	private final Set<SkillElement> nonRoots = Sets.newLinkedHashSet();
	private ScrollableSkillList.IListener listener;

	@OnlyIn(Dist.CLIENT)
	private void remove(SkillElement advancementIn) {
		for (SkillElement advancement : advancementIn.getChildren()) {
			this.remove(advancement);
		}

		LOGGER.info("Forgot about skillElement {}", (Object) advancementIn.getId());
		this.skillElementMap.remove(advancementIn.getId());
		if (advancementIn.getParent() == null) {
			this.roots.remove(advancementIn);
			if (this.listener != null) {
				this.listener.rootAdvancementRemoved(advancementIn);
			}
		} else {
			this.nonRoots.remove(advancementIn);
			if (this.listener != null) {
				this.listener.nonRootAdvancementRemoved(advancementIn);
			}
		}

	}

	@OnlyIn(Dist.CLIENT)
	public void removeAll(Set<ResourceLocation> ids) {
		for (ResourceLocation resourcelocation : ids) {
			SkillElement advancement = this.skillElementMap.get(resourcelocation);
			if (advancement == null) {
				LOGGER.warn("Told to remove advancement {} but I don't know what that is", (Object) resourcelocation);
			} else {
				this.remove(advancement);
			}
		}

	}

	public void loadSkills(Map<ResourceLocation, SkillElement.Builder> advancementsIn) {
		Function<ResourceLocation, SkillElement> function = Functions.forMap(this.skillElementMap, (SkillElement) null);

		while (!advancementsIn.isEmpty()) {
			boolean flag = false;
			Iterator<Entry<ResourceLocation, SkillElement.Builder>> iterator = advancementsIn.entrySet().iterator();

			while (iterator.hasNext()) {
				Entry<ResourceLocation, SkillElement.Builder> entry = iterator.next();
				ResourceLocation resourcelocation = entry.getKey();
				SkillElement.Builder advancement$builder = entry.getValue();
				
				if (advancement$builder.resolveParent(function)) {
					SkillElement skillElement = advancement$builder.build(resourcelocation);
					this.skillElementMap.put(resourcelocation, skillElement);
					flag = true;
					iterator.remove();
					if (skillElement.getParent() == null) {
						this.roots.add(skillElement);
						if (this.listener != null) {
							this.listener.rootAdvancementAdded(skillElement);
						}
					} else {
						this.nonRoots.add(skillElement);
						if (this.listener != null) {
							this.listener.nonRootAdvancementAdded(skillElement);
						}
					}
				}
			}

			if (!flag) {
				for (Entry<ResourceLocation, SkillElement.Builder> entry1 : advancementsIn.entrySet()) {
					LOGGER.error("Couldn't load advancement {}: {}", entry1.getKey(), entry1.getValue());
				}
				break;
			}
		}

		ScrollableSkillLoadFix.buildSortedTrees(this.roots);
		LOGGER.info("Loaded {} advancements", (int) this.skillElementMap.size());
	}

	@OnlyIn(Dist.CLIENT)
	public void clear() {
		this.skillElementMap.clear();
		this.roots.clear();
		this.nonRoots.clear();
		if (this.listener != null) {
			this.listener.advancementsCleared();
		}

	}

	public Iterable<SkillElement> getRoots() {
		return this.roots;
	}

	public Collection<SkillElement> getAll() {
		return this.skillElementMap.values();
	}

	@Nullable
	public SkillElement getAdvancement(ResourceLocation id) {
		return this.skillElementMap.get(id);
	}

	@OnlyIn(Dist.CLIENT)
	public void setListener(@Nullable ScrollableSkillList.IListener listenerIn) {
		this.listener = listenerIn;
		if (listenerIn != null) {
			for (SkillElement advancement : this.roots) {
				listenerIn.rootAdvancementAdded(advancement);
			}

			for (SkillElement advancement1 : this.nonRoots) {
				listenerIn.nonRootAdvancementAdded(advancement1);
			}
		}

	}

	public interface IListener {
		void rootAdvancementAdded(SkillElement advancementIn);

		@OnlyIn(Dist.CLIENT)
		void rootAdvancementRemoved(SkillElement advancementIn);

		void nonRootAdvancementAdded(SkillElement advancementIn);

		@OnlyIn(Dist.CLIENT)
		void nonRootAdvancementRemoved(SkillElement advancementIn);

		@OnlyIn(Dist.CLIENT)
		void advancementsCleared();
	}
}
