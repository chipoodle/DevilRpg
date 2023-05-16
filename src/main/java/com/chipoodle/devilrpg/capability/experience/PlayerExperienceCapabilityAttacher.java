package com.chipoodle.devilrpg.capability.experience;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
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

import static com.chipoodle.devilrpg.DevilRpg.LOGGER;

public class PlayerExperienceCapabilityAttacher {

    private PlayerExperienceCapabilityAttacher() {
    }

    public static void attach(final AttachCapabilitiesEvent<Entity> event) {
        if(event.getObject().getCapability(PlayerExperienceCapability.INSTANCE).isPresent()) return;
        LOGGER.info("-----> attach PlayerExperienceCapabilityProvider");
        final PlayerExperienceCapabilityProvider provider = new PlayerExperienceCapabilityProvider();
        event.addCapability(PlayerExperienceCapabilityProvider.IDENTIFIER, provider);
    }

    private static class PlayerExperienceCapabilityProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {

        public static final ResourceLocation IDENTIFIER = new ResourceLocation(DevilRpg.MODID, "experience");
        private final PlayerExperienceCapabilityInterface backend = new PlayerExperienceCapabilityImplementation();
        private final LazyOptional<PlayerExperienceCapabilityInterface> optionalData = LazyOptional.of(() -> backend);

        @Override
        @NotNull
        public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return PlayerExperienceCapability.INSTANCE.orEmpty(cap, this.optionalData);
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
