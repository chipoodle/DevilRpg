package com.chipoodle.devilrpg.eventsubscriber.common;

import com.chipoodle.devilrpg.DevilRpg;
import com.mojang.authlib.Agent;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.YggdrasilUserAuthentication;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.net.Proxy;

public class LoginEventSuscriber {

    @Mod.EventBusSubscriber(modid = DevilRpg.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public class ClientEvents {

        @SubscribeEvent
        public static void onFMLClientSetupEvent(final FMLClientSetupEvent event) {
            var auth = new YggdrasilUserAuthentication(new YggdrasilAuthenticationService(Proxy.NO_PROXY, ""), "", Agent.MINECRAFT);
            auth.setUsername("chipoodle4378"); //Set your account’s Minecraft username or account email here
            auth.setPassword("Chr1st14n&&");
            try {
                if (auth.canLogIn()) {
                    auth.logIn();
                    System.out.println("ACCESS_TOKEN " + auth.getAuthenticatedToken());
                    System.out.println("UUID " + auth.getUserID());
                    System.out.println("Agent " + auth.getAgent().getName());
                }
            } catch (AuthenticationException e) {
                e.printStackTrace();
            }
            //System.exit(0);
        }

    }
}
