package com.chipoodle.devilrpg.block;

import net.minecraft.world.level.block.Block;

/**
 * <b>Sello del sculk</b>: bloque <b>inquebrantable</b> (igual que la piedra base) con el que una guarida
 * <b>blinda su núcleo</b> mientras vive su guardián, el {@code SculkCultivatorEntity}.
 * <p>
 * El jugador no puede picarlo ni volarlo: la única forma de abrir el santuario es <b>matar al cultivador</b>.
 * Cuando eso ocurre, {@code LairManager} retira la caja de sellos y el núcleo queda expuesto y rompible.
 * Es a propósito un bloque de estructura: no tiene objeto, no sale en el inventario creativo y no dropea
 * nada.
 */
public class SculkSealBlock extends Block {

    public SculkSealBlock(Properties properties) {
        super(properties);
    }
}
