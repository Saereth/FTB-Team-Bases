package dev.ftb.mods.ftbteambases.integration;

import dev.architectury.platform.Platform;
import net.minecraft.server.level.ServerLevel;

public class FTBChunksIntegration {
    public static void maybeAutoClaimLobby(ServerLevel level) {
        if (Platform.isModLoaded("ftbchunks")) {
            AutoClaiming.handleLobbyAutoclaiming(level);
        }
    }
}
