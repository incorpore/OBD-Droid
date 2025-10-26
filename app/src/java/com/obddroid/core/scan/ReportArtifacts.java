package com.obddroid.scan;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the set of files generated for a scan report.
 * Includes machine-readable JSON, human-readable Markdown, summary text,
 * individual stage payloads, and an aggregated archive for sharing.
 */
public final class ReportArtifacts {

    private final File jsonFile;
    private final File markdownFile;
    private final File summaryFile;
    private final File archiveFile;
    private final List<File> stageFiles;

    public ReportArtifacts(File jsonFile,
                           File markdownFile,
                           File summaryFile,
                           File archiveFile,
                           List<File> stageFiles) {
        this.jsonFile = jsonFile;
        this.markdownFile = markdownFile;
        this.summaryFile = summaryFile;
        this.archiveFile = archiveFile;
        this.stageFiles = stageFiles == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(stageFiles));
    }

    public File getJsonFile() {
        return jsonFile;
    }

    public File getMarkdownFile() {
        return markdownFile;
    }

    public File getSummaryFile() {
        return summaryFile;
    }

    public File getArchiveFile() {
        return archiveFile;
    }

    public List<File> getStageFiles() {
        return stageFiles;
    }
}
