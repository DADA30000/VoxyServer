package com.dripps.voxyserver.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class VoxyUpdateChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger("VoxyServer-Update");
    private static final String UPDATE_URL = "https://gist.githubusercontent.com/PooSmacker/a4205e4b4d58a9b61054c9978c041408/raw/update.json";

    public static void checkForUpdates() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(UPDATE_URL))
                        .header("User-Agent", "VoxyServer")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

                    String latestServerVer = json.get("latest_voxyserver").getAsString();

                    List<String> supportedVoxyVersions = new ArrayList<>();
                    if (json.has("supported_voxy") && json.get("supported_voxy").isJsonArray()) {
                        JsonArray voxyArray = json.getAsJsonArray("supported_voxy");
                        for (JsonElement element : voxyArray) {
                            supportedVoxyVersions.add(element.getAsString());
                        }
                    }

                    String currentServerVer = getModVersion("voxyserver");
                    String currentVoxyVer = getModVersion("voxy");

                    boolean serverNeedsUpdate = !currentServerVer.equals("Unknown") && !currentServerVer.equals(latestServerVer);
                    boolean voxyInstalled = !currentVoxyVer.equals("Unknown");
                    boolean voxyUnsupported = voxyInstalled && !supportedVoxyVersions.isEmpty() && !supportedVoxyVersions.contains(currentVoxyVer);

                    if (serverNeedsUpdate) {
                        LOGGER.warn("=====================================================");
                        LOGGER.warn("A new version of VoxyServer is available: {}", latestServerVer);
                        LOGGER.warn("You are currently running: {}", currentServerVer);
                        LOGGER.warn("Please check modrinth for the latest version");

                        if (voxyUnsupported) {
                            LOGGER.warn("IMPORTANT: The new VoxyServer supports Voxy versions: {}", String.join(", ", supportedVoxyVersions));
                            LOGGER.warn("Your current Voxy version is: {}", currentVoxyVer);
                        }

                        LOGGER.warn("=====================================================");
                    } else if (voxyUnsupported) {
                        LOGGER.warn("=====================================================");
                        LOGGER.warn("Your Voxy version ({}) is not in the supported list for VoxyServer {}.", currentVoxyVer, currentServerVer);
                        LOGGER.warn("Supported Voxy versions: {}", String.join(", ", supportedVoxyVersions));
                        LOGGER.warn("=====================================================");
                    } else {
                        LOGGER.info("VoxyServer is up to date.");
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("Could not check for VoxyServer updates.", e);
            }
        });
    }

    private static String getModVersion(String modId) {
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(modId);
        if (container.isPresent()) {
            return container.get().getMetadata().getVersion().getFriendlyString();
        }
        return "Unknown";
    }
}