package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.entity.goal.SoulWispChopLogsGoal;
import com.chipoodle.devilrpg.entity.goal.SoulWispFollowOwnerGoal;
import com.chipoodle.devilrpg.entity.goal.SoulWispPlantSaplingsGoal;
import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wisp de la arboleda: unión del leñador (corta árboles y recoge la madera) y del guardabosque
 * (planta saplings y cosecha césped/semillas). Sus dos pasivos (recoger madera y recoger semillas) se
 * activan al subir {@code WISP_LOG_COLLECTOR} y {@code WISP_SEED_COLLECTOR}.
 */
public class SoulWispRanger extends SoulWisp {

    /** Si true está en la fase de "plantar/recolectar"; si false, en la de "cortar". */
    private boolean plantingPhase = false;
    /** Ticks transcurridos en la fase actual (para alternar cuando termina el ciclo). */
    private int phaseTicks = 0;
    /** Duración de cada ciclo de fase (cortar <-> plantar) antes de alternar (8 s). */
    private static final int PHASE_TIMEOUT_TICKS = 160;

    public SoulWispRanger(EntityType<? extends SoulWispRanger> type, Level worldIn) {
        super(type, worldIn);
    }

    public boolean isPlantingPhase() {
        return plantingPhase;
    }

    public void setPlantingPhase(boolean plantingPhase) {
        this.plantingPhase = plantingPhase;
        this.phaseTicks = 0;
    }

    /** true si la entidad lleva un hacha en la mano principal (necesario para cortar). */
    public boolean hasAxe() {
        return this.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof AxeItem;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        this.phaseTicks++;
        // Alternar: si la fase actual se estanca demasiado, pasar a la otra.
        if (this.phaseTicks > PHASE_TIMEOUT_TICKS) {
            setPlantingPhase(!this.plantingPhase);
        }
        // Si se supone que debe cortar pero no tiene hacha -> pasar a plantar (que puede hacer).
        if (!this.plantingPhase && !this.hasAxe()) {
            setPlantingPhase(true);
        }
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new PanicGoal(this, 1.25D));
        this.goalSelector.addGoal(1, new SoulWispFollowOwnerGoal(this, 1.0D, 8.0F, 12.0F, true));
        this.goalSelector.addGoal(2, new SoulWispPlantSaplingsGoal(this));
        this.goalSelector.addGoal(3, new SoulWispChopLogsGoal(this));
        this.goalSelector.addGoal(4, new FloatGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
    }

    public void updateLevel(Player owner) {
        super.updateLevel(owner, null, null, SkillEnum.SUMMON_WISP_RANGER, true);
    }

    @Nullable
    @Override
    public SoulWispRanger getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob ageableMob) {
        return ModEntities.WISP_RANGER.get().create(level);
    }

    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        if (this.getOwner() != null && player == this.getOwner() && hand == InteractionHand.MAIN_HAND) {
            if (this.isOwnedBy(player) || this.isTame()) {
                ItemStack playerItemStack = player.getItemInHand(hand);

                // Mano vacía: devolver el hacha al jugador.
                if (playerItemStack.isEmpty()) {
                    ItemStack wispItemStack = this.getItemInHand(InteractionHand.MAIN_HAND);
                    if (wispItemStack.getItem() instanceof AxeItem) {
                        player.setItemInHand(hand, wispItemStack.copy());
                        this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        this.level().playSound(player, this, SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.NEUTRAL, 2.0F, 1.0F);
                        return InteractionResult.SUCCESS;
                    }
                }

                // Darle el hacha al wisp (solo el que no se quema/el que corta usa el hacha).
                if (playerItemStack.getItem() instanceof AxeItem) {
                    dropPreviousItem(hand);
                    ItemStack itemstack = playerItemStack.copy();
                    itemstack.setCount(1);
                    this.setItemInHand(InteractionHand.MAIN_HAND, itemstack);
                    this.removeInteractionItem(player, playerItemStack);
                    // Al recibir el hacha, pasa a priorizar el corte.
                    this.setPlantingPhase(false);
                    this.level().playSound(player, this, SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.NEUTRAL, 2.0F, 1.0F);
                    return InteractionResult.SUCCESS;
                }
            }
        }
        return super.mobInteract(player, hand);
    }

    private void dropPreviousItem(@NotNull InteractionHand hand) {
        ItemStack mobItemStack = this.getItemInHand(hand);
        Item item = mobItemStack.getItem();
        if (!item.equals(Items.AIR)) {
            ItemEntity itementity = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), mobItemStack);
            this.level().addFreshEntity(itementity);
        }
    }
}
