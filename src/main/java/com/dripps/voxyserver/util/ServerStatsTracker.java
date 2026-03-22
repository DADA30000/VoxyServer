package com.dripps.voxyserver.util;

import com.dripps.voxyserver.Voxyserver;
import net.minecraft.server.MinecraftServer;

public class ServerStatsTracker {
    public static ServerStatsTracker INSTANCE;

    private int chunksVoxelized;
    private int sectionsStreamed;
    private int engineActions;
    private final int tickInterval;
    private int ticks;

    public ServerStatsTracker(int interval) {
        this.tickInterval = interval;
    }

    public void markVoxelized() {
        this.chunksVoxelized++;
    }

    public void markStreamed() {
        this.sectionsStreamed++;
    }

    public void markEngineAction() {
        this.engineActions++;
    }

    public void tick(MinecraftServer server) {
        if (++this.ticks >= this.tickInterval) {
            this.ticks = 0;
            Voxyserver.LOGGER.info("stats: chunks voxelized {} - sections streamed {} - engine actions {}", this.chunksVoxelized, this.sectionsStreamed, this.engineActions);
        }
    }
}
