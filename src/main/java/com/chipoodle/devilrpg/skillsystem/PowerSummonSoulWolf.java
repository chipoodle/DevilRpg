package com.chipoodle.devilrpg.skillsystem;

import static com.chipoodle.devilrpg.DevilRpg.LOGGER;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

public class PowerSummonSoulWolf implements IPowerContainer {

    //public static ConcurrentHashMap<PlayerEntity, ConcurrentLinkedQueue<EntitySoulWolf>> playerWolves = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<PlayerEntity, Integer> numberOfSummonsHash = new ConcurrentHashMap<>();
    private static PowerSummonSoulWolf instance = null;

    private PowerSummonSoulWolf() {

    }

    public static PowerSummonSoulWolf getInstance() {
        if (instance == null) {
            instance = new PowerSummonSoulWolf();
        }
        return instance;
    }

    @Override
    public void execute(World worldIn, PlayerEntity playerIn) {
        if (!worldIn.isRemote) {
            if (!numberOfSummonsHash.containsKey(playerIn)) {
                numberOfSummonsHash.put(playerIn, 3);
                //ConcurrentLinkedQueue<EntitySoulWolf> oldWolves = playerWolves.put(playerIn, new ConcurrentLinkedQueue<>());
                //if (oldWolves != null) {
                    //oldWolves.stream().forEach(x -> x.setDead());
               // }
            }
            cleanWolvesQueue(playerIn);
            summonWolf(worldIn, playerIn);
        }
    }

    private void summonWolf(World worldIn, PlayerEntity playerIn) {
        LOGGER.info("------------------>Summon soulWolf");
        //ConcurrentLinkedQueue<EntitySoulBear> bearClear = PowerSummonSoulBear.playerBear.get(playerIn);
       /* if (bearClear != null) {
            bearClear.forEach(x -> x.setDead());
            
        }*/

        /*Random rand = new Random();
        EntitySoulWolf sw = new EntitySoulWolf(worldIn, playerIn);
        playerWolves.get(playerIn).add(sw);
        Vec3d playerLookVector = playerIn.getLookVec();
        double spawnX = playerIn.posX + Constantes.WOLF_SPAWN_DISTANCE * playerLookVector.x;
        double spawnZ = playerIn.posZ + Constantes.WOLF_SPAWN_DISTANCE * playerLookVector.z;
        double spawnY = Helper.getHeightValue(worldIn, spawnX, spawnZ);
        sw.setLocationAndAngles(spawnX, spawnY, spawnZ, MathHelper.wrapDegrees(rand.nextFloat() * 360.0F), 0.0F);
        worldIn.spawnEntity(sw);
        System.out.println("Summoned soulWolf. is Remote? " + worldIn.isRemote);*/
    }

    private void cleanWolvesQueue(PlayerEntity player) {
        /*if (playerWolves != null) {
            ConcurrentLinkedQueue<EntitySoulWolf> remove = playerWolves.get(player);
            if (remove != null) {
                remove.removeIf(x -> x.isDead == true);
                if (playerWolves.get(player).size() >= numberOfSummonsHash.get(player)) {
                    playerWolves.get(player).poll().setDead();
                }
            }
        }*/
    }
}
