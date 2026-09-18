import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Herramienta de diagnostico/reparacion del NBT del jugador (fuera del juego, con las clases reales de MC).
 * Uso:
 *   NbtTool <archivo.dat>                         -> volcado de las listas de minions
 *   NbtTool <origen> --inject-into <destino>       -> copia Stored_Minions de origen a destino (con copia .bak)
 */
public class NbtTool {

    static List<String> uuidStrings(byte[] bytes) throws Exception {
        List<String> out = new ArrayList<>();
        if (bytes == null || bytes.length == 0) return out;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object o = in.readObject();
            if (o instanceof Collection<?> c) {
                for (Object e : c) out.add(String.valueOf(e));
            } else {
                out.add("(no es coleccion: " + o + ")");
            }
        } catch (Exception e) {
            out.add("ERROR " + e);
        }
        return out;
    }

    static CompoundTag minionTag(CompoundTag root) {
        return root.getCompound("neoforge:attachments").getCompound("devilrpg:player_minion");
    }

    /** Busca recursivamente cualquier compuesto que contenga "Wolf_Minions" (sirve para level.dat y para playerdata). */
    static CompoundTag findMinionCap(CompoundTag tag) {
        if (tag.contains("Wolf_Minions")) {
            return tag;
        }
        for (String key : tag.getAllKeys()) {
            if (tag.get(key) instanceof CompoundTag ct) {
                CompoundTag found = findMinionCap(ct);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        Path src = Path.of(args[0]);
        CompoundTag root = NbtIo.readCompressed(src, NbtAccounter.unlimitedHeap());

        if (args.length >= 2 && args[1].equals("--find")) {
            CompoundTag found = findMinionCap(root);
            System.out.println("=== " + src);
            if (found == null) {
                System.out.println("   NO tiene datos de minions");
            } else {
                for (String k : new String[]{"Wolf_Minions", "Wisp_Minions", "Bear_Minions"}) {
                    System.out.println("   " + k + " -> " + uuidStrings(found.getByteArray(k)));
                }
                System.out.println("   Stored_Minions -> " + found.getList("Stored_Minions", Tag.TAG_COMPOUND).size());
                for (Tag t : found.getList("Stored_Minions", Tag.TAG_COMPOUND)) {
                    CompoundTag e = (CompoundTag) t;
                    System.out.println("      copia: " + e.getUUID("Id") + "  " + e.getCompound("Data").getString("id"));
                }
            }
            return;
        }

        if (args.length >= 5 && args[1].equals("--restore-minions-from")) {
            Path oldPath = Path.of(args[2]);
            Path dstPath = Path.of(args[4]);
            String wispType = args[3];
            CompoundTag oldRoot = NbtIo.readCompressed(oldPath, NbtAccounter.unlimitedHeap());
            CompoundTag oldAtt = minionTag(oldRoot);
            ListTag stored = oldAtt.getList("Stored_Minions", Tag.TAG_COMPOUND).copy();

            List<String> wolves = uuidStrings(oldAtt.getByteArray("Wolf_Minions"));
            List<String> bears = uuidStrings(oldAtt.getByteArray("Bear_Minions"));
            List<String> wisps = uuidStrings(oldAtt.getByteArray("Wisp_Minions"));

            for (int i = 0; i < stored.size(); i++) {
                CompoundTag entry = stored.getCompound(i);
                String id = entry.getUUID("Id").toString();
                CompoundTag data = entry.getCompound("Data");
                String type;
                if (wolves.contains(id)) {
                    type = "devilrpg:soul_wolf";
                } else if (bears.contains(id)) {
                    type = "devilrpg:soul_bear";
                } else if (wisps.contains(id)) {
                    type = wispType;
                } else {
                    type = "(no esta en ninguna lista)";
                }
                data.putString("id", type);
                entry.put("Data", data);
                System.out.println("  entrada [" + i + "] " + id + " -> " + type);
            }

            CompoundTag dstRoot = NbtIo.readCompressed(dstPath, NbtAccounter.unlimitedHeap());
            CompoundTag dstAtt = findMinionCap(dstRoot);
            if (dstAtt == null) {
                System.out.println("ERROR: " + dstPath + " no tiene datos de minions");
                return;
            }
            dstAtt.put("Stored_Minions", stored);
            Files.copy(dstPath, Path.of(dstPath + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            NbtIo.writeCompressed(dstRoot, dstPath);
            System.out.println("OK: " + stored.size() + " entradas escritas en " + dstPath + " (respaldo en " + dstPath + ".bak)");
            return;
        }

        if (args.length >= 3 && args[1].equals("--forget")) {
            java.util.Set<UUID> drop = new java.util.HashSet<>();
            for (int i = 2; i < args.length; i++) drop.add(UUID.fromString(args[i]));
            CompoundTag att = findMinionCap(root);
            if (att == null) {
                System.out.println("ERROR: " + src + " no tiene datos de minions");
                return;
            }
            int total = 0;
            for (String k : new String[]{"Wolf_Minions", "Bear_Minions", "Wisp_Minions"}) {
                List<UUID> uuids = new ArrayList<>();
                for (String s : uuidStrings(att.getByteArray(k))) uuids.add(UUID.fromString(s));
                int before = uuids.size();
                uuids.removeAll(drop);
                ConcurrentLinkedQueue<UUID> q = new ConcurrentLinkedQueue<>(uuids);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
                    oos.writeObject(q);
                }
                att.putByteArray(k, bos.toByteArray());
                System.out.println("  " + k + ": " + before + " -> " + uuids.size());
                total += before - uuids.size();
            }
            Files.copy(src, Path.of(src + ".cleanbak"), StandardCopyOption.REPLACE_EXISTING);
            NbtIo.writeCompressed(root, src);
            System.out.println("Quitados " + total + " UUIDs. Respaldo: " + src + ".cleanbak");
            return;
        }

        if (args.length >= 3 && args[1].equals("--inject-into")) {
            Path dst = Path.of(args[2]);
            CompoundTag dstRoot = NbtIo.readCompressed(dst, NbtAccounter.unlimitedHeap());
            ListTag stored = minionTag(root).getList("Stored_Minions", Tag.TAG_COMPOUND);
            minionTag(dstRoot).put("Stored_Minions", stored.copy());
            Files.copy(dst, Path.of(dst + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            NbtIo.writeCompressed(dstRoot, dst);
            System.out.println("Inyectadas " + stored.size() + " entradas en " + dst + " (respaldo .bak)");
            return;
        }

        CompoundTag att = minionTag(root);
        System.out.println("claves de la capability de minions: " + att.getAllKeys());
        for (String k : new String[]{"Wolf_Minions", "Bear_Minions", "Wisp_Minions"}) {
            System.out.println(k + " -> " + uuidStrings(att.getByteArray(k)));
        }
        // Skill points (para saber que clase de wisp era el guardado)
        try {
            CompoundTag skillCap = root.getCompound("neoforge:attachments").getCompound("devilrpg:player_skill");
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(skillCap.getByteArray("Skills")))) {
                Object o = in.readObject();
                if (o instanceof Map<?, ?> m) {
                    List<String> lines = new ArrayList<>();
                    for (Map.Entry<?, ?> en : m.entrySet()) {
                        String k = String.valueOf(en.getKey());
                        if (k.contains("WISP") || k.contains("WOLF") || k.contains("BEAR")) {
                            lines.add(k + "=" + en.getValue());
                        }
                    }
                    Collections.sort(lines);
                    System.out.println("skills de minions: " + lines);
                }
            }
        } catch (Throwable t) {
            System.out.println("skills: no se pudo leer (" + t + ")");
        }

        ListTag stored = att.getList("Stored_Minions", Tag.TAG_COMPOUND);
        System.out.println("Stored_Minions: " + stored.size());
        for (int i = 0; i < stored.size(); i++) {
            CompoundTag e = stored.getCompound(i);
            CompoundTag d = e.getCompound("Data");
            String id = d.getString("id");
            System.out.printf("  [%d] Id=%s dim=%s pos=%s DataId=%s uuidEnData=%s claves=%d%n",
                    i, e.getUUID("Id"), e.getString("Dimension"), BlockPos.of(e.getLong("Pos")),
                    id.isEmpty() ? "(VACIO)" : id, d.contains("UUID"), d.getAllKeys().size());
            if (args.length >= 3 && args[1].equals("--snbt")) {
                int idx = Integer.parseInt(args[2]);
                CompoundTag dsnbt = stored.getCompound(idx).getCompound("Data");
                System.out.println("      SNBT: " + dsnbt);
            }
            if (args.length >= 2 && args[1].equals("--keys")) {
                List<String> keys = new ArrayList<>(d.getAllKeys());
                Collections.sort(keys);
                System.out.println("      claves: " + keys);
                List<String> attrs = new ArrayList<>();
                for (Tag t : d.getList("attributes", Tag.TAG_COMPOUND)) {
                    CompoundTag a = (CompoundTag) t;
                    attrs.add(a.getString("id") + "=" + a.getDouble("base"));
                }
                System.out.println("      atributos: " + attrs);
            }
        }
    }
}
