package com.chipoodle.devilrpg.client.gui.hud;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.SkillElement;
import com.chipoodle.devilrpg.eventsubscriber.client.ClientModKeyInputEventSubscriber;
import com.chipoodle.devilrpg.skillsystem.AbstractSkillExecutor;
import com.chipoodle.devilrpg.skillsystem.AbstractSkillSeedsInInventoryExecutor;
import com.chipoodle.devilrpg.util.PowerEnum;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.HashMap;
import java.util.Objects;

public class SkillsIconHudOverlay {
    // Ajustes modificados
    public static final int INITIAL_X = 120; // Aumentado para mover todo a la izquierda
    public static final int MAX_KEYNAME_LENGTH = 8;
    public static final int LINE_HEIGHT = 8; // Reducido para menos espacio entre líneas
    public static final int ICON_SIZE = 18; // Tamaño del icono
    public static final int BASE_Y_OFFSET = 60; // Posición vertical
    public static final float TEXT_SCALE = 0.4f; // Escala del texto
    public static final int ICON_SPACING = 20; // Espacio entre iconos reducido
    private static final String IMG_LOCATION = DevilRpg.MODID + ":textures/gui/";
    private static final ResourceLocation EMPTY_POWER_IMAGE_RESOURCE = new ResourceLocation(IMG_LOCATION + "empty-box.png");
    public static final IGuiOverlay HUD_SKILL_ICONS = (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Font font = mc.font;

        PlayerSkillCapabilityInterface skillCap = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerSkillCapability.INSTANCE);
        if (skillCap == null || player == null || player.isCreative())
            return;

        final HashMap<PowerEnum, SkillEnum> powerToSkillDictionary = skillCap.getSkillsNameOfPowers();
        PowerEnum[] powerList = PowerEnum.values();

        int i = 0;
        for (PowerEnum power : powerList) {
            SkillEnum aSkillEnum = powerToSkillDictionary.getOrDefault(power, SkillEnum.EMPTY);
            SkillElement skillElementByEnum = skillCap.getSkillElementByEnum(aSkillEnum);
            if (skillElementByEnum == null)
                throw new RuntimeException("Failed to find SkillElement for skill: " + aSkillEnum);

            Item item = Objects.requireNonNull(skillElementByEnum.getDisplay()).getIcon().getItem();
            String keyName = ClientModKeyInputEventSubscriber.KeyEvent.getKeyName(power);

            ResourceLocation resourceLocation = skillCap.getImagesOfSkills().getOrDefault(aSkillEnum, EMPTY_POWER_IMAGE_RESOURCE);

            // Posicionamiento ajustado
            int x = (screenWidth - INITIAL_X) + (i * ICON_SPACING);
            int y = screenHeight - BASE_Y_OFFSET;
            boolean onCooldown = player.getCooldowns().isOnCooldown(item);
            float cooldownPercent = player.getCooldowns().getCooldownPercent(item, 0);
            float color = 1f - (cooldownPercent) / 1.5f;

            // Renderizar icono
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, resourceLocation);
            RenderSystem.setShaderColor(1.0F, color, color, onCooldown ? 0.5F : 1.0F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            GuiComponent.blit(poseStack, x, y, 0, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

            // Indicador de semillas si es necesario
            if (!aSkillEnum.equals(SkillEnum.EMPTY)) {
                AbstractSkillExecutor loadedSkillExecutor = skillCap.getLoadedSkillExecutor(aSkillEnum);
                if (loadedSkillExecutor instanceof AbstractSkillSeedsInInventoryExecutor) {
                    renderSeedIndicator(mc, poseStack, x, y, ICON_SIZE, player);
                }
            }

            // Renderizar nombre de la tecla con menos espacio vertical
            renderKeyName(poseStack, font, x, y, ICON_SIZE, ICON_SIZE, keyName, onCooldown);
            i++;
        }
    };

    private static void renderSeedIndicator(Minecraft mc, com.mojang.blaze3d.vertex.PoseStack poseStack,
                                            int x, int y, int width, Player player) {
        ItemStack seedStack = ItemStack.EMPTY;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(Items.WHEAT_SEEDS) || stack.is(Items.BEETROOT_SEEDS) ||
                    stack.is(Items.MELON_SEEDS) || stack.is(Items.PUMPKIN_SEEDS)) {
                seedStack = stack;
                break;
            }
        }

        boolean hasSeeds = !seedStack.isEmpty();
        float seedOpacity = hasSeeds ? 1.0F : 0.3F;
        int seedX = x + width - 8;
        int seedY = y - 2;

        poseStack.pushPose();
        poseStack.translate(seedX, seedY, 0);
        poseStack.scale(0.5f, 0.5f, 1.0f);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, seedOpacity);
        if (hasSeeds) {
            mc.getItemRenderer().renderGuiItem(poseStack, seedStack, 0, 0);
        }
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static void renderKeyName(com.mojang.blaze3d.vertex.PoseStack poseStack, Font font,
                                      int x, int y, int width, int height,
                                      String keyName, boolean onCooldown) {
        poseStack.pushPose();
        try {
            int textColor = getFGColor(!onCooldown);
            poseStack.scale(TEXT_SCALE, TEXT_SCALE, TEXT_SCALE);

            // Posición ajustada para centrar el texto debajo del ícono
            float scaledX = (x + width / 2f) / TEXT_SCALE;  // Centro horizontal del ícono
            float scaledY = (y + height + 4) / TEXT_SCALE;   // Misma posición vertical

            if (keyName.length() > MAX_KEYNAME_LENGTH) {
                int splitPos = keyName.length() / 2;
                String line1 = keyName.substring(0, splitPos);
                String line2 = keyName.substring(splitPos);

                // Centrar cada línea independientemente
                float line1Width = font.width(line1);
                float line2Width = font.width(line2);

                font.draw(poseStack, line1,
                        scaledX - line1Width / 2,  // Restamos la mitad del ancho para centrar
                        scaledY, textColor);
                font.draw(poseStack, line2,
                        scaledX - line2Width / 2,
                        scaledY + LINE_HEIGHT, textColor);
            } else {
                // Centrar texto en una sola línea
                float textWidth = font.width(keyName);
                font.draw(poseStack, keyName,
                        scaledX - textWidth / 2,  // Restamos la mitad del ancho para centrar
                        scaledY, textColor);
            }
        } finally {
            poseStack.popPose();
        }
    }

    public static int getFGColor(boolean active) {
        return active ? 16777215 : 10526880; // Blanco : Gris claro
    }
}