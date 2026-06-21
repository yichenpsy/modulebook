package de.bht.modulebook.pdfimport;

import java.nio.file.Path;

public record ImportRunResult(
    int exitCode,
    Path outputJson,
    Path reportJson,
    String stdout,
    String stderr
) {
    public boolean succeeded() {
        return exitCode == 0;
    }
}
