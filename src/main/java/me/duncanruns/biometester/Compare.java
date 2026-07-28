package me.duncanruns.biometester;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Compare {
    public static void main(String[] args) throws IOException {
        try (
                // manually change to actual files
                DataInputStream in1 = new DataInputStream(new BufferedInputStream(Files.newInputStream(Paths.get("C:/Users/Duncan/Desktop/temp/vanilla/biomes_nether"))));
                DataInputStream in2 = new DataInputStream(new BufferedInputStream(Files.newInputStream(Paths.get("C:/Users/Duncan/Desktop/temp/glacier/biomes_nether"))))
        ) {
            BiomeFileInfo info1 = BiomeFileInfo.fromStream(in1);
            BiomeFileInfo info2 = BiomeFileInfo.fromStream(in2);
            if (!Objects.equals(info1, info2)) {
                System.out.println("Files not compatible");
                System.out.println(info1);
                System.out.println(info2);
            }
            System.out.println(info1);
            int total = info1.xSize * info1.zSize * info1.layers;
            {
                byte a = info1.firstBiome;
                byte b = info2.firstBiome;
                //noinspection StringEquality
                if (info1.biomeMap.get(a) != info2.biomeMap.get(b)) {
                    System.out.printf("Biome mismatch at %d %d (layer %d)\n", 0, 0, 0);
                }
            }
            int issues = 0;
            for (int i = 1; i < total; i++) {
                byte a = in1.readByte();
                byte b = in2.readByte();
                //noinspection StringEquality
                if (info1.biomeMap.get(a) != info2.biomeMap.get(b)) {
                    int z = i % info1.zSize;
                    int x = ((i - z) / info1.zSize) % info1.xSize;
                    int layer = Math.floorDiv(i, info1.xSize * info1.zSize * info1.layers);
                    System.out.printf("Biome mismatch at %d %d (layer %d)\n", x, z, layer);
                    issues++;
                }
                if (i % info1.zSize == 0) {
//                    System.out.println((i / info1.zSize) + "/" + (info1.xSize * info1.layers));
                }
            }
            if (issues == 0) {
                System.out.println("No biome mismatches!");
            } else {
                System.out.println(issues + " biomes mismatched!");
            }
        }
    }

    private static class BiomeFileInfo {
        private static final Map<String, String> stringCache = new HashMap<>();
        int xSize, zSize, layers;
        byte firstBiome;
        final Map<Byte, String> biomeMap = new HashMap<>();

        static BiomeFileInfo fromStream(DataInputStream in) throws IOException {
            BiomeFileInfo out = new BiomeFileInfo();
            out.xSize = in.readInt();
            out.zSize = in.readInt();
            out.layers = in.readInt();

            byte[] buf = new byte[1000];
            while (true) {
                byte b = in.readByte();
                if (out.biomeMap.containsKey(b)) {
                    out.firstBiome = b;
                    return out;
                }
                int length = in.readInt();
                if (length > 1000) throw new RuntimeException("biome name longer than 1k???");
                int read = in.read(buf, 0, length);
                String value = new String(buf, 0, read, StandardCharsets.UTF_8);
                String dedupeString = stringCache.computeIfAbsent(value, s -> s);
                out.biomeMap.put(b, dedupeString);
            }
        }

        private boolean biomesEqual(BiomeFileInfo other) {
            if (other == null) return false;
            Collection<String> values = this.biomeMap.values();
            Collection<String> valuesOther = other.biomeMap.values();
            if (values.size() != valuesOther.size()) return false;
            return values.containsAll(valuesOther);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            BiomeFileInfo that = (BiomeFileInfo) o;
            return xSize == that.xSize && zSize == that.zSize && layers == that.layers && biomesEqual(that);
        }

        @Override
        public int hashCode() {
            int result = xSize;
            result = 31 * result + zSize;
            result = 31 * result + layers;
            return result;
        }

        @Override
        public String toString() {
            return "BiomeFileInfo{" +
                    "layers=" + layers +
                    ", zSize=" + zSize +
                    ", xSize=" + xSize +
                    ", biomes=" + biomeMap.values() +
                    '}';
        }
    }
}
