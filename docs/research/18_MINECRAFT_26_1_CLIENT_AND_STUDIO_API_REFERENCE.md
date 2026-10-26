# Minecraft 26.1 Client & WorldGen Studio API Quick Reference

This document serves as a persistent, high-density engineering reference for Minecraft 26.1 deobfuscated client APIs, NeoForge 26.1 events, and GUI rendering internals discovered during the development of `worldgenv2-studio`.

---

## 1. GUI Rendering Pipeline (`GuiGraphicsExtractor`)

In Minecraft 26.1, Mojang overhauled client rendering to extract render state asynchronously.

### 1.1 Screen Method Signatures
Legacy `render(GuiGraphics, int, int, float)` has been replaced by:
```java
@Override
public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
    super.extractRenderState(graphics, mouseX, mouseY, a);
    // Render custom widgets, tabs, cards, wires, canvas
}
```

Background rendering methods on `Screen`:
- `this.extractMenuBackground(graphics)`: Renders tiled dirt / in-world dark slate background.
- `this.extractMenuBackground(graphics, x, y, width, height)`: Renders framed background section.
- `this.extractTransparentBackground(graphics)`: Translucent in-game background.

### 1.2 `GuiGraphicsExtractor` Drawing Methods
All 2D primitives, text, items, and textures are submitted via `GuiGraphicsExtractor`:

| Method | Signature | Description |
| :--- | :--- | :--- |
| **`fill`** | `fill(int x0, int y0, int x1, int y1, int color)` | Solid rectangle with ARGB `0xAARRGGBB` |
| **`fillGradient`** | `fillGradient(int x0, int y0, int x1, int y1, int col1, int col2)` | Vertical linear gradient |
| **`outline`** | `outline(int x, int y, int width, int height, int color)` | 1-pixel border outline |
| **`horizontalLine`** | `horizontalLine(int x0, int x1, int y, int color)` | Horizontal 1px line |
| **`verticalLine`** | `verticalLine(int x, int y0, int y1, int color)` | Vertical 1px line |
| **`enableScissor`** | `enableScissor(int x0, int y0, int x1, int y1)` | Clips drawing to a rectangle box |
| **`disableScissor`** | `disableScissor()` | Disables active clipping box |
| **`text`** | `text(Font font, String/Component text, int x, int y, int color, boolean dropShadow)` | Text rendering |
| **`centeredText`** | `centeredText(Font font, String/Component text, int x, int y, int color)` | Horizontally centered text |
| **`textWithWordWrap`** | `textWithWordWrap(Font font, FormattedText text, int x, int y, int width, int color)` | Multi-line wrapped text |
| **`fakeItem`** | `fakeItem(ItemStack stack, int x, int y)` | Renders 3D/2D block/item model |
| **`itemDecorations`**| `itemDecorations(Font font, ItemStack stack, int x, int y)` | Durability bar, stack count |
| **`setTooltipForNextFrame`** | `setTooltipForNextFrame(Component/List, int x, int y)` | Queues tooltip overlay |
| **`blit`** | `blit(Identifier location, int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1)` | Textured quad |

---

## 2. Input Event Handlers (`GuiEventListener`)

Minecraft 26.1 wraps mouse and keyboard inputs in dedicated record types:

### 2.1 Mouse Events
```java
@Override
public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    int button = event.button(); // 0 = Left, 1 = Right, 2 = Middle
    double mouseX = event.x();
    double mouseY = event.y();
    if (doubleClick && button == 0) {
        // Native double-click detection! Double-click anywhere to add nodes.
        return true;
    }
    return super.mouseClicked(event, doubleClick);
}

@Override
public boolean mouseReleased(MouseButtonEvent event) {
    return super.mouseReleased(event);
}

@Override
public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
    // Canvas panning: dx and dy give cursor delta
    return super.mouseDragged(event, dx, dy);
}

@Override
public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
    // Canvas zooming / list scrolling: scrollY gives wheel direction
    return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
}
```

### 2.2 Keyboard Events
```java
@Override
public boolean keyPressed(KeyEvent event) {
    int key = event.key(); // e.g. GLFW.GLFW_KEY_ESCAPE, InputConstants.KEY_ESCAPE
    int modifiers = event.modifiers(); // Shift, Ctrl, Alt bitmask
    return super.keyPressed(event);
}

@Override
public boolean charTyped(CharacterEvent event) {
    char c = event.character(); // Typed character for text search fields
    return super.charTyped(event);
}
```

---

## 3. Resource & Registry Naming Changes

- **Identifier**: `net.minecraft.resources.Identifier` (replaces legacy `ResourceLocation`).
  - Example: `Identifier.fromNamespaceAndPath("minecraft", "stone")` or `Identifier.parse("worldgenv2:studio")`.
- **Block & Item Lookup**:
  - `BuiltInRegistries.BLOCK.getValue(Identifier)` returns `@Nullable Block` directly (whereas `.get(Identifier)` returns `Optional<Holder.Reference<Block>>`).
  - `BuiltInRegistries.ITEM.getValue(Identifier)` returns `@Nullable Item`.
  - `block.asItem().getDefaultInstance()` produces `ItemStack` for item rendering.
- **ChunkPos is a Record**:
  - `pos.x()` and `pos.z()` accessors (not `.x` or `.z` fields).
  - Convert from BlockPos: `new ChunkPos(blockPos.getX() >> 4, blockPos.getZ() >> 4)`.
- **Event Registration**:
  - Mod event bus: `modEventBus.addListener(WorldGenStudioClient::onRegisterKeyMappings);`
  - Forge event bus: `NeoForge.EVENT_BUS.addListener(WorldGenStudioClient::onClientTick);` etc.

---

## 4. NeoForge 26.1 Client Event Bus

### 4.1 Key Mappings
Fired on the **Mod Event Bus** (`FMLJavaModLoadingContext` / Mod bus):
```java
public void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
    KeyMapping studioKey = new KeyMapping(
        "key.worldgenv2.open_studio",
        InputConstants.KEY_G,
        KeyMapping.Category.MISC
    );
    event.register(studioKey);
}
```

### 4.2 Client Tick
Fired on the **Forge Event Bus** (`NeoForge.EVENT_BUS`):
```java
public void onClientTick(ClientTickEvent.Post event) {
    while (studioKey.consumeClick()) {
        Minecraft.getInstance().setScreen(new WorldGenStudioScreen());
    }
}
```

### 4.3 Client Commands
Fired on the **Forge Event Bus**:
```java
public void onRegisterClientCommands(RegisterClientCommandsEvent event) {
    event.getDispatcher().register(
        Commands.literal("studio")
            .executes(ctx -> {
                Minecraft.getInstance().schedule(() -> {
                    Minecraft.getInstance().setScreen(new WorldGenStudioScreen());
                });
                return 1;
            })
    );
}
```

### 4.4 HUD Overlays & Viewport Status
Fired on the **Forge Event Bus**:
```java
public void onRenderGuiLayer(RenderGuiLayerEvent.Post event) {
    GuiGraphicsExtractor graphics = event.getGuiGraphics();
    // Render in-game HUD indicators, viewport status pills, and crosshairs
}
```

### 4.5 Vanilla Audio Feedback
```java
Minecraft.getInstance().getSoundManager().play(
    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
);
```

---

## 5. UI Color Palette & Theme Constants

```java
public final class StudioTheme {
    // Framing & Backgrounds
    public static final int PANEL_BG           = 0xE6141416; // Deep slate dark translucent
    public static final int PANEL_BORDER       = 0xFF3C3C44; // Beveled stone border
    public static final int PANEL_BORDER_LIGHT = 0xFF5A5A66; // Highlight top/left border
    public static final int PANEL_BORDER_DARK  = 0xFF1E1E22; // Shadow bottom/right border

    // Headers & Accents
    public static final int GOLD_HEADER        = 0xFFFFAA00; // Vanilla Gold
    public static final int YELLOW_TITLE       = 0xFFFFFF55; // Vanilla Yellow
    public static final int WHITE_TEXT         = 0xFFFFFFFF; // Vanilla White
    public static final int GRAY_TEXT          = 0xFFAAAAAA; // Vanilla Gray
    public static final int DARK_GRAY_TEXT     = 0xFF555555; // Vanilla Dark Gray

    // Sockets & Domains
    public static final int COLOR_DENSITY      = 0xFF55FF55; // Green
    public static final int COLOR_CURVE        = 0xFF55FFFF; // Blue / Cyan
    public static final int COLOR_VECTOR       = 0xFFFFAA00; // Orange
    public static final int COLOR_MATERIAL     = 0xFFAA00AA; // Purple
    public static final int COLOR_PROP         = 0xFFFFFF55; // Yellow
    public static final int COLOR_POSITIONS    = 0xFF00AAAA; // Dark Cyan
    public static final int COLOR_GRAPH        = 0xFFFF5555; // Red

    // Diagnostics & Warnings
    public static final int COLOR_WARNING      = 0xFFFF9900; // Warning Amber
    public static final int COLOR_ERROR        = 0xFFFF3333; // Error Crimson
    public static final int COLOR_SUCCESS      = 0xFF44DD44; // Success Lime
}
```


---

## 6. Minecraft 26.1 DimensionType JSON Schema Migration

In Minecraft 26.1, the `dimension_type` JSON format underwent major structural changes:

1. **New Required Field**: `"has_ender_dragon_fight": false` (mandatory boolean).
2. **New Required Fields**: `"default_clock": "minecraft:overworld"` and `"timelines": "#minecraft:in_overworld"`.
3. **Attributes Map**: Legacy top-level gameplay/visual fields were refactored into the `"attributes": { ... }` block:
   - `bed_works` / `natural` $\to$ `"minecraft:gameplay/bed_rule"`
   - `piglin_safe` $\to$ `"minecraft:gameplay/nether_portal_spawns_piglin"`
   - `respawn_anchor_works` $\to$ `"minecraft:gameplay/respawn_anchor_works"`
   - `effects_location` $\to$ `"minecraft:visual/fog_color"`, `"sky_color"`, `"cloud_color"`, `"cloud_height"`
   - Ambient sound $\to$ `"minecraft:audio/ambient_sounds"`, `"minecraft:audio/background_music"`
4. **Omission Error**: Omitting `"has_ender_dragon_fight"` or the new attributes causes a fatal `IllegalStateException: No key has_ender_dragon_fight in MapLike` on `RegistryDataLoader.loadFromResource`, which hangs the "Preparing for world creation" screen!

---

## 7. 3D In-World Gizmo Rendering (`net.minecraft.gizmos.Gizmos` & `RegisterDebugRenderersEvent`)

In Minecraft 26.1, Mojang introduced a first-class 3D gizmo rendering pipeline (`net.minecraft.gizmos.Gizmos`, `GizmoStyle`, `GizmoProperties`) driven by `DebugRenderer.SimpleDebugRenderer`.

### 7.1 Registering an Always-Active 3D World Renderer
NeoForge provides `net.neoforged.neoforge.client.event.RegisterDebugRenderersEvent` on the **Mod Event Bus**. Any renderer registered via `event.register(renderer)` is added to `DebugRenderer.renderers` and has its `emitGizmos(...)` method invoked **every single render frame** while a world is loaded!

```java
// Register on Mod Event Bus:
modEventBus.addListener(WorldGenStudioClient::onRegisterDebugRenderers);

public static void onRegisterDebugRenderers(RegisterDebugRenderersEvent event) {
    event.register(new StudioChunkHighlightRenderer());
}
```

### 7.2 Emitting 3D Lines, Cuboids, and Billboard Text in World Space
Inside `DebugRenderer.SimpleDebugRenderer#emitGizmos`:
```java
@Override
public void emitGizmos(
    double camX, double camY, double camZ,
    DebugValueAccess debugValues,
    Frustum frustum,
    float partialTicks
) {
    // 1. 3D Line (world-space Vec3 coordinates, ARGB color, line width)
    Gizmos.line(new Vec3(x0, y0, z0), new Vec3(x1, y1, z1), 0xFF00FFAA, 3.0f);

    // 2. 3D Cuboid with Stroke + Translucent Fill
    AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    Gizmos.cuboid(box, GizmoStyle.strokeAndFill(0xFF00FFAA, 2.0f, 0x2800FFAA));

    // 3. 3D Floating Billboard Label in World
    Gizmos.billboardText(
        "Dev Chunk [" + cx + ", " + cz + "]",
        new Vec3(cx * 16 + 8.0, surfaceY + 8.0, cz * 16 + 8.0),
        TextGizmo.Style.whiteAndCentered().withScale(0.6f)
    );
}
```

---

## 8. 2D Matrix Stack (`GuiGraphicsExtractor.pose()`) & Screen Layout Rules

### 8.1 JOML `Matrix3x2fStack` for Zoomable 2D Canvases
In Minecraft 26.1, `graphics.pose()` returns an `org.joml.Matrix3x2fStack` (2D affine transform stack) rather than a 3D `PoseStack`. All `fill`, `outline`, `horizontalLine`, `verticalLine`, `text`, and `fakeItem` calls respect `graphics.pose()`!
```java
graphics.enableScissor(x, y, x + width, y + height);
graphics.pose().pushMatrix();
graphics.pose().translate((float) (x + panX), (float) (y + panY));
graphics.pose().scale((float) zoom, (float) zoom);

// Draw nodes, text, fields, wires in world-canvas coordinates (wx, wy)!
// Text and cards scale uniformly together and never overflow card boundaries!

graphics.pose().popMatrix();
graphics.disableScissor();
```

### 8.2 Standard Scaled GUI Screen Dimensions & Unicode Constraints
- At default/auto GUI scale on 1080p/1440p monitors, `Screen.width` is typically **`427` to `480` px**, and `Screen.height` is **`240` to `270` px**.
- **Rule 1**: Never place top-bar widgets at fixed X > 300 without computing relative to `width`.
- **Rule 2**: Avoid multi-byte surrogate-pair emojis (`📊`, `🧱`, `🗺️`, `📖`, `💾`); Minecraft's bitmap font renders surrogate selectors as garbled box characters that shift string widths. Stick to standard BMP symbols or clean vanilla text labels.
- **Rule 3**: Always clamp dynamic strings using `font.plainSubstrByWidth(str, maxPixelWidth)` before rendering inside fixed-width cards or buttons.


---

## 9. Live In-Place Chunk Regeneration (`ClientboundLevelChunkWithLightPacket`)

In Minecraft 26.1 singleplayer (`Minecraft.getInstance().getSingleplayerServer()`), modifying a biome's `DensityFunction` or `MaterialProvider` in-memory requires regenerating the already-loaded `LevelChunk` sections on the integrated `ServerLevel` thread and broadcasting the updated sections + light data to the client:

```java
// 1. Acquire all LevelChunkSections on the ServerLevel thread and clear to AIR
MinecraftVoxelSpace voxelSpace = new MinecraftVoxelSpace(chunk);
voxelSpace.clearAllToAir();

// 2. Run StagedChunkGenerator + Bedrock/SeaWater pass + re-prime heightmaps
this.stagedGenerator.generateChunk(chunkPos.x(), chunkPos.z(), voxelSpace);
applyBedrockAndSeaWater(chunkPos, voxelSpace);

chunk.setLightCorrect(false);
for (LevelChunkSection section : chunk.getSections()) {
    if (section != null && !section.hasOnlyAir()) {
        section.recalcBlockCounts();
    }
}

Heightmap.primeHeightmaps(chunk, EnumSet.of(
    Heightmap.Types.MOTION_BLOCKING,
    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
    Heightmap.Types.OCEAN_FLOOR,
    Heightmap.Types.WORLD_SURFACE,
    Heightmap.Types.OCEAN_FLOOR_WG,
    Heightmap.Types.WORLD_SURFACE_WG
));
chunk.markUnsaved();

// 3. Immediately send chunk block updates so the viewport renders geometry without delay
ThreadedLevelLightEngine lightEngine = level.getChunkSource().getLightEngine();
ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(
    chunk,
    lightEngine,
    null,
    null
);
for (ServerPlayer player : level.players()) {
    player.connection.send(packet);
}

// 4. Queue async chunk relighting & broadcast updated light nibbles to clients
lightEngine.lightChunk(chunk, false).thenRun(() -> {
    level.getServer().execute(() -> {
        ClientboundLightUpdatePacket lightPacket = new ClientboundLightUpdatePacket(
            chunkPos,
            lightEngine,
            null,
            null
        );
        for (ServerPlayer player : level.players()) {
            player.connection.send(lightPacket);
        }
    });
});
```
