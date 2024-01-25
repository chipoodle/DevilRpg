package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.capability.stamina.PlayerStaminaCapability;
import com.chipoodle.devilrpg.capability.stamina.PlayerStaminaCapabilityInterface;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.init.ModSounds;
import com.chipoodle.devilrpg.network.handler.WerewolfAttackServerHandler;
import com.chipoodle.devilrpg.util.IRenderUtilities;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.chipoodle.devilrpg.util.TargetUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.util.LazyOptional;

import java.util.*;

public class SkillShapeshiftWerewolf extends AbstractPlayerPassiveAttributeExecutor implements ICapabilityAttributeModifier {
    // public static final String HEALTH = "HEALTH";
    public static final String SPEED = "SPEED";
    public static final String STEP = "STEP";
    private static final ResourceLocation SUMMON_SOUND = new ResourceLocation(DevilRpg.MODID, "summon");
    private static final Random rand = new Random();
    //AttributeModifier healthAttributeModifier;
    AttributeModifier speedAttributeModifier;
    AttributeModifier stepAttributeModifier;

    public SkillShapeshiftWerewolf(PlayerSkillCapabilityInterface parentCapability) {
        super(parentCapability);
        DevilRpg.LOGGER.info("----------------------->CONSTRUCTOR SkillShapeshiftWerewolf. {}", this);
    }

    @Override
    public SkillEnum getSkillEnum() {
        return SkillEnum.TRANSFORM_WEREWOLF;
    }

    @Override
    public boolean arePreconditionsMetBeforeConsumingResource(Player player) {
        Entity vehicle = player.getVehicle();
        return vehicle == null;
    }

    @Override
    public boolean isResourceConsumptionBypassed(Player player) {
        PlayerAuxiliaryCapabilityInterface unwrappedPlayerCapabilityAux = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
        return unwrappedPlayerCapabilityAux.isWerewolfTransformation();
    }

    @Override
    public void execute(Level worldIn, Player player, HashMap<String, String> parameters) {
        if (!worldIn.isClientSide) {

            DevilRpg.LOGGER.debug("==========> Step just before transformation: {}", Objects.requireNonNull(player.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get())).getValue());


            Random rand = new Random();
            PlayerStaminaCapabilityInterface stamina = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerStaminaCapability.INSTANCE);
            stamina.setStamina(0,player);

            //Attribute attribute = ForgeMod.STEP_HEIGHT_ADDITION.get();

            LazyOptional<PlayerAuxiliaryCapabilityInterface> aux = player.getCapability(PlayerAuxiliaryCapability.INSTANCE);
            boolean transformation = aux.map(PlayerAuxiliaryCapabilityInterface::isWerewolfTransformation).orElse(false);
            aux.ifPresent(x -> x.setWerewolfTransformation(!transformation, player));

            removeCurrentModifiersSpeed(player);
            removeCurrentModifiersStep(player);
            if (!transformation) {
                createNewAttributeModifiersSpeed();
                createNewAttributeModifiersStep();
                addCurrentModifiersSpeed(player);
                addCurrentModifiersStep(player);
                worldIn.playSound(null, player.getX(), player.getY(), player.getZ(),
                        ModSounds.METAL_SWORD_SOUND.get(), SoundSource.NEUTRAL, 0.5F,
                        0.4F / (rand.nextFloat() * 0.4F + 0.8F));

            } else {
                worldIn.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.NOTE_BLOCK_BASS.get(), SoundSource.NEUTRAL, 0.5F,
                        0.4F / (new Random().nextFloat() * 0.4F + 0.8F));
            }

            super.executePassiveChildren(getSkillEnum(), worldIn, player);
            double maxMovementSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
            DevilRpg.LOGGER.info("max movement speed {}", maxMovementSpeed);
            DevilRpg.LOGGER.debug("==========> Step after transformation: {}", Objects.requireNonNull(player.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get())).getValue());
        } else {
            IRenderUtilities.rotationParticles(Minecraft.getInstance().level, RandomSource.create(), player, ParticleTypes.EFFECT, 17, 1);
        }
    }

    private void createNewAttributeModifiersSpeed() {
        speedAttributeModifier = createNewAttributeModifier(
                SkillEnum.TRANSFORM_WEREWOLF.name() + SPEED,
                parentCapability.getSkillsPoints().get(SkillEnum.TRANSFORM_WEREWOLF) * 0.0045);
        DevilRpg.LOGGER.info("----------------------->createNewAttributeModifiersSpeed(): {}", speedAttributeModifier);
    }

    private void createNewAttributeModifiersStep() {
        stepAttributeModifier = createNewAttributeModifier(
                SkillEnum.TRANSFORM_WEREWOLF.name() + STEP,
                0.4);
        DevilRpg.LOGGER.info("----------------------->createNewAttributeModifiersStep(): {}", speedAttributeModifier);
    }

    private void removeCurrentModifiersSpeed(Player playerIn) {
        HashMap<String, UUID> attributeModifiers = parentCapability.getAttributeModifiers();
        removeCurrentModifierFromPlayer(playerIn, speedAttributeModifier, Attributes.MOVEMENT_SPEED);
        removeAttributeFromCapability(attributeModifiers, Attributes.MOVEMENT_SPEED);
        parentCapability.setAttributeModifiers(attributeModifiers, playerIn);
        DevilRpg.LOGGER.info("----------------------->removeCurrentModifiersSpeed(): {}", speedAttributeModifier);
    }

    private void removeCurrentModifiersStep(Player playerIn) {
        HashMap<String, UUID> attributeModifiers = parentCapability.getAttributeModifiers();
        removeCurrentModifierFromPlayer(playerIn, stepAttributeModifier,ForgeMod.STEP_HEIGHT_ADDITION.get());
        removeAttributeFromCapability(attributeModifiers, ForgeMod.STEP_HEIGHT_ADDITION.get());
        parentCapability.setAttributeModifiers(attributeModifiers, playerIn);
        DevilRpg.LOGGER.info("----------------------->removeCurrentModifiersStep(): {}", stepAttributeModifier);
    }

    private void addCurrentModifiersSpeed(Player playerIn) {
        HashMap<String, UUID> attributeModifiers = parentCapability.getAttributeModifiers();
        //addAttributeToCapability(attributeModifiers, Attributes.MAX_HEALTH, healthAttributeModifier.getId());
        addAttributeToCapability(attributeModifiers, Attributes.MOVEMENT_SPEED, speedAttributeModifier.getId());
        parentCapability.setAttributeModifiers(attributeModifiers, playerIn);
        //addCurrentModifierTransiently(playerIn, Attributes.MAX_HEALTH, healthAttributeModifier);
        addCurrentModifierTransiently(playerIn, Attributes.MOVEMENT_SPEED, speedAttributeModifier);
        DevilRpg.LOGGER.info("----------------------->addCurrentModifiersSpeed(): {}", speedAttributeModifier);
    }

    private void addCurrentModifiersStep(Player playerIn) {
        HashMap<String, UUID> attributeModifiers = parentCapability.getAttributeModifiers();
        //addAttributeToCapability(attributeModifiers, Attributes.MAX_HEALTH, healthAttributeModifier.getId());
        addAttributeToCapability(attributeModifiers, ForgeMod.STEP_HEIGHT_ADDITION.get(), stepAttributeModifier.getId());
        parentCapability.setAttributeModifiers(attributeModifiers, playerIn);
        //addCurrentModifierTransiently(playerIn, Attributes.MAX_HEALTH, healthAttributeModifier);
        addCurrentModifierTransiently(playerIn, ForgeMod.STEP_HEIGHT_ADDITION.get(), stepAttributeModifier);
        DevilRpg.LOGGER.info("----------------------->addCurrentModifiersStep(): {}", stepAttributeModifier);
    }

    /**
     * Must be called inside playerTickEvent. Client side
     *
     * @param player              the player
     * @param auxiliaryCapability Auxiliar capability
     */
    @OnlyIn(Dist.CLIENT)
    public void playerTickEventAttack(final Player player, PlayerAuxiliaryCapabilityInterface auxiliaryCapability) {
        LivingEntity closestEnemy = TargetUtils.findClosestEnemy(player);
        if (closestEnemy != null) {
            player.setLastHurtMob(closestEnemy);
            InteractionHand hand = auxiliaryCapability.swingHands(player);
            DevilRpg.LOGGER.info("-------> Closest enemy: {} hand: {}", closestEnemy, hand);
            renderHitParticles(player, hand);
            attackEnemies(closestEnemy, hand);
        }
    }

    /**
     * @param target
     * @param hand
     */
    private void attackEnemies(LivingEntity target, InteractionHand hand) {
        //if (target != null /* && TargetUtils.canReachTarget(player, target) */) {
        ModNetwork.CHANNEL.sendToServer(new WerewolfAttackServerHandler(target.getId(), hand));
        //}
    }

    /**
     * @param player
     * @param hand
     */
    private void renderHitParticles(final Player player, InteractionHand hand) {
        Vec3 vec = player.getLookAngle();
        double clawSideX = vec.z() * (hand.equals(InteractionHand.MAIN_HAND) ? 0.6 : -0.6);
        double clawSideZ = vec.x() * (hand.equals(InteractionHand.OFF_HAND) ? 0.6 : -0.6);
        double dx = player.getX() + clawSideX;
        double dz = player.getZ() + clawSideZ;
        double dy = player.getY() + player.getEyeHeight();// + 2.0f;// player.getEyeHeight(); // you
        // probably don't actually want to
        // subtract the vec.yCoord, unless the position depends on the
        // player's pitch
        float movement = 1.78572f;
        double acceleration = 0f;
        for (int i = 0; i < 2; ++i) {
            movement += 0.35714f;
            acceleration += 0.005D;
            double speedX = rand.nextGaussian() * 0.02D;
            double speedY = rand.nextGaussian() * 0.02D;
            double speedZ = rand.nextGaussian() * 0.02D;

            player.level.addParticle(ParticleTypes.CLOUD, dx + (vec.x() * movement), dy,
                    dz + (vec.z() * movement), speedX, speedY, speedZ);
        }
    }
}
