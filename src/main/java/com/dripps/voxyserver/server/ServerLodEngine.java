package com.dripps.voxyserver.server;

import com.dripps.voxyserver.Voxyserver;
import me.cortex.voxy.common.StorageConfigUtil;
import me.cortex.voxy.common.config.ConfigBuildCtx;
import me.cortex.voxy.common.config.section.SectionStorage;
import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.commonImpl.VoxyInstance;
import me.cortex.voxy.commonImpl.WorldIdentifier;

import java.nio.file.Path;

public class ServerLodEngine extends VoxyInstance {
    private final Path basePath;
    private final SectionSerializationStorage.Config storageConfig;

    public ServerLodEngine(Path worldFolder) {
        super();
        this.basePath = worldFolder.resolve("voxyserver");
        this.storageConfig = StorageConfigUtil.createDefaultSerializer();
        this.updateDedicatedThreads();
        Voxyserver.LOGGER.info("server lod engine started, storage at {}", this.basePath);
    }

    @Override
    protected SectionStorage createStorage(WorldIdentifier identifier) {
        if (com.dripps.voxyserver.util.ServerStatsTracker.INSTANCE != null) {
            com.dripps.voxyserver.util.ServerStatsTracker.INSTANCE.markEngineAction();
        }
        var ctx = new ConfigBuildCtx();
        ctx.setProperty(ConfigBuildCtx.BASE_SAVE_PATH, this.basePath.toString());
        ctx.setProperty(ConfigBuildCtx.WORLD_IDENTIFIER, identifier.getWorldId());
        ctx.pushPath(ConfigBuildCtx.DEFAULT_STORAGE_PATH);
        return this.storageConfig.build(ctx);
    }
}
