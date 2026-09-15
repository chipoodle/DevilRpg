package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation;
import com.chipoodle.devilrpg.entity.SoulBear;
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

public class SkillSummonSoulBear extends AbstractSkillExecutor {

    private static final int NUMBER_OF_SUMMONS = 1;

    public SkillSummonSoulBear(PlayerSkillCapabilityImplementation parentCapability) {
        super(parentCapability);
    }

    @Override
    public SkillEnum getSkillEnum() {
        return SkillEnum.SUMMON_SOUL_BEAR;
    }

    @Override
    public boolean arePreconditionsMetBeforeConsumingResource(Player player) {
        return !player.getCooldowns().isOnCooldown(icon.getItem());
    }

    @Override
    public void execute(Level level, Player player, HashMap<String, String> parameters) {
        if (!player.getCooldowns().isOnCooldown(icon.getItem())) {
            if (!level.isClientSide) {
                Random rand = new Random();
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 0.5F, 0.4F / (rand.nextFloat() * 0.4F + 0.8F));
                PlayerMinionCapabilityInterface min = player.getData(PlayerMinionCapability.INSTANCE);
                min.removeAllSoulWolf(player);
                ConcurrentLinkedQueue<UUID> keys = min.getSoulBearMinions();
                if (keys == null) keys = new ConcurrentLinkedQueue<>(); // getSoulBearMinions puede devolver null si falla la deserializacion del NBT

                // Limitar el numero de osos invocados a NUMBER_OF_SUMMONS. OJO: se MIRA el más viejo sin
                // quitarlo de la lista hasta saber que existe de verdad; si se quitara a ciegas (poll/remove),
                // un oso en un chunk descargado (o guardado al desconectarse) se olvidaría pero seguiría vivo,
                // y al cargarse de nuevo tendrías uno de más (duplicado).
                while (keys.size() >= NUMBER_OF_SUMMONS) {
                    UUID key = keys.peek();
                    SoulBear e = key == null ? null : (SoulBear) min.getTamableByUUID(key, player.level());
                    if (e == null) {
                        // No se puede resolver (chunk sin cargar, o murió sin avisar). Antes se cortaba y el cupo se
                        // quedaba lleno: podías acumular osos. Se olvida esa entrada y se sigue; si el oso sigue vivo
                        // y vuelve a cargarse, la limpieza de huérfanos lo quita.
                        if (!keys.remove(key)) {
                            break;
                        }
                        DevilRpg.LOGGER.warn("[Minion] el oso mas viejo ({}) no aparece: lo olvido de la lista", key);
                        continue;
                    }
                    min.removeSoulBear(player, e); // ya quita el UUID de la lista
                }
                keys.offer(summonSoulBear(level, player, rand).getUUID());
                min.setSoulBearMinions(keys, player);
            }
            player.getCooldowns().addCooldown(icon.getItem(), 20);
        }
    }

    private SoulBear summonSoulBear(Level levelIn, Player playerIn, Random rand) {
        BlockHitResult playerBlockRayResult = TargetUtils.getPlayerBlockRayResult();
        BlockPos blockPos = playerBlockRayResult != null ? playerBlockRayResult.getBlockPos() : playerIn.blockPosition();
        if (!levelIn.isEmptyBlock(blockPos))
            blockPos = blockPos.above();

        SoulBear sw = ModEntities.SOUL_BEAR.get().create((ServerLevel) levelIn, null, blockPos, MobSpawnType.MOB_SUMMONED, true, true);
        Objects.requireNonNull(sw).updateLevel(playerIn);
        sw.moveTo(blockPos, Mth.wrapDegrees(rand.nextFloat() * 360.0F), 0.0F);
        levelIn.addFreshEntity(sw);
        return sw;
    }
}
