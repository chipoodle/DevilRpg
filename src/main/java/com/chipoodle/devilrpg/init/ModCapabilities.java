package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityImplementation;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityImplementation;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityInterface;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityImplementation;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityInterface;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityImplementation;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.capability.stamina.PlayerStaminaCapabilityImplementation;
import com.chipoodle.devilrpg.capability.stamina.PlayerStaminaCapabilityInterface;
import com.chipoodle.devilrpg.capability.tamable_minion.TamableMinionCapabilityImplementation;
import com.chipoodle.devilrpg.capability.tamable_minion.TamableMinionCapabilityInterface;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * NeoForge Data Attachments for all player / minion state.
 * <p>
 * The old Forge capability system was replaced by data attachments in NeoForge.
 * Every {@link AttachmentType} is registered in the {@code neoforge:attachment_types}
 * registry and lazily created on demand via {@link net.neoforged.neoforge.attachment.IAttachmentHolder#getData(java.util.function.Supplier)}.
 */
public final class ModCapabilities {

    private ModCapabilities() {
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, DevilRpg.MODID);

    public static final Supplier<AttachmentType<PlayerAuxiliaryCapabilityInterface>> PLAYER_AUXILIARY =
            ATTACHMENT_TYPES.register("player_auxiliary",
                    () -> AttachmentType.<CompoundTag, PlayerAuxiliaryCapabilityInterface>serializable(() -> new PlayerAuxiliaryCapabilityImplementation()).build());

    public static final Supplier<AttachmentType<PlayerExperienceCapabilityInterface>> PLAYER_EXPERIENCE =
            ATTACHMENT_TYPES.register("player_experience",
                    () -> AttachmentType.<CompoundTag, PlayerExperienceCapabilityInterface>serializable(() -> new PlayerExperienceCapabilityImplementation()).build());

    public static final Supplier<AttachmentType<PlayerManaCapabilityInterface>> PLAYER_MANA =
            ATTACHMENT_TYPES.register("player_mana",
                    () -> AttachmentType.<CompoundTag, PlayerManaCapabilityInterface>serializable(() -> new PlayerManaCapabilityImplementation()).build());

    public static final Supplier<AttachmentType<PlayerMinionCapabilityInterface>> PLAYER_MINION =
            ATTACHMENT_TYPES.register("player_minion",
                    () -> AttachmentType.<CompoundTag, PlayerMinionCapabilityInterface>serializable(() -> new PlayerMinionCapabilityImplementation()).build());

    public static final Supplier<AttachmentType<PlayerSkillCapabilityInterface>> PLAYER_SKILL =
            ATTACHMENT_TYPES.register("player_skill",
                    () -> AttachmentType.<CompoundTag, PlayerSkillCapabilityInterface>serializable(() -> new PlayerSkillCapabilityImplementation()).build());

    public static final Supplier<AttachmentType<PlayerStaminaCapabilityInterface>> PLAYER_STAMINA =
            ATTACHMENT_TYPES.register("player_stamina",
                    () -> AttachmentType.<CompoundTag, PlayerStaminaCapabilityInterface>serializable(() -> new PlayerStaminaCapabilityImplementation()).build());

    public static final Supplier<AttachmentType<TamableMinionCapabilityInterface>> TAMABLE_MINION =
            ATTACHMENT_TYPES.register("tamable_minion",
                    () -> AttachmentType.<CompoundTag, TamableMinionCapabilityInterface>serializable(() -> new TamableMinionCapabilityImplementation()).build());
}
