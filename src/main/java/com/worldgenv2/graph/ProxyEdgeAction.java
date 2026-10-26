package com.worldgenv2.graph;

import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.vector.Vec3d;
import com.worldgenv2.vector.VectorProvider;

import java.util.Objects;

/**
 * Inserts a proxy node positioned relative to a host node along an edge.
 */
public final class ProxyEdgeAction implements EdgeAction {

    private final NodeSelector hostSelector;
    private final VectorProvider normal;
    private final DensityFunction angleToNormal;
    private final DensityFunction spinAngle;
    private final DensityFunction proxyDistance;
    private final double maxProxyDistance;
    private final ContentSupplier proxyContent;
    private final long actionSeed;

    public ProxyEdgeAction(
        NodeSelector hostSelector,
        VectorProvider normal,
        DensityFunction angleToNormal,
        DensityFunction spinAngle,
        DensityFunction proxyDistance,
        double maxProxyDistance,
        ContentSupplier proxyContent,
        long actionSeed
    ) {
        this.hostSelector = Objects.requireNonNull(hostSelector, "hostSelector");
        this.normal = normal != null ? normal : (x, y, z) -> Vec3d.UNIT_Y;
        this.angleToNormal = angleToNormal != null ? angleToNormal : (x, y, z) -> 0.0f;
        this.spinAngle = spinAngle != null ? spinAngle : (x, y, z) -> 0.0f;
        this.proxyDistance = proxyDistance != null ? proxyDistance : (x, y, z) -> 10.0f;
        this.maxProxyDistance = Math.max(0.0, maxProxyDistance);
        this.proxyContent = proxyContent != null ? proxyContent : (n, s) -> GraphContent.EMPTY;
        this.actionSeed = actionSeed;
    }

    @Override
    public void apply(GraphEdge edge, ProceduralGraph graph, long seed) {
        GraphNode a = graph.getNode(edge.nodeA());
        GraphNode b = graph.getNode(edge.nodeB());
        if (a == null || b == null) {
            return;
        }

        boolean selA = hostSelector.select(a, graph);
        boolean selB = hostSelector.select(b, graph);

        if (!selA && !selB) {
            return;
        }

        graph.removeEdge(a.getId(), b.getId());

        if (selA && !selB) {
            GraphNode proxy = createProxy(a, b, graph, seed);
            graph.addEdge(a.getId(), proxy.getId());
            graph.addEdge(proxy.getId(), b.getId());
        } else if (!selA && selB) {
            GraphNode proxy = createProxy(b, a, graph, seed);
            graph.addEdge(b.getId(), proxy.getId());
            graph.addEdge(proxy.getId(), a.getId());
        } else {
            // Both selected: insert proxy for each
            GraphNode proxyA = createProxy(a, b, graph, seed);
            GraphNode proxyB = createProxy(b, a, graph, seed);
            graph.addEdge(a.getId(), proxyA.getId());
            graph.addEdge(proxyA.getId(), proxyB.getId());
            graph.addEdge(proxyB.getId(), b.getId());
        }
    }

    private GraphNode createProxy(GraphNode host, GraphNode target, ProceduralGraph graph, long seed) {
        Vec3d hPos = host.getPosition();
        Vec3d n = normal.getVector(hPos.x(), hPos.y(), hPos.z()).normalize();
        if (n.lengthSquared() < 1e-6) {
            n = Vec3d.UNIT_Y;
        }

        double dist = proxyDistance.evaluate(hPos.x(), hPos.y(), hPos.z());
        if (maxProxyDistance > 0.0) {
            dist = Math.min(dist, maxProxyDistance);
        }
        dist = Math.max(0.0, dist);

        Vec3d toTarget = target.getPosition().subtract(hPos).normalize();
        Vec3d dir = toTarget.scale(dist);
        Vec3d proxyPos = hPos.add(dir);

        GraphNode proxy = graph.addNode(proxyPos, GraphContent.EMPTY);
        proxy.setContent(proxyContent.supply(proxy, seed));
        return proxy;
    }

    public NodeSelector getHostSelector() {
        return hostSelector;
    }

    public VectorProvider getNormal() {
        return normal;
    }

    public double getMaxProxyDistance() {
        return maxProxyDistance;
    }

    public ContentSupplier getProxyContent() {
        return proxyContent;
    }
}
