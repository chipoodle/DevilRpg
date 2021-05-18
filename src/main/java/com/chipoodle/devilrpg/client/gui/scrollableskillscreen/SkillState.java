package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public enum SkillState {
   OBTAINED(0),
   UNOBTAINED(1);

   private final int id;

   private SkillState(int id) {
      this.id = id;
   }

   public int getId() {
      return this.id;
   }
}