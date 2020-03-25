package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.network.handler.KeyboardSkillServerHandler;
import com.chipoodle.devilrpg.network.handler.PlayerManaClientServerHandler;
import com.chipoodle.devilrpg.network.handler.PlayerSkillClientServerHandler;
import com.chipoodle.devilrpg.network.handler.WerewolfAttackServerHandler;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

/**
 *
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
        CHANNEL.registerMessage(networkId++,
                KeyboardSkillServerHandler.class, 
                KeyboardSkillServerHandler::encode,
                KeyboardSkillServerHandler::decode,
                KeyboardSkillServerHandler::onMessage);
        
        CHANNEL.registerMessage(networkId++,
                PlayerManaClientServerHandler.class, 
                PlayerManaClientServerHandler::encode,
                PlayerManaClientServerHandler::decode,
                PlayerManaClientServerHandler::onMessage);
        
        CHANNEL.registerMessage(networkId++,
                PlayerSkillClientServerHandler.class, 
                PlayerSkillClientServerHandler::encode,
                PlayerSkillClientServerHandler::decode,
                PlayerSkillClientServerHandler::onMessage);
        
        CHANNEL.registerMessage(networkId++,
                WerewolfAttackServerHandler.class, 
                WerewolfAttackServerHandler::encode,
                WerewolfAttackServerHandler::decode,
                WerewolfAttackServerHandler::onMessage);
    }

}
