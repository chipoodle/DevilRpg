package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class SkillToast implements Toast {
    private final SkillElement advancement;
    private boolean hasPlayedSound;

    public SkillToast(SkillElement advancementIn) {
        this.advancement = advancementIn;
    }

    public Toast.Visibility render(GuiGraphics guiGraphics, ToastComponent toastComponent, long timeSinceLastVisible) {
        Minecraft minecraft = toastComponent.getMinecraft();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        SkillDisplayInfo displayinfo = this.advancement.getDisplay();
        guiGraphics.fill(0, 0, this.width(), this.height(), 0xCC000000);
        if (displayinfo != null) {
            List<FormattedCharSequence> list = minecraft.font.split(displayinfo.getTitle(), 125);
            int i = displayinfo.getFrame() == SkillFrameType.CHALLENGE ? 16746751 : 16776960;
            if (list.size() == 1) {
                guiGraphics.drawString(minecraft.font, displayinfo.getFrame().getDisplayName(), 30, 7, i | -16777216);
                guiGraphics.drawString(minecraft.font, list.get(0), 30, 18, -1);
            } else {
                int j = 1500;
                float f = 300.0F;
                if (timeSinceLastVisible < 1500L) {
                    int k = Mth.floor(Mth.clamp((float) (1500L - timeSinceLastVisible) / 300.0F, 0.0F, 1.0F) * 255.0F) << 24 | 67108864;
                    guiGraphics.drawString(minecraft.font, displayinfo.getFrame().getDisplayName(), 30, 11, i | k);
                } else {
                    int i1 = Mth.floor(Mth.clamp((float) (timeSinceLastVisible - 1500L) / 300.0F, 0.0F, 1.0F) * 252.0F) << 24 | 67108864;
                    int l = this.height() / 2 - list.size() * 9 / 2;

                    for (FormattedCharSequence ireorderingprocessor : list) {
                        guiGraphics.drawString(minecraft.font, ireorderingprocessor, 30, l, 16777215 | i1);
                        l += 9;
                    }
                }
            }

            if (!this.hasPlayedSound && timeSinceLastVisible > 0L) {
                this.hasPlayedSound = true;
                if (displayinfo.getFrame() == SkillFrameType.CHALLENGE) {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F));
                }
            }
            guiGraphics.renderItem(displayinfo.getIcon(), 8, 8);
            return (double) timeSinceLastVisible >= 5000.0D * toastComponent.getNotificationDisplayTimeMultiplier() ? Toast.Visibility.HIDE : Toast.Visibility.SHOW;
        } else {
            return Toast.Visibility.HIDE;
        }
    }
}
