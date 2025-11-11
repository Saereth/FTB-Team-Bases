package dev.ftb.mods.ftbteambases.block;

import dev.ftb.mods.ftbteambases.data.bases.BaseInstanceManager;
import dev.ftb.mods.ftbteambases.data.construction.BaseConstructionManager;
import dev.ftb.mods.ftbteambases.events.neoforge.TeamBasesPortalEvent;
import dev.ftb.mods.ftbteambases.net.ShowSelectionGuiMessage;
import dev.ftb.mods.ftbteambases.registry.ModBlocks;
import dev.ftb.mods.ftbteambases.registry.ModSounds;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

public class BasesPortalBlock extends NetherPortalBlock {
    public BasesPortalBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void randomTick(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, RandomSource randomSource) {
        // do nothing
    }

    @Override
    public void entityInside(BlockState blockState, Level level, BlockPos blockPos, Entity entity) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player) || !player.canUsePortal(false)) {
            return;
        }

        if (player.isOnPortalCooldown()) {
            // vanilla functionality here: ensure portal creation/port logic only happens when stepping into the portal,
            //   and not when loitering around in a portal block
            player.setPortalCooldown();
        } else {
            TeamBasesPortalEvent event = NeoForge.EVENT_BUS.post(new TeamBasesPortalEvent(player));
            if (!event.isCanceled()) {
                FTBTeamsAPI.api().getManager().getTeamForPlayer(player).ifPresent(team -> {
                    if (team.isPartyTeam()) {
                        BaseInstanceManager.get(player.getServer()).teleportToBaseSpawn(player, team.getId());
                    } else if (!BaseConstructionManager.INSTANCE.isConstructing(player)) {
                        // player not in a party: bring up the base selection GUI
                        player.setPortalCooldown();
                        PacketDistributor.sendToPlayer(player, ShowSelectionGuiMessage.INSTANCE);
                    }
                });
            } else {
                player.displayClientMessage(event.getCancellationReason(), true);
                player.setPortalCooldown();
            }
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
    public ItemStack getCloneItemStack(LevelReader levelReader, BlockPos blockPos, BlockState blockState) {
        return new ItemStack(ModBlocks.PORTAL_ITEM.get());
    }

    @Override
    public BlockState updateShape(BlockState blockState, Direction direction, BlockState blockState2, LevelAccessor levelAccessor, BlockPos blockPos, BlockPos blockPos2) {
        return blockState;
    }

    @Override
    public void animateTick(BlockState blockState, Level level, BlockPos pos, RandomSource randomSource) {
        if (randomSource.nextInt(100) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    ModSounds.PORTAL.get(), SoundSource.BLOCKS, 0.5F, randomSource.nextFloat() * 0.4F + 0.8F, false);
        }

        for (int i = 0; i < 4; ++i) {
            double x = pos.getX() + randomSource.nextDouble();
            double y = pos.getY() + randomSource.nextDouble();
            double z = pos.getZ() + randomSource.nextDouble();
            double xo = (randomSource.nextFloat() - 0.5) * 0.5;
            double yo = (randomSource.nextFloat() - 0.5) * 0.5;
            double zo = (randomSource.nextFloat() - 0.5) * 0.5;
            int k = randomSource.nextInt(2) * 2 - 1;
            if (!level.getBlockState(pos.west()).is(this) && !level.getBlockState(pos.east()).is(this)) {
                x = pos.getX() + 0.5 + 0.25 * k;
                xo = randomSource.nextFloat() * 2.0F * k;
            } else {
                z = pos.getZ() + 0.5 + 0.25 * k;
                zo = randomSource.nextFloat() * 2.0F * k;
            }

            level.addParticle(ParticleTypes.PORTAL, x, y, z, xo, yo, zo);
        }
    }
}
