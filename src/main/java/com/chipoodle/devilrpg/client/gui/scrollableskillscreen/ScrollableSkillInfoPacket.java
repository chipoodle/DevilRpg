package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import net.minecraft.client.network.play.IClientPlayNetHandler;
import net.minecraft.network.IPacket;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class ScrollableSkillInfoPacket implements IPacket<IClientPlayNetHandler> {
	private boolean firstSync;
	private Map<ResourceLocation, SkillElement.Builder> advancementsToAdd;
	private Set<ResourceLocation> advancementsToRemove;
	private Map<ResourceLocation, ScrollableSkillProgress> progressUpdates;

	public ScrollableSkillInfoPacket() {
	}

	public ScrollableSkillInfoPacket(boolean firstSync, Collection<SkillElement> advancementsToAddCollection,
			Set<ResourceLocation> advancementsToRemoveSet,
			Map<ResourceLocation, ScrollableSkillProgress> progressUpdatesMap) {
		this.firstSync = firstSync;
		this.advancementsToAdd = Maps.newHashMap();

		for (SkillElement advancement : advancementsToAddCollection) {
			this.advancementsToAdd.put(advancement.getId(), advancement.copy());
		}

		this.advancementsToRemove = advancementsToRemoveSet;
		this.progressUpdates = Maps.newHashMap(progressUpdatesMap);
	}

	/**
	 * Passes this Packet on to the NetHandler for processing.
	 */
	public void processPacket(IClientPlayNetHandler handler) {
		// handler.handleAdvancementInfo(this);
	}

	/**
	 * Reads the raw packet data from the data stream.
	 */
	public void readPacketData(PacketBuffer buf) throws IOException {
		this.firstSync = buf.readBoolean();
		this.advancementsToAdd = Maps.newHashMap();
		this.advancementsToRemove = Sets.newLinkedHashSet();
		this.progressUpdates = Maps.newHashMap();
		int i = buf.readVarInt();

		for (int j = 0; j < i; ++j) {
			ResourceLocation resourcelocation = buf.readResourceLocation();
			SkillElement.Builder advancement$builder = SkillElement.Builder.readFrom(buf);
			this.advancementsToAdd.put(resourcelocation, advancement$builder);
		}

		i = buf.readVarInt();

		for (int k = 0; k < i; ++k) {
			ResourceLocation resourcelocation1 = buf.readResourceLocation();
			this.advancementsToRemove.add(resourcelocation1);
		}

		i = buf.readVarInt();

		for (int l = 0; l < i; ++l) {
			ResourceLocation resourcelocation2 = buf.readResourceLocation();
			this.progressUpdates.put(resourcelocation2, ScrollableSkillProgress.fromNetwork(buf));
		}

	}

	/**
	 * Writes the raw packet data to the data stream.
	 */
	public void writePacketData(PacketBuffer buf) throws IOException {
		buf.writeBoolean(this.firstSync);
		buf.writeVarInt(this.advancementsToAdd.size());

		for (Entry<ResourceLocation, SkillElement.Builder> entry : this.advancementsToAdd.entrySet()) {
			ResourceLocation resourcelocation = entry.getKey();
			SkillElement.Builder advancement$builder = entry.getValue();
			buf.writeResourceLocation(resourcelocation);
			advancement$builder.writeTo(buf);
		}

		buf.writeVarInt(this.advancementsToRemove.size());

		for (ResourceLocation resourcelocation1 : this.advancementsToRemove) {
			buf.writeResourceLocation(resourcelocation1);
		}

		buf.writeVarInt(this.progressUpdates.size());

		for (Entry<ResourceLocation, ScrollableSkillProgress> entry1 : this.progressUpdates.entrySet()) {
			buf.writeResourceLocation(entry1.getKey());
			entry1.getValue().serializeToNetwork(buf);
		}

	}

	@OnlyIn(Dist.CLIENT)
	public Map<ResourceLocation, SkillElement.Builder> getAdvancementsToAdd() {
		return this.advancementsToAdd;
	}

	@OnlyIn(Dist.CLIENT)
	public Set<ResourceLocation> getAdvancementsToRemove() {
		return this.advancementsToRemove;
	}

	@OnlyIn(Dist.CLIENT)
	public Map<ResourceLocation, ScrollableSkillProgress> getProgressUpdates() {
		return this.progressUpdates;
	}

	@OnlyIn(Dist.CLIENT)
	public boolean isFirstSync() {
		return this.firstSync;
	}
}
