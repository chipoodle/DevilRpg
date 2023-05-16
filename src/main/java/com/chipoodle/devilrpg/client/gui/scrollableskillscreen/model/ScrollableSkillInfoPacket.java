package com.chipoodle.devilrpg.client.gui.scrollableskillscreen.model;

import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.SkillElement;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.SkillProgress;
import com.google.common.collect.Maps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Based on {{@linkplain ClientboundUpdateAdvancementsPacket}
 */
public class ScrollableSkillInfoPacket implements Packet<ClientGamePacketListener> {
    private boolean firstSync;
    private Map<ResourceLocation, SkillElement.Builder> advancementsToAdd;
    private Set<ResourceLocation> advancementsToRemove;
    private Map<ResourceLocation, SkillProgress> progressUpdates;

    public ScrollableSkillInfoPacket() {
    }

    public ScrollableSkillInfoPacket(boolean firstSync, Collection<SkillElement> advancementsToAddCollection, Set<ResourceLocation> advancementsToRemoveSet, Map<ResourceLocation, SkillProgress> progressUpdatesMap) {
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
    public void handle(ClientGamePacketListener handler) {
        // handler.handleAdvancementInfo(this);
    }

    /**
     * Reads the raw packet data from the data stream.
     */
    public void read(FriendlyByteBuf buf) throws IOException {
		/*this.firstSync = buf.readBoolean();
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
			//this.progressUpdates.put(resourcelocation2, SkillProgress.fromNetwork(buf));
		}
*/
    }

    /**
     * Writes the raw packet data to the data stream.
     */
    public void write(FriendlyByteBuf buf) {
		/*
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

		for (Entry<ResourceLocation, SkillProgress> entry1 : this.progressUpdates.entrySet()) {
			buf.writeResourceLocation(entry1.getKey());
			entry1.getValue().serializeToNetwork(buf);
		}
		 */
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
    public Map<ResourceLocation, SkillProgress> getProgressUpdates() {
        return this.progressUpdates;
    }

    @OnlyIn(Dist.CLIENT)
    public boolean isFirstSync() {
        return this.firstSync;
    }
}
