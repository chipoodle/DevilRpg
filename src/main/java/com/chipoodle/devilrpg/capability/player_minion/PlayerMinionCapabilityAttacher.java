package com.chipoodle.devilrpg.capability.player_minion;


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

public class PlayerMinionCapabilityAttacher {

    private PlayerMinionCapabilityAttacher() {
    }

    public static void attach(final AttachCapabilitiesEvent<Entity> event) {
        final PlayerMinionCapabilityProvider provider = new PlayerMinionCapabilityProvider();
        event.addCapability(PlayerMinionCapabilityProvider.IDENTIFIER, provider);
    }

    private static class PlayerMinionCapabilityProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {

        public static final ResourceLocation IDENTIFIER = new ResourceLocation(DevilRpg.MODID, "minion");
        private final PlayerMinionCapabilityInterface backend = new PlayerMinionCapabilityImplementation();
        private final LazyOptional<PlayerMinionCapabilityInterface> optionalData = LazyOptional.of(() -> backend);

        @Override
        @NotNull
        public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return PlayerMinionCapability.INSTANCE.orEmpty(cap, this.optionalData);
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
