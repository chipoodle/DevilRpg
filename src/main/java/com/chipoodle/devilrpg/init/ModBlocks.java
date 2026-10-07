package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.block.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Holds a list of all our {@link Block}s. Suppliers that create Blocks are
 * added to the DeferredRegister. The DeferredRegister is then added to our mod
 * event bus in our constructor.
 *
 * @author Cadiboo
 */
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, DevilRpg.MODID);
    public static final DeferredHolder<Block, SoulVineBlock> SOUL_VINE_BLOCK = BLOCKS.register("soulvine", () -> new SoulVineBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .randomTicks()
                    .noCollission()
                    .instabreak()
                    .sound(SoundType.VINE)
    ));
    public static final DeferredHolder<Block, SoulMinerVineBlock> SOUL_MINER_VINE_BLOCK = BLOCKS.register("soulminervine", () -> new SoulMinerVineBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .randomTicks()
                    .noCollission()
                    .instabreak()
                    .sound(SoundType.VINE)
    ));

    public static final DeferredHolder<Block, SoulShieldVineBlock> SOUL_SHIELD_VINE_BLOCK = BLOCKS.register("soulshieldvine", () -> new SoulShieldVineBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .randomTicks()
                    .dynamicShape().strength(1.5F)
                    .noOcclusion()
                    .sound(SoundType.VINE)
    ));

    public static final DeferredHolder<Block, SoulLichenBlock> SOUL_LICHEN_BLOCK = BLOCKS.register("soullichen", () -> new SoulLichenBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.GLOW_LICHEN).lightLevel(SoulLichenBlock.emission(7)).randomTicks()
    ));

    public static final DeferredHolder<Block, ManaBerryBushBlock> MANA_BERRY_BUSH_BLOCK = BLOCKS.register("mana_berry_bush", () -> new ManaBerryBushBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .randomTicks()
                    .noCollission()
                    .instabreak()
                    .sound(SoundType.SWEET_BERRY_BUSH)
    ));

    public static final DeferredHolder<Block, BloomingSanctuaryBlock> BLOOMING_SANCTUARY_BLOCK = BLOCKS.register("bloomingsanctuary", () -> new BloomingSanctuaryBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .randomTicks()
                    .dynamicShape().strength(1.5F)
                    .noOcclusion()
                    .sound(SoundType.VINE)
    ));

    public static final DeferredHolder<Block, UpwardSporeBlossomBlock> UPWARD_SPORE_BLOSSOM_BLOCK = BLOCKS.register("upward_spore_blossom", () -> new UpwardSporeBlossomBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.SPORE_BLOSSOM).randomTicks()
    ));

    public static final DeferredHolder<Block, LoreStoneBlock> LORE_STONE_BLOCK = BLOCKS.register("lore_stone", () -> new LoreStoneBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
    ));

    public static final DeferredHolder<Block, LairCoreBlock> LAIR_CORE_BLOCK = BLOCKS.register("lair_core", () -> new LairCoreBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(3.5F)
                    .sound(SoundType.SCULK)
                    .lightLevel(state -> 7)
                    .requiresCorrectToolForDrops()
    ));

    /**
     * <b>PORTÓN DOBLE ABATIBLE DEL MURO DE LA ALDEA</b> (6-oct-2026, lo pidió el jugador: *«que sean de 3 de ancho x 3
     * de alto y que sea una puerta doble abatible personalizada»*).
     * <p>
     * Es <b>un bloque propio</b> porque el hueco del muro es de <b>3×3</b> y una puerta de valla mide 1,5 de alto: con
     * un solo bloque de aire encima, un aldeano (1,95) no tiene hueco para la cabeza y <b>no puede cruzar</b> aunque el
     * juego le deje trazar el camino. Este portón ocupa las <b>nueve celdas</b> del hueco y las abre y cierra todas a
     * la vez, con sus dos hojas abatiendo hacia fuera.
     * <p>
     * Y hay una razón dura para que sea <b>este</b> bloque y no una puerta de madera: el juego sólo deja que un zombi
     * rompa {@code DoorBlock} en difícil, así que un portón así <b>no se rompe</b> — el asedio tiene que abrir brecha
     * en el MURO, que es lo que el jugador pidió ✓. Se le da la dureza del hierro ({@code ofFullCopy(IRON_DOOR)}) para
     * que quede claro que es una pieza de la muralla y no una valla de madera.
     */
    public static final DeferredHolder<Block, DoubleGateBlock> PORTON_DOBLE_BLOCK = BLOCKS.register("porton_doble",
            () -> new DoubleGateBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_DOOR)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));

    /**
     * Sello del sculk: caja inquebrantable que blinda el núcleo de una guarida mientras vive su cultivador.
     * Es un bloque de estructura y no se reparte en la pestaña creativa ({@code ModCreativeTabs} lo salta), pero
     * <b>sí tiene objeto</b>: {@code InitModEventSubscriber.onRegisterItems} crea un {@code BlockItem} para
     * TODOS los bloques del mod, así que necesita su modelo de objeto
     * ({@code assets/devilrpg/models/item/sculk_seal.json}) o el cliente avisa de que falta.
     */
    public static final DeferredHolder<Block, SculkSealBlock> SCULK_SEAL_BLOCK = BLOCKS.register("sculk_seal", () -> new SculkSealBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(-1.0F, 3_600_000.0F)
                    .sound(SoundType.SCULK)
                    .lightLevel(state -> 5)
                    .noLootTable()
    ));
}
