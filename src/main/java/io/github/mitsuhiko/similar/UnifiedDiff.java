package io.github.mitsuhiko.similar;

import java.util.ArrayList;
import java.util.List;

public final class UnifiedDiff {
    private final TextDiff diff;
    private int contextRadius = 3;
    private boolean missingNewlineHint = true;
    private String oldHeader;
    private String newHeader;

    public UnifiedDiff(TextDiff diff) {
        this.diff = diff;
    }

    public static UnifiedDiff fromTextDiff(TextDiff diff) {
        return new UnifiedDiff(diff);
    }

    public UnifiedDiff contextRadius(int n) {
        this.contextRadius = n;
        return this;
    }

    public UnifiedDiff header(String a, String b) {
        this.oldHeader = a;
        this.newHeader = b;
        return this;
    }

    public UnifiedDiff missingNewlineHint(boolean yes) {
        this.missingNewlineHint = yes;
        return this;
    }

    public List<UnifiedDiffHunk> iterHunks() {
        List<UnifiedDiffHunk> rv = new ArrayList<>();
        for (List<DiffOp> ops : diff.groupedOps(contextRadius)) {
            if (!ops.isEmpty()) {
                rv.add(new UnifiedDiffHunk(ops, diff, missingNewlineHint));
            }
        }
        return rv;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        boolean header = oldHeader != null;
        for (UnifiedDiffHunk hunk : iterHunks()) {
            if (header) {
                sb.append("--- ").append(oldHeader).append('\n');
                sb.append("+++ ").append(newHeader).append('\n');
                header = false;
            }
            sb.append(hunk);
        }
        return sb.toString();
    }
}
