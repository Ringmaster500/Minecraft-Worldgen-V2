package com.worldgenv2.prefab;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.mask.BlockMask;
import com.worldgenv2.mask.MaskedVoxelSpace;
import com.worldgenv2.prefab.Prefab.AdaptiveFoundation;
import com.worldgenv2.prefab.Prefab.PaletteEntry;
import com.worldgenv2.prefab.Prefab.PrefabSocket;
import com.worldgenv2.prefab.Prefab.Vec3i;
import com.worldgenv2.prefab.Prefab.VoxelEntry;
import com.worldgenv2.props.Prop;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Prop implementation that stamps a native {@link Prefab} into a {@link VoxelSpace}.
 * Executes:
 * 1. Optional bounding-box collision validation
 * 2. Downward Adaptive Foundation Plinth ray-marching over uneven terrain
 * 3. Sparse palette voxel stamping
 * 4. Recursive modular socket resolution for multi-room structures and dungeons
 * 5. Optional {@link BlockMask} filtering
 */
public final class PrefabProp<T> implements Prop<T> {

    private final Prefab prefab;
    private final Function<String, T> blockResolver;
    private final boolean checkCollisions;
    private final boolean useFormattedBlockState;
    private final List<Prefab> socketPool;
    private final int maxSocketDepth;
    private final BlockMask<T> mask;

    public PrefabProp(Prefab prefab, Function<String, T> blockResolver) {
        this(prefab, blockResolver, false, false, List.of(), 0, null);
    }

    public PrefabProp(
        Prefab prefab,
        Function<String, T> blockResolver,
        boolean checkCollisions,
        boolean useFormattedBlockState,
        List<Prefab> socketPool,
        int maxSocketDepth
    ) {
        this(prefab, blockResolver, checkCollisions, useFormattedBlockState, socketPool, maxSocketDepth, null);
    }

    public PrefabProp(
        Prefab prefab,
        Function<String, T> blockResolver,
        boolean checkCollisions,
        boolean useFormattedBlockState,
        List<Prefab> socketPool,
        int maxSocketDepth,
        BlockMask<T> mask
    ) {
        this.prefab = Objects.requireNonNull(prefab, "prefab");
        this.blockResolver = Objects.requireNonNull(blockResolver, "blockResolver");
        this.checkCollisions = checkCollisions;
        this.useFormattedBlockState = useFormattedBlockState;
        this.socketPool = socketPool == null ? List.of() : List.copyOf(socketPool);
        this.maxSocketDepth = Math.max(0, maxSocketDepth);
        this.mask = mask;
    }

    public Prefab prefab() {
        return prefab;
    }

    public BlockMask<T> mask() {
        return mask;
    }

    @Override
    public boolean generate(Context<T> context) {
        VoxelSpace<T> space = mask != null ? new MaskedVoxelSpace<>(context.voxelSpace(), mask) : context.voxelSpace();
        return stampPrefab(
            this.prefab,
            context.worldX(),
            context.worldY(),
            context.worldZ(),
            space,
            context.seed(),
            this.maxSocketDepth
        );
    }

    private boolean stampPrefab(
        Prefab targetPrefab,
        int worldX,
        int worldY,
        int worldZ,
        VoxelSpace<T> space,
        long seed,
        int remainingSocketDepth
    ) {
        Vec3i anchor = targetPrefab.anchor();
        int baseX = worldX - anchor.x();
        int baseY = worldY - anchor.y();
        int baseZ = worldZ - anchor.z();

        if (baseY < space.getMinY() || baseY + targetPrefab.size().y() > space.getMaxY()) {
            return false;
        }

        // 1. Optional collision check for structure volume above floor (y > 0)
        if (checkCollisions) {
            for (VoxelEntry voxel : targetPrefab.blocks()) {
                if (voxel.y() > 0) {
                    int wx = baseX + voxel.x();
                    int wy = baseY + voxel.y();
                    int wz = baseZ + voxel.z();
                    if (!space.isAir(wx, wy, wz)) {
                        return false;
                    }
                }
            }
        }

        // 2. Adaptive Foundation Plinth Ray-Marching
        for (AdaptiveFoundation foundation : targetPrefab.adaptiveFoundations()) {
            T fillBlock = blockResolver.apply(foundation.fillMaterial());
            if (fillBlock == null) {
                continue;
            }
            int minFoundationY = Math.max(space.getMinY(), baseY - foundation.maxDepth());
            for (int fx = foundation.minX(); fx <= foundation.maxX(); fx++) {
                int wx = baseX + fx;
                for (int fz = foundation.minZ(); fz <= foundation.maxZ(); fz++) {
                    int wz = baseZ + fz;
                    for (int y = baseY - 1; y >= minFoundationY; y--) {
                        if (space.isAir(wx, y, wz)) {
                            space.setBlock(wx, y, wz, fillBlock);
                        } else {
                            break; // Reached solid native terrain
                        }
                    }
                }
            }
        }

        // 3. Stamp Prefab Voxel Palette
        for (VoxelEntry voxel : targetPrefab.blocks()) {
            PaletteEntry paletteEntry = targetPrefab.resolvePalette(voxel.paletteIndex());
            if (paletteEntry == null) {
                continue;
            }
            String blockKey = useFormattedBlockState
                ? paletteEntry.formattedBlockState()
                : paletteEntry.blockId();
            T resolvedBlock = blockResolver.apply(blockKey);
            if (resolvedBlock != null) {
                space.setBlock(baseX + voxel.x(), baseY + voxel.y(), baseZ + voxel.z(), resolvedBlock);
            }
        }

        // 4. Modular Socket Expansion
        if (remainingSocketDepth > 0 && !socketPool.isEmpty() && !targetPrefab.sockets().isEmpty()) {
            for (PrefabSocket parentSocket : targetPrefab.sockets()) {
                int socketWorldX = baseX + parentSocket.position().x() + parentSocket.facing().stepX();
                int socketWorldY = baseY + parentSocket.position().y();
                int socketWorldZ = baseZ + parentSocket.position().z() + parentSocket.facing().stepZ();

                int poolSize = socketPool.size();
                int startIdx = (int) Math.floorMod(seed ^ parentSocket.name().hashCode(), poolSize);
                boolean connected = false;
                for (int i = 0; i < poolSize && !connected; i++) {
                    Prefab candidate = socketPool.get((startIdx + i) % poolSize);
                    for (PrefabSocket childSocket : candidate.sockets()) {
                        if (parentSocket.isCompatibleWith(childSocket)) {
                            int childAnchorWorldX = socketWorldX + (candidate.anchor().x() - childSocket.position().x());
                            int childAnchorWorldY = socketWorldY + (candidate.anchor().y() - childSocket.position().y());
                            int childAnchorWorldZ = socketWorldZ + (candidate.anchor().z() - childSocket.position().z());

                            stampPrefab(
                                candidate,
                                childAnchorWorldX,
                                childAnchorWorldY,
                                childAnchorWorldZ,
                                space,
                                seed * 31L + i,
                                remainingSocketDepth - 1
                            );
                            connected = true;
                            break;
                        }
                    }
                }
            }
        }

        return true;
    }
}
