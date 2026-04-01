package it.einjojo.plotsquared.mod.schematic;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;

/**
 * Holds a loaded schematic with precomputed metadata for fast placement.
 */
public final class LoadedSchematic {

    private static final Logger log = LogManager.getLogger(LoadedSchematic.class);

    private final String name;
    private final Clipboard clipboard;
    private final int width;
    private final int height;
    private final int length;
    private final BlockVector3 origin;
    private final BlockVector3 minPoint;

    private LoadedSchematic(String name, Clipboard clipboard, int width, int height, int length,
                           BlockVector3 origin, BlockVector3 minPoint) {
        this.name = name;
        this.clipboard = clipboard;
        this.width = width;
        this.height = height;
        this.length = length;
        this.origin = origin;
        this.minPoint = minPoint;
    }

    public static LoadedSchematic load(File file) {
        if (!file.exists() || !file.getName().endsWith(".schem")) {
            return null;
        }

        try (FileInputStream fis = new FileInputStream(file);
             ClipboardReader reader = ClipboardFormats.findByFile(file).getReader(fis)) {

            Clipboard clipboard = reader.read();
            int width = clipboard.getDimensions().getX();
            int height = clipboard.getDimensions().getY();
            int length = clipboard.getDimensions().getZ();
            BlockVector3 minPoint = clipboard.getMinimumPoint();
            BlockVector3 origin = clipboard.getOrigin().subtract(minPoint);

            log.info("Loaded schematic '{}': {}x{}x{}", file.getName(), width, height, length);
            return new LoadedSchematic(file.getName(), clipboard, width, height, length, origin, minPoint);

        } catch (Exception e) {
            log.error("Failed to load schematic: {}", file.getName(), e);
            return null;
        }
    }

    public String name() {
        return name;
    }

    public Clipboard clipboard() {
        return clipboard;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int length() {
        return length;
    }

    public BlockVector3 origin() {
        return origin;
    }

    public BlockVector3 minPoint() {
        return minPoint;
    }

}

