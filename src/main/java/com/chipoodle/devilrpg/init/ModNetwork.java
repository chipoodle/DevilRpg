package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.network.handler.*;

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
        
        CHANNEL.registerMessage(networkId++,
                PlayerExperienceClientServerHandler.class, 
                PlayerExperienceClientServerHandler::encode,
                PlayerExperienceClientServerHandler::decode,
                PlayerExperienceClientServerHandler::onMessage);
        
        CHANNEL.registerMessage(networkId++,
        		PlayerAuxiliarClientServerHandler.class, 
        		PlayerAuxiliarClientServerHandler::encode,
        		PlayerAuxiliarClientServerHandler::decode,
        		PlayerAuxiliarClientServerHandler::onMessage);

        CHANNEL.registerMessage(networkId++,
        		PlayerMinionClientServerHandler.class, 
        		PlayerMinionClientServerHandler::encode,
        		PlayerMinionClientServerHandler::decode,
        		PlayerMinionClientServerHandler::onMessage);
        
        CHANNEL.registerMessage(networkId++,
        		PotionClientServerHandler.class, 
        		PotionClientServerHandler::encode,
        		PotionClientServerHandler::decode,
        		PotionClientServerHandler::onMessage);

        CHANNEL.registerMessage(networkId++,
                PassiveSkillServerHandler.class,
                PassiveSkillServerHandler::encode,
                PassiveSkillServerHandler::decode,
                PassiveSkillServerHandler::onMessage);
    }

}
