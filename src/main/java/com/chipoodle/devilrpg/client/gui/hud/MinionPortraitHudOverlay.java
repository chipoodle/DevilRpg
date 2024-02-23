package com.chipoodle.devilrpg.client.gui.hud;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.entity.*;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.text.DecimalFormat;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MinionPortraitHudOverlay extends GuiComponent {

    private final static ResourceLocation soulwolfPortrait = new ResourceLocation(
            DevilRpg.MODID + ":textures/entity/soulwolf/soulwolf_portrait_256x256.png");
    private final static ResourceLocation soulbearPortrait = new ResourceLocation(
            DevilRpg.MODID + ":textures/entity/soulbear/soulbear_portrait_256x256.png");
    private final static ResourceLocation wispPortrait = new ResourceLocation(
            DevilRpg.MODID + ":textures/entity/flyingwisp/wisp_portrait_a_256x256.png");
    private final static ResourceLocation bars = new ResourceLocation(
            DevilRpg.MODID + ":textures/gui/minionbars_portrait_256x256.png");

    /* These two variables describe the size of the bar */
    private final static int BAR_WIDTH = 81;
    private final static int BAR_HEIGHT = 81;
    // we will draw the status bar just above the hotbar. obtained by inspecting the
    // vanilla hotbar rendering code
    private final static int vanillaExpLeftX = 1; // leftmost edge of the experience bar
    private final static int vanillaExpTopY = 1; // top of the experience bar

    public static final IGuiOverlay HUD_MINION_PORTRAITS = (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null)
            return;

        PlayerSkillCapabilityInterface skillCap = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerSkillCapability.INSTANCE);
        PlayerMinionCapabilityInterface minionCap = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerMinionCapability.INSTANCE);


        if (skillCap == null || minionCap == null)
            return;

        ConcurrentLinkedQueue<UUID> soulwolfMinionKeys = minionCap.getSoulWolfMinions();
        ConcurrentLinkedQueue<UUID> soulbearMinionKeys = minionCap.getSoulBearMinions();
        ConcurrentLinkedQueue<UUID> wispMinionKeys = minionCap.getWispMinions();

        int i = 0;
        for (UUID wolfKey : soulwolfMinionKeys) {
            SoulWolf h = (SoulWolf) minionCap.getTameableByUUID(wolfKey, player.level); // se puede optimizar con singleton
            if (h != null && h.getOwner() != null) {
                renderEntityPortrait(gui, poseStack, i++, h.getHealth(), h.getMaxHealth(), skillCap.getImagesOfSkills().get(SkillEnum.SUMMON_SOUL_WOLF), h);
            }

        }

        for (UUID bearKey : soulbearMinionKeys) {
            SoulBear h = (SoulBear) minionCap.getTameableByUUID(bearKey, player.level);
            if (h != null && h.getOwner() != null) {
                renderEntityPortrait(gui, poseStack, i++, h.getHealth(), h.getMaxHealth(), skillCap.getImagesOfSkills().get(SkillEnum.SUMMON_SOUL_BEAR), h);
            }

        }

        for (UUID wispKey : wispMinionKeys) {
            SoulWisp h = (SoulWisp) minionCap.getTameableByUUID(wispKey, player.level);
            if (h != null && h.getOwner() != null) {
                if (h instanceof SoulWispHealth)
                    renderEntityPortrait(gui, poseStack, i++, h.getHealth(), h.getMaxHealth(), skillCap.getImagesOfSkills().get(SkillEnum.SUMMON_WISP_HEALTH), h);
                if (h instanceof SoulWispCurse)
                    renderEntityPortrait(gui, poseStack, i++, h.getHealth(), h.getMaxHealth(), skillCap.getImagesOfSkills().get(SkillEnum.SUMMON_WISP_CURSE), h);
                if (h instanceof SoulWispArcher)
                    renderEntityPortrait(gui, poseStack, i++, h.getHealth(), h.getMaxHealth(), skillCap.getImagesOfSkills().get(SkillEnum.SUMMON_WISP_ARCHER), h);
                if (h instanceof SoulWispBomber)
                    renderEntityPortrait(gui, poseStack, i++, h.getHealth(), h.getMaxHealth(), skillCap.getImagesOfSkills().get(SkillEnum.SUMMON_WISP_BOMB), h);

            }

        }
    };

    private static void renderEntityPortrait(ForgeGui gui, PoseStack poseStack, int i, float health, float maxHealth, ResourceLocation overlayBar, LivingEntity entity) {

        Minecraft mc = Minecraft.getInstance();
        Font fr = mc.font;
        DecimalFormat d = new DecimalFormat("#,###");
        poseStack.pushPose();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, overlayBar);
        poseStack.translate(vanillaExpLeftX + 20 * i, vanillaExpTopY, 0);

        poseStack.scale(0.2f, 0.2f, 0.2f);
        blit(poseStack, 0, 0, 0, 0, BAR_WIDTH, BAR_HEIGHT, BAR_WIDTH, BAR_HEIGHT);
        poseStack.pushPose();
        RenderSystem.setShaderTexture(0, bars);


        poseStack.pushPose();
        poseStack.scale(1.0f, 2.0f, 1.0f);
        poseStack.translate(0, -40, 0);
        //Barra negra de fondo
        blit(poseStack, 0, BAR_HEIGHT, 0, BAR_HEIGHT, (BAR_WIDTH + 20), 9);
        poseStack.scale(1.04f, 1.2f, 1.0f);
        poseStack.translate(-1.2f, -12.0f, 0);
        blit(poseStack, 0, BAR_HEIGHT - 2, 0, BAR_HEIGHT + 9, (int) (BAR_WIDTH * (entity.getArmorValue() / 20f)), 9);
        poseStack.popPose();
        poseStack.pushPose();
        poseStack.translate(0, BAR_HEIGHT + 2, 0);
        poseStack.scale(1.0f, 2.0f, 1.0f);
        poseStack.translate(1, 1, 0);
        float maxHp = entity.getMaxHealth();
        float absorptionAmount = entity.getAbsorptionAmount();
        float effectiveHp = entity.getHealth() + absorptionAmount;
        poseStack.pushPose();
        poseStack.scale((BAR_WIDTH - 2) * Math.min(1, effectiveHp / maxHp), 1, 1);

        final int WITHER_EFFECT_ID = 20; // is now MobEffects.WITHER
        final int POISON_EFFECT_ID = 19; // is now MobEffects.POISON
        final int REGEN_EFFECT_ID = 10; // is now MobEffects.REGENERATION
        final int NORMAL_TEXTURE_U = BAR_WIDTH; // red texels - see mbe40_hud_overlay.png
        final int HEALTH_BOOST_TEXTURE_U = BAR_WIDTH + 1; // green texels
        final int POISON_TEXTURE_U = BAR_WIDTH + 2; // black texels
        final int WITHER_TEXTURE_U = BAR_WIDTH + 3; // brown texels
        final int ABSORPION_TEXTURE_U = BAR_WIDTH + 3; // brown texels

        if (entity.hasEffect(MobEffects.ABSORPTION)) {
            blit(poseStack, 0, 0, ABSORPION_TEXTURE_U, 0, 1, BAR_HEIGHT - 2);
            //entity.setAbsorptionAmount(entity.getEffect(MobEffects.ABSORPTION).getAmplifier());
        } else if (entity.hasEffect(MobEffects.WITHER)) {
            blit(poseStack, 0, 0, WITHER_TEXTURE_U, 0, 1, BAR_HEIGHT - 2);
        } else if (entity.hasEffect(MobEffects.POISON)) {
            blit(poseStack, 0, 0, POISON_TEXTURE_U, 0, 1, BAR_HEIGHT - 2);
        } else if (entity.hasEffect(MobEffects.HEALTH_BOOST)) {
            blit(poseStack, 0, 0, HEALTH_BOOST_TEXTURE_U, 0, 1, BAR_HEIGHT - 2);
        } else {
            blit(poseStack, 0, 0, NORMAL_TEXTURE_U, 0, 1, BAR_HEIGHT - 2);
        }
        poseStack.popPose();
        String s = d.format(effectiveHp) + "/" + d.format(maxHp);
        int textWidth = fr.width(s);

        poseStack.translate(3 + textWidth + (float) textWidth / 2, -0.5f, 0);
        poseStack.pushPose();
        poseStack.scale(1.6f, 0.8f, 1);

        if (entity.hasEffect(MobEffects.ABSORPTION)) {

            /* Draw the shadow string */
            fr.draw(poseStack, s, -fr.width(s) + 1, 2, 0x5A2B00);

            /* Draw the actual string */
            fr.draw(poseStack, s, -fr.width(s), 1, 0xFFD200);
        } else {
            fr.draw(poseStack, s, -fr.width(s) + 1, 2, 0x4D0000);
            fr.draw(poseStack, s, -fr.width(s), 1, 0xFFFFFF);
        }
        poseStack.popPose();
        poseStack.popPose();
        poseStack.popPose();
        poseStack.popPose();
    }

}