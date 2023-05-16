package com.chipoodle.devilrpg.capability.auxiliar;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.chipoodle.devilrpg.DevilRpg.LOGGER;


public class PlayerAuxiliaryCapabilityAttacher {

    private PlayerAuxiliaryCapabilityAttacher() {
    }

    public static void attach(final AttachCapabilitiesEvent<Entity> event) {
        if(event.getObject().getCapability(PlayerAuxiliaryCapability.INSTANCE).isPresent()) return;
        LOGGER.info("-----> attach PlayerAuxiliaryCapabilityProvider");
        final PlayerAuxiliaryCapabilityProvider provider = new PlayerAuxiliaryCapabilityProvider();
        event.addCapability(PlayerAuxiliaryCapabilityProvider.IDENTIFIER, provider);
    }

    private static class PlayerAuxiliaryCapabilityProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {

        public static final ResourceLocation IDENTIFIER = new ResourceLocation(DevilRpg.MODID, "auxiliary");
        private final PlayerAuxiliaryCapabilityInterface backend = new PlayerAuxiliaryCapabilityImplementation();
        private final LazyOptional<PlayerAuxiliaryCapabilityInterface> optionalData = LazyOptional.of(() -> backend);

        @Override
        @NotNull
        public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return PlayerAuxiliaryCapability.INSTANCE.orEmpty(cap, this.optionalData);
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
