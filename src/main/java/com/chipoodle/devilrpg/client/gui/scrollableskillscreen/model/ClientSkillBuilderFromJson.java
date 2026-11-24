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
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Carga el árbol de skills del mod.
 * <p>
 * <b>Lado cliente y servidor</b> (24-nov-2026): antes leía el JSON con
 * {@code Minecraft.getInstance().getResourceManager()}, una API de cliente, pero esta clase la construye
 * {@code PlayerSkillCapabilityImplementation}, que es común y se instancia en el servidor dedicado al
 * entrar un jugador. Eso hacía que la capability dependiera de una clase que no existe en el servidor.
 * El JSON es un recurso del propio mod y no cambia en caliente, así que se lee del <b>classpath</b>
 * ({@code /assets/devilrpg/skills/root_complete.json}): mismo contenido en ambos lados, sin
 * {@code Minecraft}. Si el recurso falta o no es un array, falla con un mensaje explícito en vez de
 * seguir con un {@code null}.
 */
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
        ResourceLocation resourceFile = ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID,
                "skills/root_complete.json");
        String classpathPath = "/assets/" + resourceFile.getNamespace() + "/" + resourceFile.getPath();
        try (InputStream inputStream = ClientSkillBuilderFromJson.class.getResourceAsStream(classpathPath)) {
            if (inputStream == null) {
                throw new IllegalStateException("No se encontro el arbol de skills en el classpath: "
                        + classpathPath);
            }
            String text = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
            JsonElement jsonRootElement = JsonParser.parseString(text);
            if (!jsonRootElement.isJsonArray()) {
                throw new IllegalStateException("El arbol de skills no es un array JSON: " + classpathPath);
            }
            DevilRpg.LOGGER.debug("Skill Json tree loaded from classpath {}", classpathPath);
            return jsonRootElement.getAsJsonArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el arbol de skills " + classpathPath, e);
        }
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

    /** Raíces del árbol (las tres ramas). Expuesto para diagnóstico y pruebas headless. */
    public Iterable<SkillElement> getRoots() {
        return scrollableSkillList.getRoots();
    }

    /** Todos los nodos cargados (raíces + skills). Expuesto para diagnóstico y pruebas headless. */
    public java.util.Collection<SkillElement> getAll() {
        return scrollableSkillList.getAll();
    }

    public interface IListener extends ScrollableSkillList.IListener {
        void onUpdateAdvancementProgress(SkillElement advancementIn, SkillProgress progress);

        void setSelectedTab(@Nullable SkillElement advancementIn);
    }

}
