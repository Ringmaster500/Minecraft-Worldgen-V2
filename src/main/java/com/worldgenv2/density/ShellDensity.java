package com.worldgenv2.density;

import com.worldgenv2.curve.Curve;
import com.worldgenv2.vector.Vec3d;

/**
 * Volumetric Shell density node.
 * Modulates density based on distance from the origin and angle relative to an axis.
 */
public final class ShellDensity implements DensityFunction {

    private final Vec3d axis;
    private final boolean mirror;
    private final Curve angleCurve;
    private final Curve distanceCurve;

    public ShellDensity(Vec3d axis, boolean mirror, Curve angleCurve, Curve distanceCurve) {
        this.axis = (axis != null ? axis : Vec3d.UNIT_Y).normalize();
        this.mirror = mirror;
        this.angleCurve = angleCurve;
        this.distanceCurve = distanceCurve;
    }

    public ShellDensity(float innerRadius, float outerRadius) {
        this(Vec3d.UNIT_Y, false, null, dist -> {
            if (dist < innerRadius || dist > outerRadius) {
                return -1.0;
            }
            double mid = 0.5 * (innerRadius + outerRadius);
            double halfThick = 0.5 * (outerRadius - innerRadius);
            return 1.0 - Math.abs(dist - mid) / halfThick;
        });
    }

    @Override
    public float compute(float x, float y, float z) {
        double dist = Math.sqrt(x * x + y * y + z * z);
        double dVal = distanceCurve != null ? distanceCurve.evaluate(dist) : 1.0;

        if (angleCurve == null || dist <= 1e-12) {
            return (float) dVal;
        }

        double dot = (x * axis.x() + y * axis.y() + z * axis.z()) / dist;
        if (mirror) {
            dot = Math.abs(dot);
        }
        double angleDeg = Math.toDegrees(Math.acos(Math.clamp(dot, -1.0, 1.0)));
        double aVal = angleCurve.evaluate(angleDeg);

        return (float) (dVal * aVal);
    }

    public Vec3d getAxis() {
        return axis;
    }

    public boolean isMirror() {
        return mirror;
    }

    public Curve getAngleCurve() {
        return angleCurve;
    }

    public Curve getDistanceCurve() {
        return distanceCurve;
    }
}
