package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.network.handler.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;


/**
 * @author Christian
 */
public class ModNetwork {

    private static final String NETWORK_PROTOCOL_VERSION = "1";


    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DevilRpg.MODID, "main"),
            () -> NETWORK_PROTOCOL_VERSION,
            NETWORK_PROTOCOL_VERSION::equals,
            NETWORK_PROTOCOL_VERSION::equals
    );

    public static void register() {

        int networkId = 0;
        CHANNEL.registerMessage(++networkId,
                KeyboardSkillServerHandler.class,
                KeyboardSkillServerHandler::encode,
                KeyboardSkillServerHandler::decode,
                KeyboardSkillServerHandler::onMessage);

        CHANNEL.registerMessage(++networkId,
                PlayerManaClientServerHandler.class,
                PlayerManaClientServerHandler::encode,
                PlayerManaClientServerHandler::decode,
                PlayerManaClientServerHandler::onMessage);

        CHANNEL.registerMessage(++networkId,
                PlayerSkillTreeClientServerHandler.class,
                PlayerSkillTreeClientServerHandler::encode,
                PlayerSkillTreeClientServerHandler::decode,
                PlayerSkillTreeClientServerHandler::onMessage);

        CHANNEL.registerMessage(++networkId,
                WerewolfAttackServerHandler.class,
                WerewolfAttackServerHandler::encode,
                WerewolfAttackServerHandler::decode,
                WerewolfAttackServerHandler::onMessage);

        CHANNEL.registerMessage(++networkId,
                PlayerExperienceClientServerHandler.class,
                PlayerExperienceClientServerHandler::encode,
                PlayerExperienceClientServerHandler::decode,
                PlayerExperienceClientServerHandler::onMessage);

        CHANNEL.registerMessage(++networkId,
                PlayerAuxiliarClientServerHandler.class,
                PlayerAuxiliarClientServerHandler::encode,
                PlayerAuxiliarClientServerHandler::decode,
                PlayerAuxiliarClientServerHandler::onMessage);

        CHANNEL.registerMessage(++networkId,
                PlayerMinionClientServerHandler.class,
                PlayerMinionClientServerHandler::encode,
                PlayerMinionClientServerHandler::decode,
                PlayerMinionClientServerHandler::onMessage);

        CHANNEL.registerMessage(++networkId,
                PotionClientHandler.class,
                PotionClientHandler::encode,
                PotionClientHandler::decode,
                PotionClientHandler::onMessage);

        CHANNEL.registerMessage(++networkId,
                PlayerPassiveSkillServerHandler.class,
                PlayerPassiveSkillServerHandler::encode,
                PlayerPassiveSkillServerHandler::decode,
                PlayerPassiveSkillServerHandler::onMessage);

        CHANNEL.registerMessage(++networkId,
                PlayerStaminaClientServerHandler.class,
                PlayerStaminaClientServerHandler::encode,
                PlayerStaminaClientServerHandler::decode,
                PlayerStaminaClientServerHandler::onMessage);

        CHANNEL.registerMessage(++networkId,
                DirectSkillExecutionServerHandler.class,
                DirectSkillExecutionServerHandler::encode,
                DirectSkillExecutionServerHandler::decode,
                DirectSkillExecutionServerHandler::onMessage);
    }

}
