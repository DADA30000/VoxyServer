package com.dripps.voxyserver.server;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// waits a few ticks before pushing dirty updates bc was causing issues
public class DirtyTracker {
    public static volatile DirtyTracker INSTANCE;

    private static final int PUSH_DELAY_TICKS = 2;
    private static final int MAX_PUSH_ATTEMPTS = 6;

    private final ConcurrentHashMap<DirtyChunk, Boolean> dirtyChunks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<DirtyChunk, PendingPush> pendingPushes = new ConcurrentHashMap<>();
    private final ChunkVoxelizer voxelizer;
    private final LodStreamingService streamingService;
    private final int flushInterval;
    private int tickCounter = 0;
    private long currentTick = 0L;

    private record DirtyChunk(Identifier dimension, int chunkX, int chunkZ) {}
    private record PendingPush(int nextTick, int attemptsLeft) {}

    public DirtyTracker(ChunkVoxelizer voxelizer, LodStreamingService streamingService, int flushInterval) {
        this.voxelizer = voxelizer;
        this.streamingService = streamingService;
        this.flushInterval = flushInterval;
    }

    public void markDirty(ServerLevel level, int chunkX, int chunkZ) {
        Identifier dim = level.dimension().identifier();
        DirtyChunk dirtyChunk = new DirtyChunk(dim, chunkX, chunkZ);
        dirtyChunks.put(dirtyChunk, Boolean.TRUE);
        pendingPushes.remove(dirtyChunk);
    }

    public void tick(MinecraftServer server) {
        currentTick++;
        flushPendingPushes(server);

        if (++tickCounter < flushInterval) return;
        tickCounter = 0;

        if (dirtyChunks.isEmpty()) return;

        // drain all dirty chunks
        Set<DirtyChunk> toProcess = ConcurrentHashMap.newKeySet();
        var iter = dirtyChunks.keySet().iterator();
        while (iter.hasNext()) {
            toProcess.add(iter.next());
            iter.remove();
        }

        for (DirtyChunk dc : toProcess) {
            ServerLevel level = findLevel(server, dc.dimension);
            if (level == null) continue;

            LevelChunk chunk = level.getChunkSource().getChunkNow(dc.chunkX, dc.chunkZ);
            if (chunk == null) continue;

            voxelizer.revoxelizeChunk(level, chunk);
            pendingPushes.put(dc, new PendingPush((int) (currentTick + PUSH_DELAY_TICKS), MAX_PUSH_ATTEMPTS));
        }
    }

    private void flushPendingPushes(MinecraftServer server) {
        if (pendingPushes.isEmpty()) return;

        Set<DirtyChunk> ready = ConcurrentHashMap.newKeySet();
        for (var entry : pendingPushes.entrySet()) {
            if (entry.getValue().nextTick <= currentTick) {
                ready.add(entry.getKey());
            }
        }

        for (DirtyChunk dc : ready) {
            PendingPush pending = pendingPushes.get(dc);
            if (pending == null || pending.nextTick > currentTick) continue;

            ServerLevel level = findLevel(server, dc.dimension);
            if (level == null) {
                pendingPushes.remove(dc, pending);
                continue;
            }

            LevelChunk chunk = level.getChunkSource().getChunkNow(dc.chunkX, dc.chunkZ);
            if (chunk == null) {
                pendingPushes.remove(dc, pending);
                continue;
            }

            streamingService.onChunkDirty(server, level, dc.chunkX, dc.chunkZ);

            if (pending.attemptsLeft <= 1) {
                pendingPushes.remove(dc, pending);
            } else {
                pendingPushes.replace(dc, pending, new PendingPush((int) (currentTick + PUSH_DELAY_TICKS), pending.attemptsLeft - 1));
            }
        }
    }

    private static ServerLevel findLevel(MinecraftServer server, Identifier dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().identifier().equals(dimension)) {
                return level;
            }
        }
        return null;
    }
}
