package dev.ftb.mods.ftbteambases.util;

import dev.ftb.mods.ftbteambases.FTBTeamBases;
import dev.ftb.mods.ftbteambases.config.ServerConfig;
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
        List<Path> subDirs = new ArrayList<>(INITIAL_SUBDIRS);

        Path initialPath = server.getServerDirectory().resolve(PREGEN_INITIAL_PATH);
        Path worldPath = server.getWorldPath(LevelResource.ROOT);
        if (Files.isDirectory(initialPath) && !Files.isDirectory(worldPath.resolve("region"))) {
            // looks like a brand-new world, just created - copy over any pregen MCA files for overworld/nether/end if they exist
            addExtras(subDirs);

            for (Path subDir : subDirs) {
                Path srcDir = initialPath.resolve(subDir);
                Path destDir = worldPath.resolve(subDir);
                if (Files.isDirectory(srcDir) && !Files.isDirectory(destDir)) {
                    try {
                        FileUtils.copyDirectory(srcDir.toFile(), destDir.toFile());
                        FTBTeamBases.LOGGER.info("Copied initial pregen MCA files from {} to {}", srcDir, destDir);
                    } catch (IOException e) {
                        FTBTeamBases.LOGGER.error("Failed to copy initial MCA files from {} to {}: {}", srcDir, destDir, e.getMessage());
                    }
                }
            }
            ServerConfig.lobbyPos().ifPresent(pos -> {
                BaseInstanceManager mgr = BaseInstanceManager.get(server);
                mgr.setLobbySpawnPos(pos, false);
                mgr.setLobbyCreated(true);
                mgr.forceSave(server);
            });
            return true;
        }
        return false;
    }

    private static void addExtras(List<Path> paths) {
        // if the lobby dimension isn't a vanilla one (overworld/nether/end), consider files in that dimension for pregen too
        ServerConfig.lobbyDimension().ifPresent(key -> {
            ResourceLocation rl = key.location();
            if (!rl.getNamespace().equals("minecraft")) {
                paths.add(Path.of("dimensions", rl.getNamespace(), rl.getPath()));
            }
        });

        // also add any additional dimensions specified in config
        for (ResourceLocation rl : ServerConfig.additionalPregenDimensions()) {
            Path dimPath = Path.of("dimensions", rl.getNamespace(), rl.getPath());
            if (!paths.contains(dimPath)) {
                paths.add(dimPath);
            }
        }
    }
}
