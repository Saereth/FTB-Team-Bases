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

    SNBTConfig LOBBY = CONFIG.addGroup("lobby");
    StringValue LOBBY_STRUCTURE_LOCATION = LOBBY.addString("lobby_structure_location", FTBTeamBases.rl("lobby").toString())
            .comment("Resource location of the structure NBT for the overworld lobby");
    ResourceLocationListValue ADDITIONAL_PREGEN_DIMENSIONS = LOBBY.add(new ResourceLocationListValue(
            LOBBY,
            "additional_pregen_dimensions",
            List.of(),
            "Additional dimensions to copy pregen files for on new world creation.\n" +
                    "Place MCA files in ftbteambases/pregen_initial/dimensions/<namespace>/<path>/region/"
    ));
    IntValue LOBBY_Y_POS = LOBBY.addInt("lobby_y_pos", 0, -64, 256)
            .comment("Y position at which the lobby structure will be pasted into the level. " +
                    "Note: too near world min/max build height may result in parts of the structure being cut off - beware.");
    EnumValue<GameType> LOBBY_GAME_MODE = LOBBY.addEnum("lobby_game_mode", GAME_TYPE_NAME_MAP)
            .comment("The default game mode given to players when in the lobby. Note that admin-mode players are free to change this.");
    IntArrayValue LOBBY_SPAWN = LOBBY.addIntArray("lobby_spawn_pos", new int[]{ 0, 0, 0})
            .comment("Position at which new players spawn. Only used if the lobby structure comes from a pregenerated region!");
    StringValue LOBBY_DIMENSION = LOBBY.addString("lobby_dimension", "minecraft:overworld")
            .comment("Dimension ID of the level in which the lobby is created. This *must* be a static pre-existing dimension, not a dynamically created one! New players will be automatically teleported to this dimension the first time they connect to the server. This setting should be defined in default config so the server has it before any levels are created - do NOT modify this on existing worlds!");
    DoubleValue LOBBY_PLAYER_YAW = LOBBY.addDouble("lobby_player_yaw", 0.0, 0.0, 360.0)
            .comment("Player Y-axis rotation when initially spawning in, or returning to, the lobby. (0 = south, 90 = west, 180 = north, 270 = east)");
    IntArrayValue LOBBY_CLAIM_CENTER = LOBBY.addIntArray("lobby_claim_center", new int[]{ 0, 0 })
            .comment("X/Z chunk position for the centre of the claimed area");

    SNBTConfig WORLDGEN = CONFIG.addGroup("worldgen");
    EnumValue<ChunkGenerators> CHUNK_GENERATOR = WORLDGEN.addEnum("chunk_generator", ChunkGenerators.NAME_MAP)
            .comment("The chunk generator to use. SIMPLE_VOID (void dim, one biome), MULTI_BIOME_VOID (void dim, overworld-like biome distribution) and CUSTOM (full worldgen, customisable biome source & noise settings)");
    StringValue SINGLE_BIOME_ID = WORLDGEN.addString("single_biome_id", "")
            .comment("Only used by the CUSTOM and SIMPLE_VOID generators; if non-empty (e.g. 'minecraft:the_void'), the dimension will generate with only this biome. If empty, CUSTOM generator will use 'copy_generator_from_dimension' or 'custom_biome_param_list` to get the biome(s), and SIMPLE_VOID will use 'minecraft:the_void'");
    StringValue COPY_GENERATOR_FROM_DIMENSION = WORLDGEN.addString("copy_generator_from_dimension", "")
            .comment("Only used by the CUSTOM generator; if non-empty, this is the dimension ID of an existing static dimension from which the chunk generator settings will be copied when creating dynamic dimensions. If empty, CUSTOM generator will use 'custom_biome_param_list` to get the biomes.");
    StringValue CUSTOM_BIOME_PARAM_LIST = WORLDGEN.addString("custom_biome_param_list", "minecraft:overworld")
            .comment("Only used by the CUSTOM generator, and when 'single_biome_id' and 'copy_generator_from_dimension' are both empty; this can be either 'minecraft:overworld' or 'minecraft:nether' - no other values are acceptable (presets are hardcoded by vanilla)");
    EnumValue<FeatureGeneration> FEATURE_GEN = WORLDGEN.addEnum("feature_gen", FeatureGeneration.NAME_MAP)
            .comment("DEFAULT: generate features in non-void worlds, don't generate in void worlds; NEVER: never generate; ALWAYS: always generate");
    StringValue NOISE_SETTINGS = WORLDGEN.addString("noise_settings", "minecraft:overworld")
            .comment("Only used by the CUSTOM generator when not using 'copy_generator_from_dimension'; resource location for the noise settings to use.");
    BooleanValue ENTITIES_IN_START_STRUCTURE = WORLDGEN.addBoolean("entities_in_start_structure", true)
            .comment("If true, then any entities saved in the starting structure NBT will be included when the structure is generated");

    SNBTConfig NETHER = CONFIG.addGroup("nether");
    BooleanValue ALLOW_NETHER_PORTALS = NETHER.addBoolean("allow_nether_portals", true)
            .comment("When set to true, nether portals may be constructed in team dimensions");
    BooleanValue TEAM_SPECIFIC_NETHER_ENTRY_POINT = NETHER.addBoolean("team_specific_nether_entry_point", true)
            .comment("If true, then players going to the Nether via Nether Portal will be sent to a random (but deterministic for the team) position in the Nether");

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

    static Optional<ResourceLocation> lobbyLocation() {
        if (LOBBY_STRUCTURE_LOCATION.get().isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new ResourceLocation(LOBBY_STRUCTURE_LOCATION.get()));
        } catch (ResourceLocationException ignored) {
            FTBTeamBases.LOGGER.error("invalid lobby resource location: {}", LOBBY_STRUCTURE_LOCATION.get());
            return Optional.empty();
        }
    }

    static Optional<ResourceKey<Level>> lobbyDimension() {
        try {
            return Optional.of(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(LOBBY_DIMENSION.get())));
        } catch (ResourceLocationException ignored) {
            FTBTeamBases.LOGGER.error("invalid dimension ID in config 'lobby_dimension': {}", ServerConfig.LOBBY_DIMENSION.get());
            return Optional.empty();
        }
    }

    static Optional<BlockPos> lobbyPos() {
        int[] pos = ServerConfig.LOBBY_SPAWN.get();
        if (pos.length == 3) {
            return Optional.of(new BlockPos(pos[0], pos[1], pos[2]));
        } else {
            FTBTeamBases.LOGGER.error("invalid lobby spawn pos! expected 3 integers, got {}", pos.length);
            return Optional.empty();
        }
    }

    static List<ResourceLocation> additionalPregenDimensions() {
        return ADDITIONAL_PREGEN_DIMENSIONS.get();
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
