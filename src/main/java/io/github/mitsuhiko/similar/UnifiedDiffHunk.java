package io.github.mitsuhiko.similar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class UnifiedDiffHunk {
    private final TextDiff diff;
    private final List<DiffOp> ops;
    private final boolean missingNewlineHint;

    public UnifiedDiffHunk(List<DiffOp> ops, TextDiff diff, boolean missingNewlineHint) {
        this.diff = diff;
        this.ops = new ArrayList<>(ops);
        this.missingNewlineHint = missingNewlineHint;
    }

    public UnifiedHunkHeader header() {
        return new UnifiedHunkHeader(ops);
    }

    public List<DiffOp> ops() {
        return Collections.unmodifiableList(ops);
    }

    public boolean missingNewlineHint() {
        return missingNewlineHint;
    }

    public List<Change<String>> iterChanges() {
        List<Change<String>> rv = new ArrayList<>();
        for (DiffOp op : ops) {
            rv.addAll(diff.iterChanges(op));
        }
        return rv;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        int idx = 0;
        for (Change<String> change : iterChanges()) {
            if (idx++ == 0) {
                sb.append(header()).append('\n');
            }
            sb.append(change.tag()).append(change.value());
            if (!diff.newlineTerminated()) {
                sb.append('\n');
            }
            if (diff.newlineTerminated() && change.missingNewline()) {
                if (missingNewlineHint) {
                    sb.append("\n\\ No newline at end of file");
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }
}
