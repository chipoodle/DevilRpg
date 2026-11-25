package com.chipoodle.devilrpg.eventsubscriber.client;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.capability.stamina.PlayerStaminaCapability;
import com.chipoodle.devilrpg.capability.stamina.PlayerStaminaCapabilityInterface;
import com.chipoodle.devilrpg.network.payload.DirectSkillExecutionPayload;
import com.chipoodle.devilrpg.skillsystem.WerewolfManaPolicy;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Random;

@EventBusSubscriber(modid = DevilRpg.MODID, value = Dist.CLIENT)
public final class ClientForgeResourceEventSubscriber {

    public static final int TICK_COUNT_REGENERATION = 10;
    public static final float BASE_MANA_DEGENERATION = com.chipoodle.devilrpg.util.PlayerResourceRegenPolicy.BASE_MANA_DEGENERATION;
    public static final float BASE_STAMINA_DEGENERATION = com.chipoodle.devilrpg.util.PlayerResourceRegenPolicy.BASE_STAMINA_DEGENERATION;

    @SubscribeEvent
    public static void onPlayerTickResource(PlayerTickEvent.Pre event) {
        if (event.getEntity().level().isClientSide) {
            if (event.getEntity().tickCount % TICK_COUNT_REGENERATION == 0) {
                PlayerManaCapabilityInterface unwrappedPlayerCapabilityMana = IGenericCapability.getUnwrappedPlayerCapability(event.getEntity(), PlayerManaCapability.INSTANCE);
                PlayerStaminaCapabilityInterface unwrappedPlayerCapabilityStamina = IGenericCapability.getUnwrappedPlayerCapability(event.getEntity(), PlayerStaminaCapability.INSTANCE);
                PlayerAuxiliaryCapabilityInterface unwrappedPlayerCapabilityAux = IGenericCapability.getUnwrappedPlayerCapability(event.getEntity(), PlayerAuxiliaryCapability.INSTANCE);
                PlayerSkillCapabilityInterface unwrappedPlayerCapabilitySkill = IGenericCapability.getUnwrappedPlayerCapability(event.getEntity(), PlayerSkillCapability.INSTANCE);

                float manaDegeneration = 0.0f;
                if (unwrappedPlayerCapabilityAux.isWerewolfTransformation()) {
                    manaDegeneration = com.chipoodle.devilrpg.util.PlayerResourceRegenPolicy.manaDegeneration(true,
                            unwrappedPlayerCapabilitySkill.getSkillsPoints().get(SkillEnum.TRANSFORM_WEREWOLF));
                    unwrappedPlayerCapabilityStamina.onPlayerTickEventRegeneration(event.getEntity(), BASE_STAMINA_DEGENERATION);
                }

                unwrappedPlayerCapabilityMana.onPlayerTickEventRegeneration(event.getEntity(), manaDegeneration);
                if (WerewolfManaPolicy.debePedirReversion(unwrappedPlayerCapabilityAux.isWerewolfTransformation(),
                        unwrappedPlayerCapabilityMana.getMana(), event.getEntity().tickCount)) {
                    PacketDistributor.sendToServer(new DirectSkillExecutionPayload(SkillEnum.TRANSFORM_WEREWOLF));
                    event.getEntity().level().playSound(event.getEntity(), event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(),
                            SoundEvents.NOTE_BLOCK_BASS, SoundSource.NEUTRAL, 0.5F,
                            0.4F / (new Random().nextFloat() * 0.4F + 0.8F));
                }
            }
        }
    }
}