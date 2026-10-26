package com.worldgenv2.vector;

/**
 * Immutable 3D vector record representing continuous spatial coordinates and vectors.
 */
public record Vec3d(double x, double y, double z) {

    public static final Vec3d ZERO = new Vec3d(0.0, 0.0, 0.0);
    public static final Vec3d UNIT_X = new Vec3d(1.0, 0.0, 0.0);
    public static final Vec3d UNIT_Y = new Vec3d(0.0, 1.0, 0.0);
    public static final Vec3d UNIT_Z = new Vec3d(0.0, 0.0, 1.0);

    public Vec3d add(Vec3d other) {
        return new Vec3d(this.x + other.x, this.y + other.y, this.z + other.z);
    }

    public Vec3d add(double dx, double dy, double dz) {
        return new Vec3d(this.x + dx, this.y + dy, this.z + dz);
    }

    public Vec3d subtract(Vec3d other) {
        return new Vec3d(this.x - other.x, this.y - other.y, this.z - other.z);
    }

    public Vec3d multiply(Vec3d other) {
        return new Vec3d(this.x * other.x, this.y * other.y, this.z * other.z);
    }

    public Vec3d scale(double scalar) {
        return new Vec3d(this.x * scalar, this.y * scalar, this.z * scalar);
    }

    public double dot(Vec3d other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }

    public Vec3d cross(Vec3d other) {
        return new Vec3d(
            this.y * other.z - this.z * other.y,
            this.z * other.x - this.x * other.z,
            this.x * other.y - this.y * other.x
        );
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public double distanceSquared(Vec3d other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distance(Vec3d other) {
        return Math.sqrt(distanceSquared(other));
    }

    public Vec3d normalize() {
        double len = length();
        if (len <= 1e-12) {
            return ZERO;
        }
        double inv = 1.0 / len;
        return new Vec3d(x * inv, y * inv, z * inv);
    }

    public Vec3d withX(double newX) {
        return new Vec3d(newX, this.y, this.z);
    }

    public Vec3d withY(double newY) {
        return new Vec3d(this.x, newY, this.z);
    }

    public Vec3d withZ(double newZ) {
        return new Vec3d(this.x, this.y, newZ);
    }
}
