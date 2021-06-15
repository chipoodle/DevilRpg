package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import java.util.List;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SimpleSound;
import net.minecraft.client.gui.toasts.IToast;
import net.minecraft.client.gui.toasts.ToastGui;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SkillToast implements IToast {
   private final SkillElement advancement;
   private boolean hasPlayedSound;

   public SkillToast(SkillElement advancementIn) {
      this.advancement = advancementIn;
   }

   public IToast.Visibility render(MatrixStack p_230444_1_, ToastGui p_230444_2_, long p_230444_3_) {
      Minecraft minecraft = p_230444_2_.getMinecraft();
      minecraft.getTextureManager().bind(TEXTURE);
      RenderSystem.color3f(1.0F, 1.0F, 1.0F);
      SkillDisplayInfo displayinfo = this.advancement.getDisplay();
      p_230444_2_.blit(p_230444_1_, 0, 0, 0, 0, this.width(), this.height());
      if (displayinfo != null) {
         List<IReorderingProcessor> list = minecraft.font.split(displayinfo.getTitle(), 125);
         int i = displayinfo.getFrame() == ScrollableSkillFrameType.CHALLENGE ? 16746751 : 16776960;
         if (list.size() == 1) {
            minecraft.font.draw(p_230444_1_, displayinfo.getFrame().getTranslatedToast(), 30.0F, 7.0F, i | -16777216);
            minecraft.font.draw(p_230444_1_, list.get(0), 30.0F, 18.0F, -1);
         } else {
            int j = 1500;
            float f = 300.0F;
            if (p_230444_3_ < 1500L) {
               int k = MathHelper.floor(MathHelper.clamp((float)(1500L - p_230444_3_) / 300.0F, 0.0F, 1.0F) * 255.0F) << 24 | 67108864;
               minecraft.font.draw(p_230444_1_, displayinfo.getFrame().getTranslatedToast(), 30.0F, 11.0F, i | k);
            } else {
               int i1 = MathHelper.floor(MathHelper.clamp((float)(p_230444_3_ - 1500L) / 300.0F, 0.0F, 1.0F) * 252.0F) << 24 | 67108864;
               int l = this.height() / 2 - list.size() * 9 / 2;

               for(IReorderingProcessor ireorderingprocessor : list) {
                  minecraft.font.draw(p_230444_1_, ireorderingprocessor, 30.0F, (float)l, 16777215 | i1);
                  l += 9;
               }
            }
         }

         if (!this.hasPlayedSound && p_230444_3_ > 0L) {
            this.hasPlayedSound = true;
            if (displayinfo.getFrame() == ScrollableSkillFrameType.CHALLENGE) {
               minecraft.getSoundManager().play(SimpleSound.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F));
            }
         }

         minecraft.getItemRenderer().renderAndDecorateFakeItem(displayinfo.getIcon(), 8, 8);
         return p_230444_3_ >= 5000L ? IToast.Visibility.HIDE : IToast.Visibility.SHOW;
      } else {
         return IToast.Visibility.HIDE;
      }
   }
}