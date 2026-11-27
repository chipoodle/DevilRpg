package com.chipoodle.devilrpg.entity;

import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.Set;

/** Pruebas puras del selector de orillas compartido (sin mundo ni servidor). */
public final class WaterEscapeControllerTest {
    private static int assertions;

    public static void main(String[] args) {
        BlockPos origin = new BlockPos(0, 64, 0);
        BlockPos nearGround = new BlockPos(0, 63, 1);
        BlockPos farGround = new BlockPos(1, 63, 0);
        Set<BlockPos> attempted = new HashSet<>();

        BlockPos selected = WaterEscapeController.findNearestShore(origin, 2, attempted,
                p -> p.equals(nearGround) || p.equals(farGround),
                feet -> feet.equals(farGround.above()));
        check("rechaza el rellano más cercano si no valida", farGround.above().equals(selected));

        attempted.clear();
        attempted.add(nearGround.above());
        selected = WaterEscapeController.findNearestShore(origin, 2, attempted,
                p -> p.equals(nearGround) || p.equals(farGround), feet -> true);
        check("no repite orillas intentadas", farGround.above().equals(selected));

        attempted.clear();
        BlockPos highGround = new BlockPos(0, 65, 1);
        BlockPos highLanding = highGround.above();
        selected = WaterEscapeController.findNearestShore(origin, 2,
                WaterEscapeController.GUARD_MIN_GROUND_DY, WaterEscapeController.GUARD_MAX_GROUND_DY,
                attempted, p -> p.equals(highGround), feet -> feet.equals(highLanding));
        check("guardia encuentra orilla elevada dentro del rango vertical", highLanding.equals(selected));
        selected = WaterEscapeController.findNearestShore(origin, 2, attempted,
                p -> p.equals(highGround), feet -> feet.equals(highLanding));
        check("zombie conserva rango vertical histórico", selected == null);

        attempted.clear();
        selected = WaterEscapeController.findNearestShore(origin, 0, attempted,
                p -> p.equals(nearGround), feet -> true);
        check("respeta radio máximo", selected == null);

        attempted.clear();
        attempted.add(nearGround.above());
        attempted.add(farGround.above());
        selected = WaterEscapeController.findNearestShore(origin, 2, attempted,
                p -> p.equals(nearGround) || p.equals(farGround), feet -> true);
        check("devuelve null cuando no hay orillas nuevas", selected == null);

        check("contador registra el tick transcurrido desde la selección",
                WaterEscapeController.elapsedTicks(100L, 101L) == 1L);
        check("contador nunca informa tiempo negativo",
                WaterEscapeController.elapsedTicks(101L, 100L) == 0L);

        System.out.println("WATER_ESCAPE_CONTROLLER_TEST: OK (" + assertions + " assertions)");
    }

    private static void check(String name, boolean condition) {
        assertions++;
        if (!condition) {
            throw new AssertionError(name);
        }
    }
}
