package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.UUID;

public class PlayerPassiveSkillSkinArmorAttributeExecutor extends AbstractPlayerPassiveAttributeExecutor implements ICapabilityAttributeModifier {
    public static final String ATTRIBUTE_MODIFIER_ARMOR_UNIQUE_NAME = SkillEnum.SKIN_ARMOR.name() + "_ARMOR_" + "ADDITION";
    public static final String ATTRIBUTE_MODIFIER_TOUGHNESS_UNIQUE_NAME = SkillEnum.SKIN_ARMOR.name() + "_TOUGHNESS_" + "ADDITION";
    public static final String IS_COMPLETE_ARMOR = "IS_COMPLETE_ARMOR";
    public static final double ARMOR_FACTOR = 0.65D;
    private static final Double TOUGHNESS_FACTOR = 0.60D;
    AttributeModifier skinArmorAttributeModifier;
    AttributeModifier skinToughnessAttributeModifier;
    private Player playerIn;

    /*public PlayerPassiveSkillSkinArmor(Player playerIn) {
        this.playerIn = playerIn;
        parentCapability = IGenericCapability.getUnwrappedPlayerCapability(playerIn, PlayerSkillCapabilityProvider.SKILL_CAP);
    }*/

    public PlayerPassiveSkillSkinArmorAttributeExecutor(PlayerSkillCapabilityInterface parentCapability) {
        super(parentCapability);
        DevilRpg.LOGGER.info("----------------------->CONSTRUCTOR PlayerPassiveSkillSkinArmorAttributeExecutor. Parent capability: {}", parentCapability);
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
            initializeArmorAttributes(playerIn);
            initializeToughnessAttributes(playerIn);

            PlayerAuxiliaryCapabilityInterface auxiliary = IGenericCapability.getUnwrappedPlayerCapability(playerIn, PlayerAuxiliaryCapability.INSTANCE);
            if (auxiliary.isWerewolfTransformation()) {
                testSkinArmorToGetParameters(playerIn, parameters);
                if (parameters.get(IS_COMPLETE_ARMOR).equals("true")) {
                    add();
                } else {
                    remove();
                }
                DevilRpg.LOGGER.info("----------------------->IS_COMPLETE_ARMOR: {}", parameters.get(IS_COMPLETE_ARMOR).toLowerCase());
            } else {
                remove();
            }
            //DevilRpg.LOGGER.info("Player armorValue {}", playerIn.getArmorValue());
            DevilRpg.LOGGER.info("Player Att armor {}",playerIn.getAttributeValue(Attributes.ARMOR));
            DevilRpg.LOGGER.info("Player Att toughness {}", playerIn.getAttributeValue(Attributes.ARMOR_TOUGHNESS));


        }
    }

    private void initializeArmorAttributes(Player playerIn) {
        if (skinArmorAttributeModifier == null ||
                skinArmorAttributeModifier.getAmount() != Double.valueOf(parentCapability.getSkillsPoints().get(SkillEnum.SKIN_ARMOR)) * ARMOR_FACTOR) {
            removeCurrentSkinArmorModifiers();
            skinArmorAttributeModifier = createNewAttributeModifiers(ATTRIBUTE_MODIFIER_ARMOR_UNIQUE_NAME, ARMOR_FACTOR);
            HashMap<String, UUID> capAttModifiersHashMap = parentCapability.getAttributeModifiers();
            addAttributeToCapability(capAttModifiersHashMap, Attributes.ARMOR, skinArmorAttributeModifier.getId());
            parentCapability.setAttributeModifiers(capAttModifiersHashMap, playerIn);
            DevilRpg.LOGGER.info("----------------------->Add {}", skinArmorAttributeModifier.getId());
        }
    }

    private void initializeToughnessAttributes(Player playerIn) {
        if (skinToughnessAttributeModifier == null ||
                skinToughnessAttributeModifier.getAmount() != Double.valueOf(parentCapability.getSkillsPoints().get(SkillEnum.SKIN_ARMOR)) * TOUGHNESS_FACTOR) {
            removeCurrentSkinToughnessModifiers();
            skinToughnessAttributeModifier = createNewAttributeModifiers(ATTRIBUTE_MODIFIER_TOUGHNESS_UNIQUE_NAME, TOUGHNESS_FACTOR);
            HashMap<String, UUID> capAttModifiersHashMap = parentCapability.getAttributeModifiers();
            addAttributeToCapability(capAttModifiersHashMap, Attributes.ARMOR_TOUGHNESS, skinToughnessAttributeModifier.getId());
            parentCapability.setAttributeModifiers(capAttModifiersHashMap, playerIn);
            DevilRpg.LOGGER.info("----------------------->Add {}", skinToughnessAttributeModifier.getId());
        }
    }

    public SkillEnum getSkillEnum() {
        return SkillEnum.SKIN_ARMOR;
    }

    private AttributeModifier createNewAttributeModifiers(String uniqueName, double factor) {
        AttributeModifier newAttributeModifier = createNewAttributeModifier(
                uniqueName,
                Double.valueOf(parentCapability.getSkillsPoints().get(SkillEnum.SKIN_ARMOR)) * factor
        );
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

    private void removeCurrentSkinToughnessModifiers() {
        //HashMap<String, UUID> attributeModifiers = parentCapability.getAttributeModifiers();
        removeCurrentModifierFromPlayer(playerIn, skinToughnessAttributeModifier, Attributes.ARMOR_TOUGHNESS);
        //UUID uuid = removeAttributeFromCapability(attributeModifiers, Attributes.ARMOR);
        //parentCapability.setAttributeModifiers(attributeModifiers, playerIn);
        //DevilRpg.LOGGER.info("----------------------->Remove {}",uuid);
        DevilRpg.LOGGER.info("----------------------->removeCurrentModifiers(): {}", skinToughnessAttributeModifier);
    }

    private void addCurrentModifiers() {
        //HashMap<String, UUID> attributeModifiers = parentCapability.getAttributeModifiers();
        //addAttributeToCapability(attributeModifiers, Attributes.ARMOR, skinArmorAttributeModifier.getId());
        addCurrentModifierTransiently(playerIn, Attributes.ARMOR, skinArmorAttributeModifier);
        addCurrentModifierTransiently(playerIn, Attributes.ARMOR_TOUGHNESS, skinToughnessAttributeModifier);
        //parentCapability.setAttributeModifiers(attributeModifiers, playerIn);
        //DevilRpg.LOGGER.info("----------------------->Add {}",skinArmorAttributeModifier.getId());
        DevilRpg.LOGGER.info("----------------------->addCurrentModifierTransiently(): {}", skinArmorAttributeModifier);
        DevilRpg.LOGGER.info("----------------------->addCurrentModifierTransiently(): {}", skinToughnessAttributeModifier);
    }

    public void add() {
        //removeCurrentModifiers();
        addCurrentModifiers();

    }

    public void remove() {
        removeCurrentSkinArmorModifiers();
        removeCurrentSkinToughnessModifiers();
    }

    private void testSkinArmorToGetParameters(Player player, HashMap<String, String> parameters) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        ItemStack feet = player.getItemBySlot(EquipmentSlot.FEET);


        DevilRpg.LOGGER.info("----------------------->head: {} chest: {} legs: {} feet: {}", head.toString(), chest, legs, feet);
        if (head.getItem() instanceof ArmorItem && chest.getItem() instanceof ArmorItem &&
                legs.getItem() instanceof ArmorItem && feet.getItem() instanceof ArmorItem) {
            ArmorItem headItem = (ArmorItem) head.getItem();
            ArmorItem chestItem = (ArmorItem) chest.getItem();
            ArmorItem legsItem = (ArmorItem) legs.getItem();
            ArmorItem feetItem = (ArmorItem) feet.getItem();

            ArmorMaterial headMaterial = headItem.getMaterial();
            ArmorMaterial chestMaterial = chestItem.getMaterial();
            ArmorMaterial legsMaterial = legsItem.getMaterial();
            ArmorMaterial feetMaterial = feetItem.getMaterial();

            DevilRpg.LOGGER.info("----------------------->Material head : {} chest: {} legs: {} feet: {}", headMaterial.getName(), chestMaterial.getName(), legsMaterial.getName(), feetMaterial.getName());

            if (headMaterial.equals(ArmorMaterials.LEATHER) &&
                    chestMaterial.equals(ArmorMaterials.LEATHER) &&
                    legsMaterial.equals(ArmorMaterials.LEATHER) &&
                    feetMaterial.equals(ArmorMaterials.LEATHER)) {
                parameters.put(PlayerPassiveSkillSkinArmorAttributeExecutor.IS_COMPLETE_ARMOR, "true");
            } else {
                parameters.put(PlayerPassiveSkillSkinArmorAttributeExecutor.IS_COMPLETE_ARMOR, "false");
            }
        } else {
            parameters.put(PlayerPassiveSkillSkinArmorAttributeExecutor.IS_COMPLETE_ARMOR, "false");
        }


    }
}
