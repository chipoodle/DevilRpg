package com.chipoodle.devilrpg.capability.skill;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.neoforged.neoforge.network.PacketDistributor;
import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityInterface;
import com.chipoodle.devilrpg.capability.stamina.PlayerStaminaCapability;
import com.chipoodle.devilrpg.capability.stamina.PlayerStaminaCapabilityInterface;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.ResourceType;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.SkillElement;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.SkillResourceCost;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.model.ClientSkillBuilderFromJson;
import com.chipoodle.devilrpg.entity.ITamableEntity;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.payload.PlayerSkillTreePayload;
import com.chipoodle.devilrpg.network.payload.SkillLoadoutPayload;
import com.chipoodle.devilrpg.network.payload.SkillPointSpendPayload;
import com.chipoodle.devilrpg.network.payload.SkillSetRotatePayload;
import com.chipoodle.devilrpg.skillsystem.AbstractSkillExecutor;
import com.chipoodle.devilrpg.skillsystem.SingletonSkillExecutorFactory;
import com.chipoodle.devilrpg.util.BytesUtil;
import com.chipoodle.devilrpg.util.PowerEnum;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public class PlayerSkillCapabilityImplementation implements PlayerSkillCapabilityInterface {
    public final static String POWERS_KEY = "Powers";
    public final static String SKILLS_KEY = "Skills";
    public final static String MAX_SKILLS_KEY = "MaxSkills";
    public final static String MANA_COST_KEY = "ManaCost";
    public final static String RESOURCE_TYPE_KEY = "ResourceType";
    public final static String MINIONS_KEY = "Minions";
    public final static String ATTRIBUTE_MODIFIER_KEY = "AttributeModifier";
    public final static String SKILL_BYTE_ARRAY_KEY = "PassiveKey";
    private static final String IMAGES_OF_SKILLS_KEY = "ImagesOfSkills";
    /** Lista de conjuntos de skills asignados a los botones de poder (loadouts). */
    public static final String SKILL_SETS_KEY = "SkillSets";
    /** Indice del conjunto de skills activo. */
    public static final String ACTIVE_SKILL_SET_KEY = "ActiveSkillSet";
    /** Numero maximo de conjuntos de skills rotables (tecla K). */
    public static final int MAX_SKILL_SETS = 3;
    private final ClientSkillBuilderFromJson clientBuilder = new ClientSkillBuilderFromJson();
    private final SingletonSkillExecutorFactory singletonSkillExecutorFactory;
    private CompoundTag nbt = new CompoundTag();
    /**
     * Copia del NBT recién creado (con TODAS las skills del mod, sus niveles máximos, costes e iconos). Sirve
     * para agregar a las partidas guardadas las skills que se hayan añadido al mod DESPUÉS de guardarlas.
     */
    private CompoundTag defaultNbt;

    public PlayerSkillCapabilityImplementation() {
        if (nbt.isEmpty()) {
            initNbt();
        }
        defaultNbt = nbt.copy();
        singletonSkillExecutorFactory = new SingletonSkillExecutorFactory(this);
    }

    private void initNbt() {
        clientBuilder.buildSkillTrees();
        HashMap<PowerEnum, SkillEnum> powers = new HashMap<>();
        HashMap<SkillEnum, Integer> skills = new HashMap<>();
        HashMap<SkillEnum, Integer> maxSkills = new HashMap<>();
        HashMap<SkillEnum, Integer> manaCostContainer = new HashMap<>();
        HashMap<SkillEnum, ResourceType> resourceTypeContainer = new HashMap<>();
        ConcurrentLinkedQueue<ITamableEntity> minions = new ConcurrentLinkedQueue<>();
        HashMap<String, String> attributeModifiers = new HashMap<>();
        List<SkillEnum> filteredSkillList = SkillEnum.getSkillsWithoutEmpty();
        HashMap<SkillEnum, String> imagesOfSkills = new HashMap<>();

        for (PowerEnum p : PowerEnum.values()) {
            powers.put(p, null);
        }

        for (SkillEnum s : filteredSkillList) {
            SkillResourceCost skillResourceCostRelation = extractSkillResourceCost(clientBuilder, s);
            skills.put(s, 0);
            if (skillResourceCostRelation != null)
                maxSkills.put(s, skillResourceCostRelation.getMaxSkillLevel());
        }

        for (SkillEnum s : filteredSkillList) {
            SkillResourceCost skillResourceCostRelation = extractSkillResourceCost(clientBuilder, s);
            if (skillResourceCostRelation != null) {
                manaCostContainer.put(s, skillResourceCostRelation.getManaCost());
                resourceTypeContainer.put(s, skillResourceCostRelation.getResourceType());
            }
        }
        for (SkillEnum s : filteredSkillList) {
            ResourceLocation imageOfSkill = clientBuilder.getImageOfSkill(s);
            if (imageOfSkill != null) {
                imagesOfSkills.put(s, imageOfSkill.toString());
            }
        }

        try {
            nbt.putByteArray(POWERS_KEY, BytesUtil.toByteArray(powers));
            nbt.putByteArray(SKILLS_KEY, BytesUtil.toByteArray(skills));
            nbt.putByteArray(MAX_SKILLS_KEY, BytesUtil.toByteArray(maxSkills));
            nbt.putByteArray(MANA_COST_KEY, BytesUtil.toByteArray(manaCostContainer));
            nbt.putByteArray(RESOURCE_TYPE_KEY, BytesUtil.toByteArray(resourceTypeContainer));
            nbt.putByteArray(MINIONS_KEY, BytesUtil.toByteArray(minions));
            nbt.putByteArray(ATTRIBUTE_MODIFIER_KEY, BytesUtil.toByteArray(attributeModifiers));
            nbt.putByteArray(IMAGES_OF_SKILLS_KEY, BytesUtil.toByteArray(imagesOfSkills));
        } catch (IOException e) {
            DevilRpg.LOGGER.error("Error en constructor PlayerSkillCapability", e);
        }
    }

    private SkillResourceCost extractSkillResourceCost(ClientSkillBuilderFromJson client, SkillEnum s) {
        SkillElement skillElementByEnum = client.getSkillElementByEnum(s);
        //DevilRpg.LOGGER.debug("extractSkillManaCost: {}-> {} ", s, skillElementByEnum.getSkillManaCost());

        return skillElementByEnum == null ? null : skillElementByEnum.getSkillManaCost();
    }

    @SuppressWarnings("unchecked")
    @Override
    public HashMap<PowerEnum, SkillEnum> getSkillsNameOfPowers() {
        List<HashMap<PowerEnum, SkillEnum>> sets = loadSkillSets();
        int idx = clampActiveIndex(sets.size());
        return sets.get(idx);
    }

    @Override
    public void setSkillsNameOfPowers(HashMap<PowerEnum, SkillEnum> names, Player player) {
        List<HashMap<PowerEnum, SkillEnum>> sets = loadSkillSets();
        int idx = clampActiveIndex(sets.size());
        if (names == null) return;
        if (player.level().isClientSide) {
            HashMap<PowerEnum, SkillEnum> current = sets.get(idx);
            PowerEnum changedPower = null;
            SkillEnum changedSkill = null;
            int changes = 0;
            for (PowerEnum power : PowerEnum.values()) {
                SkillEnum before = current.getOrDefault(power, SkillEnum.EMPTY);
                SkillEnum after = names.getOrDefault(power, SkillEnum.EMPTY);
                if (before != after) {
                    changedPower = power;
                    changedSkill = after;
                    changes++;
                }
            }
            if (changes == 1 && changedPower != null && changedSkill != null) {
                // Predicción local para que la pantalla actualice inmediatamente; el servidor valida
                // la acción y su respuesta S2C reemplaza esta copia si la rechaza.
                sets.set(idx, new HashMap<>(names));
                saveSkillSets(sets);
                PacketDistributor.sendToServer(new SkillLoadoutPayload(changedPower, changedSkill));
            } else {
                DevilRpg.LOGGER.warn("[SkillTree] cambio de loadout local rechazado: {} ranuras modificadas", changes);
            }
            return;
        }
        sets.set(idx, new HashMap<>(names));
        saveSkillSets(sets);
        sendSkillChangesToClient((ServerPlayer) player);
    }

    @Override
    public int getSkillSetCount() {
        return MAX_SKILL_SETS;
    }

    @Override
    public int getActiveSkillSetIndex() {
        return nbt.getInt(ACTIVE_SKILL_SET_KEY);
    }

    @Override
    public void rotateSkillSet(int delta, Player player) {
        if (delta != -1 && delta != 1) return;
        List<HashMap<PowerEnum, SkillEnum>> sets = loadSkillSets();
        // Crear conjuntos vacios de sobra hasta MAX_SKILL_SETS (3) para poder rotar entre ellos.
        boolean grew = false;
        while (sets.size() < MAX_SKILL_SETS) {
            sets.add(new HashMap<>());
            grew = true;
        }
        if (grew) {
            saveSkillSets(sets);
        }
        int count = sets.size();
        int newIdx = ((getActiveSkillSetIndex() + delta) % count + count) % count;
        nbt.putInt(ACTIVE_SKILL_SET_KEY, newIdx);
        if (player.level().isClientSide) {
            PacketDistributor.sendToServer(new SkillSetRotatePayload(delta));
        } else {
            sendSkillChangesToClient((ServerPlayer) player);
        }
    }

    private int clampActiveIndex(int count) {
        return Math.max(0, Math.min(nbt.getInt(ACTIVE_SKILL_SET_KEY), count - 1));
    }

    @SuppressWarnings("unchecked")
    private List<HashMap<PowerEnum, SkillEnum>> loadSkillSets() {
        // Solo intentar deserializar si hay datos (evita EOFException por array vacio cuando la
        // clave aun no existe, p. ej. en mundos/jugadores que nunca rotaron de conjunto).
        byte[] data = nbt.getByteArray(SKILL_SETS_KEY);
        if (data != null && data.length > 0) {
            try {
                Object o = BytesUtil.toObject(data);
                if (o instanceof List<?> list) {
                    List<HashMap<PowerEnum, SkillEnum>> sets = new ArrayList<>();
                    for (Object el : list) {
                        if (el instanceof HashMap<?, ?> m) {
                            sets.add((HashMap<PowerEnum, SkillEnum>) m);
                        }
                    }
                    if (!sets.isEmpty()) {
                        return sets;
                    }
                }
            } catch (Exception e) {
                DevilRpg.LOGGER.error("Error en loadSkillSets", e);
            }
        }
        // Fallback: un unico conjunto desde POWERS_KEY (compatibilidad con datos antiguos).
        List<HashMap<PowerEnum, SkillEnum>> sets = new ArrayList<>();
        HashMap<PowerEnum, SkillEnum> existing = null;
        try {
            existing = (HashMap<PowerEnum, SkillEnum>) BytesUtil.toObject(nbt.getByteArray(POWERS_KEY));
        } catch (Exception ignored) {
        }
        sets.add(existing == null ? new HashMap<>() : existing);
        return sets;
    }

    private void saveSkillSets(List<HashMap<PowerEnum, SkillEnum>> sets) {
        try {
            nbt.putByteArray(SKILL_SETS_KEY, BytesUtil.toByteArray(new ArrayList<>(sets)));
        } catch (IOException e) {
            DevilRpg.LOGGER.error("Error en saveSkillSets", e);
        }
    }

    // Cache del mapa de skill points: evita deserializar el byte[] (serializacion Java) en CADA
    // llamada. Se invalida en setSkillsPoints y deserializeNBT para no usar datos desactualizados.
    private HashMap<SkillEnum, Integer> skillsCache;

    @SuppressWarnings("unchecked")
    @Override
    public HashMap<SkillEnum, Integer> getSkillsPoints() {
        if (skillsCache != null) return skillsCache;
        try {
            skillsCache = (HashMap<SkillEnum, Integer>) BytesUtil.toObject(nbt.getByteArray(SKILLS_KEY));
        } catch (ClassNotFoundException | IOException e) {
            DevilRpg.LOGGER.error("Error en getSkillsPoints", e);
            return null;
        }
        return skillsCache;
    }

    @Override
    public void setSkillsPoints(HashMap<SkillEnum, Integer> points, Player player) {
        if (points == null) return;
        if (player.level().isClientSide) {
            HashMap<SkillEnum, Integer> before = readStoredSkillsPoints();
            Set<SkillEnum> all = new HashSet<>(before.keySet());
            all.addAll(points.keySet());
            SkillEnum requestedSkill = null;
            int changes = 0;
            boolean valid = true;
            for (SkillEnum skill : all) {
                if (skill == null || skill == SkillEnum.EMPTY) continue;
                int oldLevel = before.getOrDefault(skill, 0);
                int newLevel = points.getOrDefault(skill, 0);
                if (oldLevel != newLevel) {
                    if (newLevel != oldLevel + 1) { valid = false; break; }
                    requestedSkill = skill;
                    changes++;
                }
            }
            skillsCache = before;
            if (valid && changes == 1 && requestedSkill != null) {
                PacketDistributor.sendToServer(new SkillPointSpendPayload(requestedSkill));
            } else if (changes > 0 || !valid) {
                DevilRpg.LOGGER.warn("[SkillTree] solicitud local de puntos rechazada: cambios={}, valida={}", changes, valid);
            }
            return;
        }
        skillsCache = new HashMap<>(points);
        try {
            nbt.putByteArray(SKILLS_KEY, BytesUtil.toByteArray(new HashMap<>(points)));
            sendSkillChangesToClient((ServerPlayer) player);
        } catch (IOException e) {
            DevilRpg.LOGGER.error("Error en setSkillsPoints", e);
        }
    }

    private HashMap<SkillEnum, Integer> readStoredSkillsPoints() {
        try {
            Object stored = BytesUtil.toObject(nbt.getByteArray(SKILLS_KEY));
            if (stored instanceof HashMap<?, ?> map) {
                HashMap<SkillEnum, Integer> result = new HashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() instanceof SkillEnum skill && entry.getValue() instanceof Integer level) {
                        result.put(skill, level);
                    }
                }
                return result;
            }
        } catch (Exception e) {
            DevilRpg.LOGGER.error("No se pudo leer el árbol persistido para validar la solicitud local", e);
        }
        return new HashMap<>();
    }

    /**
     * Los máximos son reglas de diseño, no progreso del jugador: siempre se leen del árbol JSON
     * cargado por el servidor. Así, cambiar maxskilllevel aplica también a partidas ya existentes
     * sin tocar ni reiniciar los niveles que el jugador ya compró.
     */
    @Override
    public HashMap<SkillEnum, Integer> getMaxSkillsPoints() {
        HashMap<SkillEnum, Integer> maximums = new HashMap<>();
        for (SkillElement element : clientBuilder.getAll()) {
            SkillEnum skill = element.getSkillCapability();
            SkillResourceCost cost = element.getSkillManaCost();
            if (skill != null && skill != SkillEnum.EMPTY && cost != null && cost.getMaxSkillLevel() != null) {
                maximums.put(skill, cost.getMaxSkillLevel());
            }
        }
        return maximums;
    }

    @Override
    public void setMaxSkillsPoints(HashMap<SkillEnum, Integer> points, Player player) {
        DevilRpg.LOGGER.warn("[SkillTree] maxskilllevel es autoritativo desde root_complete.json; se ignora la mutación runtime");
    }

    /** Los costes se obtienen siempre de skillmanacost en root_complete.json. */
    @Override
    public HashMap<SkillEnum, Integer> getResourceCostPoints() {
        HashMap<SkillEnum, Integer> costs = new HashMap<>();
        for (SkillElement element : clientBuilder.getAll()) {
            SkillEnum skill = element.getSkillCapability();
            SkillResourceCost cost = element.getSkillManaCost();
            if (skill != null && skill != SkillEnum.EMPTY && cost != null && cost.getManaCost() != null) {
                costs.put(skill, cost.getManaCost());
            }
        }
        return costs;
    }

    @Override
    public void setResourceCostPoints(HashMap<SkillEnum, Integer> points, Player player) {
        DevilRpg.LOGGER.warn("[SkillTree] manacost es autoritativo desde root_complete.json; se ignora la mutación runtime");
    }

    /** Los tipos de recurso se obtienen siempre de skillmanacost en root_complete.json. */
    @Override
    public HashMap<SkillEnum, ResourceType> getResourceType() {
        HashMap<SkillEnum, ResourceType> types = new HashMap<>();
        for (SkillElement element : clientBuilder.getAll()) {
            SkillEnum skill = element.getSkillCapability();
            SkillResourceCost cost = element.getSkillManaCost();
            if (skill != null && skill != SkillEnum.EMPTY && cost != null && cost.getResourceType() != null) {
                types.put(skill, cost.getResourceType());
            }
        }
        return types;
    }

    @Override
    public void setResourceType(HashMap<SkillEnum, ResourceType> points, Player player) {
        DevilRpg.LOGGER.warn("[SkillTree] resourcetype es autoritativo desde root_complete.json; se ignora la mutación runtime");
    }

    @SuppressWarnings("unchecked")
    @Override
    public HashMap<String, String> getAttributeModifiers() {
        try {
            return (HashMap<String, String>) BytesUtil
                    .toObject(nbt.getByteArray(ATTRIBUTE_MODIFIER_KEY));
        } catch (ClassNotFoundException | IOException e) {
            DevilRpg.LOGGER.error("Error en getAttributeModifiers", e);
            return null;
        }
    }

    @Override
    public void setAttributeModifiers(HashMap<String, String> modifiers, Player player) {
        if (player.level().isClientSide) {
            DevilRpg.LOGGER.warn("[SkillTree] el cliente no puede modificar modificadores de atributos");
            return;
        }
        try {
            nbt.putByteArray(ATTRIBUTE_MODIFIER_KEY, BytesUtil.toByteArray(modifiers));
            sendSkillChangesToClient((ServerPlayer) player);
        } catch (IOException e) {
            DevilRpg.LOGGER.error("Error en setAttributeModifiers", e);
        }
    }

    @Override
    public SkillEnum getSkillFromByteArray(CompoundTag triggeredSkill) {
        try {
            return (SkillEnum) BytesUtil.toObject(triggeredSkill.getByteArray(SKILL_BYTE_ARRAY_KEY));
        } catch (ClassNotFoundException | IOException e) {
            DevilRpg.LOGGER.error("Error en getSkillFromByteArray", e);
            return null;
        }
    }

    @Override
    public CompoundTag setSkillToByteArray(SkillEnum skillEnum) {
        try {
            CompoundTag skill = new CompoundTag();
            skill.putByteArray(SKILL_BYTE_ARRAY_KEY, BytesUtil.toByteArray(skillEnum));
            return skill;
        } catch (IOException e) {
            DevilRpg.LOGGER.error("Error en setSkillToByteArray", e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public HashMap<SkillEnum, ResourceLocation> getImagesOfSkills() {
        try {
            HashMap<SkillEnum, String> object = (HashMap<SkillEnum, String>) BytesUtil.toObject(nbt.getByteArray(IMAGES_OF_SKILLS_KEY));
            return object.entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> ResourceLocation.parse(entry.getValue()),
                            (e1, e2) -> e1, HashMap::new
                    ));

        } catch (ClassNotFoundException | IOException e) {
            DevilRpg.LOGGER.error("Error en getImagesOfSkills", e);
            return null;
        }
    }

    @Override
    public void setImagesOfSkills(HashMap<SkillEnum, ResourceLocation> names, Player player) {
        if (player.level().isClientSide) {
            DevilRpg.LOGGER.warn("[SkillTree] el cliente no puede modificar los iconos de habilidades");
            return;
        }
        try {
            Map<SkillEnum, String> adaptedMap = names.entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().toString(),
                            (e1, e2) -> e1, HashMap::new
                    ));
            nbt.putByteArray(IMAGES_OF_SKILLS_KEY, BytesUtil.toByteArray(adaptedMap));
            sendSkillChangesToClient((ServerPlayer) player);
        } catch (IOException e) {
            DevilRpg.LOGGER.error("Error en setImagesOfSkills", e);
        }
    }

    @Override
    public void triggerAction(Player playerIn, PowerEnum triggeredPower) {
        //DevilRpg.LOGGER.info("------ side: {} PlayerSkillCapability triggerAction(SPlayer, triggeredPower) {} {}", (playerIn.level().isClientSide ? "CLIENT" : "SERVER"), playerIn.getName().getString(), triggeredPower);
        if (getSkillLevelFromAssociatedPower(triggeredPower) != 0) {
            AbstractSkillExecutor skill = getSkill(triggeredPower);
            // Las skills pasivas y las mejoras activas de minion pueden existir en el árbol,
            // pero no son asignables a los poderes del jugador y no tienen ejecutor aquí.
            // Un loadout antiguo o datos inválidos no deben provocar un NPE al pulsar la tecla.
            if (skill == null) {
                return;
            }
            if (skill.arePreconditionsMetBeforeConsumingResource(playerIn) && consumeResource(playerIn, skill)) {
                skill.execute(playerIn.level(), playerIn, new HashMap<>());
            } else {
                playerIn.level().playSound(null, playerIn.getX(), playerIn.getY(), playerIn.getZ(),
                        SoundEvents.NOTE_BLOCK_BASS, SoundSource.NEUTRAL, 0.5F,
                        0.4F / (new Random().nextFloat() * 0.4F + 0.8F));
					
					/*String message = "Not enough mana.";
					playerIn.sendMessage(new StringTextComponent(message),playerIn.getUniqueID());*/
            }
        }
    }

    @Override
    public void triggerPassive(Player sender, SkillEnum skillFromByteArray) {
        if (skillFromByteArray == null || skillFromByteArray == SkillEnum.EMPTY
                || !skillFromByteArray.isPassive() || skillFromByteArray.isForMinion()
                || isChildOfActiveSkill(skillFromByteArray)) return;
        Integer nivel = getSkillsPoints() == null ? null : getSkillsPoints().get(skillFromByteArray);
        if (nivel == null || nivel <= 0) return;
        AbstractSkillExecutor skill = getLoadedSkillExecutor(skillFromByteArray);
        if (skill != null) skill.execute(sender.level(), sender, new HashMap<>());
    }

    /** Devuelve true si el pasivo es descendiente (hijo o nieto...) de algun skill activo del arbol. */
    private boolean isChildOfActiveSkill(SkillEnum passive) {
        for (SkillEnum active : SkillEnum.getActiveSkills()) {
            if (getPassivesFromActiveSkill(active).contains(passive)) {
                return true;
            }
        }
        return false;
    }

    private AbstractSkillExecutor getSkill(PowerEnum triggeredPower) {
        HashMap<PowerEnum, SkillEnum> nombres = getSkillsNameOfPowers();
        SkillEnum skillEnum = nombres == null ? null : nombres.get(triggeredPower);
        if (!com.chipoodle.devilrpg.skillsystem.SkillCastPolicy.esAsignableAPoder(skillEnum)) {
            return null;
        }
        return singletonSkillExecutorFactory.getOrCreate(skillEnum);
    }

    private int getSkillLevelFromAssociatedPower(PowerEnum triggeredPower) {
        HashMap<SkillEnum, Integer> skillsPoints = getSkillsPoints();
        HashMap<PowerEnum, SkillEnum> skillsNameOfPowers = getSkillsNameOfPowers();
        if (skillsPoints != null && skillsNameOfPowers != null) {
            SkillEnum skillEnum = skillsNameOfPowers.get(triggeredPower);
            if (skillEnum != null) {
                Integer integer = skillsPoints.get(skillEnum);
                return integer != null ? integer : 0;
            }
        }
        return 0;
    }

    private boolean consumeResource(Player playerIn, AbstractSkillExecutor power) {
        HashMap<SkillEnum, Integer> costes = getResourceCostPoints();
        HashMap<SkillEnum, ResourceType> tipos = getResourceType();
        if (costes == null || tipos == null) {
            return false;
        }
        Integer coste = costes.get(power.getSkillEnum());
        ResourceType type = tipos.get(power.getSkillEnum());
        if (coste == null || type == null) {
            return false;
        }
        float resourceCost = coste;

        if (playerIn.isCreative() || power.isResourceConsumptionBypassed(playerIn))
            return true;

        switch (type) {
            case MANA -> {
                PlayerManaCapabilityInterface manaCap = IGenericCapability.getUnwrappedPlayerCapability(playerIn, PlayerManaCapability.INSTANCE);
                if (manaCap.getMana() - resourceCost >= 0) {
                    manaCap.setMana(manaCap.getMana() - resourceCost, playerIn);
                    return true;
                }
            }
            case STAMINA -> {
                PlayerStaminaCapabilityInterface staminaCap = IGenericCapability.getUnwrappedPlayerCapability(playerIn, PlayerStaminaCapability.INSTANCE);
                if (staminaCap.getStamina() - resourceCost >= 0) {
                    staminaCap.setStamina(staminaCap.getStamina() - resourceCost, playerIn);
                    return true;
                }
            }
            default -> {
                return false;
            }
        }
        return false;
    }

    @Override
    public AbstractSkillExecutor getLoadedSkillExecutor(SkillEnum skillEnum) {
        return singletonSkillExecutorFactory.getExistingSkill(skillEnum);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return nbt;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        this.nbt = nbt;
        this.addMissingSkills(); // partidas viejas: agrega las skills nuevas del mod
        skillsCache = null; // el NBT cambia -> invalidar la cache de skill points
    }

    @Override
    public void applyClientTree(CompoundTag incoming, HolderLookup.Provider provider) {
        // El árbol del cliente puede traer Minions/AttributeModifier (estado del servidor). Se copian
        // solo las claves que el cliente sí posee; lo demás se queda como lo tiene el servidor.
        CompoundTag merged = nbt.copy();
        for (String key : new String[]{POWERS_KEY, SKILLS_KEY, MAX_SKILLS_KEY, MANA_COST_KEY,
                RESOURCE_TYPE_KEY, IMAGES_OF_SKILLS_KEY, SKILL_SETS_KEY, ACTIVE_SKILL_SET_KEY}) {
            if (incoming.contains(key)) {
                merged.put(key, incoming.get(key));
            }
        }
        deserializeNBT(provider, merged);
    }

    /**
     * Agrega a la partida guardada las skills que el mod haya añadido <b>después</b> de guardarla.
     * <p>
     * El NBT del jugador guarda los mapas enteros (puntos, nivel máximo, coste de maná, tipo de recurso e
     * icono). Una skill nueva no está en esos mapas: el árbol la mostraba a 0/0 y, al pulsarla, reventaba con
     * {@code NullPointerException} porque su nivel máximo era {@code null}. Aquí solo se <b>añaden</b> las
     * claves que falten: nunca se toca un valor que el jugador ya tenía.
     */
    private void addMissingSkills() {
        if (defaultNbt == null) {
            return;
        }
        mergeMissingEntries(SKILLS_KEY);
        mergeMissingEntries(MAX_SKILLS_KEY);
        mergeMissingEntries(MANA_COST_KEY);
        mergeMissingEntries(RESOURCE_TYPE_KEY);
        mergeMissingEntries(IMAGES_OF_SKILLS_KEY);
    }

    @SuppressWarnings("unchecked")
    private void mergeMissingEntries(String key) {
        if (!nbt.contains(key) || !defaultNbt.contains(key)) {
            return;
        }
        try {
            HashMap<Object, Object> current = (HashMap<Object, Object>) BytesUtil.toObject(nbt.getByteArray(key));
            HashMap<Object, Object> defaults = (HashMap<Object, Object>) BytesUtil.toObject(defaultNbt.getByteArray(key));
            if (current == null || defaults == null) {
                return;
            }
            boolean changed = false;
            for (Object skill : defaults.keySet()) {
                Object value = defaults.get(skill);
                if (value == null) {
                    continue; // p. ej. una skill sin imagen: no se copia la nada
                }
                if (current.putIfAbsent(skill, value) == null) {
                    changed = true;
                }
            }
            if (changed) {
                nbt.putByteArray(key, BytesUtil.toByteArray(current));
            }
        } catch (IOException | ClassNotFoundException e) {
            DevilRpg.LOGGER.error("Error al agregar las skills nuevas del mod al jugador ({})", key, e);
        }
    }


    @Override
    public ClientSkillBuilderFromJson getClientSkillBuilder() {
        return clientBuilder;
    }

    private void sendSkillChangesToClient(ServerPlayer pe) {
        PacketDistributor.sendToPlayer(pe,
                new PlayerSkillTreePayload(serializeNBT(RegistryAccess.EMPTY).copy()));
    }

    @Override
    public List<SkillEnum> getPassivesFromActiveSkill(SkillEnum skillEnum) {
        SkillElement skillElementByEnum = clientBuilder.getSkillElementByEnum(skillEnum);
        List<SkillEnum> passiveChildren = new ArrayList<>();

        Iterable<SkillElement> children = skillElementByEnum.getChildren();
        for (SkillElement child : children) {
            if (child.getSkillCapability().isPassive()) {
                passiveChildren.add(child.getSkillCapability());
                passiveChildren.addAll(getPassivesFromActiveSkill(child.getSkillCapability()));
            }

        }
        return passiveChildren;
    }

    @Override
    public SkillElement getSkillElementByEnum(SkillEnum skillEnum) {
        return clientBuilder.getSkillElementByEnum(skillEnum);
    }

    @Override
    public int getSkillsPoints(SkillEnum skillEnum) {
        HashMap<SkillEnum, Integer> skillsPoints = this.getSkillsPoints();
        if (skillsPoints == null) return 0;
        return skillsPoints.getOrDefault(skillEnum, 0);
    }

}
