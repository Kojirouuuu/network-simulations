package sirsim.utils;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Path utilities.
 */
public final class PathsEx {
    private PathsEx() {}

    /**
     * If the given path exists, returns a new path with an index suffix
     * like "name (1).ext", "name (2).ext", ... that does not exist yet.
     * If the path does not exist, returns it unchanged.
     */
    public static Path resolveIndexed(Path path) {
        if (path == null) return null;
        if (!Files.exists(path)) return path;

        Path dir = path.getParent();
        String filename = path.getFileName().toString();

        int dot = filename.lastIndexOf('.');
        String base;
        String ext;
        if (dot > 0) { // dot at position 0 means hidden file without ext
            base = filename.substring(0, dot);
            ext = filename.substring(dot); // includes dot
        } else {
            base = filename;
            ext = "";
        }

        int i = 1;
        while (true) {
            String candidate = String.format("%s (%d)%s", base, i, ext);
            Path p = dir == null ? Path.of(candidate) : dir.resolve(candidate);
            if (!Files.exists(p)) return p;
            i++;
        }
    }
}

