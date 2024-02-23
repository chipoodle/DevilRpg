package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.UUID;

public class PlayerPassiveWerewolfVitalityAttributeExecutor extends AbstractPlayerPassiveAttributeExecutor implements ICapabilityAttributeModifier {
    public static final String ATTRIBUTE_MODIFIER_UNIQUE_NAME = SkillEnum.WEREWOLF_VITALITY.name() + "_" + "ADDITION";
    public static final double HEALTH_FACTOR = 1.00D;
    AttributeModifier hitAttributeModifier;
    private Player playerIn;

    public PlayerPassiveWerewolfVitalityAttributeExecutor(PlayerSkillCapabilityInterface parentCapability) {
        super(parentCapability);
        DevilRpg.LOGGER.info("----------------------->CONSTRUCTOR PlayerPassiveWerewolfVitalityAttributeExecutor. Parent capability: {}", parentCapability);
    }

    /**
     * @param levelIn    The world level
     * @param playerIn   the player
     * @param parameters Server side called
     */
    @Override
    public void execute(Level levelIn, Player playerIn, HashMap<String, String> parameters) {
        if (!levelIn.isClientSide) {

            if (this.playerIn == null) {
                this.playerIn = playerIn;
            }
            initializeAttributes(playerIn);

            PlayerAuxiliaryCapabilityInterface auxiliary = IGenericCapability.getUnwrappedPlayerCapability(playerIn, PlayerAuxiliaryCapability.INSTANCE);
            if (auxiliary.isWerewolfTransformation()) {
                add();
            } else {
                remove();
            }
            double maxHealth = playerIn.getAttributeValue(Attributes.MAX_HEALTH);
            DevilRpg.LOGGER.info("max health {}", maxHealth);
        }
    }

    private void initializeAttributes(Player playerIn) {
        if (hitAttributeModifier == null ||
                hitAttributeModifier.getAmount() != Double.valueOf(parentCapability.getSkillsPoints().get(SkillEnum.WEREWOLF_VITALITY)) * HEALTH_FACTOR) {
            removeCurrentWerwolfVitalityModifiers();
            hitAttributeModifier = createNewAttributeModifiers();
            HashMap<String, UUID> capAttModifiersHashMap = parentCapability.getAttributeModifiers();
            addAttributeToCapability(capAttModifiersHashMap, Attributes.MAX_HEALTH, hitAttributeModifier.getId());
            parentCapability.setAttributeModifiers(capAttModifiersHashMap, playerIn);
            DevilRpg.LOGGER.info("----------------------->Add {}", hitAttributeModifier.getId());
        }
    }

    public SkillEnum getSkillEnum() {
        return SkillEnum.WEREWOLF_VITALITY;
    }

    private AttributeModifier createNewAttributeModifiers() {
        AttributeModifier newAttributeModifier = createNewAttributeModifier(
                ATTRIBUTE_MODIFIER_UNIQUE_NAME,
                Double.valueOf(parentCapability.getSkillsPoints().get(SkillEnum.WEREWOLF_VITALITY)) * HEALTH_FACTOR
        );
        //DevilRpg.LOGGER.info("||----------------------->createNewAttributeModifiers SKIN_ARMOR: {}", parentCapability.getSkillsPoints().get(SkillEnum.SKIN_ARMOR));
        DevilRpg.LOGGER.info("----------------------->createNewAttributeModifiers(): {}", newAttributeModifier);
        return newAttributeModifier;
    }

    private void removeCurrentWerwolfVitalityModifiers() {
        //HashMap<String, UUID> attributeModifiers = parentCapability.getAttributeModifiers();
        removeCurrentModifierFromPlayer(playerIn, hitAttributeModifier, Attributes.MAX_HEALTH);
        //UUID uuid = removeAttributeFromCapability(attributeModifiers, Attributes.MAX_HEALTH);
        //parentCapability.setAttributeModifiers(attributeModifiers, playerIn);
        //DevilRpg.LOGGER.info("----------------------->Remove {}",uuid);
        if (playerIn.getHealth() > playerIn.getMaxHealth())
            playerIn.setHealth(playerIn.getMaxHealth());
        DevilRpg.LOGGER.info("----------------------->removeCurrentModifiers(): {}", hitAttributeModifier);
    }

    private void addCurrentModifiers() {
        //HashMap<String, UUID> attributeModifiers = parentCapability.getAttributeModifiers();
        //addAttributeToCapability(attributeModifiers, Attributes.ARMOR, skinArmorAttributeModifier.getId());
        addCurrentModifierTransiently(playerIn, Attributes.MAX_HEALTH, hitAttributeModifier);
        //parentCapability.setAttributeModifiers(attributeModifiers, playerIn);
        //DevilRpg.LOGGER.info("----------------------->Add {}",skinArmorAttributeModifier.getId());
        DevilRpg.LOGGER.info("----------------------->addCurrentModifierTransiently(): {}", hitAttributeModifier);
    }

    public void add() {
        //removeCurrentModifiers();
        addCurrentModifiers();

    }

    public void remove() {
        removeCurrentWerwolfVitalityModifiers();
    }
}