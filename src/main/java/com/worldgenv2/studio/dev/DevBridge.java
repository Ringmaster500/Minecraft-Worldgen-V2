package com.worldgenv2.studio.dev;

import com.worldgenv2.studio.gui.WorldGenStudioScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Development-only remote control for the running client. Lets tooling (and Claude) look at
 * and drive the Studio UI without a human at the keyboard.
 * <p>
 * Enabled only when the JVM is started with {@code -Dworldgenv2.devbridge=<port>}
 * (the Gradle {@code client} run sets this). Binds to loopback only.
 * <pre>
 *   GET /state                      -> JSON: screen class, gui size/scale, world loaded
 *   GET /screenshot[?scale=2]       -> PNG of the current frame (also saved to run/devbridge/shot.png)
 *   GET /open?screen=studio         -> opens a screen        GET /close
 *   GET /world?name=Test            -> opens a singleplayer save
 *   GET /click?x=&y=[&button=0]     -> press+release at GUI-scaled coordinates
 *   GET /move?x=&y=                 -> mouse move (hover)
 *   GET /drag?x1=&y1=&x2=&y2=       -> press, drag, release
 *   GET /scroll?x=&y=&dy=           -> mouse wheel
 *   GET /key?code=&mods=            -> GLFW key press+release (e.g. 256 = ESC, 83 = S)
 *   GET /type?text=                 -> typed characters
 *   GET /guiscale?n=                -> force GUI scale for reproducible screenshots (0 = auto)
 * </pre>
 */
public final class DevBridge {

    private static volatile boolean started;

    private DevBridge() {}

    public static void startIfEnabled() {
        String prop = System.getProperty("worldgenv2.devbridge");
        if (prop == null || started) return;
        started = true;
        int port;
        try {
            port = Integer.parseInt(prop);
        } catch (NumberFormatException e) {
            port = 5006;
        }
        final int p = port;
        Thread t = new Thread(() -> serve(p), "worldgenv2-devbridge");
        t.setDaemon(true);
        t.start();
    }

    private static void serve(int port) {
        try (ServerSocket server = new ServerSocket(port, 8, InetAddress.getByName("127.0.0.1"))) {
            System.out.println("[worldgenv2] DevBridge listening on 127.0.0.1:" + port);
            while (true) {
                Socket s = server.accept();
                Thread h = new Thread(() -> handle(s), "worldgenv2-devbridge-conn");
                h.setDaemon(true);
                h.start();
            }
        } catch (IOException e) {
            System.err.println("[worldgenv2] DevBridge failed: " + e);
        }
    }

    private static void handle(Socket s) {
        try (s) {
            s.setSoTimeout(5000);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            String line = in.readLine();
            if (line == null) return;
            String[] parts = line.split(" ");
            String target = parts.length > 1 ? parts[1] : "/";
            String path = target;
            Map<String, String> q = new HashMap<>();
            int qi = target.indexOf('?');
            if (qi >= 0) {
                path = target.substring(0, qi);
                for (String kv : target.substring(qi + 1).split("&")) {
                    int e = kv.indexOf('=');
                    if (e > 0) q.put(dec(kv.substring(0, e)), dec(kv.substring(e + 1)));
                }
            }
            byte[] body;
            String type = "application/json";
            try {
                if (path.equals("/screenshot")) {
                    body = screenshot();
                    type = "image/png";
                } else {
                    body = route(path, q).getBytes(StandardCharsets.UTF_8);
                }
            } catch (Throwable t) {
                body = ("{\"error\":\"" + esc(String.valueOf(t)) + "\"}").getBytes(StandardCharsets.UTF_8);
            }
            OutputStream out = s.getOutputStream();
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length
                    + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(body);
            out.flush();
        } catch (IOException ignored) {
        }
    }

    private static String route(String path, Map<String, String> q) throws Exception {
        return switch (path) {
            case "/state" -> onRender(() -> {
                Minecraft mc = Minecraft.getInstance();
                var w = mc.getWindow();
                return "{\"screen\":\"" + (mc.screen == null ? "none" : mc.screen.getClass().getName())
                        + "\",\"guiWidth\":" + w.getGuiScaledWidth() + ",\"guiHeight\":" + w.getGuiScaledHeight()
                        + ",\"guiScale\":" + w.getGuiScale() + ",\"pixelWidth\":" + w.getWidth()
                        + ",\"pixelHeight\":" + w.getHeight() + ",\"inWorld\":" + (mc.level != null)
                        + (mc.player == null ? "" : ",\"pos\":\"" + String.format("%.1f %.1f %.1f yaw %.0f pitch %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot()) + "\"")
                        + "}";
            });
            case "/open" -> onRender(() -> {
                Minecraft mc = Minecraft.getInstance();
                String which = q.getOrDefault("screen", "studio");
                if (which.equals("studio")) {
                    if (q.containsKey("fresh")) WorldGenStudioScreen.resetSession();
                    mc.setScreen(WorldGenStudioScreen.getInstance());
                } else {
                    return "{\"error\":\"unknown screen " + esc(which) + "\"}";
                }
                return ok();
            });
            case "/close" -> onRender(() -> {
                Minecraft.getInstance().setScreen(null);
                return ok();
            });
            case "/world" -> onRender(() -> {
                Minecraft mc = Minecraft.getInstance();
                mc.createWorldOpenFlows().openWorld(q.getOrDefault("name", "Test"), () -> {});
                return ok();
            });
            case "/click" -> onRender(() -> {
                var scr = Minecraft.getInstance().screen;
                if (scr == null) return "{\"error\":\"no screen\"}";
                double x = cx(q, "x"), y = cy(q, "y");
                int b = (int) d(q, "button", 0);
                var ev = new MouseButtonEvent(x, y, new MouseButtonInfo(b, 0));
                scr.mouseMoved(x, y);
                scr.mouseClicked(ev, d(q, "dbl", 0) != 0);
                scr.mouseReleased(ev);
                return ok();
            });
            case "/move" -> onRender(() -> {
                var scr = Minecraft.getInstance().screen;
                if (scr == null) return "{\"error\":\"no screen\"}";
                scr.mouseMoved(cx(q, "x"), cy(q, "y"));
                return ok();
            });
            case "/drag" -> onRender(() -> {
                var scr = Minecraft.getInstance().screen;
                if (scr == null) return "{\"error\":\"no screen\"}";
                double x1 = cx(q, "x1"), y1 = cy(q, "y1"), x2 = cx(q, "x2"), y2 = cy(q, "y2");
                int b = (int) d(q, "button", 0);
                scr.mouseMoved(x1, y1);
                scr.mouseClicked(new MouseButtonEvent(x1, y1, new MouseButtonInfo(b, 0)), false);
                int steps = 8;
                for (int i = 1; i <= steps; i++) {
                    double x = x1 + (x2 - x1) * i / steps, y = y1 + (y2 - y1) * i / steps;
                    scr.mouseDragged(new MouseButtonEvent(x, y, new MouseButtonInfo(b, 0)),
                            (x2 - x1) / steps, (y2 - y1) / steps);
                    scr.mouseMoved(x, y);
                }
                scr.mouseReleased(new MouseButtonEvent(x2, y2, new MouseButtonInfo(b, 0)));
                return ok();
            });
            case "/scroll" -> onRender(() -> {
                var scr = Minecraft.getInstance().screen;
                if (scr == null) return "{\"error\":\"no screen\"}";
                scr.mouseScrolled(cx(q, "x"), cy(q, "y"), d(q, "dx", 0), d(q, "dy", 0));
                return ok();
            });
            case "/key" -> onRender(() -> {
                var scr = Minecraft.getInstance().screen;
                if (scr == null) return "{\"error\":\"no screen\"}";
                int code = (int) d(q, "code");
                int mods = (int) d(q, "mods", 0);
                scr.keyPressed(new KeyEvent(code, 0, mods));
                scr.keyReleased(new KeyEvent(code, 0, mods));
                return ok();
            });
            case "/type" -> onRender(() -> {
                var scr = Minecraft.getInstance().screen;
                if (scr == null) return "{\"error\":\"no screen\"}";
                q.getOrDefault("text", "").codePoints().forEach(cp -> scr.charTyped(new CharacterEvent(cp)));
                return ok();
            });
            case "/newworld" -> onRender(() -> {
                // name, project (a world setup), preset (default worldgenv2:project): a fresh world with the setup's height
                String name = q.getOrDefault("name", "devworld");
                String project = q.getOrDefault("project", "");
                try {
                    if (!project.isBlank()) com.worldgenv2.pack.ProjectRuntime.setActiveProject(project);
                } catch (Exception e) { return "{\"error\":\"" + esc(String.valueOf(e)) + "\"}"; }
                int[] h = project.isBlank() ? null : com.worldgenv2.minecraft.WorldHeightDimensions.heightOf(project);
                if (q.containsKey("minY")) h = new int[]{(int) d(q, "minY"), (int) d(q, "depth", 384)};
                final int[] height = h;
                Minecraft mc = Minecraft.getInstance();
                var settings = new net.minecraft.world.level.LevelSettings(name, net.minecraft.world.level.GameType.CREATIVE,
                    net.minecraft.world.level.LevelSettings.DifficultySettings.DEFAULT, true, net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                var options = new net.minecraft.world.level.levelgen.WorldOptions(d(q, "seed", 1337) == 0 ? 1337L : (long) d(q, "seed", 1337), true, false);
                var presetKey = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.WORLD_PRESET,
                    net.minecraft.resources.Identifier.parse(q.getOrDefault("preset", "worldgenv2:project")));
                mc.createWorldOpenFlows().createFreshLevel(name, settings, options, provider -> {
                    var dims = provider.lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getOrThrow(presetKey).value().createWorldDimensions();
                    return height == null ? dims : com.worldgenv2.minecraft.WorldHeightDimensions.apply(provider, dims, height[0], height[1]);
                }, mc.screen);
                return ok();
            });
            case "/openproject" -> onRender(() -> {
                var app = com.worldgenv2.studio.shell.StudioApp.get();
                app.openProject(q.getOrDefault("name", ""));
                return app.project == null ? "{\"error\":\"could not open\"}" : ok();
            });
            case "/doc" -> onRender(() -> {
                var app = com.worldgenv2.studio.shell.StudioApp.get();
                app.openDoc(q.getOrDefault("path", ""));
                String v = q.get("view");
                if (v != null && app.activeDoc != null) app.setView(app.activeDoc, com.worldgenv2.studio.shell.StudioApp.View.valueOf(v.toUpperCase()));
                return ok();
            });
            case "/tp" -> onRender(() -> {
                com.worldgenv2.studio.shell.StudioApp.get().teleportPlayer((int) d(q, "x"), (int) d(q, "z"), (int) d(q, "up", 0));
                return ok();
            });
            case "/probe" -> {
                String res = com.worldgenv2.studio.shell.StudioApp.get().probeWorld((int) d(q, "x"), (int) d(q, "z"), (int) d(q, "r", 6));
                yield "{\"probe\":\"" + esc(res).replace("\n", "\\n") + "\"}";
            }
            case "/regen" -> onRender(() -> {
                com.worldgenv2.studio.shell.StudioApp.get().regenAroundPlayer();
                return ok();
            });
            case "/newasset" -> onRender(() -> {
                var app = com.worldgenv2.studio.shell.StudioApp.get();
                if (app.project == null) return "{\"error\":\"no project\"}";
                app.createAsset(com.worldgenv2.studio.project.AssetType.valueOf(q.getOrDefault("type", "ZONE").toUpperCase()), q.getOrDefault("name", "dev_zone"));
                return ok();
            });
            case "/newproject" -> onRender(() -> {
                var app = com.worldgenv2.studio.shell.StudioApp.get();
                app.createProject(q.getOrDefault("name", "dev_new"), q.getOrDefault("preset", "worldgenv2:starter"));
                return app.project == null ? "{\"error\":\"failed\"}" : ok();
            });
            case "/cmd" -> {
                String res = com.worldgenv2.studio.shell.StudioApp.get().serverCommand(q.getOrDefault("c", ""));
                yield "{\"cmd\":\"" + esc(res) + "\"}";
            }
            case "/spans" -> {
                String res = com.worldgenv2.studio.shell.StudioApp.get().spansNear((int) d(q, "x"), (int) d(q, "z"), q.containsKey("r") ? (int) d(q, "r") : 400);
                yield "{\"spans\":\"" + esc(res) + "\"}";
            }
            case "/heights" -> {
                String res = com.worldgenv2.studio.shell.StudioApp.get().heightsLine((int) d(q, "x"), (int) d(q, "z"), (int) d(q, "dx", 1), (int) d(q, "dz", 0), (int) d(q, "n", 64));
                yield "{\"heights\":\"" + esc(res) + "\"}";
            }
            case "/hash" -> {
                String res = com.worldgenv2.studio.shell.StudioApp.get().hashArea((int) d(q, "x"), (int) d(q, "z"), q.containsKey("r") ? (int) d(q, "r") : 3);
                yield "{\"hash\":\"" + esc(res) + "\"}";
            }
            case "/census" -> {
                String res = com.worldgenv2.studio.shell.StudioApp.get().censusWorld((int) d(q, "x"), (int) d(q, "z"));
                yield "{\"census\":\"" + esc(res) + "\"}";
            }
            case "/save" -> onRender(() -> {
                com.worldgenv2.studio.shell.StudioApp.get().save();
                return ok();
            });
            case "/apply" -> onRender(() -> {
                com.worldgenv2.studio.shell.StudioApp.get().applyToWorld();
                return ok();
            });
            case "/patch" -> onRender(() -> {
                var app = com.worldgenv2.studio.shell.StudioApp.get();
                if (app.project == null) return "{\"error\":\"no project\"}";
                var asset = app.project.get(q.getOrDefault("path", ""));
                if (asset == null) return "{\"error\":\"no such asset\"}";
                var patch = com.google.gson.JsonParser.parseString(q.getOrDefault("json", "{}")).getAsJsonObject();
                app.project.edit(asset, "Dev patch", j -> patch.entrySet().forEach(e -> j.add(e.getKey(), e.getValue())));
                return ok();
            });
            case "/guiscale" -> onRender(() -> {
                Minecraft mc = Minecraft.getInstance();
                mc.options.guiScale().set((int) d(q, "n", 0));
                mc.resizeGui();
                return ok();
            });
            default -> "{\"error\":\"unknown route " + esc(path) + "\"}";
        };
    }

    /** Runs on the render thread and waits for the result. */
    private static String onRender(Supplier<String> task) throws Exception {
        CompletableFuture<String> f = new CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            try {
                f.complete(task.get());
            } catch (Throwable t) {
                f.complete("{\"error\":\"" + esc(String.valueOf(t)) + "\"}");
            }
        });
        return f.get(10, TimeUnit.SECONDS);
    }

    private static byte[] screenshot() throws Exception {
        Path out = Path.of("devbridge", "shot.png").toAbsolutePath();
        Files.createDirectories(out.getParent());
        CompletableFuture<Void> done = new CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            try {
                Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget(), img -> {
                    try (img) {
                        img.writeToFile(out);
                        done.complete(null);
                    } catch (Throwable t) {
                        done.completeExceptionally(t);
                    }
                });
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        done.get(10, TimeUnit.SECONDS);
        return Files.readAllBytes(out);
    }

    private static String ok() {
        return "{\"ok\":true}";
    }

    /** Accepts GUI-scaled coords (x) or screenshot pixels (px) so clicks can be taken straight from an image. */
    private static double cx(Map<String, String> q, String k) {
        String px = q.get("p" + k);
        if (px != null) return Double.parseDouble(px) / Minecraft.getInstance().getWindow().getGuiScale();
        return d(q, k);
    }

    private static double cy(Map<String, String> q, String k) { return cx(q, k); }

    private static double d(Map<String, String> q, String k) {
        String v = q.get(k);
        if (v == null) throw new IllegalArgumentException("missing param " + k);
        return Double.parseDouble(v);
    }

    private static double d(Map<String, String> q, String k, double def) {
        String v = q.get(k);
        return v == null ? def : Double.parseDouble(v);
    }

    private static String dec(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "'").replace("\n", " ");
    }
}
