package it.einjojo.plotsquared.mod;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A category of schematics (e.g., "tree", "stone") with shared placement parameters.
 */
public final class SchematicCategory {

    private static final Logger log = LogManager.getLogger(SchematicCategory.class);

    private final String name;
    private final List<LoadedSchematic> schematics;
    private final int spawnChance;      // 0-255 threshold for spawning
    private final int maxPerPlot;       // Maximum instances per plot

    // Precomputed bounds for fast rejection
    private final int maxWidth;
    private final int maxLength;

    private SchematicCategory(String name, List<LoadedSchematic> schematics,
                             int spawnChance, int maxPerPlot, int maxWidth, int maxLength) {
        this.name = name;
        this.schematics = schematics;
        this.spawnChance = spawnChance;
        this.maxPerPlot = maxPerPlot;
        this.maxWidth = maxWidth;
        this.maxLength = maxLength;
    }

    /**
     * Loads all schematics from a directory into a category.
     */
    public static SchematicCategory load(File directory, int spawnChance, int maxPerPlot) {
        if (!directory.exists() || !directory.isDirectory()) {
            log.warn("Schematic directory not found: {}", directory.getAbsolutePath());
            return new SchematicCategory(directory.getName(), Collections.emptyList(),
                    spawnChance, maxPerPlot, 0, 0);
        }

        List<LoadedSchematic> schematics = new ArrayList<>();
        int maxWidth = 0;
        int maxLength = 0;

        File[] files = directory.listFiles((dir, name) -> name.endsWith(".schem"));
        if (files != null) {
            for (File file : files) {
                LoadedSchematic loaded = LoadedSchematic.load(file);
                if (loaded != null) {
                    schematics.add(loaded);
                    maxWidth = Math.max(maxWidth, loaded.width());
                    maxLength = Math.max(maxLength, loaded.length());
                }
            }
        }

        log.info("Loaded {} schematics for category '{}' (maxSize: {}x{})",
                schematics.size(), directory.getName(), maxWidth, maxLength);

        return new SchematicCategory(directory.getName(), schematics,
                spawnChance, maxPerPlot, maxWidth, maxLength);
    }

    public String name() {
        return name;
    }

    public boolean isEmpty() {
        return schematics.isEmpty();
    }

    public int size() {
        return schematics.size();
    }

    /**
     * Selects a random schematic using a hash value for determinism.
     */
    public LoadedSchematic select(int hash) {
        if (schematics.isEmpty()) {
            return null;
        }
        int index = Math.abs(hash) % schematics.size();
        return schematics.get(index);
    }

    public int spawnChance() {
        return spawnChance;
    }

    public int maxPerPlot() {
        return maxPerPlot;
    }

    public int maxWidth() {
        return maxWidth;
    }

    public int maxLength() {
        return maxLength;
    }

}

