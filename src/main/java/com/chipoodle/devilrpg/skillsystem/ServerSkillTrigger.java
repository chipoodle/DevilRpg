package com.chipoodle.devilrpg.skillsystem;

import static com.chipoodle.devilrpg.DevilRpg.LOGGER;
import com.chipoodle.devilrpg.capability.mana.IBaseManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityProvider;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerManaClientServerHandler;
import com.chipoodle.devilrpg.util.ConstantesPower;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.network.PacketDistributor;

public class ServerSkillTrigger {

    public static ServerSkillTrigger SKILL_TRIGGER;
    public static ServerSkillTranslator SERVER_DB_SKILLS;

    public static void register() {
        SKILL_TRIGGER = new ServerSkillTrigger();
        SERVER_DB_SKILLS = new ServerSkillTranslator();
    }

    public void triggerAction(ServerPlayerEntity playerIn, ConstantesPower triggeredPower) {
        LOGGER.info("----------------->Trigger Action. Is remote? "+playerIn.world.isRemote);
        if (!playerIn.world.isRemote) {
            IPowerContainer poder = SERVER_DB_SKILLS.getSkill(playerIn.world, playerIn, triggeredPower);
            LOGGER.info("-----------------> is remote?"+playerIn.world.isRemote);
            if (SERVER_DB_SKILLS.getSkillLevelFromUserCapability(playerIn, poder) != 0) {
                if (consumeMana(playerIn, poder)) {
                    poder.execute(playerIn.world, playerIn);
                }
            }
        }
    }

    private boolean consumeMana(ServerPlayerEntity playerIn, IPowerContainer poder) {
        float consumedMana = SERVER_DB_SKILLS.getManaCost(poder, playerIn);
        LazyOptional<IBaseManaCapability> mana = playerIn.getCapability(PlayerManaCapabilityProvider.MANA_CAP, null);
        if (mana.map(x -> x.getMana()).orElse(null) - consumedMana >= 0) {
            mana.ifPresent(m -> m.setMana(mana.map(x -> x.getMana()).orElse(null) - consumedMana));
            LOGGER.info("=====Sending to server mana consumed " + mana.map(x -> x.getMana()).orElse(null) + " Cost: " + consumedMana + " player: " + playerIn.getName());
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> playerIn), new PlayerManaClientServerHandler(mana.map(x -> x.getNBTData()).orElse(null)));
            return true;
        }
        return false;
    }
}
