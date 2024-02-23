package com.chipoodle.devilrpg.client.gui.scrollableskillscreen.model;

import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.SkillElement;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.SkillProgress;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.reflect.TypeToken;
import com.mojang.datafixers.DataFixer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Map;
import java.util.Set;


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
    private ServerPlayer player;
    @Nullable
    private SkillElement lastSelectedTab;
    private final boolean isFirstPacket = true;

    public PlayerScrollableSkills(DataFixer dataFixer, PlayerList playerList, File progressFile, ServerPlayer player) {
        this.dataFixer = dataFixer;
        this.playerList = playerList;
        this.progressFile = progressFile;
        this.player = player;

    }

    public void setPlayer(ServerPlayer player) {
        this.player = player;
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

    @Override
    public String toString() {
        return "PlayerScrollableSkills [dataFixer=" + dataFixer + ", playerList=" + playerList + ", progressFile="
                + progressFile + ", progress=" + progress + ", visible=" + visible + ", visibilityChanged="
                + visibilityChanged + ", progressChanged=" + progressChanged + ", player=" + player
                + ", lastSelectedTab=" + lastSelectedTab + ", isFirstPacket=" + isFirstPacket + "]";
    }


}
