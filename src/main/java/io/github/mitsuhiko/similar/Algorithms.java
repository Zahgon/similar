package io.github.mitsuhiko.similar;

import java.util.List;

public final class Algorithms {
    private Algorithms() {
    }

    public static <T> void diff(Algorithm alg, DiffHook d, List<T> old, int oldStart, int oldEnd, List<T> new_,
            int newStart, int newEnd) {
        dispatch(alg, d, Lookup.of(old), oldStart, oldEnd, Lookup.of(new_), newStart, newEnd);
    }

    public static <T> void diffRaw(Algorithm alg, DiffHook d, List<T> old, int oldStart, int oldEnd, List<T> new_,
            int newStart, int newEnd) {
        Lookup o = Lookup.of(old);
        Lookup n = Lookup.of(new_);
        switch (alg) {
            case MYERS, RAW_MYERS -> Myers.diffRaw(d, o, oldStart, oldEnd, n, newStart, newEnd);
            case PATIENCE -> Patience.diff(d, o, oldStart, oldEnd, n, newStart, newEnd, true);
            case LCS -> Lcs.diff(d, o, oldStart, oldEnd, n, newStart, newEnd, true);
            case HUNT -> Hunt.diff(d, o, oldStart, oldEnd, n, newStart, newEnd, true);
            case HISTOGRAM -> Histogram.diff(d, o, oldStart, oldEnd, n, newStart, newEnd, true);
        }
    }

    public static <T> void diffSlices(Algorithm alg, DiffHook d, List<T> old, List<T> new_) {
        diff(alg, d, old, 0, old.size(), new_, 0, new_.size());
    }

    static void dispatch(Algorithm alg, DiffHook d, Lookup old, int os, int oe, Lookup nw, int ns, int ne) {
        switch (alg) {
            case MYERS -> Myers.diff(d, old, os, oe, nw, ns, ne);
            case RAW_MYERS -> Myers.diffRaw(d, old, os, oe, nw, ns, ne);
            case PATIENCE -> Patience.diff(d, old, os, oe, nw, ns, ne, false);
            case LCS -> Lcs.diff(d, old, os, oe, nw, ns, ne, false);
            case HUNT -> Hunt.diff(d, old, os, oe, nw, ns, ne, false);
            case HISTOGRAM -> Histogram.diff(d, old, os, oe, nw, ns, ne, false);
        }
    }

    static java.util.List<DiffOp> captureDiff(Algorithm alg, Lookup old, int os, int oe, Lookup nw, int ns, int ne) {
        Compact<Replace<Capture>> d = new Compact<>(new Replace<>(new Capture()), old, nw);
        dispatch(alg, d, old, os, oe, nw, ns, ne);
        return d.intoInner().intoInner().intoOps();
    }
}
