package dev.ftb.mods.ftbteambases.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.ftb.mods.ftbteambases.config.ServerConfig;
import dev.ftb.mods.ftbteambases.config.StartupConfig;
import dev.ftb.mods.ftbteambases.data.bases.BaseInstanceManager;
import dev.ftb.mods.ftbteambases.util.MiscUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class SetLobbyPosCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return literal("setlobbypos")
                .requires(ctx -> ctx.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> setLobbyPos(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos")))
                );
    }

    private static int setLobbyPos(CommandSourceStack source, BlockPos pos) {
        BaseInstanceManager.get(source.getServer()).setLobbySpawnPos(pos, true);

        source.sendSuccess(() -> Component.literal("lobby pos updated to " + MiscUtil.blockPosStr(pos)), false);

        StartupConfig.lobbyDimension().ifPresent(dim -> {
            ServerLevel level = source.getServer().getLevel(dim);
            if (level != null) {
                level.setDefaultSpawnPos(pos, ServerConfig.LOBBY_PLAYER_YAW.get().floatValue());
            }
        });

        return Command.SINGLE_SUCCESS;
    }
}
