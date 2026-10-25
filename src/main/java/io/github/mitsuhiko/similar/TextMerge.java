package io.github.mitsuhiko.similar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TextMerge {
    private final List<String> base;
    private final List<String> ours;
    private final List<String> theirs;
    private final List<MergeRegion> regions;
    private final Algorithm algorithm;
    private final WhitespaceMode whitespaceMode;
    private ConflictStyle conflictStyle = ConflictStyle.MERGE;
    private int markerSize = 7;
    private String ancestorLabel = "base";
    private String oursLabel = "ours";
    private String theirsLabel = "theirs";

    TextMerge(List<String> base, List<String> ours, List<String> theirs, Algorithm algorithm, WhitespaceMode mode) {
        this.base = base;
        this.ours = ours;
        this.theirs = theirs;
        this.algorithm = algorithm;
        this.whitespaceMode = mode;
        this.regions = buildRegions(algorithm, mode, base, ours, theirs);
    }

    public static TextMergeConfig configure() {
        return new TextMergeConfig();
    }

    public static TextMerge fromLines(String base, String ours, String theirs) {
        return new TextMergeConfig().mergeLines(base, ours, theirs);
    }

    public List<MergeRegion> regions() {
        return Collections.unmodifiableList(regions);
    }

    public List<MergeRegion> conflicts() {
        List<MergeRegion> rv = new ArrayList<>();
        for (MergeRegion r : regions) {
            if (r.isConflict()) {
                rv.add(r);
            }
        }
        return rv;
    }

    public int conflictCount() {
        return conflicts().size();
    }

    public boolean isConflicted() {
        return conflictCount() > 0;
    }

    public Algorithm algorithm() {
        return algorithm;
    }

    public WhitespaceMode whitespaceMode() {
        return whitespaceMode;
    }

    public TextMerge conflictStyle(ConflictStyle style) {
        this.conflictStyle = style;
        return this;
    }

    public TextMerge conflictMarkerSize(int size) {
        this.markerSize = size == 0 ? 7 : size;
        return this;
    }

    public TextMerge labels(String ancestor, String ours, String theirs) {
        this.ancestorLabel = sanitize(ancestor);
        this.oursLabel = sanitize(ours);
        this.theirsLabel = sanitize(theirs);
        return this;
    }

    private static String sanitize(String label) {
        return label.replace('\r', ' ').replace('\n', ' ');
    }

    public int baseLen() {
        return base.size();
    }

    public int oursLen() {
        return ours.size();
    }

    public int theirsLen() {
        return theirs.size();
    }

    public String baseLine(int index) {
        return index >= 0 && index < base.size() ? base.get(index) : null;
    }

    public String oursLine(int index) {
        return index >= 0 && index < ours.size() ? ours.get(index) : null;
    }

    public String theirsLine(int index) {
        return index >= 0 && index < theirs.size() ? theirs.get(index) : null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        boolean[] atLineStart = {true};
        for (MergeRegion region : regions) {
            switch (region.resolution()) {
                case UNCHANGED, OURS, BOTH -> writeRange(sb, ours, region.oursRange(), atLineStart);
                case THEIRS -> writeRange(sb, theirs, region.theirsRange(), atLineStart);
                case CONFLICT -> {
                    if (!atLineStart[0]) {
                        sb.append('\n');
                    }
                    marker(sb, '<', oursLabel);
                    atLineStart[0] = true;
                    writeRange(sb, ours, region.oursRange(), atLineStart);
                    terminate(sb, atLineStart);
                    if (conflictStyle == ConflictStyle.DIFF3) {
                        marker(sb, '|', ancestorLabel);
                        writeRange(sb, base, region.baseRange(), atLineStart);
                        terminate(sb, atLineStart);
                    }
                    marker(sb, '=', null);
                    writeRange(sb, theirs, region.theirsRange(), atLineStart);
                    terminate(sb, atLineStart);
                    marker(sb, '>', theirsLabel);
                    atLineStart[0] = true;
                }
            }
        }
        return sb.toString();
    }

    private static void writeRange(StringBuilder sb, List<String> side, Range range, boolean[] atLineStart) {
        for (int i = range.start(); i < range.end(); i++) {
            String line = side.get(i);
            sb.append(line);
            atLineStart[0] = TextTokens.endsWithNewline(line);
        }
    }

    private void marker(StringBuilder sb, char c, String label) {
        for (int i = 0; i < markerSize; i++) {
            sb.append(c);
        }
        if (label != null) {
            sb.append(' ').append(label);
        }
        sb.append('\n');
    }

    private static void terminate(StringBuilder sb, boolean[] atLineStart) {
        if (!atLineStart[0]) {
            sb.append('\n');
            atLineStart[0] = true;
        }
    }

    private record SideEdit(int baseStart, int baseEnd, int sideStart, int sideEnd) {
        boolean baseEmpty() {
            return !(baseStart < baseEnd);
        }
    }

    private static List<SideEdit> captureEdits(Algorithm algorithm, List<String> base, List<String> side,
            WhitespaceMode mode) {
        List<DiffOp> ops;
        if (mode == WhitespaceMode.EXACT) {
            ops = Algorithms.captureDiff(algorithm, Lookup.of(base), 0, base.size(), Lookup.of(side), 0, side.size());
        } else {
            List<String> bk = TextTokens.keys(base, mode);
            List<String> sk = TextTokens.keys(side, mode);
            ops = Algorithms.captureDiff(algorithm, Lookup.of(bk), 0, bk.size(), Lookup.of(sk), 0, sk.size());
        }
        List<SideEdit> edits = new ArrayList<>();
        for (DiffOp op : ops) {
            if (op.tag() == DiffTag.EQUAL) {
                continue;
            }
            SideEdit edit = new SideEdit(op.oldRange().start(), op.oldRange().end(), op.newRange().start(),
                    op.newRange().end());
            if (!edits.isEmpty()) {
                SideEdit prev = edits.get(edits.size() - 1);
                if (prev.baseEnd() == edit.baseStart() && prev.sideEnd() == edit.sideStart()) {
                    edits.set(edits.size() - 1, new SideEdit(prev.baseStart(), edit.baseEnd(), prev.sideStart(),
                            edit.sideEnd()));
                    continue;
                }
            }
            edits.add(edit);
        }
        return edits;
    }

    private static List<MergeRegion> buildRegions(Algorithm algorithm, WhitespaceMode mode, List<String> base,
            List<String> ours, List<String> theirs) {
        List<SideEdit> oe = captureEdits(algorithm, base, ours, mode);
        List<SideEdit> te = captureEdits(algorithm, base, theirs, mode);
        List<MergeRegion> regions = new ArrayList<>();
        int oi = 0;
        int ti = 0;
        int cursor = 0;
        while (oi < oe.size() || ti < te.size()) {
            SideEdit o = oi < oe.size() ? oe.get(oi) : null;
            SideEdit t = ti < te.size() ? te.get(ti) : null;
            if (o != null && t != null && overlap(o, t)) {
                pushUnchanged(regions, cursor, Math.min(o.baseStart(), t.baseStart()), oe, te);
                int ob = oi;
                int tb = ti;
                int cs = Math.min(o.baseStart(), t.baseStart());
                int ce = Math.max(o.baseEnd(), t.baseEnd());
                oi++;
                ti++;
                while (true) {
                    boolean changed = false;
                    if (oi < oe.size() && overlapsCluster(oe.get(oi), cs, ce)) {
                        cs = Math.min(cs, oe.get(oi).baseStart());
                        ce = Math.max(ce, oe.get(oi).baseEnd());
                        oi++;
                        changed = true;
                    }
                    if (ti < te.size() && overlapsCluster(te.get(ti), cs, ce)) {
                        cs = Math.min(cs, te.get(ti).baseStart());
                        ce = Math.max(ce, te.get(ti).baseEnd());
                        ti++;
                        changed = true;
                    }
                    if (!changed) {
                        break;
                    }
                }
                Range baseRange = new Range(cs, ce);
                Range oursRange = clusterSideRange(oe, ob, oi, baseRange);
                Range theirsRange = clusterSideRange(te, tb, ti, baseRange);
                MergeResolution res = rangesEqual(ours, oursRange, theirs, theirsRange, mode) ? MergeResolution.BOTH
                        : MergeResolution.CONFLICT;
                regions.add(new MergeRegion(res, baseRange, oursRange, theirsRange));
                cursor = Math.max(cursor, ce);
            } else if (o != null && t != null) {
                if (isBefore(o, t)) {
                    pushSingle(regions, MergeResolution.OURS, o, cursor, oe, te);
                    cursor = Math.max(cursor, o.baseEnd());
                    oi++;
                } else {
                    pushSingle(regions, MergeResolution.THEIRS, t, cursor, oe, te);
                    cursor = Math.max(cursor, t.baseEnd());
                    ti++;
                }
            } else if (o != null) {
                pushSingle(regions, MergeResolution.OURS, o, cursor, oe, te);
                cursor = Math.max(cursor, o.baseEnd());
                oi++;
            } else {
                pushSingle(regions, MergeResolution.THEIRS, t, cursor, oe, te);
                cursor = Math.max(cursor, t.baseEnd());
                ti++;
            }
        }
        pushUnchanged(regions, cursor, base.size(), oe, te);
        return regions;
    }

    private static void pushSingle(List<MergeRegion> regions, MergeResolution res, SideEdit edit, int cursor,
            List<SideEdit> oe, List<SideEdit> te) {
        pushUnchanged(regions, cursor, edit.baseStart(), oe, te);
        Range baseRange = new Range(edit.baseStart(), edit.baseEnd());
        Range oursRange = res == MergeResolution.OURS ? new Range(edit.sideStart(), edit.sideEnd())
                : project(oe, baseRange);
        Range theirsRange = res == MergeResolution.THEIRS ? new Range(edit.sideStart(), edit.sideEnd())
                : project(te, baseRange);
        regions.add(new MergeRegion(res, baseRange, oursRange, theirsRange));
    }

    private static void pushUnchanged(List<MergeRegion> regions, int start, int end, List<SideEdit> oe,
            List<SideEdit> te) {
        if (start >= end) {
            return;
        }
        Range r = new Range(start, end);
        regions.add(new MergeRegion(MergeResolution.UNCHANGED, r, project(oe, r), project(te, r)));
    }

    private static boolean overlap(SideEdit a, SideEdit b) {
        return a.baseStart() <= b.baseEnd() && b.baseStart() <= a.baseEnd();
    }

    private static boolean overlapsCluster(SideEdit e, int start, int end) {
        return e.baseStart() <= end && start <= e.baseEnd();
    }

    private static boolean isBefore(SideEdit a, SideEdit b) {
        if (a.baseStart() != b.baseStart()) {
            return a.baseStart() < b.baseStart();
        }
        return a.baseEmpty() && !b.baseEmpty();
    }

    private static int boundaryBefore(List<SideEdit> edits, int position) {
        int baseCursor = 0;
        int sideCursor = 0;
        for (SideEdit e : edits) {
            if (position < e.baseStart()) {
                return sideCursor + position - baseCursor;
            }
            int sideAtStart = sideCursor + e.baseStart() - baseCursor;
            if (position == e.baseStart()) {
                return sideAtStart;
            }
            if (position < e.baseEnd()) {
                return e.sideStart();
            }
            baseCursor = e.baseEnd();
            sideCursor = e.sideEnd();
        }
        return sideCursor + position - baseCursor;
    }

    private static int boundaryAfterInsertions(List<SideEdit> edits, int position) {
        int mapped = boundaryBefore(edits, position);
        for (SideEdit e : edits) {
            if (e.baseStart() > position) {
                break;
            }
            if (e.baseEmpty() && e.baseStart() == position) {
                mapped = Math.max(mapped, e.sideEnd());
            }
        }
        return mapped;
    }

    private static Range project(List<SideEdit> edits, Range range) {
        if (range.isEmpty()) {
            int p = boundaryBefore(edits, range.start());
            return new Range(p, p);
        }
        return new Range(boundaryAfterInsertions(edits, range.start()), boundaryBefore(edits, range.end()));
    }

    private static Range clusterSideRange(List<SideEdit> edits, int from, int to, Range baseRange) {
        if (!baseRange.isEmpty()) {
            return new Range(boundaryBefore(edits, baseRange.start()), boundaryAfterInsertions(edits, baseRange.end()));
        }
        if (from < to) {
            return new Range(edits.get(from).sideStart(), edits.get(to - 1).sideEnd());
        }
        int p = boundaryBefore(edits, baseRange.start());
        return new Range(p, p);
    }

    private static boolean rangesEqual(List<String> ours, Range or, List<String> theirs, Range tr,
            WhitespaceMode mode) {
        if (or.len() != tr.len()) {
            return false;
        }
        for (int i = 0; i < or.len(); i++) {
            String a = ours.get(or.start() + i);
            String b = theirs.get(tr.start() + i);
            if (mode == WhitespaceMode.EXACT) {
                if (!a.equals(b)) {
                    return false;
                }
            } else if (!TextTokens.whitespaceKey(a, mode).equals(TextTokens.whitespaceKey(b, mode))) {
                return false;
            }
        }
        return true;
    }
}
