package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.entity.SoulWolf;
import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.skillsystem.AbstractSkillExecutor;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.chipoodle.devilrpg.util.TargetUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import java.util.HashMap;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SkillSummonSoulWolf extends AbstractSkillExecutor {

    /** El cupo vive en la capability: lo respetan la invocación y la entrada al mundo. */
    private static final int NUMBER_OF_SUMMONS = PlayerMinionCapabilityInterface.SOUL_WOLF_CAPACITY;

    public SkillSummonSoulWolf(PlayerSkillCapabilityInterface parentCapability) {
        super(parentCapability);
    }

    @Override
    public SkillEnum getSkillEnum() {
        return SkillEnum.SUMMON_SOUL_WOLF;
    }

    @Override
    public boolean arePreconditionsMetBeforeConsumingResource(Player player) {
        return !player.getCooldowns().isOnCooldown(icon.getItem());
    }

    @Override
    public void execute(Level level, Player player, HashMap<String, String> parameters) {
        if (!player.getCooldowns().isOnCooldown(icon.getItem())) {
            if (!level.isClientSide) {
                try {
                Random rand = new Random();
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 0.5F, 0.4F / (rand.nextFloat() * 0.4F + 0.8F));
                PlayerMinionCapabilityInterface min = player.getData(PlayerMinionCapability.INSTANCE);
                min.removeAllSoulBear(player);
                ConcurrentLinkedQueue<UUID> keys = min.getSoulWolfMinions();
                if (keys == null) keys = new ConcurrentLinkedQueue<>(); // getSoulWolfMinions puede devolver null si falla la deserializacion del NBT

                keys.offer(summonSoulWolf(level, player, rand).getUUID());
                DevilRpg.LOGGER.info("[Minion] lobo invocado: {} en la lista (cupo {})", keys.size(), NUMBER_OF_SUMMONS);
                // Antes esto era un `if` (un solo intento) y, si el más viejo no aparecía, la lista crecía sin
                // límite: invocabas y no sustituía a nadie. Ahora es un bucle hasta bajar del cupo, con tope de
                // seguridad y logs, para que converja siempre.
                int intentos = 0;
                while (keys.size() > NUMBER_OF_SUMMONS && intentos++ < NUMBER_OF_SUMMONS + 4) {
                    UUID key = keys.peek();
                    SoulWolf viejo = key == null ? null : (SoulWolf) min.getTamableByUUID(key, player.level());
                    if (viejo == null) {
                        // Diagnostico: la persistencia SI encuentra a los minions con level.getEntity(uuid), asi
                        // que si aqui no aparece hay que ver si es el nivel o la busqueda.
                        net.minecraft.world.entity.Entity directo = key == null ? null
                                : (player.level() instanceof ServerLevel sl ? sl.getEntity(key) : null);
                        DevilRpg.LOGGER.warn("[Minion] el lobo mas viejo ({}) no aparece: nivel={} clientSide={} getEntity directo={} claseJugador={}",
                                key, player.level().dimension().location(), player.level().isClientSide, directo, player.getClass().getSimpleName());
                        break;
                    }
                    int antes = keys.size();
                    DevilRpg.LOGGER.info("[Minion] sustituyo al lobo mas viejo {}", key);
                    min.removeSoulWolf(player, viejo); // ya quita el UUID de la lista
                    if (keys.size() >= antes) {
                        DevilRpg.LOGGER.warn("[Minion] quite el lobo {} pero la lista sigue en {}: la lista que uso la skill no es la de la capability", key, keys.size());
                        break;
                    }
                }
                min.setSoulWolfMinions(keys, player);
                DevilRpg.LOGGER.info("[Minion] fin de la invocacion: {} lobo(s) en la lista", keys.size());
            } catch (Exception e) {
                // Si algo peta aqui, el lobo YA esta invocado pero no se sustituye a nadie: exactamente el
                // sintoma de "invoco y no sustituye". Antes esa excepcion se perdia.
                DevilRpg.LOGGER.error("[Minion] ERROR invocando lobo: la lista puede haber crecido sin sustituir", e);
            }
            }
            player.getCooldowns().addCooldown(icon.getItem(), 20);
        }
    }

    private SoulWolf summonSoulWolf(Level levelIn, Player playerIn, Random rand) {
        BlockHitResult playerBlockRayResult = TargetUtils.getPlayerBlockRayResult();
        BlockPos blockPos = playerBlockRayResult != null ? playerBlockRayResult.getBlockPos() : playerIn.blockPosition();
        if (!levelIn.isEmptyBlock(blockPos))
            blockPos = blockPos.above();

        SoulWolf sw = ModEntities.SOUL_WOLF.get().create((ServerLevel) levelIn, null, blockPos, MobSpawnType.MOB_SUMMONED, true, true);
        Objects.requireNonNull(sw).updateLevel(playerIn);
        sw.moveTo(blockPos, Mth.wrapDegrees(rand.nextFloat() * 360.0F), 0.0F);
        levelIn.addFreshEntity(sw);
        return sw;
    }
}
