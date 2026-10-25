package io.github.mitsuhiko.similar;

import java.util.List;

public final class UnifiedHunkHeader {
    private final int oldStart;
    private final int oldEnd;
    private final int newStart;
    private final int newEnd;

    public UnifiedHunkHeader(List<DiffOp> ops) {
        DiffOp first = ops.get(0);
        DiffOp last = ops.get(ops.size() - 1);
        this.oldStart = first.oldRange().start();
        this.newStart = first.newRange().start();
        this.oldEnd = last.oldRange().end();
        this.newEnd = last.newRange().end();
    }

    private static String range(int start, int end) {
        long beginning = (long) start + 1;
        long len = end > start ? (long) end - start : 0;
        if (len == 1) {
            return Long.toString(beginning);
        }
        if (len == 0) {
            beginning -= 1;
        }
        return beginning + "," + len;
    }

    @Override
    public String toString() {
        return "@@ -" + range(oldStart, oldEnd) + " +" + range(newStart, newEnd) + " @@";
    }
}
