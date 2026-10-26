package com.worldgenv2.graph;

import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.material.MaterialProvider;
import com.worldgenv2.positions.PositionsProvider;
import com.worldgenv2.props.Prop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Procedural content carried by a graph node, partitioned into named layers across four content types:
 * Density, MaterialProvider, PropDistribution, and Positions.
 */
public final class GraphContent {

    public record DensityEntry(DensityFunction density, double range, String contentLayer) {
        public DensityEntry {
            Objects.requireNonNull(density, "density");
            if (contentLayer == null) contentLayer = "";
            range = Math.max(0.0, range);
        }
    }

    public record MaterialEntry(MaterialProvider provider, double range, String contentLayer) {
        public MaterialEntry {
            Objects.requireNonNull(provider, "provider");
            if (contentLayer == null) contentLayer = "";
            range = Math.max(0.0, range);
        }
    }

    public record PropDistributionEntry(Prop prop, double range, String contentLayer) {
        public PropDistributionEntry {
            Objects.requireNonNull(prop, "prop");
            if (contentLayer == null) contentLayer = "";
            range = Math.max(0.0, range);
        }
    }

    public record PositionsEntry(PositionsProvider positions, double range, String contentLayer) {
        public PositionsEntry {
            Objects.requireNonNull(positions, "positions");
            if (contentLayer == null) contentLayer = "";
            range = Math.max(0.0, range);
        }
    }

    public static final GraphContent EMPTY = new Builder().build();

    private final List<DensityEntry> densityContent;
    private final List<MaterialEntry> materialContent;
    private final List<PropDistributionEntry> propDistributionContent;
    private final List<PositionsEntry> positionsContent;
    private final Set<String> contentTags;

    private GraphContent(Builder builder) {
        this.densityContent = List.copyOf(builder.densityContent);
        this.materialContent = List.copyOf(builder.materialContent);
        this.propDistributionContent = List.copyOf(builder.propDistributionContent);
        this.positionsContent = List.copyOf(builder.positionsContent);
        this.contentTags = Set.copyOf(builder.contentTags);
    }

    public List<DensityEntry> getDensityContent() {
        return densityContent;
    }

    public List<MaterialEntry> getMaterialContent() {
        return materialContent;
    }

    public List<PropDistributionEntry> getPropDistributionContent() {
        return propDistributionContent;
    }

    public List<PositionsEntry> getPositionsContent() {
        return positionsContent;
    }

    public Set<String> getContentTags() {
        return contentTags;
    }

    public boolean hasTag(String tag) {
        return contentTags.contains(tag);
    }

    public Optional<DensityEntry> getFirstDensity(String layer) {
        String l = layer == null ? "" : layer;
        for (DensityEntry entry : densityContent) {
            if (entry.contentLayer().equals(l)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public Optional<MaterialEntry> getFirstMaterial(String layer) {
        String l = layer == null ? "" : layer;
        for (MaterialEntry entry : materialContent) {
            if (entry.contentLayer().equals(l)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public Optional<PropDistributionEntry> getFirstProp(String layer) {
        String l = layer == null ? "" : layer;
        for (PropDistributionEntry entry : propDistributionContent) {
            if (entry.contentLayer().equals(l)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public Optional<PositionsEntry> getFirstPositions(String layer) {
        String l = layer == null ? "" : layer;
        for (PositionsEntry entry : positionsContent) {
            if (entry.contentLayer().equals(l)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public static final class Builder {
        private final List<DensityEntry> densityContent = new ArrayList<>();
        private final List<MaterialEntry> materialContent = new ArrayList<>();
        private final List<PropDistributionEntry> propDistributionContent = new ArrayList<>();
        private final List<PositionsEntry> positionsContent = new ArrayList<>();
        private final Set<String> contentTags = new LinkedHashSet<>();

        public Builder addDensity(DensityFunction density, double range, String layer) {
            densityContent.add(new DensityEntry(density, range, layer));
            return this;
        }

        public Builder addMaterial(MaterialProvider provider, double range, String layer) {
            materialContent.add(new MaterialEntry(provider, range, layer));
            return this;
        }

        public Builder addProp(Prop prop, double range, String layer) {
            propDistributionContent.add(new PropDistributionEntry(prop, range, layer));
            return this;
        }

        public Builder addPositions(PositionsProvider positions, double range, String layer) {
            positionsContent.add(new PositionsEntry(positions, range, layer));
            return this;
        }

        public Builder addTag(String tag) {
            if (tag != null) contentTags.add(tag);
            return this;
        }

        public Builder addTags(Iterable<String> tags) {
            if (tags != null) {
                for (String t : tags) {
                    if (t != null) contentTags.add(t);
                }
            }
            return this;
        }

        public GraphContent build() {
            return new GraphContent(this);
        }
    }
}
