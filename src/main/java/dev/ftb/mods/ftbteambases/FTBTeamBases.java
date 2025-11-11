package dev.ftb.mods.ftbteambases;

import dev.ftb.mods.ftblibrary.config.manager.ConfigManager;
import dev.ftb.mods.ftbteambases.command.CommandUtils;
import dev.ftb.mods.ftbteambases.config.ClientConfig;
import dev.ftb.mods.ftbteambases.config.ServerConfig;
import dev.ftb.mods.ftbteambases.data.bases.BaseInstanceManager;
import dev.ftb.mods.ftbteambases.data.construction.BaseConstructionManager;
import dev.ftb.mods.ftbteambases.data.construction.RelocatorTracker;
import dev.ftb.mods.ftbteambases.data.definition.BaseDefinitionManager;
import dev.ftb.mods.ftbteambases.data.purging.PurgeManager;
import dev.ftb.mods.ftbteambases.integration.FTBChunksIntegration;
import dev.ftb.mods.ftbteambases.net.SyncBaseTemplatesMessage;
import dev.ftb.mods.ftbteambases.net.VoidTeamDimensionMessage;
import dev.ftb.mods.ftbteambases.registry.ModArgumentTypes;
import dev.ftb.mods.ftbteambases.registry.ModBlocks;
import dev.ftb.mods.ftbteambases.registry.ModSounds;
import dev.ftb.mods.ftbteambases.registry.ModWorldGen;
import dev.ftb.mods.ftbteambases.util.*;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;

import static net.minecraft.world.level.Level.NETHER;
import static net.minecraft.world.level.Level.OVERWORLD;

@Mod(FTBTeamBases.MOD_ID)
public class FTBTeamBases {
    public static final String MOD_ID = "ftbteambases";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static final ResourceLocation NO_TEMPLATE_ID = rl("none");
    public static final ResourceLocation SHARED_DIMENSION_ID = rl("bases");

    public FTBTeamBases(IEventBus modBus) {
        try {
            Files.createDirectories(RegionFileRelocator.PREGEN_PATH);
        } catch (IOException e) {
            LOGGER.error("can't create {}: {}", RegionFileRelocator.PREGEN_PATH, e.getMessage());
        }

        ModWorldGen.init(modBus);
        ModBlocks.init(modBus);
        ModSounds.init(modBus);
        ModArgumentTypes.init(modBus);

        ConfigManager.getInstance().registerServerConfig(ServerConfig.CONFIG, "server", false);
        ConfigManager.getInstance().registerClientConfig(ClientConfig.CONFIG, "client");

        NeoForge.EVENT_BUS.addListener(FTBTeamBases::serverBeforeStart);
        NeoForge.EVENT_BUS.addListener(FTBTeamBases::serverStarting);
        NeoForge.EVENT_BUS.addListener(FTBTeamBases::serverStarted);
        NeoForge.EVENT_BUS.addListener(FTBTeamBases::serverStopping);

        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::onSleepFinished);

        NeoForge.EVENT_BUS.addListener(FTBTeamBases::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(CommandUtils::registerCommands);
        NeoForge.EVENT_BUS.addListener(FTBTeamBases::onServerTick);
        NeoForge.EVENT_BUS.addListener(FTBTeamBases::playerEnterServer);
        NeoForge.EVENT_BUS.addListener(FTBTeamBases::playerJoinLevel);
        NeoForge.EVENT_BUS.addListener(FTBTeamBases::playerChangedDimension);
        NeoForge.EVENT_BUS.addListener(FTBTeamBases::registerReloadListeners);
        NeoForge.EVENT_BUS.addListener(FTBTeamBases::onPlayerRespawn);

        TeamEvent.PLAYER_JOINED_PARTY.register(TeamEventListener::teamPlayerJoin);
        TeamEvent.PLAYER_LEFT_PARTY.register(TeamEventListener::teamPlayerLeftParty);
        TeamEvent.DELETED.register(TeamEventListener::teamDeleted);
    }

    private static void registerReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new BaseDefinitionManager.ReloadListener());
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        RelocatorTracker.INSTANCE.tick(event.getServer());
        BaseConstructionManager.INSTANCE.tick(event.getServer());
        DynamicDimensionManager.unregisterScheduledDimensions(event.getServer());
    }

    private static void serverBeforeStart(ServerAboutToStartEvent event) {
        PurgeManager.INSTANCE.init(event.getServer());
    }

    private static void serverStarting(ServerStartingEvent event) {
        FTBTeamsAPI.api().setPartyCreationFromAPIOnly(true);
    }

    private static void serverStarted(ServerStartedEvent event) {
        ServerConfig.lobbyDimension().ifPresent(dim -> {
            // only override overworld default spawn pos if the lobby is actually in the overworld
            if (dim.equals(OVERWORLD)) {
                ServerLevel level = event.getServer().getLevel(OVERWORLD);
                if (level == null) {
                    LOGGER.error("Missed spawn reset event due to overworld being null?!");
                    return;
                }

                BaseInstanceManager mgr = BaseInstanceManager.get(event.getServer());
                if (mgr.isLobbyCreated() && !level.getSharedSpawnPos().equals(mgr.getLobbySpawnPos())) {
                    level.setDefaultSpawnPos(mgr.getLobbySpawnPos(), 180F);
                    LOGGER.info("Updating overworld spawn pos to the lobby spawn pos: {}", mgr.getLobbySpawnPos());
                }
            }
        });
    }

    private static void serverStopping(ServerStoppingEvent server) {
        PurgeManager.INSTANCE.onShutdown();
    }

    private static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            if (serverLevel.dimension() == OVERWORLD) {
                if (LobbyPregen.maybePregenLobby(serverLevel.getServer())) {
                    FTBChunksIntegration.maybeAutoClaimLobby(serverLevel);
                    return;
                }
            }

            ServerConfig.lobbyDimension().ifPresent(rl -> {
                if (serverLevel.dimension().equals(rl)) {
                    maybeCreateLobbyFromStructure(serverLevel);
                    FTBChunksIntegration.maybeAutoClaimLobby(serverLevel);
                }
            });

        }
    }

    private static void playerEnterServer(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SyncBaseTemplatesMessage.syncTo(player);
            BaseInstanceManager.get().checkForOrphanedPlayer(player);
        }
    }

    private static void playerJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel serverLevel) {
            if (isFirstTimeConnecting(player, serverLevel)) {
                ServerLevel destLevel = ServerConfig.lobbyDimension()
                        .map(dim -> serverLevel.getServer().getLevel(dim))
                        .orElse(serverLevel);

                // Send new players to the lobby. Note that respawn position after death is handled by
                //   the PlayerRespawnPositionEvent handler, so we don't use player.setRespawnPosition() anymore
                BlockPos lobbySpawnPos = BaseInstanceManager.get(player.server).getLobbySpawnPos();
                player.teleportTo(destLevel, lobbySpawnPos.getX(), lobbySpawnPos.getY(), lobbySpawnPos.getZ(),
                        ServerConfig.LOBBY_PLAYER_YAW.get().floatValue(), -10F);
                BaseInstanceManager.get().addKnownPlayer(player);
            }

            if (DimensionUtils.isVoidChunkGen(serverLevel.getChunkSource().getGenerator())) {
                VoidTeamDimensionMessage.syncTo(player);
            }
            switchGameMode(player, null, serverLevel.dimension());
        }
    }

    private static boolean isFirstTimeConnecting(ServerPlayer player, ServerLevel level) {
        return level.dimension().equals(OVERWORLD)
                && player.getRespawnDimension().equals(OVERWORLD)
                && !BaseInstanceManager.get(player.server).isPlayerKnown(player);
    }

    private static void playerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            switchGameMode(player, event.getFrom(), event.getTo());
            handleNetherTravel(player, event.getFrom(), event.getTo());
        }
    }

    private static void switchGameMode(ServerPlayer player, @Nullable ResourceKey<Level> oldDim, ResourceKey<Level> newDim) {
        GameType lobbyGameMode = ServerConfig.LOBBY_GAME_MODE.get();
        ResourceKey<Level> lobby = ServerConfig.lobbyDimension().orElse(OVERWORLD);

        if (newDim.equals(lobby) && player.gameMode.getGameModeForPlayer() != lobbyGameMode && player.gameMode.getGameModeForPlayer() != GameType.CREATIVE) {
            player.setGameMode(lobbyGameMode);
        } else if (lobby.equals(oldDim) && !newDim.equals(lobby) && player.gameMode.getGameModeForPlayer() == lobbyGameMode) {
            player.setGameMode(GameType.SURVIVAL);
        }
    }

    private static void handleNetherTravel(ServerPlayer player, ResourceKey<Level> oldDim, ResourceKey<Level> newDim) {
        if (player.isOnPortalCooldown()) {
            var mgr = BaseInstanceManager.get(player.server);

            if (newDim.equals(NETHER)) {
                // travelling to the Nether: if from our team dimension, store the player's location (in the from-dimension!) to later return there
                BlockPos portalPos = mgr.getBaseForPlayer(player)
                        .filter(base -> oldDim.equals(base.dimension()))
                        .map(base -> BlockPos.containing(player.xOld, player.yOld, player.zOld))
                        .orElse(null);
                mgr.setPlayerNetherPortalLoc(player, portalPos);
            } else if (oldDim.equals(NETHER) && newDim.equals(OVERWORLD)) {
                // returning from the Nether: intercept this and send the player to their base portal instead
                //   (or the base spawn point if for some reason we don't have their portal return point stored)
                mgr.getBaseForPlayer(player).ifPresentOrElse(base -> {
                    ResourceKey<Level> teamDim = base.dimension();
                    BlockPos portalPos = mgr.getPlayerNetherPortalLoc(player).orElse(base.spawnPos());
                    DimensionUtils.teleport(player, teamDim, portalPos);
                }, () -> mgr.teleportToLobby(player));
            }
        }
    }

    private static void maybeCreateLobbyFromStructure(ServerLevel serverLevel) {
        BaseInstanceManager mgr = BaseInstanceManager.get(serverLevel.getServer());
        if (!mgr.isLobbyCreated()) {
            ServerConfig.lobbyLocation().ifPresent(lobbyLocation -> {
                // paste the lobby structure into the lobby level (typically the overworld, but can be changed in config)
                StructureTemplate lobby = serverLevel.getStructureManager().getOrCreate(lobbyLocation);
                StructurePlaceSettings placeSettings = DimensionUtils.makePlacementSettings(lobby);

                BlockPos lobbyPos = BlockPos.ZERO.offset(-(lobby.getSize().getX() / 2), ServerConfig.LOBBY_Y_POS.get(), -(lobby.getSize().getZ() / 2));
                lobby.placeInWorld(serverLevel, lobbyPos, lobbyPos, placeSettings, serverLevel.random, Block.UPDATE_ALL);

                BlockPos relativePos = DimensionUtils.findSpawnBlockInStructure(lobby).orElse(BlockPos.ZERO);
                BlockPos playerSpawn = lobbyPos.offset(relativePos.getX(), relativePos.getY(), relativePos.getZ());

                mgr.setLobbySpawnPos(playerSpawn, false);
                serverLevel.removeBlock(playerSpawn, false);
                serverLevel.setDefaultSpawnPos(playerSpawn, ServerConfig.LOBBY_PLAYER_YAW.get().floatValue());

                mgr.setLobbyCreated(true);
                mgr.forceSave(serverLevel.getServer());

                LOGGER.info("Spawned lobby structure @ {} / {}", serverLevel.dimension().location(), lobbyPos);
            });
        }
    }

    private void onSleepFinished(final SleepFinishedTimeEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().location().getNamespace().equals(FTBTeamBases.MOD_ID)) {
            // player has slept in a dynamic dimension
            // sleeping in dynamic dimensions doesn't work in general: https://bugs.mojang.com/browse/MC-188578
            // best we can do here is advance the overworld time
            MiscUtil.setOverworldTime(level.getServer(), event.getNewTime());
        }
    }

    private static void onPlayerRespawn(PlayerRespawnPositionEvent event) {
        MinecraftServer server = event.getEntity().getServer();
        if (server != null) {
            BaseInstanceManager mgr = BaseInstanceManager.get(server);
            ServerLevel lobbyLvl = server.getLevel(ServerConfig.lobbyDimension().orElse(Level.OVERWORLD));
            if (lobbyLvl != null) {
                event.setDimensionTransition(new DimensionTransition(
                        lobbyLvl, Vec3.atCenterOf(mgr.getLobbySpawnPos()), Vec3.ZERO,
                        ServerConfig.LOBBY_PLAYER_YAW.get().floatValue(), 0f, DimensionTransition.DO_NOTHING
                ));
            }
        }
    }

    public static ResourceLocation rl(String id) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, id);
    }
}

