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

public class PlayerPassiveWerewolfHitAttributeExecutor extends AbstractPlayerPassiveAttributeExecutor implements ICapabilityAttributeModifier {
    public static final String ATTRIBUTE_MODIFIER_UNIQUE_NAME = SkillEnum.WEREWOLF_HIT.name() + "_" + "ADDITION";
    public static final double HIT_FACTOR = 0.40D;
    AttributeModifier hitAttributeModifier;
    private Player playerIn;

    public PlayerPassiveWerewolfHitAttributeExecutor(PlayerSkillCapabilityInterface parentCapability) {
        super(parentCapability);
        DevilRpg.LOGGER.info("----------------------->CONSTRUCTOR PlayerPassiveWerewolfHitAttributeExecutor. Parent capability: {}", parentCapability);
    }

    /**
     * *
     *
     * @param levelIn
     * @param playerIn
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
            double attackDamage = playerIn.getAttributeValue(Attributes.ATTACK_DAMAGE);
            DevilRpg.LOGGER.info("attackDamage {}", attackDamage);

        }
    }

    private void initializeAttributes(Player playerIn) {
        if (hitAttributeModifier == null ||
                hitAttributeModifier.getAmount() != Double.valueOf(parentCapability.getSkillsPoints().get(SkillEnum.WEREWOLF_HIT)) * HIT_FACTOR) {
            removeCurrentWerewolfHitModifiers();
            hitAttributeModifier = createNewAttributeModifiers();
            HashMap<String, UUID> capAttModifiersHashMap = parentCapability.getAttributeModifiers();
            addAttributeToCapability(capAttModifiersHashMap, Attributes.ATTACK_DAMAGE, hitAttributeModifier.getId());
            parentCapability.setAttributeModifiers(capAttModifiersHashMap, playerIn);
            DevilRpg.LOGGER.info("----------------------->Add {}", hitAttributeModifier.getId());
        }
    }

    public SkillEnum getSkillEnum() {
        return SkillEnum.WEREWOLF_HIT;
    }

    private AttributeModifier createNewAttributeModifiers() {
        AttributeModifier newAttributeModifier = createNewAttributeModifier(
                ATTRIBUTE_MODIFIER_UNIQUE_NAME,
                Double.valueOf(parentCapability.getSkillsPoints().get(SkillEnum.WEREWOLF_HIT)) * HIT_FACTOR
        );
        //DevilRpg.LOGGER.info("||----------------------->createNewAttributeModifiers SKIN_ARMOR: {}", parentCapability.getSkillsPoints().get(SkillEnum.SKIN_ARMOR));
        DevilRpg.LOGGER.info("----------------------->createNewAttributeModifiers(): {}", newAttributeModifier);
        return newAttributeModifier;
    }

    private void removeCurrentWerewolfHitModifiers() {
        //HashMap<String, UUID> attributeModifiers = parentCapability.getAttributeModifiers();
        removeCurrentModifierFromPlayer(playerIn, hitAttributeModifier, Attributes.ATTACK_DAMAGE);
        //UUID uuid = removeAttributeFromCapability(attributeModifiers, Attributes.ARMOR);
        //parentCapability.setAttributeModifiers(attributeModifiers, playerIn);
        //DevilRpg.LOGGER.info("----------------------->Remove {}",uuid);
        DevilRpg.LOGGER.info("----------------------->removeCurrentModifiers(): {}", hitAttributeModifier);
    }

    private void addCurrentModifiers() {
        //HashMap<String, UUID> attributeModifiers = parentCapability.getAttributeModifiers();
        //addAttributeToCapability(attributeModifiers, Attributes.ARMOR, skinArmorAttributeModifier.getId());
        addCurrentModifierTransiently(playerIn, Attributes.ATTACK_DAMAGE, hitAttributeModifier);
        //parentCapability.setAttributeModifiers(attributeModifiers, playerIn);
        //DevilRpg.LOGGER.info("----------------------->Add {}",skinArmorAttributeModifier.getId());
        DevilRpg.LOGGER.info("----------------------->addCurrentModifierTransiently(): {}", hitAttributeModifier);
    }

    public void add() {
        //removeCurrentModifiers();
        addCurrentModifiers();

    }

    public void remove() {
        removeCurrentWerewolfHitModifiers();
    }
}