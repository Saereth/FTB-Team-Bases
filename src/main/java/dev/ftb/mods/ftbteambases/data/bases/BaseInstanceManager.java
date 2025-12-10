package dev.ftb.mods.ftbteambases.data.bases;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ftb.mods.ftblibrary.math.XZ;
import dev.ftb.mods.ftbteambases.FTBTeamBases;
import dev.ftb.mods.ftbteambases.command.CommandUtils;
import dev.ftb.mods.ftbteambases.config.ServerConfig;
import dev.ftb.mods.ftbteambases.config.StartupConfig;
import dev.ftb.mods.ftbteambases.data.definition.BaseDefinition;
import dev.ftb.mods.ftbteambases.data.purging.PurgeManager;
import dev.ftb.mods.ftbteambases.events.BaseArchivedEvent;
import dev.ftb.mods.ftbteambases.util.*;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.data.TeamArgument;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static dev.ftb.mods.ftbteambases.command.CommandUtils.DIM_MISSING;
import static dev.ftb.mods.ftbteambases.command.CommandUtils.NOT_TEAM_NETHER;

/**
 * Keeps track of live and archived base instances. Base details (location etc.) are tracked by team UUID.
 */
public class BaseInstanceManager extends SavedData {
    private static final int MAX_REGION_X = 2000;  // allows for x coord = 2000 * 512 -> X = 1,024,000
    private static final String SAVE_NAME = FTBTeamBases.MOD_ID + "_bases";

    // serialization!  using xmap here, so we get mutable hashmaps in the live manager
    private static final Codec<Map<UUID,LiveBaseDetails>> LIVE_BASES_CODEC
            = Codec.unboundedMap(UUIDUtil.STRING_CODEC, LiveBaseDetails.CODEC).xmap(HashMap::new, Map::copyOf);
    private static final Codec<Map<ResourceLocation,RegionCoords>> GEN_POS_CODEC
            = Codec.unboundedMap(ResourceLocation.CODEC, RegionCoords.CODEC).xmap(HashMap::new, Map::copyOf);
    private static final Codec<Map<ResourceLocation,Integer>> Z_OFF_CODEC
            = Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).xmap(HashMap::new, Map::copyOf);
    private static final Codec<Map<String,ArchivedBaseDetails>> ARCHIVED_BASES_CODEC
            = Codec.unboundedMap(Codec.STRING, ArchivedBaseDetails.CODEC).xmap(HashMap::new, Map::copyOf);
    private static final Codec<Map<UUID,BlockPos>> NETHER_PORTAL_POS_CODEC
            = Codec.unboundedMap(UUIDUtil.STRING_CODEC, BlockPos.CODEC).xmap(HashMap::new, Map::copyOf);
    private static final Codec<Set<UUID>> PLAYER_ID_LIST_CODEC
            = UUIDUtil.CODEC.listOf().xmap(HashSet::new, List::copyOf);

    private static final Codec<BaseInstanceManager> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            LIVE_BASES_CODEC.fieldOf("bases").forGetter(mgr -> mgr.liveBases),
            GEN_POS_CODEC.fieldOf("gen_pos").forGetter(mgr -> mgr.storedGenPos),
            Z_OFF_CODEC.fieldOf("z_offset").forGetter(mgr -> mgr.storedZoffset),
            ARCHIVED_BASES_CODEC.fieldOf("archived_bases").forGetter(mgr -> mgr.archivedBases),
            Codec.INT.fieldOf("next_archive_id").forGetter(mgr -> mgr.nextArchiveId),
            Codec.BOOL.fieldOf("is_lobby_created").forGetter(mgr -> mgr.isLobbyCreated),
            BlockPos.CODEC.fieldOf("lobby_spawn_pos").forGetter(mgr -> mgr.lobbySpawnPos),
            NETHER_PORTAL_POS_CODEC.fieldOf("nether_portal_pos").forGetter(mgr -> mgr.playerNetherPortalLocs),
            PLAYER_ID_LIST_CODEC.fieldOf("known_players").forGetter(mgr -> mgr.knownPlayers),
            PLAYER_ID_LIST_CODEC.optionalFieldOf("orphaned_players", new HashSet<>()).forGetter(mgr -> mgr.orphanedPlayers)
    ).apply(inst, BaseInstanceManager::new));

    // maps team UUID to live base details
    private final Map<UUID, LiveBaseDetails> liveBases;
    // list of all archived bases (which haven't yet been purged)
    private final Map<String, ArchivedBaseDetails> archivedBases;
    // region relocation: maps dimension ID to next available generation pos (as far as we know!) for that dimension
    private final Map<ResourceLocation, RegionCoords> storedGenPos;
    // region relocation: maps dimension ID to Z-axis-increment
    private final Map<ResourceLocation, Integer> storedZoffset;
    // stores player nether portal return positions
    private final Map<UUID,BlockPos> playerNetherPortalLocs;
    // stores uuids of all players who have connected to this server
    private final Set<UUID> knownPlayers;
    // stores uuids of players whose team got disbanded while they were offline
    private final Set<UUID> orphanedPlayers;

    private boolean isLobbyCreated;
    private @Nullable BlockPos lobbySpawnPos;
    private int nextArchiveId;

    private BaseInstanceManager(Map<UUID, LiveBaseDetails> liveBases, Map<ResourceLocation, RegionCoords> genPos,
                                Map<ResourceLocation, Integer> zOffsets, Map<String, ArchivedBaseDetails> archivedBases,
                                int nextArchiveId, boolean isLobbyCreated, @Nullable BlockPos lobbySpawnPos,
                                Map<UUID,BlockPos> netherPortalPos, Set<UUID> knownPlayers, Set<UUID> orphanedPlayers) {
        this.liveBases = liveBases;
        this.storedGenPos = genPos;
        this.storedZoffset = zOffsets;
        this.archivedBases = archivedBases;
        this.nextArchiveId = nextArchiveId;
        this.isLobbyCreated = isLobbyCreated;
        this.lobbySpawnPos = lobbySpawnPos;
        this.playerNetherPortalLocs = netherPortalPos;
        this.knownPlayers = knownPlayers;
        this.orphanedPlayers = orphanedPlayers;
    }

    private static BaseInstanceManager createNew() {
        return new BaseInstanceManager(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                0, false, null, new HashMap<>(), new HashSet<>(), new HashSet<>());
    }

    public static BaseInstanceManager get() {
        return get(Objects.requireNonNull(ServerLifecycleHooks.getCurrentServer()));
    }

    public static BaseInstanceManager get(MinecraftServer server) {
        DimensionDataStorage dataStorage = Objects.requireNonNull(server.getLevel(Level.OVERWORLD)).getDataStorage();

        return dataStorage.computeIfAbsent(factory(), SAVE_NAME);
    }

    private static SavedData.Factory<BaseInstanceManager> factory() {
        return new SavedData.Factory<>(BaseInstanceManager::createNew, BaseInstanceManager::load, null);
    }

    /**
     * Get the position to generate the next team base in the given dimension, and update the saved pos.
     *
     * @param server the server instance
     * @param baseDefinition the base definition
     * @param dim            the dimension to generate in
     * @param size           the size of the base, in regions
     * @return start region coords to do the generation in
     */
    public RegionCoords nextGenerationPos(MinecraftServer server, BaseDefinition baseDefinition, ResourceLocation dim, XZ size) {
        if (baseDefinition.dimensionSettings().privateDimension()) {
            // simple case: only one base in the dimension
            return new RegionCoords(0, 0);
        } else {
            // find a place where no existing region files are present
            RegionCoords genPos;
            do {
                genPos = getNextRegionCoords(dim, size);
            } while (anyMCAFilesPresent(server, dim, genPos, size));
            return genPos;
        }
    }

    /**
     * Add a new live base to the manager
     *
     * @param ownerId UUID of the owning team
     * @param liveBaseDetails the details to add
     */
    public void addNewBase(UUID ownerId, LiveBaseDetails liveBaseDetails) {
        liveBases.put(ownerId, liveBaseDetails);

        setDirty();
    }

    @NotNull
    private RegionCoords getNextRegionCoords(ResourceLocation dimensionId, XZ baseSize) {
        RegionCoords genPos = storedGenPos.computeIfAbsent(dimensionId, k -> new RegionCoords(0, 0));
        int zOffset = Math.max(storedZoffset.computeIfAbsent(dimensionId, k -> baseSize.z()), baseSize.z());
        storedZoffset.put(dimensionId, zOffset);
        int separation = ServerConfig.BASE_SEPARATION.get();
        // move east on the X axis
        RegionCoords nextPos = genPos.offsetBy(baseSize.x() + separation, 0);
        if (nextPos.x() > MAX_REGION_X) {
            // return to X=0 and move south on the Z axis
            nextPos = new RegionCoords(0, nextPos.z() + zOffset + separation);
        }
        storedGenPos.put(dimensionId, nextPos);
        setDirty();
        return genPos;
    }

    private boolean anyMCAFilesPresent(MinecraftServer server, ResourceLocation dim, RegionCoords genPos, XZ size) {
        Path path = RegionFileUtil.getPathForDimension(server, ResourceKey.create(Registries.DIMENSION, dim), "region");

        for (int x = 0; x < size.x(); x++) {
            for (int z = 0; z < size.z(); z++) {
                if (Files.exists(path.resolve(genPos.offsetBy(x, z).filename()))) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean teleportToBaseSpawn(ServerPlayer player, UUID baseId) {
        LiveBaseDetails base = liveBases.get(baseId);
        if (base != null) {
            return DimensionUtils.teleport(player, base.dimension(), base.spawnPos(), player.getYRot());
//            ServerLevel level = player.getServer().getLevel(base.dimension());
//            if (level != null) {
//                Vec3 vec = Vec3.atCenterOf(base.spawnPos());
//                player.getServer().tell(new TickTask(player.getServer().getTickCount(), () ->
//                        player.teleportTo(level, vec.x, vec.y, vec.z, player.getYRot(), player.getXRot())
//                ));
//                return true;
//            }
        }
        return false;
    }

    public boolean teleportToNether(ServerPlayer player) throws CommandSyntaxException {
        if (!ServerConfig.TEAM_SPECIFIC_NETHER_ENTRY_POINT.get()) {
            throw NOT_TEAM_NETHER.create();
        }

        ServerLevel nether = player.getServer().getLevel(Level.NETHER);
        if (nether == null) {
            throw DIM_MISSING.create(Level.NETHER.location().toString());
        }

        DimensionTransition transition = NetherPortalPlacement.getTeamEntryPoint(nether, player, null);
        if (transition == null) {
            return false;
        }

        BlockPos pos = BlockPos.containing(transition.pos().x(), transition.pos().y(), transition.pos().z());

        ChunkPos chunkpos = new ChunkPos(pos);
        nether.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunkpos, 1, player.getId());
        player.stopRiding();
        if (player.isSleeping()) {
            player.stopSleepInBed(true, true);
        }
        player.teleportTo(nether, transition.pos().x(), transition.pos().y() + 0.01, transition.pos().z(), player.getYRot(), player.getXRot());
        player.setPortalCooldown();

        return true;
    }

    @Override
    public CompoundTag save(CompoundTag compoundTag, HolderLookup.Provider provider) {
        return Util.make(new CompoundTag(), tag ->
                tag.put("manager", CODEC.encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), this)
                        .resultOrPartial(err -> FTBTeamBases.LOGGER.error("failed to serialize base instance data: {}", err))
                        .orElse(new CompoundTag())));
    }

    private static BaseInstanceManager load(CompoundTag tag, HolderLookup.Provider provider) {
        BaseInstanceManager res = CODEC.parse(provider.createSerializationContext(NbtOps.INSTANCE), tag.getCompound("manager"))
                .resultOrPartial(err -> FTBTeamBases.LOGGER.error("failed to deserialize base instance data: {}", err))
                .orElse(BaseInstanceManager.createNew());

        PurgeManager.INSTANCE.cleanUpPurgedArchives(res);

        return res;
    }

    public Optional<LiveBaseDetails> getBaseForPlayer(ServerPlayer player) {
        return FTBTeamsAPI.api().getManager().getTeamForPlayer(player)
                .map(team -> liveBases.get(team.getTeamId()));
    }

    public Optional<LiveBaseDetails> getBaseForTeam(Team team) {
        return getBaseForTeamId(team.getTeamId());
    }

    public Optional<LiveBaseDetails> getBaseForTeamId(UUID id) {
        return Optional.ofNullable(liveBases.get(id));
    }

    public boolean teleportToLobby(ServerPlayer serverPlayer) {
        if (lobbySpawnPos != null) {
            ResourceKey<Level> destLevel = StartupConfig.lobbyDimension().orElse(Level.OVERWORLD);
            return DimensionUtils.teleport(serverPlayer, destLevel, lobbySpawnPos, ServerConfig.LOBBY_PLAYER_YAW.get().floatValue());
        }
        return false;
    }

    public void deleteStaleBase(UUID teamId) {
        LiveBaseDetails base = liveBases.remove(teamId);
        if (base != null) {
            String name = teamId.toString() + "-" + nextArchiveId;
            archivedBases.put(name, new ArchivedBaseDetails(name, base.extents(), base.dimension(), base.spawnPos(), teamId, Util.getEpochMillis()));
            nextArchiveId++;

            setDirty();
            FTBTeamBases.LOGGER.debug("stale team base for team {} has been archived", teamId);
        }
    }

    public void deleteAndArchive(MinecraftServer server, Team team) {
        LiveBaseDetails base = liveBases.remove(team.getTeamId());

        if (base != null) {
            String name = server.getProfileCache().get(team.getOwner()).map(GameProfile::getName).orElse("unknown");
            name += "-" + nextArchiveId;
            archivedBases.put(name, new ArchivedBaseDetails(name, base.extents(), base.dimension(), base.spawnPos(), team.getOwner(), Util.getEpochMillis()));
            nextArchiveId++;

            // the team should be empty of players at this point, but just in case...
            team.getMembers().forEach(id -> onPlayerLeaveTeam(server.getPlayerList().getPlayer(id), id));

            setDirty();
            BaseArchivedEvent.ARCHIVED.invoker().deleted(this, team);

            FTBTeamBases.LOGGER.debug("team base for team {} has been archived", team.getId());
        }
    }

    public void onPlayerLeaveTeam(@Nullable ServerPlayer player, UUID playerId) {
        if (player != null) {
            if (ServerConfig.CLEAR_PLAYER_INV_ON_LEAVE.get()) {
                MiscUtil.clearPlayerInventory(player);
            }
            teleportToLobby(player);
            FTBTeamBases.LOGGER.debug("player {} left team, sending back to lobby", playerId);
        } else {
            orphanedPlayers.add(playerId);
            setDirty();
            FTBTeamBases.LOGGER.debug("player {} removed from team, but is offline - marked as orphaned", playerId);
        }
    }

    public void checkForOrphanedPlayer(@NotNull ServerPlayer player) {
        if (orphanedPlayers.contains(player.getUUID())) {
            player.displayClientMessage(Component.translatable("ftbteambases.message.team_was_disbanded").withStyle(ChatFormatting.GOLD), false);
            onPlayerLeaveTeam(player, player.getUUID());
            orphanedPlayers.remove(player.getUUID());
            setDirty();
        }
    }

    public Map<UUID,LiveBaseDetails> allLiveBases() {
        return Collections.unmodifiableMap(liveBases);
    }

    public Collection<ArchivedBaseDetails> getArchivedBases() {
        return Collections.unmodifiableCollection(archivedBases.values());
    }

    public Collection<ArchivedBaseDetails> getArchivedBasesFor(UUID owner) {
        return archivedBases.values().stream().filter(b -> b.ownerId().equals(owner)).toList();
    }

    public Optional<ArchivedBaseDetails> getArchivedBase(String archiveId) {
        return Optional.ofNullable(archivedBases.get(archiveId));
    }

    public void removeArchivedBase(String archiveId) {
        if (archivedBases.remove(archiveId) != null) {
            setDirty();
        }
    }

    public void unarchiveBase(MinecraftServer server, ArchivedBaseDetails base) throws CommandSyntaxException {
        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayerID(base.ownerId())
                .orElseThrow(() -> TeamArgument.TEAM_NOT_FOUND.create(base.ownerId()));

        if (team.isPlayerTeam()) {
            Team newParty = team.createParty("", null);

            archivedBases.remove(base.archiveId());
            addNewBase(newParty.getId(), base.makeLiveBaseDetails());
            BaseInstanceManager.get(server).forceSave(server);

            ServerPlayer player = server.getPlayerList().getPlayer(base.ownerId());
            if (player != null) {
                BaseInstanceManager.get(server).teleportToBaseSpawn(player, newParty.getId());
                player.displayClientMessage(Component.translatable("ftbteambases.message.restored_yours"), false);
            }

            // in case it was scheduled for purging
            PurgeManager.INSTANCE.removePending(List.of(base));
        } else {
            String ownerName = server.getProfileCache().get(base.ownerId())
                    .map(GameProfile::getName)
                    .orElse(base.ownerId().toString());
            throw CommandUtils.PLAYER_IN_PARTY.create(ownerName);
        }
    }

    public void teleportToArchivedBase(ServerPlayer player, String archiveName) {
        ArchivedBaseDetails base = archivedBases.get(archiveName);
        if (base != null) {
            ServerLevel level = player.getServer().getLevel(base.dimension());
            if (level != null) {
                Vec3 vec = Vec3.atCenterOf(base.spawnPos());
                player.getServer().executeIfPossible(() ->
                        player.teleportTo(level, vec.x, vec.y, vec.z, player.getYRot(), player.getXRot())
                );
            }
        }
    }

    public boolean isLobbyCreated() {
        return isLobbyCreated;
    }

    public void setLobbyCreated(boolean lobbyCreated) {
        isLobbyCreated = lobbyCreated;
        setDirty();
    }

    public BlockPos getLobbySpawnPos() {
        if (lobbySpawnPos == null) {
            FTBTeamBases.LOGGER.warn("lobby spawn pos uninitialized in base instance manager? defaulting to (0,0,0)");
            return BlockPos.ZERO;
        }
        return lobbySpawnPos;
    }

    public void setLobbySpawnPos(BlockPos lobbySpawnPos, boolean fromCommand) {
        if (this.lobbySpawnPos != null && !fromCommand) {
            FTBTeamBases.LOGGER.error("ignored attempt to override lobby spawn pos (current {}, attempted {}})",
                    MiscUtil.blockPosStr(this.lobbySpawnPos), MiscUtil.blockPosStr(lobbySpawnPos));
        }
        this.lobbySpawnPos = lobbySpawnPos;
        setDirty();
    }

    public void setPlayerNetherPortalLoc(ServerPlayer player, BlockPos portalPos) {
        if (portalPos == null) {
            if (playerNetherPortalLocs.remove(player.getUUID()) != null) {
                setDirty();
            }
        } else {
            playerNetherPortalLocs.put(player.getUUID(), portalPos);
            setDirty();
        }
    }

    public Optional<BlockPos> getPlayerNetherPortalLoc(ServerPlayer player) {
        return Optional.ofNullable(playerNetherPortalLocs.get(player.getUUID()));
    }

    public void addKnownPlayer(ServerPlayer player) {
        if (knownPlayers.add(player.getUUID())) {
            setDirty();
        }
    }

    public boolean isPlayerKnown(ServerPlayer player) {
        return knownPlayers.contains(player.getUUID());
    }

    public void forceSave(MinecraftServer server) {
        // this is a kludge, but we need to be 100% sure that base instance data gets saved when a base is created
        // losing this info due to a server crash will cause serious subsequent problems for base sanity...
        ServerLevel overworld = Objects.requireNonNull(server.getLevel(Level.OVERWORLD));
        overworld.getChunkSource().getDataStorage().save();
        FTBTeamBases.LOGGER.info("force-saved team bases data to data/{}.dat", SAVE_NAME);
    }
}
