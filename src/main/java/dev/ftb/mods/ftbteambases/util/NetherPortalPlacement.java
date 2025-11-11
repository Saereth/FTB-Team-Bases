package dev.ftb.mods.ftbteambases.util;

import dev.ftb.mods.ftbteambases.FTBTeamBases;
import dev.ftb.mods.ftbteambases.config.ServerConfig;
import dev.ftb.mods.ftbteambases.data.bases.BaseInstanceManager;
import dev.ftb.mods.ftbteambases.mixin.NetherPortalBlockAccess;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class NetherPortalPlacement {
    /**
     * Hook for {@link dev.ftb.mods.ftbteambases.mixin.NetherPortalBlockMixin}, also called via the
     * nether-visit command.
     *
     * @param serverLevel level the portal is in
     * @param entity the entity entering the portal
     * @param srcPos blockpos at which the portal is entered, or null if due to using the nether-visit command
     * @return portal destination if this should be a team transition, null otherwise
     */
    public static DimensionTransition getTeamEntryPoint(ServerLevel serverLevel, Entity entity, BlockPos srcPos) {
        if (!(entity instanceof ServerPlayer serverPlayer)) {
            return null;
        }
        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(serverPlayer).orElse(null);
        if (team == null) {
            return null;
        }

        var mgr = BaseInstanceManager.get(serverPlayer.server);

        if (DimensionUtils.isTeamDimension(serverLevel) || srcPos == null) {
            // going to the Nether from a team base or the nether-visit command; go to team-specific entry point
            ServerLevel netherLevel = serverLevel.getServer().getLevel(Level.NETHER);
            if (netherLevel != null) {
                BlockPos basePos = getTeamNetherBasePos(serverPlayer, team);
                WorldBorder worldborder = netherLevel.getWorldBorder();
                BlockPos destPos = worldborder.clampToBounds(basePos.getX(), basePos.getY(), basePos.getZ());

                if (srcPos != null) {
                    mgr.setPlayerNetherPortalLoc(serverPlayer, srcPos);
                }
                BlockPos srcPos1 = srcPos == null ? entity.blockPosition() : srcPos;
                return ((NetherPortalBlockAccess) Blocks.NETHER_PORTAL).invokeGetExitPortal(netherLevel, serverPlayer, srcPos1, destPos, true, worldborder);
            }
        } else if (serverLevel.dimension().equals(ServerLevel.NETHER)) {
            // returning from the Nether; go to where the player entered the nether portal if possible, otherwise their base spawn point
            return mgr.getBaseForPlayer(serverPlayer).map(base -> {
                ResourceKey<Level> teamDim = base.dimension();
                ServerLevel newLevel = serverLevel.getServer().getLevel(teamDim);
                if (newLevel != null) {
                    BlockPos portalPos = mgr.getPlayerNetherPortalLoc(serverPlayer).orElse(base.spawnPos());
                    return new DimensionTransition(newLevel, Vec3.atBottomCenterOf(portalPos), serverPlayer.getDeltaMovement(), serverPlayer.getYRot(), serverPlayer.getXRot(), DimensionTransition.PLAY_PORTAL_SOUND);
                }
                return null;
            }).orElse(null);
        }

        // fallback: use default vanilla nether portal behaviour
        return null;
    }

    private static BlockPos getTeamNetherBasePos(ServerPlayer serverPlayer, @Nullable Team team) {
        // seed the random generator based on the UUID of team being visited
        // this *should* give a deterministic and distinct location in the Nether, based on the team ID

        UUID id = team == null ? getPlayerTeamId(serverPlayer.getUUID()) : team.getId();
        RandomSource rand = RandomSource.create(id.getLeastSignificantBits() ^ id.getMostSignificantBits());

        double angle = rand.nextDouble() * Math.PI * 2.0;
        int min = ServerConfig.MIN_DIST_FROM_ORIGIN.get();
        int max = ServerConfig.MAX_DIST_FROM_ORIGIN.get();
        if (min >= max) {
            FTBTeamBases.LOGGER.warn("invalid min-max distance in config for nether portal placement, defaulting to 1000-25000");
            min = 1000;
            max = 25000;
        }
        int dist = min + rand.nextInt(max - min);

        return BlockPos.containing(Math.cos(angle) * dist, ServerConfig.getNetherPortalYPos(serverPlayer), Math.sin(angle) * dist);
    }

    private static UUID getPlayerTeamId(UUID playerId) {
        return FTBTeamsAPI.api().getManager().getTeamForPlayerID(playerId)
                .map(Team::getTeamId)
                .orElse(playerId);
    }
}
