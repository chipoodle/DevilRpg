package com.chipoodle.devilrpg.capability.skill;


import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlayerSkillCapabilityAttacher  {

    private PlayerSkillCapabilityAttacher() {
    }

    public static void attach(final AttachCapabilitiesEvent<Entity> event) {
        final PlayerSkillCapabilityProvider provider = new PlayerSkillCapabilityProvider();
        event.addCapability(PlayerSkillCapabilityProvider.IDENTIFIER, provider);
    }

    private static class PlayerSkillCapabilityProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {

        public static final ResourceLocation IDENTIFIER = new ResourceLocation(DevilRpg.MODID, "skill");
        private final PlayerSkillCapabilityInterface backend = new PlayerSkillCapabilityImplementation();
        private final LazyOptional<PlayerSkillCapabilityInterface> optionalData = LazyOptional.of(() -> backend);

        @Override
        @NotNull
        public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return PlayerSkillCapability.INSTANCE.orEmpty(cap, this.optionalData);
        }

        void invalidate() {
            this.optionalData.invalidate();
        }

        @Override
        public CompoundTag serializeNBT() {
            return this.backend.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            this.backend.deserializeNBT(nbt);
        }

    }

}
