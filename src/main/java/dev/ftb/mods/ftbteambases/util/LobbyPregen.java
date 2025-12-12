package dev.ftb.mods.ftbteambases.util;

import dev.ftb.mods.ftbteambases.FTBTeamBases;
import dev.ftb.mods.ftbteambases.config.StartupConfig;
import dev.ftb.mods.ftbteambases.data.bases.BaseInstanceManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class LobbyPregen {
    private static final Path PREGEN_INITIAL_PATH = Path.of(FTBTeamBases.MOD_ID, "pregen_initial");

    private static final List<Path> INITIAL_SUBDIRS = Stream.of("region", "entities", "poi", "DIM1", "DIM-1").map(Path::of).toList();

    public static boolean maybePregenLobby(MinecraftServer server) {
        Path initialPath = server.getServerDirectory().resolve(PREGEN_INITIAL_PATH);
        Path worldPath = server.getWorldPath(LevelResource.ROOT);

        if (!Files.isDirectory(initialPath) || Files.isDirectory(worldPath.resolve("region"))) {
            // No pregen_initial folder, or world already has region data - not a new world
            return false;
        }

        // This is a brand-new world - copy over any pregen MCA files
        boolean copiedAnything = false;

        // Copy lobby/overworld pregen files (region, entities, poi, DIM1, DIM-1)
        List<Path> lobbySubDirs = new ArrayList<>(INITIAL_SUBDIRS);
        addLobbyExtras(lobbySubDirs);

        for (Path subDir : lobbySubDirs) {
            Path srcDir = initialPath.resolve(subDir);
            Path destDir = worldPath.resolve(subDir);
            if (Files.isDirectory(srcDir) && !Files.isDirectory(destDir)) {
                if (copyDirectory(srcDir, destDir)) {
                    copiedAnything = true;
                }
            }
        }

        // Copy additional pregen dimensions from startup config
        for (ResourceLocation rl : StartupConfig.additionalPregenDimensions()) {
            Path dimPath = Path.of("dimensions", rl.getNamespace(), rl.getPath());
            Path srcDir = initialPath.resolve(dimPath);
            Path destDir = worldPath.resolve(dimPath);

            if (Files.isDirectory(srcDir) && !Files.isDirectory(destDir.resolve("region"))) {
                if (copyDirectory(srcDir, destDir)) {
                    copiedAnything = true;
                    FTBTeamBases.LOGGER.info("Copied additional pregen dimension: {}", rl);
                }
            }
        }

        if (copiedAnything) {
            StartupConfig.lobbyPos().ifPresent(pos -> {
                BaseInstanceManager mgr = BaseInstanceManager.get(server);
                mgr.setLobbySpawnPos(pos, false);
                mgr.setLobbyCreated(true);
                mgr.forceSave(server);
            });
        }

        return copiedAnything;
    }

    private static boolean copyDirectory(Path srcDir, Path destDir) {
        try {
            FileUtils.copyDirectory(srcDir.toFile(), destDir.toFile());
            FTBTeamBases.LOGGER.info("Copied initial pregen MCA files from {} to {}", srcDir, destDir);
            return true;
        } catch (IOException e) {
            FTBTeamBases.LOGGER.error("Failed to copy initial MCA files from {} to {}: {}", srcDir, destDir, e.getMessage());
            return false;
        }
    }

    private static void addLobbyExtras(List<Path> paths) {
        // if the lobby dimension isn't a vanilla one (overworld/nether/end), consider files in that dimension for pregen too
        StartupConfig.lobbyDimension().ifPresent(key -> {
            ResourceLocation rl = key.location();
            if (!rl.getNamespace().equals("minecraft")) {
                paths.add(Path.of("dimensions", rl.getNamespace(), rl.getPath()));
            }
        });
    }
}
