package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.network.payload.*;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers the custom payloads (networking) of the mod with NeoForge.
 *
 * @author Christian
 */
public class ModNetwork {

    private static final String NETWORK_PROTOCOL_VERSION = "1";

    public static void registerPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(NETWORK_PROTOCOL_VERSION);

        // Serverbound only
        registrar.playToServer(KeyboardSkillPayload.TYPE, KeyboardSkillPayload.STREAM_CODEC, KeyboardSkillPayload::handle);
        registrar.playToServer(WerewolfAttackPayload.TYPE, WerewolfAttackPayload.STREAM_CODEC, WerewolfAttackPayload::handle);
        registrar.playToServer(PlayerPassiveSkillPayload.TYPE, PlayerPassiveSkillPayload.STREAM_CODEC, PlayerPassiveSkillPayload::handle);
        registrar.playToServer(DirectSkillExecutionPayload.TYPE, DirectSkillExecutionPayload.STREAM_CODEC, DirectSkillExecutionPayload::handle);

        // Bidirectional (client <-> server)
        registrar.playBidirectional(PlayerManaPayload.TYPE, PlayerManaPayload.STREAM_CODEC, PlayerManaPayload::handle);
        registrar.playBidirectional(PlayerSkillTreePayload.TYPE, PlayerSkillTreePayload.STREAM_CODEC, PlayerSkillTreePayload::handle);
        registrar.playBidirectional(PlayerExperiencePayload.TYPE, PlayerExperiencePayload.STREAM_CODEC, PlayerExperiencePayload::handle);
        registrar.playBidirectional(PlayerAuxiliarPayload.TYPE, PlayerAuxiliarPayload.STREAM_CODEC, PlayerAuxiliarPayload::handle);
        registrar.playBidirectional(PlayerMinionPayload.TYPE, PlayerMinionPayload.STREAM_CODEC, PlayerMinionPayload::handle);
        registrar.playBidirectional(PlayerStaminaPayload.TYPE, PlayerStaminaPayload.STREAM_CODEC, PlayerStaminaPayload::handle);

        // Clientbound only
        registrar.playToClient(PotionPayload.TYPE, PotionPayload.STREAM_CODEC, PotionPayload::handle);
    }
}
