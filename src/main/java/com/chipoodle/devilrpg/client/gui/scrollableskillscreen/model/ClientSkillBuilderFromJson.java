package com.chipoodle.devilrpg.client.gui.scrollableskillscreen.model;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.SkillElement;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.SkillProgress;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@OnlyIn(Dist.CLIENT)
public class ClientSkillBuilderFromJson {
    private static final Logger LOGGER = LogManager.getLogger();

    private final ScrollableSkillList scrollableSkillList = new ScrollableSkillList();
    private final Map<SkillElement, SkillProgress> advancementToProgress = Maps.newHashMap();
    @Nullable
    private ClientSkillBuilderFromJson.IListener listener;
    @Nullable
    private SkillElement selectedTab;

    public ClientSkillBuilderFromJson() {

    }

    public void buildSkillTrees() {
        LOGGER.debug("ClientSkillBuilderFromJson -> Building SkillTrees");
        Map<ResourceLocation, SkillElement.Builder> skillelementMap = new HashMap<>();

        JsonArray asJsonArray = getJsonElements();

        asJsonArray.forEach(jsonElement -> {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            SkillElement.Builder skill$builder = SkillElement.Builder.deserialize(jsonObject);
            if (skill$builder != null) {
                skillelementMap.put(skill$builder.getId(), skill$builder);
            }
        });
        scrollableSkillList.loadSkills(skillelementMap);
    }

    private static JsonArray getJsonElements() {
        InputStream inputStream = null;
        try {
            ResourceLocation resourceFile = new ResourceLocation(DevilRpg.MODID, "skills/root_complete.json");
            inputStream = Minecraft.getInstance().getResourceManager().getResource(resourceFile).orElseThrow().open();
        } catch (Exception e1) {
            DevilRpg.LOGGER.error("Ocurrió un error con buildSkillTrees()", e1);
        }
        String text = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));

        DevilRpg.LOGGER.debug("Skill Json tree loaded successfully ");
        JsonParser parser = new JsonParser();
        JsonElement jsonRootElement = parser.parse(text);

        JsonArray asJsonArray = jsonRootElement.getAsJsonArray();
        return asJsonArray;
    }

    public void setSelectedTab(@Nullable SkillElement skillIn, boolean tellServer) {

        if (this.selectedTab != skillIn) {
            this.selectedTab = skillIn;
            if (this.listener != null) {
                this.listener.setSelectedTab(skillIn);
            }
        }

    }

    public void setListener(@Nullable ClientSkillBuilderFromJson.IListener listenerIn) {
        this.listener = listenerIn;
        this.scrollableSkillList.setListener(listenerIn);
        if (listenerIn != null) {

            for (Entry<SkillElement, SkillProgress> entry : this.advancementToProgress.entrySet()) {
                listenerIn.onUpdateAdvancementProgress(entry.getKey(), entry.getValue());
            }
            listenerIn.setSelectedTab(this.selectedTab);
        }

    }

    public SkillElement getSkillElementByEnum(SkillEnum skillEnum) {
        return scrollableSkillList.getSkillElementByEnum(skillEnum);
    }

    public ResourceLocation getImageOfSkill(SkillEnum skillEnum) {
        return scrollableSkillList.getImageOfSkill(skillEnum);
    }

    @OnlyIn(Dist.CLIENT)
    public interface IListener extends ScrollableSkillList.IListener {
        void onUpdateAdvancementProgress(SkillElement advancementIn, SkillProgress progress);

        void setSelectedTab(@Nullable SkillElement advancementIn);
    }

}
