package com.worldgenv2.studio.shell;

/** Colour coding for biome categories, shared by the explorer, tabs and pools. */
final class Cat {
    private Cat() {}

    static int color(String category) {
        if (category == null) return 0xFF8A90A0;
        return switch (category.toLowerCase()) {
            case "plains" -> 0xFF8BC34A;
            case "forest" -> 0xFF3E9B4F;
            case "taiga" -> 0xFF5E9C84;
            case "desert" -> 0xFFE3B341;
            case "mountain" -> 0xFFB0BEC5;
            case "tundra" -> 0xFF9FD8F0;
            case "swamp" -> 0xFF6B8E5A;
            case "ocean" -> 0xFF3FA3FF;
            case "river" -> 0xFF3FC5FF;
            case "cave" -> 0xFFB37BE8;
            default -> 0xFFE89A3C;
        };
    }
}
