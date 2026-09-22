package com.chipoodle.devilrpg.block;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.survival.ObjectiveTargets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Roca de los clérigos: la piedra central del círculo ritual de invocación del druida. Al darle clic
 * muestra la historia de su invocación y le da la dirección a la primera aldea (el objetivo ya apunta
 * hacia allí desde el ancla).
 * <p>
 * Además, la <b>primera</b> lectura le regala al jugador la experiencia justa para <b>subir un nivel</b>
 * (una sola vez por jugador: si no, la piedra sería una granja de XP infinita).
 */
public class LoreStoneBlock extends Block {

    private static final Component LORE = Component.literal(
            "La piedra está grabada con runas antiguas. 'Fuiste invocado desde otro mundo por los clérigos"
                    + " para combatir la corrupción que pudre esta tierra. La runa ardiente marca el camino"
                    + " hacia la primera aldea.'");

    public LoreStoneBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        showLore(level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        showLore(level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private void showLore(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            player.displayClientMessage(LORE, false);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.0F);
            grantFirstReadLevelUp(player);
            revelarLaAldea(player);
            entregarElDiario(player);
        }
    }

    /**
     * Le entrega el <b>Diario del Invocado</b> si no lo tiene ya: es donde va apuntando las aldeas que descubre, con
     * sus coordenadas y su estado (lo pidió el jugador: *"un libro o algo que vaya guardando las aldeas
     * descubiertas… junto con su estatus y sus coordenadas"*). Si lo perdió, la piedra se lo vuelve a dar (y si no
     * le cabe en el inventario, lo suelta a sus pies: nunca se pierde la herramienta).
     */
    private void entregarElDiario(Player player) {
        net.minecraft.world.item.ItemStack diario =
                new net.minecraft.world.item.ItemStack(com.chipoodle.devilrpg.init.ModItems.DIARIO_DEL_INVOCADO.get());
        if (player.getInventory().contains(diario)) {
            return;
        }
        if (!player.getInventory().add(diario)) {
            player.drop(diario, false);
        }
        player.displayClientMessage(Component.literal(
                "La piedra te entrega un cuaderno ajado: el Diario del Invocado. Úsalo para ver las aldeas que"
                        + " descubras, con sus coordenadas y su suerte."), false);
    }

    /**
     * <b>La piedra revela la dirección de la aldea del objetivo actual</b>: es el "inicio de la misión" —*"la runa
     * ardiente marca el camino hacia la primera aldea"*— y hasta que no se lee (o el clérigo habla), la barra de aldea
     * no enseña ni la dirección (lo pidió el jugador: *"la siguiente aldea no va a aparecer su dirección hasta que
     * uno obtenga algo del mundo o alguien de la primera aldea o piedra de invocación al inicio de esa misión"*).
     * <p>
     * El camino <b>normal</b> para las siguientes es el <b>clérigo</b> de la aldea que se salve (te lo dice en el
     * sitio, gratis); la piedra es el <b>seguro contra perderse</b>: hay que volver al círculo ritual, que está a
     * cientos de bloques, así que no es un atajo cómodo — pero nunca deja al jugador sin dirección (una aldea que cae
     * no revela nada y sin esta salida se quedaría sin saber hacia dónde ir).
     */
    private void revelarLaAldea(Player player) {
        PlayerAuxiliaryCapabilityInterface aux =
                IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
        if (aux == null) {
            return;
        }
        int index = aux.getObjectiveIndex();
        Vec3 ancla = aux.getAnchorPoint();
        if (ancla == null) {
            ancla = aux.getSpawnPoint();
        }
        if (ancla == null) {
            return;
        }
        BlockPos objetivo = ObjectiveTargets.targetOf(ancla, index);
        String hacia = ObjectiveTargets.direccionHacia(player.blockPosition(), objetivo);
        boolean yaLaSabia = aux.isAldeaRevelada(index) || aux.isAldeaVisitada(index);
        if (yaLaSabia) {
            player.displayClientMessage(Component.literal(
                    "La runa sigue encendida: la aldea queda hacia el " + hacia + "."), false);
            return;
        }
        aux.revelarAldea(index, player);
        player.displayClientMessage(Component.literal(index == 0
                ? "La runa se enciende en tu cabeza: la primera aldea queda hacia el " + hacia + "."
                : "La runa vuelve a encenderse: la aldea que buscas queda hacia el " + hacia + "."), false);
        DevilRpg.LOGGER.info("[LoreStone] {}: revelada la aldea {} (hacia el {})",
                player.getName().getString(), index, hacia);
    }

    /**
     * Regala la experiencia justa para subir un nivel, <b>solo la primera vez</b> que el jugador lee la
     * piedra (el estado se guarda en la capability auxiliar del jugador, así que sobrevive al reinicio).
     * <p>
     * {@code getXpNeededForNextLevel()} es lo que pide la barra del nivel actual, así que al darla el jugador
     * sube exactamente un nivel y se queda con el progreso que ya tuviera de sobra. Al subir de nivel, el mod
     * concede además el punto de habilidad que corresponde (evento {@code PlayerXpEvent.LevelChange}).
     */
    private void grantFirstReadLevelUp(Player player) {
        PlayerAuxiliaryCapabilityInterface aux =
                IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
        if (aux == null || aux.isLoreStoneRead()) {
            return;
        }
        aux.setLoreStoneRead(true, player);

        int xp = Math.max(1, player.getXpNeededForNextLevel());
        int levelBefore = player.experienceLevel;
        player.giveExperiencePoints(xp);
        player.displayClientMessage(Component.literal(
                "La piedra te bendice: +" + xp + " de experiencia (subes de nivel)."), false);
        DevilRpg.LOGGER.info("[LoreStone] Primera lectura de {}: +{} XP (nivel {} -> {})",
                player.getName().getString(), xp, levelBefore, player.experienceLevel);
    }
}
