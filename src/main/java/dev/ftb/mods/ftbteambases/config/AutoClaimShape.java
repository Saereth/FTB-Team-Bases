package dev.ftb.mods.ftbteambases.config;

import dev.ftb.mods.ftblibrary.config.NameMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.function.Consumer;

public enum AutoClaimShape {
    SQUARE("square"),
    CIRCLE("circle");

    public static final NameMap<AutoClaimShape> NAME_MAP = NameMap.of(SQUARE, values()).id(AutoClaimShape::getId).create();

    private final String shape;

    AutoClaimShape(String id) {
        this.shape = id;
    }

    public String getId() {
        return shape;
    }

    public void forEachChunk(ChunkPos origin, int radius, Consumer<ChunkPos> consumer) {
        BlockPos pos0 = origin.getMiddleBlockPosition(0);
        int blockRadiusSq = (radius << 4) * (radius << 4);
        switch (radius) {
            case 0 -> {
            }
            case 1 -> consumer.accept(origin);
            default -> {
                int r = radius - 1;
                for (int cx = origin.x - r; cx <= origin.x + r; cx++) {
                    for (int cz = origin.z - r; cz <= origin.z + r; cz++) {
                        ChunkPos cp = new ChunkPos(cx, cz);
                        if (this == SQUARE || cp.getMiddleBlockPosition(0).distSqr(pos0) < blockRadiusSq) {
                            consumer.accept(cp);
                        }
                    }
                }
            }
        }
        ;
    }
}
