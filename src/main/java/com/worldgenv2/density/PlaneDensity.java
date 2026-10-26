package com.worldgenv2.density;

import com.worldgenv2.curve.Curve;
import com.worldgenv2.vector.Vec3d;
import java.util.Objects;

/**
 * Volumetric Plane density node.
 * Evaluates the continuous signed distance to a geometric plane defined by a unit normal vector and offset.
 */
public final class PlaneDensity implements DensityFunction {

    private final Vec3d normal;
    private final float offset;
    private final Curve curve;

    public PlaneDensity(Vec3d normal, float offset) {
        this(normal, offset, null);
    }

    public PlaneDensity(Vec3d normal, float offset, Curve curve) {
        Objects.requireNonNull(normal, "normal");
        this.normal = normal.normalize();
        this.offset = offset;
        this.curve = curve;
    }

    @Override
    public float compute(float x, float y, float z) {
        double dist = (x * normal.x() + y * normal.y() + z * normal.z()) - offset;
        if (curve != null) {
            return (float) curve.evaluate(dist);
        }
        return (float) -dist;
    }

    public Vec3d getNormal() {
        return normal;
    }

    public float getOffset() {
        return offset;
    }

    public Curve getCurve() {
        return curve;
    }
}
