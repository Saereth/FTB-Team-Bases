package dev.ftb.mods.ftbteambases.block;

import dev.ftb.mods.ftbteambases.config.ServerConfig;
import dev.ftb.mods.ftbteambases.data.construction.BaseConstructionManager;
import dev.ftb.mods.ftbteambases.data.bases.BaseInstanceManager;
import dev.ftb.mods.ftbteambases.net.ShowSelectionGuiMessage;
import dev.ftb.mods.ftbteambases.registry.ModBlocks;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class BasesPortalBlock extends NetherPortalBlock {
    public BasesPortalBlock() {
        super(Properties.copy(Blocks.NETHER_PORTAL));
    }

    @Override
    public void randomTick(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, RandomSource randomSource) {
        // do nothing
    }

    @Override
    public void entityInside(BlockState blockState, Level level, BlockPos blockPos, Entity entity) {
        if (level.isClientSide || entity.isPassenger() || entity.isVehicle() || !entity.canChangeDimensions() || !(entity instanceof ServerPlayer player)) {
            return;
        }

        if (player.isOnPortalCooldown()) {
            // vanilla functionality here: ensure portal creation/port logic only happens when stepping into the portal,
            //   and not when loitering around in a portal block
            player.setPortalCooldown();
        } else {
            FTBTeamsAPI.api().getManager().getTeamForPlayer(player).ifPresent(team -> {
                if (team.isPartyTeam()) {
                    BaseInstanceManager.get(player.getServer()).teleportToBaseSpawn(player, team.getId());
                } else if (!BaseConstructionManager.INSTANCE.isConstructing(player)) {
                    // player not in a party: bring up the base selection GUI
                    player.setPortalCooldown();
                    new ShowSelectionGuiMessage().sendTo(player);
                }
            });
        }
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction.Axis facing = ctx.getHorizontalDirection().getAxis();
        facing = facing == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;

        return defaultBlockState().setValue(NetherPortalBlock.AXIS, facing);
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter blockGetter, BlockPos blockPos, BlockState blockState) {
        return new ItemStack(ModBlocks.PORTAL_ITEM.get());
    }

    @Override
    public BlockState updateShape(BlockState blockState, Direction direction, BlockState blockState2, LevelAccessor levelAccessor, BlockPos blockPos, BlockPos blockPos2) {
        return blockState;
    }

    public static void checkForSpectator(Entity entity) {
        // spectator mode players don't normally activate portals - see EntityMixin
        if (entity instanceof Player player && player.isSpectator() && ServerConfig.ALLOW_LOBBY_SPECTATORS.get()) {
            BlockPos pos = player.blockPosition().above();
            if (player.level().getBlockState(pos).getBlock() == ModBlocks.PORTAL.get()) {
                player.getFeetBlockState().entityInside(player.level(), player.blockPosition(), player);
            }
        }
    }
}
