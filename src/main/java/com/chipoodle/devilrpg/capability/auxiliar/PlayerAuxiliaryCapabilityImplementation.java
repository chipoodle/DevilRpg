package com.chipoodle.devilrpg.capability.auxiliar;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.neoforged.neoforge.network.PacketDistributor;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.payload.PlayerAuxiliarPayload;
import com.chipoodle.devilrpg.util.TargetUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class PlayerAuxiliaryCapabilityImplementation implements PlayerAuxiliaryCapabilityInterface {

    protected boolean werewolfAttack = false;
    protected boolean werewolfTransformation = false;
    protected boolean swingingMainHand = false;

    protected Vec3 spawnPoint = null;
    protected Vec3 anchorPoint = null;
    protected int objectiveIndex = 0;
    /** Si ya leyó la piedra de lore del círculo ritual (la primera lectura da la XP de un nivel). */
    protected boolean loreStoneRead = false;
    /** Aldeas en las que el jugador ya ha ENTRADO (índice de objetivo): su "descubrimiento" (ver la interfaz). */
    protected final java.util.Set<Integer> aldeasVisitadas = new java.util.HashSet<>();
    /** Aldeas cuya DIRECCIÓN ya le han revelado (la piedra, o el clérigo al vencer el asedio). */
    protected final java.util.Set<Integer> aldeasReveladas = new java.util.HashSet<>();

    @Override
    public boolean isWerewolfAttack() {
        return werewolfAttack;
    }

    @Override
    public void setWerewolfAttack(boolean active, Player player) {
        werewolfAttack = active;
        //DevilRpg.LOGGER.info("------Client sending to server attaking werewolf: {} isClientSide {}, main hand? {}",active, player.level().isClientSide,swingingMainHand);
        if (!player.level().isClientSide) {
            //player.sendMessage(new StringTextComponent("Sending to client attaking werewolf: " + active),player.getUUID());
            sendAuxiliaryChangesToClient((ServerPlayer) player);
        } else {
            //player.sendMessage(new StringTextComponent("Sending to server attaking werewolf: " + active),player.getUUID());
            sendAuxiliaryChangesToServer();

        }
    }

    @Override
    public boolean isWerewolfTransformation() {
        return werewolfTransformation;
    }

    @Override
    public void setWerewolfTransformation(boolean active, Player player) {
        werewolfTransformation = active;
        if (!player.level().isClientSide) sendAuxiliaryChangesToClient((ServerPlayer) player);
        else sendAuxiliaryChangesToServer();
    }

    @Override
    public boolean isSwingingMainHand() {
        return swingingMainHand;
    }

    @Override
    public void setSwingingMainHand(boolean active, Player player) {
        swingingMainHand = active;
        if (!player.level().isClientSide) sendAuxiliaryChangesToClient((ServerPlayer) player);
        else sendAuxiliaryChangesToServer();
    }

    @Override
    public InteractionHand swingHands(Player player) {
        InteractionHand interactionHand = isSwingingMainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        player.swing(interactionHand);
        setSwingingMainHand(!isSwingingMainHand(), player);
        return interactionHand;
    }

    @Override
    public Vec3 getSpawnPoint() {
        return spawnPoint;
    }

    @Override
    public void setSpawnPoint(Vec3 spawnPoint, Player player) {
        this.spawnPoint = spawnPoint;
        if (!player.level().isClientSide) sendAuxiliaryChangesToClient((ServerPlayer) player);
        else sendAuxiliaryChangesToServer();
    }

    @Override
    public int getObjectiveIndex() {
        return objectiveIndex;
    }

    @Override
    public void setObjectiveIndex(int objectiveIndex, Player player) {
        this.objectiveIndex = objectiveIndex;
        if (!player.level().isClientSide) sendAuxiliaryChangesToClient((ServerPlayer) player);
        else sendAuxiliaryChangesToServer();
    }

    @Override
    public Vec3 getAnchorPoint() {
        return anchorPoint;
    }

    @Override
    public boolean isLoreStoneRead() {
        return loreStoneRead;
    }

    @Override
    public void setLoreStoneRead(boolean read, Player player) {
        this.loreStoneRead = read;
        if (!player.level().isClientSide) sendAuxiliaryChangesToClient((ServerPlayer) player);
        else sendAuxiliaryChangesToServer();
    }

    @Override
    public boolean isAldeaVisitada(int objectiveIndex) {
        return aldeasVisitadas.contains(objectiveIndex);
    }

    @Override
    public void visitarAldea(int objectiveIndex, Player player) {
        if (!aldeasVisitadas.add(objectiveIndex)) {
            return; // ya estaba: idempotente (esto se llama en cada latido mientras el jugador está en la aldea)
        }
        if (!player.level().isClientSide) sendAuxiliaryChangesToClient((ServerPlayer) player);
        else sendAuxiliaryChangesToServer();
    }

    @Override
    public boolean isAldeaRevelada(int objectiveIndex) {
        return aldeasReveladas.contains(objectiveIndex);
    }

    @Override
    public void revelarAldea(int objectiveIndex, Player player) {
        if (!aldeasReveladas.add(objectiveIndex)) {
            return; // ya se la habían revelado: idempotente
        }
        if (!player.level().isClientSide) sendAuxiliaryChangesToClient((ServerPlayer) player);
        else sendAuxiliaryChangesToServer();
    }

    @Override
    public java.util.Set<Integer> getAldeasVisitadas() {
        return java.util.Collections.unmodifiableSet(aldeasVisitadas);
    }

    @Override
    public java.util.Set<Integer> getAldeasReveladas() {
        return java.util.Collections.unmodifiableSet(aldeasReveladas);
    }

    @Override
    public void setAnchorPoint(Vec3 anchorPoint, Player player) {
        this.anchorPoint = anchorPoint;
        if (!player.level().isClientSide) sendAuxiliaryChangesToClient((ServerPlayer) player);
        else sendAuxiliaryChangesToServer();
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag nbt = new CompoundTag();
        nbt.putBoolean("werewolfAttack", werewolfAttack);
        nbt.putBoolean("werewolfTransformation", werewolfTransformation);
        //no es necesario persistir que mano está moviendose
        //nbt.putBoolean("swingingMainHand", swingingMainHand);
        if (spawnPoint != null) {
            nbt.putString("spawnPoint", spawnPoint.toString());
        }
        if (anchorPoint != null) {
            nbt.putString("anchorPoint", anchorPoint.toString());
        }
        nbt.putInt("objectiveIndex", objectiveIndex);
        nbt.putBoolean("loreStoneRead", loreStoneRead);
        nbt.putIntArray("aldeasVisitadas", aIntArray(aldeasVisitadas));
        nbt.putIntArray("aldeasReveladas", aIntArray(aldeasReveladas));
        return nbt;
    }

    /** Los índices de un conjunto, como arreglo de enteros para el NBT (y para el paquete de sincronización). */
    private static int[] aIntArray(java.util.Set<Integer> conjunto) {
        int[] out = new int[conjunto.size()];
        int i = 0;
        for (int v : conjunto) {
            out[i++] = v;
        }
        return out;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        werewolfAttack = nbt.getBoolean("werewolfAttack");
        werewolfTransformation = nbt.getBoolean("werewolfTransformation");
        //no es necesario leer que mano está moviendose
        //swingingMainHand = nbt.getBoolean("swingingMainHand");
        // Verificar si existe el campo "spawnPoint" antes de deserializar
        if (nbt.contains("spawnPoint")) {
            spawnPoint = TargetUtils.stringToVec3(nbt.getString("spawnPoint"));
        }
        if (nbt.contains("anchorPoint")) {
            anchorPoint = TargetUtils.stringToVec3(nbt.getString("anchorPoint"));
        }
        if (nbt.contains("objectiveIndex")) {
            objectiveIndex = nbt.getInt("objectiveIndex");
        }
        // Partidas viejas (sin el campo): false = todavía no la ha leído, así que la primera lectura sí da XP.
        loreStoneRead = nbt.getBoolean("loreStoneRead");
        // Las aldeas descubiertas y reveladas (partidas viejas: sin los campos -> vacío = no conoce ninguna, que es
        // justo la verdad: la barra de aldea no enseñará dirección hasta que la piedra o un clérigo se la den).
        aldeasVisitadas.clear();
        for (int v : nbt.getIntArray("aldeasVisitadas")) {
            aldeasVisitadas.add(v);
        }
        aldeasReveladas.clear();
        for (int v : nbt.getIntArray("aldeasReveladas")) {
            aldeasReveladas.add(v);
        }
    }

    private void sendAuxiliaryChangesToServer() {
        PacketDistributor.sendToServer(new PlayerAuxiliarPayload(serializeNBT(RegistryAccess.EMPTY).copy()));
    }

    private void sendAuxiliaryChangesToClient(ServerPlayer pe) {
        PacketDistributor.sendToPlayer(pe, new PlayerAuxiliarPayload(serializeNBT(RegistryAccess.EMPTY).copy()));
    }
}
