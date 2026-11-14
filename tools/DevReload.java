import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;

/**
 * Hot-swaps freshly compiled classes into the running dev client over JDWP (127.0.0.1:5005).
 * Run as:  java tools/DevReload.java <classesDir> [port]
 *
 * Only classes that (a) changed since the last reload (SHA-256 vs tools/.reload-hashes) and
 * (b) are already loaded in the game are redefined. Brand-new classes need no action: the game
 * classloader reads them straight from the classes directory when first used.
 * Structural changes (added/removed fields or methods, changed signatures) cannot be hot-swapped;
 * the tool then prints RESTART_REQUIRED and exits with code 2.
 */
public class DevReload {
    public static void main(String[] args) throws Exception {
        Path classesDir = Path.of(args[0]);
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5005;
        Path hashFile = Path.of("tools", ".reload-hashes");

        Map<String, String> old = new HashMap<>();
        if (Files.exists(hashFile)) {
            for (String l : Files.readAllLines(hashFile)) {
                int t = l.indexOf('\t');
                if (t > 0) old.put(l.substring(0, t), l.substring(t + 1));
            }
        }

        Map<String, byte[]> changed = new TreeMap<>();
        Map<String, String> now = new HashMap<>();
        try (Stream<Path> s = Files.walk(classesDir)) {
            for (Path p : (Iterable<Path>) s.filter(f -> f.toString().endsWith(".class"))::iterator) {
                byte[] bytes = Files.readAllBytes(p);
                String name = classesDir.relativize(p).toString().replace('\\', '/');
                name = name.substring(0, name.length() - 6).replace('/', '.');
                String h = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                now.put(name, h);
                if (!h.equals(old.get(name))) changed.put(name, bytes);
            }
        }

        AttachingConnector conn = Bootstrap.virtualMachineManager().attachingConnectors().stream()
                .filter(c -> c.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
        Map<String, Connector.Argument> a = conn.defaultArguments();
        a.get("hostname").setValue("127.0.0.1");
        a.get("port").setValue(String.valueOf(port));
        VirtualMachine vm;
        try {
            vm = conn.attach(a);
        } catch (IOException e) {
            System.out.println("NOT_RUNNING: cannot attach to game on port " + port + " (" + e.getMessage() + ")");
            System.exit(3);
            return;
        }
        try {
            Map<ReferenceType, byte[]> redefs = new LinkedHashMap<>();
            for (var e : changed.entrySet()) {
                for (ReferenceType rt : vm.classesByName(e.getKey())) redefs.put(rt, e.getValue());
            }
            if (redefs.isEmpty()) {
                System.out.println("OK: " + changed.size() + " changed class(es), none loaded in game; nothing to swap.");
            } else {
                try {
                    vm.redefineClasses(redefs);
                    System.out.println("OK: hot-swapped " + redefs.size() + " class(es).");
                } catch (UnsupportedOperationException | LinkageError ex) {
                    System.out.println("RESTART_REQUIRED: " + ex);
                    System.exit(2);
                }
            }
            StringBuilder sb = new StringBuilder();
            now.forEach((k, v) -> sb.append(k).append('\t').append(v).append('\n'));
            Files.writeString(hashFile, sb.toString());
        } finally {
            vm.dispose();
        }
    }
}
