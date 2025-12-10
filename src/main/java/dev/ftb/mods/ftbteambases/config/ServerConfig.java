package dev.ftb.mods.ftbteambases.config;

import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.snbt.config.*;
import dev.ftb.mods.ftbteambases.FTBTeamBases;
import dev.ftb.mods.ftbteambases.worldgen.chunkgen.ChunkGenerators;
import net.minecraft.ResourceLocationException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

public interface ServerConfig {
    NameMap<GameType> GAME_TYPE_NAME_MAP = NameMap.of(GameType.ADVENTURE, GameType.values()).create();

    SNBTConfig CONFIG = SNBTConfig.create(FTBTeamBases.MOD_ID + "-server");

    SNBTConfig GENERAL = CONFIG.addGroup("general");
    BooleanValue CLEAR_PLAYER_INV_ON_JOIN = GENERAL.addBoolean("clear_player_inv_on_join", false)
            .comment("When set to true, the player's inventory will be cleared when joining a team");
    BooleanValue HEAL_PLAYER_ON_JOIN = GENERAL.addBoolean("heal_player_on_join", true)
            .comment("When set to true, the player will be healed (and fully fed) when joining a team");
    BooleanValue CLEAR_PLAYER_INV_ON_LEAVE = GENERAL.addBoolean("clear_player_inv_on_leave", true)
            .comment("When set to true, the player's inventory will be cleared when leaving a team");
    BooleanValue TEAM_NETHER_ENTRY_POINT = GENERAL.addBoolean("team_nether_entry_point", true)
            .comment("If true, then players going to the Nether via Nether Portal will be sent to a team-specific position in the Nether");
    IntValue BASE_SEPARATION = GENERAL.addInt("base_separation", 4, 0, 16)
            .comment("Base separation (in 512-block regions) when allocating regions for new bases in shared dimensions; the amount of clear space between the edges of two adjacent bases");
    IntValue HOME_CMD_PERMISSION_LEVEL = GENERAL.addInt("home_cmd_permission_level", 0, 0, 4)
            .comment("Permission level required to use the '/ftbteambases home' command; 0 = player, 2 = admin, 4 = server op");

    SNBTConfig LOBBY = CONFIG.addGroup("lobby");
    StringValue LOBBY_STRUCTURE_LOCATION = LOBBY.addString("lobby_structure_location", FTBTeamBases.rl("lobby").toString())
            .comment("Resource location of the structure NBT for the lobby",
                    "This is ignored if using pregenerated lobby region files (.mca files copied from ftbteambases/pregen_initial/)");
    IntValue LOBBY_Y_POS = LOBBY.addInt("lobby_y_pos", 0, -64, 256)
            .comment("Y position at which the lobby structure will be pasted into the level. " +
                    "Note: too near world min/max build height may result in parts of the structure being cut off - beware.");
    EnumValue<GameType> LOBBY_GAME_MODE = LOBBY.addEnum("lobby_game_mode", GAME_TYPE_NAME_MAP)
            .comment("The default game mode given to players when in the lobby.",
                    "Note that admin-mode players are free to change this.");
    DoubleValue LOBBY_PLAYER_YAW = LOBBY.addDouble("lobby_player_yaw", 0.0, 0.0, 360.0)
            .comment("Player Y-axis rotation when initially spawning in, or returning to, the lobby. (0 = south, 90 = west, 180 = north, 270 = east)");

    SNBTConfig WORLDGEN = CONFIG.addGroup("worldgen");
    EnumValue<ChunkGenerators> CHUNK_GENERATOR = WORLDGEN.addEnum("chunk_generator", ChunkGenerators.NAME_MAP)
            .comment("The chunk generator to use. SIMPLE_VOID (void dim, one biome), MULTI_BIOME_VOID (void dim, overworld-like biome distribution) and CUSTOM (full worldgen, customisable biome source & noise settings)");
    StringValue SINGLE_BIOME_ID = WORLDGEN.addString("single_biome_id", "")
            .comment("Only used by the CUSTOM and SIMPLE_VOID generators; if non-empty (e.g. 'minecraft:the_void'), the dimension will generate with only this biome. If empty, CUSTOM generator will use either 'biome_source_from_dimension' or an overworld-like biome distribution, and SIMPLE_VOID will use 'minecraft:the_void'");
    StringValue BIOME_SOURCE_FROM_DIMENSION = WORLDGEN.addString("biome_source_from_dimension", "")
            .comment("Only used by the CUSTOM generator and only when 'single_biome_id' is empty; if non-empty (e.g. 'minecraft:the_nether'), the generated dimension will use the biome source from the dimension specified here.");
    EnumValue<FeatureGeneration> FEATURE_GEN = WORLDGEN.addEnum("feature_gen", FeatureGeneration.NAME_MAP)
            .comment("DEFAULT: generate features in non-void worlds, don't generate in void worlds; NEVER: never generate; ALWAYS: always generate");
    StringValue NOISE_SETTINGS = WORLDGEN.addString("noise_settings", "minecraft:overworld")
            .comment("Only used by the CUSTOM generator; resource location for the noise settings to use.");
    BooleanValue ENTITIES_IN_START_STRUCTURE = WORLDGEN.addBoolean("entities_in_start_structure", true)
            .comment("If true, then any entities saved in the starting structure NBT will be included when the structure is generated");

    SNBTConfig NETHER = CONFIG.addGroup("nether");
    BooleanValue ALLOW_NETHER_PORTALS = NETHER.addBoolean("allow_nether_portals", true)
            .comment("When set to true, nether portals may be constructed in team dimensions");
    BooleanValue TEAM_SPECIFIC_NETHER_ENTRY_POINT = NETHER.addBoolean("team_specific_nether_entry_point", true)
            .comment("If true, then players going to the Nether via Nether Portal will be sent to a random (but deterministic for the team) position in the Nether");
    IntValue MIN_DIST_FROM_ORIGIN = NETHER.addInt("min_dist_from_origin", 1000, 0, Integer.MAX_VALUE)
            .comment("When 'team_specific_nether_entry_point' is true, this is the minimum distance from XZ=(0,0) this spot can be");
    IntValue MAX_DIST_FROM_ORIGIN = NETHER.addInt("max_dist_from_origin", 25000, 1000, Integer.MAX_VALUE)
            .comment("When 'team_specific_nether_entry_point' is true, this is the maximum distance from XZ=(0,0) this spot can be. Must be greater than 'min_dist_from_origin'.");
    BooleanValue USE_CUSTOM_PORTAL_Y_POS = NETHER.addBoolean("use_custom_portal_y", false)
            .comment("If true, use the value 'portal_y_pos' for the Y position of the Nether entry position for players. If false, use the player's current Y position.",
                    "Note that the actual portal position may be slightly different, depending on the terrain");
    IntValue CUSTOM_PORTAL_Y_POS = NETHER.addInt("portal_y_pos", 0)
            .comment("See 'use_custom_portal_y'.");

    SNBTConfig AUTOCLAIMING = CONFIG.addGroup("autoclaiming")
            .comment("Autoclaim lobby areas (FTB Chunks required)");
    IntValue LOBBY_RADIUS = AUTOCLAIMING.addInt("lobby_radius", 0, 0, Integer.MAX_VALUE)
            .comment("Radius in chunks for the lobby area to autoclaim",
                    "0 = autoclaiming disabled",
                    "1 = autoclaim just the chunk containing the lobby origin pos",
                    "2+ = extend the autoclaim distance out by 1 chunk per amount beyond 1");
    EnumValue<AutoClaimShape> LOBBY_SHAPE = AUTOCLAIMING.addEnum("lobby_shape", AutoClaimShape.NAME_MAP)
            .comment("Shape to be autoclaimed");
    StringValue LOBBY_SERVER_TEAM_NAME = AUTOCLAIMING.addString("server_team_name", "Lobby")
            .comment("The display name for the server team which is used to claim the lobby chunks",
                    "This name shows up on FTB Chunks mapping");
    StringValue LOBBY_CLAIM_COLOR = AUTOCLAIMING.addString("lobby_claim_color", "#FF40FF")
            .comment("The server team color",
                    "Many color names work, and hex codes in the form '#RRGGBB' are accepted");
    IntArrayValue LOBBY_CLAIM_CENTER = LOBBY.addIntArray("lobby_claim_center", new int[]{ 0, 0 })
            .comment("X/Z chunk position for the centre of the claimed area");

    static Optional<ResourceLocation> lobbyLocation() {
        try {
            return Optional.of(ResourceLocation.parse(LOBBY_STRUCTURE_LOCATION.get()));
        } catch (ResourceLocationException ignored) {
            FTBTeamBases.LOGGER.error("invalid lobby resource location: {}", LOBBY_STRUCTURE_LOCATION.get());
            return Optional.empty();
        }
    }

    static int getNetherPortalYPos(Player player) {
        return USE_CUSTOM_PORTAL_Y_POS.get() ? CUSTOM_PORTAL_Y_POS.get() : player.blockPosition().getY();
    }

    static Color4I getLobbyTeamColor() {
        Color4I teamColor = Color4I.fromString(ServerConfig.LOBBY_CLAIM_COLOR.get());
        return teamColor.isEmpty() ? Color4I.rgb(0xFF40FF) : teamColor;
    }

    static ChunkPos getClaimCenter() {
        int[] pos = ServerConfig.LOBBY_CLAIM_CENTER.get();
        if (pos.length == 2) {
            return new ChunkPos(pos[0], pos[1]);
        } else {
            FTBTeamBases.LOGGER.error("invalid lobby claim centre pos! expected 2 integers, got {}. default to (0, 0)_", pos.length);
            return new ChunkPos(0, 0);
        }
    }

    enum FeatureGeneration {
        DEFAULT,
        NEVER,
        ALWAYS;

        public static final NameMap<FeatureGeneration> NAME_MAP = NameMap.of(DEFAULT, values()).create();

        public boolean shouldGenerate(boolean isVoid) {
            return this == ALWAYS || this == DEFAULT && !isVoid;
        }
    }
}
