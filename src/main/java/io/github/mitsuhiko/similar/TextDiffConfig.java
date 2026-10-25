package io.github.mitsuhiko.similar;

import java.util.ArrayList;
import java.util.List;

public final class TextDiffConfig {
    private Algorithm algorithm = Algorithm.MYERS;
    private Boolean newlineTerminated;
    private WhitespaceMode whitespaceMode = WhitespaceMode.EXACT;

    public TextDiffConfig() {
    }

    public TextDiffConfig algorithm(Algorithm alg) {
        this.algorithm = alg;
        return this;
    }

    public TextDiffConfig newlineTerminated(boolean yes) {
        this.newlineTerminated = yes;
        return this;
    }

    public TextDiffConfig whitespaceMode(WhitespaceMode mode) {
        this.whitespaceMode = mode;
        return this;
    }

    public TextDiff diffLines(String old, String new_) {
        return diff(TextTokens.lines(old), TextTokens.lines(new_), true, whitespaceMode);
    }

    public TextDiff diffWords(String old, String new_) {
        return diff(TextTokens.words(old), TextTokens.words(new_), false, WhitespaceMode.EXACT);
    }

    public TextDiff diffChars(String old, String new_) {
        return diff(TextTokens.chars(old), TextTokens.chars(new_), false, WhitespaceMode.EXACT);
    }

    public TextDiff diffSlices(List<String> old, List<String> new_) {
        return diff(new ArrayList<>(old), new ArrayList<>(new_), false, WhitespaceMode.EXACT);
    }

    private TextDiff diff(List<String> old, List<String> new_, boolean defaultNewlineTerminated,
            WhitespaceMode mode) {
        List<DiffOp> ops;
        if (mode == WhitespaceMode.EXACT) {
            ops = captureOps(Lookup.of(old), old.size(), Lookup.of(new_), new_.size());
        } else {
            List<String> ok = TextTokens.keys(old, mode);
            List<String> nk = TextTokens.keys(new_, mode);
            ops = captureOps(Lookup.of(ok), ok.size(), Lookup.of(nk), nk.size());
        }
        boolean nl = newlineTerminated != null ? newlineTerminated : defaultNewlineTerminated;
        return new TextDiff(old, new_, ops, nl, algorithm, mode);
    }

    private List<DiffOp> captureOps(Lookup old, int oldLen, Lookup nw, int newLen) {
        boolean remap = (oldLen > 100 || newLen > 100) && algorithm != Algorithm.HUNT
                && algorithm != Algorithm.HISTOGRAM;
        if (remap && (algorithm == Algorithm.MYERS || algorithm == Algorithm.RAW_MYERS)) {
            return captureOpsPretrimmed(old, oldLen, nw, newLen);
        }
        boolean equalInputs = false;
        if (remap && oldLen == newLen) {
            equalInputs = true;
            for (int i = 0; i < oldLen; i++) {
                if (!Lookup.eq(nw, i, old, i)) {
                    equalInputs = false;
                    break;
                }
            }
        }
        if (equalInputs) {
            List<DiffOp> rv = new ArrayList<>();
            rv.add(DiffOp.equal(0, 0, oldLen));
            return rv;
        } else if (remap) {
            IdentifyDistinct ih = new IdentifyDistinct(old, 0, oldLen, nw, 0, newLen, false);
            return Algorithms.captureDiff(algorithm, ih.oldIds(), 0, oldLen, ih.newIds(), 0, newLen);
        }
        return Algorithms.captureDiff(algorithm, old, 0, oldLen, nw, 0, newLen);
    }

    private List<DiffOp> captureOpsPretrimmed(Lookup old, int oldLen, Lookup nw, int newLen) {
        int prefix = AlgoUtils.commonPrefixLen(old, 0, oldLen, nw, 0, newLen);
        int suffix = AlgoUtils.commonSuffixLen(old, prefix, oldLen, nw, prefix, newLen);
        int oms = prefix;
        int ome = oldLen - suffix;
        int nms = prefix;
        int nme = newLen - suffix;
        Compact<Replace<Capture>> d = new Compact<>(new Replace<>(new Capture()), old, nw);
        if (prefix > 0) {
            d.equal(0, 0, prefix);
        }
        if (oms < ome && nms < nme) {
            NoFinishHook<Compact<Replace<Capture>>> middle = new NoFinishHook<>(d);
            if (ome - oms > 100 || nme - nms > 100) {
                IdentifyDistinct ih = new IdentifyDistinct(old, oms, ome, nw, nms, nme, true);
                Algorithms.dispatch(algorithm, middle, ih.oldIds(), oms, ome, ih.newIds(), nms, nme);
            } else {
                Algorithms.dispatch(algorithm, middle, old, oms, ome, nw, nms, nme);
            }
        } else if (oms < ome) {
            d.delete(oms, ome - oms, nms);
        } else if (nms < nme) {
            d.insert(oms, nms, nme - nms);
        }
        if (suffix > 0) {
            d.equal(oldLen - suffix, newLen - suffix, suffix);
        }
        d.finish();
        return d.intoInner().intoInner().intoOps();
    }
}
