package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.UUID;

public class PlayerPassiveSkillSkinArmor extends AbstractPlayerPassive implements ISkillContainer, ICapabilityAttributeModifier {
    public static final String ATTRIBUTE_MODIFIER_UNIQUE_NAME = SkillEnum.SKIN_ARMOR.name() + "_" + "ADDITION";
    public static final String IS_COMPLETE_ARMOR = "IS_COMPLETE_ARMOR";
    public static final double ARMOR_FACTOR = 0.70D;
    AttributeModifier skinArmorAttributeModifier;
    private final PlayerSkillCapabilityInterface parentCapability;
    private PlayerEntity playerIn;

    /*public PlayerPassiveSkillSkinArmor(PlayerEntity playerIn) {
        this.playerIn = playerIn;
        parentCapability = IGenericCapability.getUnwrappedPlayerCapability(playerIn, PlayerSkillCapabilityProvider.SKILL_CAP);
    }*/

    public PlayerPassiveSkillSkinArmor(PlayerSkillCapabilityImplementation parentCapability) {
        this.parentCapability = parentCapability;
        DevilRpg.LOGGER.info("----------------------->CONSTRUCTOR PlayerPassiveSkillSkinArmor. Parnt capability: {}", parentCapability);
    }

    /**
     * *
     * @param worldIn
     * @param playerIn
     * @param parameters
     * Server side called
     */
    @Override
    public void execute(World worldIn, PlayerEntity playerIn, HashMap<String, String> parameters) {
        if (!worldIn.isClientSide) {

            if (this.playerIn == null) {
                this.playerIn = playerIn;
            }
            initializeAttributes(playerIn);

            PlayerAuxiliaryCapabilityInterface auxiliary = IGenericCapability.getUnwrappedPlayerCapability(playerIn, PlayerAuxiliaryCapability.AUX_CAP);
            if (auxiliary.isWerewolfTransformation()) {
                testSkinArmorToGetParameters(playerIn, parameters);
                DevilRpg.LOGGER.info("----------------------->IS_COMPLETE_ARMOR: {}", parameters.get(IS_COMPLETE_ARMOR).toLowerCase());
                if (parameters.get(IS_COMPLETE_ARMOR).equals("true")) {
                    add();
                } else {
                    remove();
                }
            } else {
                remove();
            }
            DevilRpg.LOGGER.info("armorValue {}", playerIn.getArmorValue());
        }
    }

    private void initializeAttributes(PlayerEntity playerIn) {
        if (skinArmorAttributeModifier == null ||
                skinArmorAttributeModifier.getAmount() != Double.valueOf(parentCapability.getSkillsPoints().get(SkillEnum.SKIN_ARMOR)) * ARMOR_FACTOR) {
            removeCurrentSkinArmorModifiers();
            skinArmorAttributeModifier = createNewAttributeModifiers();
            HashMap<String, UUID> capAttModifiersHashMap = parentCapability.getAttributeModifiers();
            addAttributeToCapability(capAttModifiersHashMap, Attributes.ARMOR, skinArmorAttributeModifier.getId());
            parentCapability.setAttributeModifiers(capAttModifiersHashMap, playerIn);
            DevilRpg.LOGGER.info("----------------------->Add {}",skinArmorAttributeModifier.getId());
        }
    }

    public SkillEnum getSkillEnum() {
        return SkillEnum.SKIN_ARMOR;
    }

    private AttributeModifier createNewAttributeModifiers() {
        AttributeModifier newAttributeModifier = createNewAttributeModifier(
                ATTRIBUTE_MODIFIER_UNIQUE_NAME,
                Double.valueOf(parentCapability.getSkillsPoints().get(SkillEnum.SKIN_ARMOR)) * ARMOR_FACTOR
                , AttributeModifier.Operation.ADDITION);
        //DevilRpg.LOGGER.info("||----------------------->createNewAttributeModifiers SKIN_ARMOR: {}", parentCapability.getSkillsPoints().get(SkillEnum.SKIN_ARMOR));
        DevilRpg.LOGGER.info("----------------------->createNewAttributeModifiers(): {}", newAttributeModifier);
        return newAttributeModifier;
    }

    private void removeCurrentSkinArmorModifiers() {
        //HashMap<String, UUID> attributeModifiers = parentCapability.getAttributeModifiers();
        removeCurrentModifierFromPlayer(playerIn, skinArmorAttributeModifier, Attributes.ARMOR);
        //UUID uuid = removeAttributeFromCapability(attributeModifiers, Attributes.ARMOR);
        //parentCapability.setAttributeModifiers(attributeModifiers, playerIn);
        //DevilRpg.LOGGER.info("----------------------->Remove {}",uuid);
        DevilRpg.LOGGER.info("----------------------->removeCurrentModifiers(): {}", skinArmorAttributeModifier);
    }

    private void addCurrentModifiers() {
        //HashMap<String, UUID> attributeModifiers = parentCapability.getAttributeModifiers();
        //addAttributeToCapability(attributeModifiers, Attributes.ARMOR, skinArmorAttributeModifier.getId());
        addCurrentModifierTransiently(playerIn, Attributes.ARMOR, skinArmorAttributeModifier);
        //parentCapability.setAttributeModifiers(attributeModifiers, playerIn);
        //DevilRpg.LOGGER.info("----------------------->Add {}",skinArmorAttributeModifier.getId());
        DevilRpg.LOGGER.info("----------------------->addCurrentModifierTransiently(): {}", skinArmorAttributeModifier);
    }

    public void add() {
        //removeCurrentModifiers();
        addCurrentModifiers();

    }

    public void remove() {
        removeCurrentSkinArmorModifiers();
    }

    private void testSkinArmorToGetParameters(PlayerEntity player, HashMap<String, String> parameters) {
        ItemStack head = player.getItemBySlot(EquipmentSlotType.HEAD);
        ItemStack chest = player.getItemBySlot(EquipmentSlotType.CHEST);
        ItemStack legs = player.getItemBySlot(EquipmentSlotType.LEGS);
        ItemStack feet = player.getItemBySlot(EquipmentSlotType.FEET);


        DevilRpg.LOGGER.info("----------------------->head: {} chest: {} legs: {} feet: {}", head.toString(), chest, legs, feet);
        if (head.getItem() instanceof ArmorItem && chest.getItem() instanceof ArmorItem &&
                legs.getItem() instanceof ArmorItem && feet.getItem() instanceof ArmorItem) {
            ArmorItem headItem = (ArmorItem) head.getItem();
            ArmorItem chestItem = (ArmorItem) chest.getItem();
            ArmorItem legsItem = (ArmorItem) legs.getItem();
            ArmorItem feetItem = (ArmorItem) feet.getItem();

            ArmorMaterial headMaterial = (ArmorMaterial) headItem.getMaterial();
            ArmorMaterial chestMaterial = (ArmorMaterial) chestItem.getMaterial();
            ArmorMaterial legsMaterial = (ArmorMaterial) legsItem.getMaterial();
            ArmorMaterial feetMaterial = (ArmorMaterial) feetItem.getMaterial();


            if (headMaterial.equals(ArmorMaterial.LEATHER) &&
                    chestMaterial.equals(ArmorMaterial.LEATHER) &&
                    legsMaterial.equals(ArmorMaterial.LEATHER) &&
                    feetMaterial.equals(ArmorMaterial.LEATHER)) {
                parameters.put(PlayerPassiveSkillSkinArmor.IS_COMPLETE_ARMOR, "true");
            } else {
                parameters.put(PlayerPassiveSkillSkinArmor.IS_COMPLETE_ARMOR, "false");
            }
        } else {
            parameters.put(PlayerPassiveSkillSkinArmor.IS_COMPLETE_ARMOR, "false");
        }


    }
}
