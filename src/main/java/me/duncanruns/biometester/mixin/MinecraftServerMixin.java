package me.duncanruns.biometester.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTask;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.util.snooper.SnooperListener;
import net.minecraft.util.thread.ReentrantThreadExecutor;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin extends ReentrantThreadExecutor<ServerTask> implements SnooperListener, CommandOutput, AutoCloseable {
    @Shadow
    @Nullable
    public abstract ServerWorld getWorld(RegistryKey<World> key);

    @Shadow
    protected abstract void shutdown();

    @Unique
    private boolean done = false;

    public MinecraftServerMixin() {
        super(null);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) throws IOException {
        if (done) {
            shutdown();
            return;
        }
        writeBiomes(false);
        writeBiomes(true);
        done = true;
    }

    @Unique
    @SuppressWarnings("UnnecessaryLocalVariable")
    private void writeBiomes(boolean nether) throws IOException {
        String fileName = nether ? "biomes_nether" : "biomes_overworld";
        if (Files.exists(Paths.get(fileName))) return;

        ServerWorld world = getWorld(nether ? World.NETHER : World.OVERWORLD);
        assert world != null;

        int xSize = 10000, zSize = xSize;
        int layers = nether ? 4 : 1;

        ByteBuffer buffer = ByteBuffer.allocate(65536);
        buffer.putInt(xSize);
        buffer.putInt(zSize);
        buffer.putInt(layers);

        byte i = 0;
        Map<Biome, Byte> biomeMap = new HashMap<>();
        for (Biome biome : Registry.BIOME.stream().collect(Collectors.toList())) {
            buffer.put(++i);
            if (i < 0) throw new RuntimeException("Too many biomes!");
            Identifier biomeId = Registry.BIOME.getId(biome);
            assert biomeId != null;
            byte[] idBytes = biomeId.toString().getBytes(StandardCharsets.UTF_8);
            buffer.putInt(idBytes.length);
            buffer.put(idBytes);
            biomeMap.put(biome, i);
        }

        try (FileOutputStream outputStream = new FileOutputStream(fileName)) {
            outputStream.write(buffer.array(), 0, buffer.position());
            buffer.clear();
            for (int layer = 0; layer < layers; layer++) {
                int y = layer * 64;
                for (int x = 0; x < xSize; x++) {
                    System.out.println((x + layer * xSize) + "/" + (xSize * layers));
                    for (int z = 0; z < zSize; z++) {
                        Biome biome = world.getBiomeForNoiseGen(x, y, z);
                        buffer.put(biomeMap.get(biome));
                    }
                    outputStream.write(buffer.array(), 0, buffer.position());
                    buffer.clear();
                }
            }
        }
        if (nether) {
            System.out.println("Done (nether)");
        } else {
            System.out.println("Done (overworld)");
        }
    }
}
