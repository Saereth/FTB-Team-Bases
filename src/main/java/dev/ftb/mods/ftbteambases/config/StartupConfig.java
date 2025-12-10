package dev.ftb.mods.ftbteambases.config;

import dev.ftb.mods.ftblibrary.snbt.config.IntArrayValue;
import dev.ftb.mods.ftblibrary.snbt.config.SNBTConfig;
import dev.ftb.mods.ftblibrary.snbt.config.StringValue;
import dev.ftb.mods.ftbteambases.FTBTeamBases;
import net.minecraft.ResourceLocationException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Startup configs are loaded immediately from the mod constructor.
 * Use this for values that must be available before the server starts.
 * These configs cannot be edited in-game and are not synced to clients.
 */
public interface StartupConfig {
    SNBTConfig CONFIG = SNBTConfig.create(FTBTeamBases.MOD_ID + "-startup");

    StringValue LOBBY_DIMENSION = CONFIG.addString("lobby_dimension", "minecraft:overworld")
            .comment("Dimension ID of the level in which the lobby is created.",
                    "This *must* be a static pre-existing dimension, not a dynamically created one!",
                    "New players will be automatically teleported to this dimension the first time they connect.",
                    "WARNING: Do NOT modify this on existing worlds!");

    ResourceLocationListValue ADDITIONAL_PREGEN_DIMENSIONS = CONFIG.add(new ResourceLocationListValue(
            CONFIG,
            "additional_pregen_dimensions",
            List.of(),
            "Additional dimensions to copy pregen files for on new world creation.\n" +
                    "Place MCA files in ftbteambases/pregen_initial/dimensions/<namespace>/<path>/region/"
    ));

    IntArrayValue LOBBY_SPAWN = CONFIG.addIntArray("lobby_spawn_pos", new int[]{0, 0, 0})
            .comment("Position at which new players spawn.",
                    "Only used if the lobby structure comes from a pregenerated region!");

    static Optional<ResourceKey<Level>> lobbyDimension() {
        try {
            return Optional.of(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(LOBBY_DIMENSION.get())));
        } catch (ResourceLocationException ignored) {
            FTBTeamBases.LOGGER.error("invalid dimension ID in config 'lobby_dimension': {}", LOBBY_DIMENSION.get());
            return Optional.empty();
        }
    }

    static List<ResourceLocation> additionalPregenDimensions() {
        return ADDITIONAL_PREGEN_DIMENSIONS.get();
    }

    static Optional<BlockPos> lobbyPos() {
        int[] pos = LOBBY_SPAWN.get();
        if (pos.length == 3) {
            return Optional.of(new BlockPos(pos[0], pos[1], pos[2]));
        } else {
            FTBTeamBases.LOGGER.error("invalid lobby spawn pos! expected 3 integers, got {}", pos.length);
            return Optional.empty();
        }
    }
}
