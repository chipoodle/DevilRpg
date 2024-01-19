package com.chipoodle.devilrpg.client.gui.hud;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.SkillElement;
import com.chipoodle.devilrpg.eventsubscriber.client.ClientModKeyInputEventSubscriber;
import com.chipoodle.devilrpg.util.PowerEnum;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.HashMap;
import java.util.Objects;

public class SkillsIconHudOverlay {
    private static final String IMG_LOCATION = DevilRpg.MODID + ":textures/gui/";
    private static final ResourceLocation EMPTY_POWER_IMAGE_RESOURCE = new ResourceLocation(IMG_LOCATION + "empty-box.png");
    public static final IGuiOverlay HUD_SKILL_ICONS = (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player.isCreative())
            return;

        PlayerSkillCapabilityInterface skillCap = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerSkillCapability.INSTANCE);

        if (skillCap == null)
            return;

        final HashMap<PowerEnum, SkillEnum> powerToSkillDictionary = skillCap.getSkillsNameOfPowers();

        PowerEnum[] powerList = PowerEnum.values();

        int i = 0;
        for (PowerEnum power : powerList) {
            SkillEnum aSkillEnum = powerToSkillDictionary.get(power);
            if (aSkillEnum == null)
                aSkillEnum = SkillEnum.EMPTY;

            SkillElement skillElementByEnum = skillCap.getSkillElementByEnum(aSkillEnum);
            if (skillElementByEnum == null)
                throw new RuntimeException("---->Falló la busqueda de del Skillelement para el skill: " + aSkillEnum);

            Item item = Objects.requireNonNull(skillElementByEnum.getDisplay()).getIcon().getItem();

            ResourceLocation resourceLocation = skillCap.getImagesOfSkills().get(aSkillEnum);
            if (resourceLocation == null)
                resourceLocation = EMPTY_POWER_IMAGE_RESOURCE;
            int x = (screenWidth - 102) + (i * 21);
            int y = screenHeight - 40;
            int width = 12;
            int height = 12;
            boolean onCooldown = player.getCooldowns().isOnCooldown(item);

            //////////player.getCooldowns().getCooldownPercent(item,)
            float cooldownPercent = player.getCooldowns().getCooldownPercent(item, 0);
            float color = 1f - (cooldownPercent)/1.5f;

            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, resourceLocation);
            RenderSystem.setShaderColor(1.0F, color, color, 1.0F);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            // Pinta el icono del botón
            GuiComponent.blit(poseStack, x, y, 0, 0.0F, 0.0F, width, height, width, height);

            String keyName = ClientModKeyInputEventSubscriber.KeyEvent.getKeyName(power);

            //Pinta el texto debajo del botón
            poseStack.pushPose();
            poseStack.translate((x - x * 0.3f) - 1, (y - y * 0.3f) - 1, 0);
            poseStack.scale(0.3f, 0.3f, 0);
            //GuiComponent.drawCenteredString(poseStack, mc.font, ""+cooldownPercent, x + width + (keyName.length() / 2) + (width / 2) + (1 / keyName.length()) * 7, y + (height) - 32, getFGColor(!onCooldown));
            GuiComponent.drawCenteredString(poseStack, mc.font, keyName, x + width + (keyName.length() / 2) + (width / 2) + (1 / keyName.length()) * 7, y + (height) + 32, getFGColor(!onCooldown));
            poseStack.popPose();
            i++;
        }
    };

    public static int getFGColor(boolean active) {
        return active ? 16777215 : 10526880; // White : Light Grey
    }
}