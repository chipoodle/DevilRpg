package com.chipoodle.devilrpg.capability.skill;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.mana.IBaseManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityProvider;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.SkillElement;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.SkillManaCost;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.model.ClientSkillBuilder;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerSkillClientServerHandler;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.skillsystem.SingletonSkillFactory;
import com.chipoodle.devilrpg.util.BytesUtil;
import com.chipoodle.devilrpg.util.PowerEnum;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.network.PacketDistributor;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PlayerSkillCapability implements IBasePlayerSkillCapability {
    public final static String POWERS_KEY = "Powers";
    public final static String SKILLS_KEY = "Skills";
    public final static String MAX_SKILLS_KEY = "MaxSkills";
    public final static String MANA_COST_KEY = "ManaCost";
    public final static String MINIONS_KEY = "Minions";
    public final static String ATTRIBUTE_MODIFIER_KEY = "AttributeModifier";
    public final static String SKILL_BYTE_ARRAY_KEY = "PassiveKey";
    private final ClientSkillBuilder clientBuilder;

    private CompoundNBT nbt = new CompoundNBT();
    private final SingletonSkillFactory singletonSkillFactory;

    public PlayerSkillCapability() {
        clientBuilder = new ClientSkillBuilder();
        clientBuilder.buildSkillTrees();
        if (nbt.isEmpty()) {
            HashMap<PowerEnum, SkillEnum> powers = new HashMap<>();
            HashMap<SkillEnum, Integer> skills = new HashMap<>();
            HashMap<SkillEnum, Integer> maxSkills = new HashMap<>();
            HashMap<SkillEnum, Integer> manaCostContainer = new HashMap<>();
            ConcurrentLinkedQueue<TameableEntity> minions = new ConcurrentLinkedQueue<>();
            HashMap<Attribute, UUID> attributeModifiers = new HashMap<>();
            List<SkillEnum> filteredSkillList = SkillEnum.getSkillsWithoutEmpty();

            for (PowerEnum p : PowerEnum.values()) {
                powers.put(p, null);
            }

            for (SkillEnum s : filteredSkillList) {
                SkillManaCost skillManaCostRelation = extractSkillManaCost(clientBuilder, s);
                skills.put(s, 0);
                if (skillManaCostRelation != null)
                    maxSkills.put(s, skillManaCostRelation.getMaxSkillLevel());
            }

            for (SkillEnum s : filteredSkillList) {
                SkillManaCost skillManaCostRelation = extractSkillManaCost(clientBuilder, s);
                if (skillManaCostRelation != null)
                    manaCostContainer.put(s, skillManaCostRelation.getManaCost());
            }

            try {
                nbt.putByteArray(POWERS_KEY, BytesUtil.toByteArray(powers));
                nbt.putByteArray(SKILLS_KEY, BytesUtil.toByteArray(skills));
                nbt.putByteArray(MAX_SKILLS_KEY, BytesUtil.toByteArray(maxSkills));
                nbt.putByteArray(MANA_COST_KEY, BytesUtil.toByteArray(manaCostContainer));
                nbt.putByteArray(MINIONS_KEY, BytesUtil.toByteArray(minions));
                nbt.putByteArray(ATTRIBUTE_MODIFIER_KEY, BytesUtil.toByteArray(attributeModifiers));
            } catch (IOException e) {
                DevilRpg.LOGGER.error("Error en constructor PlayerSkillCapability", e);
            }
        }
        singletonSkillFactory = new SingletonSkillFactory(this);
    }

    private SkillManaCost extractSkillManaCost(ClientSkillBuilder client, SkillEnum s) {
        SkillElement skillElementByEnum = client.getSkillElementByEnum(s);
        DevilRpg.LOGGER.debug("extractSkillManaCost: {}-> {} ", s, skillElementByEnum.getSkillManaCost());

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
    public void setSkillsNameOfPowers(HashMap<PowerEnum, SkillEnum> names, PlayerEntity player) {
        try {
            nbt.putByteArray(POWERS_KEY, BytesUtil.toByteArray(names));
            if (!player.level.isClientSide) {
                sendSkillChangesToClient((ServerPlayerEntity) player);
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
    public void setSkillsPoints(HashMap<SkillEnum, Integer> points, PlayerEntity player) {
        try {
            nbt.putByteArray(SKILLS_KEY, BytesUtil.toByteArray(points));
            if (!player.level.isClientSide) {
                sendSkillChangesToClient((ServerPlayerEntity) player);
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
    public void setMaxSkillsPoints(HashMap<SkillEnum, Integer> points, PlayerEntity player) {
        try {
            nbt.putByteArray(MAX_SKILLS_KEY, BytesUtil.toByteArray(points));
            if (!player.level.isClientSide) {
                sendSkillChangesToClient((ServerPlayerEntity) player);
            } else {
                sendSkillChangesToServer();
            }
        } catch (IOException e) {
            DevilRpg.LOGGER.error("Error en setMaxSkillsPoints", e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public HashMap<SkillEnum, Integer> getManaCostPoints() {
        try {
            return (HashMap<SkillEnum, Integer>) BytesUtil
                    .toObject(nbt.getByteArray(MANA_COST_KEY));
        } catch (ClassNotFoundException | IOException e) {
            DevilRpg.LOGGER.error("Error en getManaCost", e);
            return null;
        }
    }

    @Override
    public void setManaCostPoints(HashMap<SkillEnum, Integer> points, PlayerEntity player) {
        try {
            nbt.putByteArray(MANA_COST_KEY, BytesUtil.toByteArray(points));
            if (!player.level.isClientSide) {
                sendSkillChangesToClient((ServerPlayerEntity) player);
            } else {
                sendSkillChangesToServer();
            }
        } catch (IOException e) {
            DevilRpg.LOGGER.error("Error en setManaCost", e);
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
    public void setAttributeModifiers(HashMap<String, UUID> modifiers, PlayerEntity player) {
        try {
            nbt.putByteArray(ATTRIBUTE_MODIFIER_KEY, BytesUtil.toByteArray(modifiers));
            if (!player.level.isClientSide) {
                sendSkillChangesToClient((ServerPlayerEntity) player);
            } else {
                sendSkillChangesToServer();
            }
        } catch (IOException e) {
            DevilRpg.LOGGER.error("Error en setAttributeModifiers", e);
        }

    }

    @Override
    public SkillEnum getSkillFromByteArray(CompoundNBT triggeredSkill) {
        try {
            return (SkillEnum) BytesUtil.toObject(triggeredSkill.getByteArray(SKILL_BYTE_ARRAY_KEY));
        } catch (ClassNotFoundException | IOException e) {
            DevilRpg.LOGGER.error("Error en getSkillFromByteArray", e);
            return null;
        }
    }

    @Override
    public CompoundNBT setSkillToByteArray(SkillEnum skillEnum) {
        try {
            CompoundNBT triggeredSkill = new CompoundNBT();
            triggeredSkill.putByteArray(SKILL_BYTE_ARRAY_KEY, BytesUtil.toByteArray(skillEnum));
            return triggeredSkill;
        } catch (IOException e) {
            DevilRpg.LOGGER.error("Error en setSkillToByteArray", e);
            return null;
        }
    }

    @Override
    public void triggerAction(ServerPlayerEntity playerIn, PowerEnum triggeredPower) {
        if (!playerIn.level.isClientSide) {
            DevilRpg.LOGGER.info("PlayerSkillCapability triggerAction(ServerPlayerEntity, triggeredPower) {} {}", playerIn.getName().getString(), triggeredPower);
            if (getSkillLevelFromAssociatedPower(triggeredPower) != 0) {
                ISkillContainer skill = getSkill(triggeredPower);
                if (consumeMana(playerIn, skill)) {
                    skill.execute(playerIn.level, playerIn, null);
                } else {

                    Random rand = new Random();
                    playerIn.level.playSound(null, playerIn.getX(), playerIn.getY(), playerIn.getZ(),
                            SoundEvents.NOTE_BLOCK_BASS, SoundCategory.NEUTRAL, 0.5F,
                            0.4F / (rand.nextFloat() * 0.4F + 0.8F));
					
					/*String message = "Not enough mana.";
					playerIn.sendMessage(new StringTextComponent(message),playerIn.getUniqueID());*/
                }
            }
        }
    }

    @Override
    public void triggerPassive(ServerPlayerEntity sender, CompoundNBT triggeredSkill) {
        if (!sender.level.isClientSide) {
            SkillEnum skillFromByteArray = getSkillFromByteArray(triggeredSkill);
            DevilRpg.LOGGER.info("PlayerSkillCapability triggerPassive(ServerPlayerEntity, triggeredSkill) {} {}", sender, skillFromByteArray);
            ISkillContainer skill = getLoadedSkill(skillFromByteArray);
            skill.execute(sender.level, sender, new HashMap<>());
        }
    }

    private ISkillContainer getSkill(PowerEnum triggeredPower) {
        SkillEnum skillEnum = getSkillsNameOfPowers().get(triggeredPower);
        return singletonSkillFactory.create(skillEnum);
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

    private boolean consumeMana(ServerPlayerEntity playerIn, ISkillContainer poder) {
        float consumedMana = getManaCostPoints().get(poder.getSkillEnum());
        LazyOptional<IBaseManaCapability> mana = playerIn.getCapability(PlayerManaCapabilityProvider.MANA_CAP, null);
        if (mana.map(x -> x.getMana() - consumedMana >= 0).orElse(false)) {
            mana.ifPresent(m -> m.setMana(m.getMana() - consumedMana, playerIn));
            return true;
        }
        return false;
    }

    @Override
    public ISkillContainer getLoadedSkill(SkillEnum skillEnum) {
        return singletonSkillFactory.getExistingSkill(skillEnum);
    }

    @Override
    public ISkillContainer create(SkillEnum skillEnum) {
        return singletonSkillFactory.create(skillEnum);
    }

    @Override
    public CompoundNBT getNBTData() {
        return nbt;
    }

    @Override
    public void setNBTData(CompoundNBT nbt) {
        this.nbt = nbt;
    }


    @Override
    public ClientSkillBuilder getClientSkillBuilder() {
        return clientBuilder;
    }

    private void sendSkillChangesToServer() {
        ModNetwork.CHANNEL.sendToServer(new PlayerSkillClientServerHandler(getNBTData()));
    }

    private void sendSkillChangesToClient(ServerPlayerEntity pe) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> pe),
                new PlayerSkillClientServerHandler(getNBTData()));
    }

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

}
