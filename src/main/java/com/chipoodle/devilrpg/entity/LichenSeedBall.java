package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.block.SoulLichenBlock;
import com.chipoodle.devilrpg.blockentity.SoulLichenBlockEntity;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.init.ModBlocks;
import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static com.chipoodle.devilrpg.block.SoulLichenBlock.*;


public class LichenSeedBall extends ThrowableItemProjectile implements ISoulEntity {

    public LichenSeedBall(EntityType<? extends LichenSeedBall> p_i50159_1_, Level p_i50159_2_) {
        super(p_i50159_1_, p_i50159_2_);
    }

    public LichenSeedBall(Level levelIn, LivingEntity throwerIn) {
        super(ModEntities.LICHEN_SEEDBALL.get(), throwerIn, levelIn);
    }

    @Override
    protected @NotNull Item getDefaultItem() {
        return Items.SNOWBALL;
    }

    @OnlyIn(Dist.CLIENT)
    private ParticleOptions getParticle() {
        ItemStack itemstack = this.getItemRaw();
        return itemstack.isEmpty() ? ParticleTypes.SNOWFLAKE : new ItemParticleOption(ParticleTypes.ITEM, itemstack);
    }


    @OnlyIn(Dist.CLIENT)
    @Override
    public void handleEntityEvent(byte id) {
        if (id == 3) {
            ParticleOptions iparticledata = this.getParticle();

            for (int i = 0; i < 8; ++i) {
                this.level.addParticle(iparticledata, this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
            }
        }

    }

    @Override
    protected void onHitBlock(@NotNull BlockHitResult blockHitResult) {
        DevilRpg.LOGGER.debug("---onHitBlock");
        super.onHitBlock(blockHitResult);
        if (!this.level.isClientSide && this.getOwner() != null) {
            BlockPos blockPos = blockHitResult.getBlockPos();
            PlayerSkillCapabilityInterface unwrappedPlayerCapability = IGenericCapability.getUnwrappedPlayerCapability((Player) this.getOwner(), PlayerSkillCapability.INSTANCE);
            setLichen(this.level, (Player) this.getOwner(), blockPos, unwrappedPlayerCapability);
        }
    }

    /**
     * Called when the arrow hits an entity
     */
    @Override
    protected void onHitEntity(@NotNull EntityHitResult result) {
        super.onHitEntity(result);
        Entity targetEntity = result.getEntity();

        if (!this.level.isClientSide) {

            BlockPos blockPos = targetEntity.blockPosition();
            PlayerSkillCapabilityInterface unwrappedPlayerCapability = IGenericCapability.getUnwrappedPlayerCapability((Player) this.getOwner(), PlayerSkillCapability.INSTANCE);
            if (targetEntity.isOnGround()) {
                setLichen(this.level, (Player) this.getOwner(), blockPos, unwrappedPlayerCapability);
            } else {
                // Si la entidad objetivo no está en el suelo, desarrolla raíces

            }


        }

    }

    /**
     * Called when this Entity hits a block or entity.
     */

    @Override
    protected void onHit(@NotNull HitResult result) {
        super.onHit(result);
        if (!this.level.isClientSide) {
            this.level.broadcastEntityEvent(this, (byte) 3);
            //this.remove(RemovalReason.DISCARDED);
            this.discard();
        }
    }

    public void updateLevel(Player owner, SkillEnum callerSkillEnum) {
        this.setOwner(owner);
        LazyOptional<PlayerSkillCapabilityInterface> skill = owner.getCapability(PlayerSkillCapability.INSTANCE);
        if (skill.isPresent()) {
            int puntosAsignados = Objects.requireNonNull(skill.map(PlayerSkillCapabilityInterface::getSkillsPoints).orElse(null)).get(callerSkillEnum);
            float damage = puntosAsignados * 0.15F;
        }
    }

    @Override
    public @NotNull Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
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
                    if (levelIn.getBlockState(airBlockPos).isAir()) {
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
