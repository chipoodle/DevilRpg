package com.chipoodle.devilrpg.effects;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.block.SoulLichenBlock;
import com.chipoodle.devilrpg.blockentity.SoulLichenBlockEntity;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.init.ModBlocks;
import com.chipoodle.devilrpg.init.ModEffects;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import static com.chipoodle.devilrpg.block.SoulLichenBlock.*;

public class MobEffectEntangling extends MobEffect {
    private Player owner;

    public MobEffectEntangling() {
        super(MobEffectCategory.HARMFUL, 0x00FF0E); // Adjust the color as needed
    }

    public static MobEffectInstance createInstance(int duration, int amplifier, Player owner) {
        // Create an instance of your custom MobEffect with the specified duration and amplifier
        MobEffectEntangling mobEffect = (MobEffectEntangling) ModEffects.ENTANGLING.get();
        mobEffect.setOwner(owner);
        //DevilRpg.LOGGER.debug("--- crated. duration {} amplifier {}, seconds {}", duration, amplifier, duration / 20);
        return new MobEffectInstance(mobEffect, duration, amplifier);
    }

    @Override
    public void applyEffectTick(@NotNull LivingEntity entity, int amplifier) {
        // Calculate the entangling based on the amplifier
        amplifier = Math.max(1, Math.min(amplifier + 1, 5));
        float percentage = amplifier * 0.2f;

        if (owner != null)
            applySoulLichenEffects(entity.getLevel(), entity, owner, percentage);


    }

    @Override
    public void removeAttributeModifiers(LivingEntity entityLivingBaseIn, @NotNull AttributeMap attributeMapIn, int amplifier) {
        // Remove the entangling  resistance attribute modifier when the effect is removed
        DevilRpg.LOGGER.debug(entityLivingBaseIn.getName().getString() + "'s entangling effect has worn off.");
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // This method controls how often the applyEffectTick method is called
        // You can adjust it based on your needs
        return true;
    }

    private void setOwner(Player owner) {
        this.owner = owner;
    }

    private BlockPos findSolidGround(Level level, BlockPos pos) {
        while (pos.getY() > 0 && !level.getBlockState(pos).getMaterial().isSolid()) {
            pos = pos.below();
        }
        return pos;
    }

    private void applySoulLichenEffects(@NotNull Level level, @NotNull Entity entity, Player owner, float percentage) {
        double slowDown = 1.0 - percentage;
        entity.setDeltaMovement(entity.getDeltaMovement().multiply(-slowDown, 0.0, -slowDown).add(0.0,-0.1,0.0));
        entity.hurt(level.damageSources().playerAttack(owner), 1.0F);
        if (!entity.isAlive()) {
            BlockPos blockPos = entity.blockPosition();
            PlayerSkillCapabilityInterface unwrappedPlayerCapability = IGenericCapability.getUnwrappedPlayerCapability(owner, PlayerSkillCapability.INSTANCE);
            if (entity.isOnGround() || (entity.getY() % 1.0 == 0 && entity.getDeltaMovement().y > 0)) {
                // Si la entidad objetivo está en el suelo o volando, coloca SoulLichen
                setLichen(level, owner, blockPos, unwrappedPlayerCapability);
            } else {
                // Encuentra la posición del primer bloque sólido debajo de la entidad
                BlockPos groundPos = findSolidGround(level, blockPos);

                if (groundPos != null) {
                    // Coloca el bloque de SoulLichen en la posición encontrada
                    setLichen(level, owner, groundPos, unwrappedPlayerCapability);
                }
            }
        }
    }

    private void setLichen(Level levelIn, Player player, BlockPos entityBlockPos, PlayerSkillCapabilityInterface skillCap) {
        SoulLichenBlock createdLichenBlock = ModBlocks.SOUL_LICHEN_BLOCK.get();
        //BlockState blockState = levelIn.getBlockState(entityBlockPos);
        Vec3 playerLookVector = player.getLookAngle();
        Direction nearestHorizontalDirection = Direction.getNearest(playerLookVector.x, 0, playerLookVector.z);
        // BlockHitResult playerBlockRayResult = TargetUtils.getPlayerBlockRayResult();
        //BlockPos playerLookPos = playerBlockRayResult.getBlockPos();
        //Direction playerLookDirection = playerBlockRayResult.getDirection();

        // Búsqueda en todos los bloques adyacentes a 1 bloque de distancia
        for (int xOffset = 0; xOffset <= 2; xOffset++) {
            for (int yOffset = 0; yOffset <= 2; yOffset++) {
                for (int zOffset = 0; zOffset <= 2; zOffset++) {

                    int xOffset0 = xOffset == 2 ? -1 : xOffset;
                    int yOffset0 = yOffset == 2 ? -1 : yOffset;
                    int zOffset0 = zOffset == 2 ? -1 : zOffset;

                    BlockPos airBlockPos = entityBlockPos.offset(xOffset0, yOffset0, zOffset0);
                    //levelIn.getBlockState(airBlockPos).isAir()
                    if (SoulLichenBlock.stateCanBeReplaced(levelIn.getBlockState(airBlockPos), createdLichenBlock)) {
                        // Se encontró un bloque de aire, coloca el SoulLichenBlock
                        int skillPoints = skillCap.getSkillsPoints().get(SkillEnum.SOULLICHEN);
                        BooleanProperty faceProperty = SoulLichenBlock.getFaceProperty(Direction.DOWN);

                        BlockState blockState = createdLichenBlock.defaultBlockState()
                                .setValue(SKILL_LEVEL, skillPoints + SoulLichenBlockEntity.LICHEN_BLOCK_LEVEL_OFFSET)
                                .setValue(FACE, Direction.DOWN)
                                .setValue(DIRECTION, nearestHorizontalDirection)
                                .setValue(faceProperty, Boolean.TRUE);
                        createdLichenBlock.setPlacedBy(levelIn, entityBlockPos, blockState, player, ItemStack.EMPTY);
                        //DevilRpg.LOGGER.info("--------> SkillSoulLichen.setLichen Face: {} Direction: {}", Direction.DOWN, nearestHorizontalDirection);
                        levelIn.setBlockAndUpdate(airBlockPos, blockState);
                        return; // No es necesario seguir buscando
                    }
                }
            }
        }
    }
}