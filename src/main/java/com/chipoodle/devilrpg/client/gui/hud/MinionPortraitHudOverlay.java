package com.chipoodle.devilrpg.client.gui.hud;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.entity.*;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.text.DecimalFormat;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MinionPortraitHudOverlay {

    private final static ResourceLocation soulwolfPortrait = ResourceLocation.parse(
            DevilRpg.MODID + ":textures/entity/soulwolf/soulwolf_portrait_256x256.png");
    private final static ResourceLocation soulbearPortrait = ResourceLocation.parse(
            DevilRpg.MODID + ":textures/entity/soulbear/soulbear_portrait_256x256.png");
    private final static ResourceLocation wispPortrait = ResourceLocation.parse(
            DevilRpg.MODID + ":textures/entity/flyingwisp/wisp_portrait_a_256x256.png");
    private final static ResourceLocation bars = ResourceLocation.parse(
            DevilRpg.MODID + ":textures/gui/minionbars_portrait_256x256.png");

    /* These two variables describe the size of the bar */
    private final static int BAR_WIDTH = 81;
    private final static int BAR_HEIGHT = 81;
    // we will draw the status bar just above the hotbar. obtained by inspecting the
    // vanilla hotbar rendering code
    private final static int vanillaExpLeftX = 1; // leftmost edge of the experience bar
    private final static int vanillaExpTopY = 1; // top of the experience bar

    private final static DecimalFormat dCurrent = new DecimalFormat("#,###.##");
    public static final LayeredDraw.Layer HUD_MINION_PORTRAITS = (guiGraphics, deltaTracker) -> {
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
            SoulWolf h = (SoulWolf) minionCap.getTamableByUUID(wolfKey, player.level()); // se puede optimizar con singleton
            if (h != null && h.isAlive()) {
                renderEntityPortrait(guiGraphics, i++, h.getHealth(), h.getMaxHealth(), skillCap.getImagesOfSkills().get(SkillEnum.SUMMON_SOUL_WOLF), h);
            }
        }

        for (UUID bearKey : soulbearMinionKeys) {
            SoulBear h = (SoulBear) minionCap.getTamableByUUID(bearKey, player.level());
            if (h != null && h.isAlive()) {
                renderEntityPortrait(guiGraphics, i++, h.getHealth(), h.getMaxHealth(), skillCap.getImagesOfSkills().get(SkillEnum.SUMMON_SOUL_BEAR), h);
            }
        }

        for (UUID wispKey : wispMinionKeys) {
            SoulWisp h = (SoulWisp) minionCap.getTamableByUUID(wispKey, player.level());
            if (h != null && h.isAlive()) {
                if (h instanceof SoulWispHealth)
                    renderEntityPortrait(guiGraphics, i++, h.getHealth(), h.getMaxHealth(), skillCap.getImagesOfSkills().get(SkillEnum.SUMMON_WISP_HEALTH), h);
                if (h instanceof SoulWispArcher)
                    renderEntityPortrait(guiGraphics, i++, h.getHealth(), h.getMaxHealth(), skillCap.getImagesOfSkills().get(SkillEnum.SUMMON_WISP_ARCHER), h);
                if (h instanceof SoulWispChopper)
                    renderEntityPortrait(guiGraphics, i++, h.getHealth(), h.getMaxHealth(), skillCap.getImagesOfSkills().get(SkillEnum.SUMMON_WISP_CHOPPER), h);
                if (h instanceof SoulWispForester)
                    renderEntityPortrait(guiGraphics, i++, h.getHealth(), h.getMaxHealth(), skillCap.getImagesOfSkills().get(SkillEnum.SUMMON_WISP_FORESTER), h);
            }
        }
    };

    private static void renderEntityPortrait(GuiGraphics guiGraphics, int i, float health, float maxHealth, ResourceLocation overlayBar, LivingEntity entity) {

        Minecraft mc = Minecraft.getInstance();
        Font fr = mc.font;
        var poseStack = guiGraphics.pose();

        poseStack.pushPose();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.translate(vanillaExpLeftX + 20 * i, vanillaExpTopY, 0);

        poseStack.scale(0.1f, 0.1f, 0.1f);
        // Mostrar el ICONO COMPLETO dentro del recuadro. El blit de 7 args asumia textura 256x256
        // y recortaba solo un trozo del icono (por eso se veia enorme y solo una parte). Usamos
        // la variante de 9 args escalando la textura 256x256 al tamano del recuadro.
        poseStack.pushPose();
        float iconScale = (float) BAR_WIDTH / 256.0F;
        poseStack.scale(iconScale, iconScale, 1.0F);
        guiGraphics.blit(overlayBar, 0, 0, 0, 0, 256, 256, 256, 256);
        poseStack.popPose();
        poseStack.pushPose();

        poseStack.pushPose();
        poseStack.scale(1.0f, 2.0f, 1.0f);
        poseStack.translate(0, -40, 0);
        //Barra negra de fondo
        guiGraphics.blit(bars, 0, BAR_HEIGHT, 0, BAR_HEIGHT, (BAR_WIDTH + 20), 9);
        poseStack.scale(1.04f, 1.2f, 1.0f);
        poseStack.translate(-1.2f, -12.0f, 0);
        guiGraphics.blit(bars, 0, BAR_HEIGHT - 2, 0, BAR_HEIGHT + 9, (int) (BAR_WIDTH * (entity.getArmorValue() / 20f)), 9);
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

        final int NORMAL_TEXTURE_U = BAR_WIDTH; // red texels - see mbe40_hud_overlay.png
        final int HEALTH_BOOST_TEXTURE_U = BAR_WIDTH + 1; // green texels
        final int POISON_TEXTURE_U = BAR_WIDTH + 2; // black texels
        final int WITHER_TEXTURE_U = BAR_WIDTH + 3; // brown texels
        final int ABSORPION_TEXTURE_U = BAR_WIDTH + 3; // brown texels

        if (entity.hasEffect(MobEffects.ABSORPTION)) {
            guiGraphics.blit(bars, 0, 0, ABSORPION_TEXTURE_U, 0, 1, BAR_HEIGHT - 2);
        } else if (entity.hasEffect(MobEffects.WITHER)) {
            guiGraphics.blit(bars, 0, 0, WITHER_TEXTURE_U, 0, 1, BAR_HEIGHT - 2);
        } else if (entity.hasEffect(MobEffects.POISON)) {
            guiGraphics.blit(bars, 0, 0, POISON_TEXTURE_U, 0, 1, BAR_HEIGHT - 2);
        } else if (entity.hasEffect(MobEffects.HEALTH_BOOST)) {
            guiGraphics.blit(bars, 0, 0, HEALTH_BOOST_TEXTURE_U, 0, 1, BAR_HEIGHT - 2);
        } else {
            guiGraphics.blit(bars, 0, 0, NORMAL_TEXTURE_U, 0, 1, BAR_HEIGHT - 2);
        }
        poseStack.popPose();
        String s = dCurrent.format(effectiveHp) + "/" + dCurrent.format(maxHp);
        int textWidth = fr.width(s);

        poseStack.translate(3 + textWidth + (float) textWidth / 2, -0.5f, 0);
        poseStack.pushPose();
        poseStack.scale(1.6f, 0.8f, 1);

        if (entity.hasEffect(MobEffects.ABSORPTION)) {
            /* Draw the shadow string */
            guiGraphics.drawString(fr, s, -fr.width(s) + 1, 2, 0x5A2B00);
            /* Draw the actual string */
            guiGraphics.drawString(fr, s, -fr.width(s), 1, 0xFFD200);
        } else {
            guiGraphics.drawString(fr, s, -fr.width(s) + 1, 2, 0x4D0000);
            guiGraphics.drawString(fr, s, -fr.width(s), 1, 0xFFFFFF);
        }
        poseStack.popPose();
        poseStack.popPose();
        poseStack.popPose();
        poseStack.popPose();
    }

}
