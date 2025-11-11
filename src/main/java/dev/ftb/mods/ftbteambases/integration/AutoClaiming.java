package dev.ftb.mods.ftbteambases.integration;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimResult;
import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteambases.FTBTeamBases;
import dev.ftb.mods.ftbteambases.config.ServerConfig;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import dev.ftb.mods.ftbteams.api.property.TeamProperties;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.UUID;

public class AutoClaiming {
    private static final UUID LOBBY_SERVER_ID = UUID.fromString("ddc10a3e-b566-4e84-af66-8c315272ab02");

    public static void handleLobbyAutoclaiming(ServerLevel serverLevel) {
        // at this point, it's safe to assume that FTB Chunks is present
        if (ServerConfig.LOBBY_RADIUS.get() == 0 || ServerConfig.lobbyPos().isEmpty()) {
            return;
        }

        String teamName = ServerConfig.LOBBY_SERVER_TEAM_NAME.get();
        Color4I lobbyTeamColor = ServerConfig.getLobbyTeamColor();
        try {
            TeamManager teamMgr = FTBTeamsAPI.api().getManager();
            ClaimedChunkManager chunkMgr = FTBChunksAPI.api().getManager();

            CommandSourceStack serverCmdSource = serverLevel.getServer().createCommandSourceStack();
            Team lobbyTeam = teamMgr.getTeamByID(LOBBY_SERVER_ID)
                    .orElse(teamMgr.createServerTeam(serverCmdSource, teamName, null, lobbyTeamColor, LOBBY_SERVER_ID));

            // in case they've changed in config...
            lobbyTeam.setProperty(TeamProperties.DISPLAY_NAME, teamName);
            lobbyTeam.setProperty(TeamProperties.COLOR, lobbyTeamColor);

            ChunkTeamData chunkTeamData = chunkMgr.getOrCreateData(lobbyTeam);

            // unclaim any existing claims; allows config changes to radius or shape to work correctly
            chunkTeamData.getClaimedChunks().forEach(cc -> cc.unclaim(serverCmdSource, false));

            MutableInt claimed = new MutableInt(0);
            ServerConfig.LOBBY_SHAPE.get().forEachChunk(ServerConfig.getClaimCenter(), ServerConfig.LOBBY_RADIUS.get(), cp -> {
                ClaimResult claimRes = chunkTeamData.claim(serverCmdSource, new ChunkDimPos(serverLevel.dimension(), cp), false);
                if (!claimRes.isSuccess()) {
                    FTBTeamBases.LOGGER.error("Couldn't autoclaim lobby chunkpos {}: {}", cp, claimRes.getMessage().getString());
                } else {
                    claimed.increment();
                }
            });
            FTBTeamBases.LOGGER.info("autoclaimed {} chunks around lobby pos {} ({}) for server team {}",
                    claimed.getValue(), ServerConfig.lobbyPos().get(), ServerConfig.LOBBY_SHAPE.get(), lobbyTeam.getShortName());
        } catch (CommandSyntaxException e) {
            FTBTeamBases.LOGGER.error("can't create server team {}: {}", teamName, e.getMessage());
        }
    }
}
