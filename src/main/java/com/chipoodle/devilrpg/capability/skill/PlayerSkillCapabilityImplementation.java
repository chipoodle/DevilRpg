package com.chipoodle.devilrpg.capability.skill;

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
import com.chipoodle.devilrpg.network.handler.PlayerSkillTreeClientServerHandler;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.skillsystem.SingletonSkillExecutorFactory;
import com.chipoodle.devilrpg.util.BytesUtil;
import com.chipoodle.devilrpg.util.PowerEnum;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

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
    private final ClientSkillBuilderFromJson clientBuilder = new ClientSkillBuilderFromJson();
    private final SingletonSkillExecutorFactory singletonSkillExecutorFactory;
    private CompoundTag nbt = new CompoundTag();

    public PlayerSkillCapabilityImplementation() {
        if (nbt.isEmpty()) {
            clientBuilder.buildSkillTrees();
            HashMap<PowerEnum, SkillEnum> powers = new HashMap<>();
            HashMap<SkillEnum, Integer> skills = new HashMap<>();
            HashMap<SkillEnum, Integer> maxSkills = new HashMap<>();
            HashMap<SkillEnum, Integer> manaCostContainer = new HashMap<>();
            HashMap<SkillEnum, ResourceType> resourceTypeContainer = new HashMap<>();
            ConcurrentLinkedQueue<ITamableEntity> minions = new ConcurrentLinkedQueue<>();
            HashMap<Attribute, UUID> attributeModifiers = new HashMap<>();
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
                imagesOfSkills.put(s, imageOfSkill.toString());
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
        singletonSkillExecutorFactory = new SingletonSkillExecutorFactory(this);
    }

    private SkillResourceCost extractSkillResourceCost(ClientSkillBuilderFromJson client, SkillEnum s) {
        SkillElement skillElementByEnum = client.getSkillElementByEnum(s);
        //DevilRpg.LOGGER.debug("extractSkillManaCost: {}-> {} ", s, skillElementByEnum.getSkillManaCost());

        return skillElementByEnum == null ? null : skillElementByEnum.getSkillManaCost();
    }

    @SuppressWarnings("unchecked")
    @Override
    public HashMap<PowerEnum, SkillEnum> getSkillsNameOfPowers() {
        try {
            return (HashMap<PowerEnum, SkillEnum>) BytesUtil.toObject(nbt.getByteArray(POWERS_KEY));
        } catch (ClassNotFoundException | IOException e) {
            DevilRpg.LOGGER.error("Error en getSkillsNameOfPowers", e);
            return null;
        }
    }

    @Override
    public void setSkillsNameOfPowers(HashMap<PowerEnum, SkillEnum> names, Player player) {
        try {
            nbt.putByteArray(POWERS_KEY, BytesUtil.toByteArray(names));
            if (!player.level.isClientSide) {
                sendSkillChangesToClient((ServerPlayer) player);
            } else {
                sendSkillChangesToServer();
            }
        } catch (IOException e) {
            DevilRpg.LOGGER.error("Error en setSkillsNameOfPowers", e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public HashMap<SkillEnum, Integer> getSkillsPoints() {
        try {
            return (HashMap<SkillEnum, Integer>) BytesUtil.toObject(nbt.getByteArray(SKILLS_KEY));
        } catch (ClassNotFoundException | IOException e) {
            DevilRpg.LOGGER.error("Error en getSkillsPoints", e);
            return null;
        }
    }

    @Override
    public void setSkillsPoints(HashMap<SkillEnum, Integer> points, Player player) {
        try {
            nbt.putByteArray(SKILLS_KEY, BytesUtil.toByteArray(points));
            if (!player.level.isClientSide) {
                sendSkillChangesToClient((ServerPlayer) player);
            } else {
                sendSkillChangesToServer();
            }
        } catch (IOException e) {
            DevilRpg.LOGGER.error("Error en setSkillsPoints", e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public HashMap<SkillEnum, Integer> getMaxSkillsPoints() {
        try {
            return (HashMap<SkillEnum, Integer>) BytesUtil.toObject(nbt.getByteArray(MAX_SKILLS_KEY));
        } catch (ClassNotFoundException | IOException e) {
            DevilRpg.LOGGER.error("Error en getMaxSkillsPoints", e);
            return null;
        }
    }

    @Override
    public void setMaxSkillsPoints(HashMap<SkillEnum, Integer> points, Player player) {
        try {
            nbt.putByteArray(MAX_SKILLS_KEY, BytesUtil.toByteArray(points));
            if (!player.level.isClientSide) {
                sendSkillChangesToClient((ServerPlayer) player);
            } else {
                sendSkillChangesToServer();
            }
        } catch (IOException e) {
            DevilRpg.LOGGER.error("Error en setMaxSkillsPoints", e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public HashMap<SkillEnum, Integer> getResourceCostPoints() {
        try {
            return (HashMap<SkillEnum, Integer>) BytesUtil
                    .toObject(nbt.getByteArray(MANA_COST_KEY));
        } catch (ClassNotFoundException | IOException e) {
            DevilRpg.LOGGER.error("Error en getResourceCostPoints", e);
            return null;
        }
    }

    @Override
    public void setResourceCostPoints(HashMap<SkillEnum, Integer> points, Player player) {
        try {
            nbt.putByteArray(MANA_COST_KEY, BytesUtil.toByteArray(points));
            if (!player.level.isClientSide) {
                sendSkillChangesToClient((ServerPlayer) player);
            } else {
                sendSkillChangesToServer();
            }
        } catch (IOException e) {
            DevilRpg.LOGGER.error("Error en setResourceCostPoints", e);
        }

    }

    @SuppressWarnings("unchecked")
    @Override
    public HashMap<SkillEnum, ResourceType> getResourceType() {
        try {
            return (HashMap<SkillEnum, ResourceType>) BytesUtil.toObject(nbt.getByteArray(RESOURCE_TYPE_KEY));
        } catch (ClassNotFoundException | IOException e) {
            DevilRpg.LOGGER.error("Error en getResourceType", e);
            return null;
        }
    }

    @Override
    public void setResourceType(HashMap<SkillEnum, ResourceType> points, Player player) {
        try {
            nbt.putByteArray(RESOURCE_TYPE_KEY, BytesUtil.toByteArray(points));
            if (!player.level.isClientSide) {
                sendSkillChangesToClient((ServerPlayer) player);
            } else {
                sendSkillChangesToServer();
            }
        } catch (IOException e) {
            DevilRpg.LOGGER.error("Error en setResourceType", e);
        }

    }

    @SuppressWarnings("unchecked")
    @Override
    public HashMap<String, UUID> getAttributeModifiers() {
        try {
            return (HashMap<String, UUID>) BytesUtil
                    .toObject(nbt.getByteArray(ATTRIBUTE_MODIFIER_KEY));
        } catch (ClassNotFoundException | IOException e) {
            DevilRpg.LOGGER.error("Error en getAttributeModifiers", e);
            return null;
        }
    }

    @Override
    public void setAttributeModifiers(HashMap<String, UUID> modifiers, Player player) {
        try {
            nbt.putByteArray(ATTRIBUTE_MODIFIER_KEY, BytesUtil.toByteArray(modifiers));
            if (!player.level.isClientSide) {
                sendSkillChangesToClient((ServerPlayer) player);
            } else {
                sendSkillChangesToServer();
            }
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
                            entry -> new ResourceLocation(entry.getValue()),
                            (e1, e2) -> e1,HashMap::new
                    ));

        } catch (ClassNotFoundException | IOException e) {
            DevilRpg.LOGGER.error("Error en getImagesOfSkills", e);
            return null;
        }
    }

    @Override
    public void setImagesOfSkills(HashMap<SkillEnum, ResourceLocation> names, Player player) {
        try {
            Map<SkillEnum, String> adaptedMap = names.entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().toString(),
                            (e1, e2) -> e1, HashMap::new
                    ));
            nbt.putByteArray(IMAGES_OF_SKILLS_KEY, BytesUtil.toByteArray(adaptedMap));
            if (!player.level.isClientSide) {
                sendSkillChangesToClient((ServerPlayer) player);
            } else {
                sendSkillChangesToServer();
            }
        } catch (IOException e) {
            DevilRpg.LOGGER.error("Error en setImagesOfSkills", e);
        }
    }

    @Override
    public void triggerAction(Player playerIn, PowerEnum triggeredPower) {
        DevilRpg.LOGGER.info("------ side: {} PlayerSkillCapability triggerAction(SPlayer, triggeredPower) {} {}"
                , (playerIn.level.isClientSide ? "CLIENT" : "SERVER"), playerIn.getName().getString(), triggeredPower);
        if (getSkillLevelFromAssociatedPower(triggeredPower) != 0) {
            ISkillContainer skill = getSkill(triggeredPower);
            if (skill.arePreconditionsMetBeforeConsumingResource(playerIn) && consumeResource(playerIn, skill)) {
                skill.execute(playerIn.level, playerIn, new HashMap<>());
            } else {
                playerIn.level.playSound(null, playerIn.getX(), playerIn.getY(), playerIn.getZ(),
                        SoundEvents.NOTE_BLOCK_BASS.get(), SoundSource.NEUTRAL, 0.5F,
                        0.4F / (new Random().nextFloat() * 0.4F + 0.8F));
					
					/*String message = "Not enough mana.";
					playerIn.sendMessage(new StringTextComponent(message),playerIn.getUniqueID());*/
            }
        }
    }

    @Override
    public void triggerPassive(Player sender, CompoundTag triggeredSkill) {
        if (!sender.level.isClientSide) {
            SkillEnum skillFromByteArray = getSkillFromByteArray(triggeredSkill);
            DevilRpg.LOGGER.info("PlayerSkillCapability triggerPassive(ServerPlayer, triggeredSkill) {} {}", sender, skillFromByteArray);
            ISkillContainer skill = getLoadedSkillExecutor(skillFromByteArray);
            skill.execute(sender.level, sender, new HashMap<>());
        }
    }

    private ISkillContainer getSkill(PowerEnum triggeredPower) {
        SkillEnum skillEnum = getSkillsNameOfPowers().get(triggeredPower);
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

    private boolean consumeResource(Player playerIn, ISkillContainer power) {
        float resourceCost = getResourceCostPoints().get(power.getSkillEnum());
        ResourceType type = getResourceType().get(power.getSkillEnum());

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
    public ISkillContainer getLoadedSkillExecutor(SkillEnum skillEnum) {
        return singletonSkillExecutorFactory.getExistingSkill(skillEnum);
    }

    @Override
    public ISkillContainer createSkillExecutor(SkillEnum skillEnum) {
        return singletonSkillExecutorFactory.getOrCreate(skillEnum);
    }

    @Override
    public CompoundTag serializeNBT() {
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.nbt = nbt;
    }


    @Override
    public ClientSkillBuilderFromJson getClientSkillBuilder() {
        return clientBuilder;
    }

    private void sendSkillChangesToServer() {
        ModNetwork.CHANNEL.sendToServer(new PlayerSkillTreeClientServerHandler(serializeNBT()));
    }

    private void sendSkillChangesToClient(ServerPlayer pe) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> pe),
                new PlayerSkillTreeClientServerHandler(serializeNBT()));
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
    public SkillElement getSkillElementByEnum(SkillEnum skillEnum){
        return clientBuilder.getSkillElementByEnum(skillEnum);
    }

}
