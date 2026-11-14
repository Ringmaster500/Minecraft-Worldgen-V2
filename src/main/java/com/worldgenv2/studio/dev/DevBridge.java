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
                        + ",\"pixelHeight\":" + w.getHeight() + ",\"inWorld\":" + (mc.level != null) + "}";
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
                scr.mouseClicked(ev, false);
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
