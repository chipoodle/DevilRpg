package com.chipoodle.devilrpg.config;

import java.util.List;

import net.minecraft.item.DyeColor;

/**
 * This holds the baked (runtime) values for our config. These values should
 * never be from changed outside this package. This can be split into multiple
 * classes (Server, Client, Player, Common) but has been kept in one class for
 * simplicity
 *
 * @author Cadiboo
 */
public final class DevilRpgConfig {

    // Client
    public static boolean clientBoolean;
    public static List<String> clientStringList;
    public static DyeColor clientDyeColorEnum;

    public static boolean modelTranslucency;
    public static float modelScale;

    // Server
    public static boolean serverBoolean;
    public static List<String> serverStringList;
    public static DyeColor serverEnumDyeColor;

    public static int electricFurnaceEnergySmeltCostPerTick = 100;
    public static int heatCollectorTransferAmountPerTick = 100;
    
    public static final int WOLF_SPAWN_DISTANCE = 3;
	public static final double WISP_SPAWN_DISTANCE = 3;
	public static final double BEAR_SPAWN_DISTANCE = 3;	
	
	public static final int COMMAND_PERMISSION_LEVEL = 2;

}
