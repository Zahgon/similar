package io.github.mitsuhiko.similar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TextDiff {
    private final List<String> old;
    private final List<String> new_;
    private final List<DiffOp> ops;
    private final boolean newlineTerminated;
    private final Algorithm algorithm;
    private final WhitespaceMode whitespaceMode;

    TextDiff(List<String> old, List<String> new_, List<DiffOp> ops, boolean newlineTerminated, Algorithm algorithm,
            WhitespaceMode whitespaceMode) {
        this.old = old;
        this.new_ = new_;
        this.ops = ops;
        this.newlineTerminated = newlineTerminated;
        this.algorithm = algorithm;
        this.whitespaceMode = whitespaceMode;
    }

    public static TextDiffConfig configure() {
        return new TextDiffConfig();
    }

    public static TextDiff fromLines(String old, String new_) {
        return configure().diffLines(old, new_);
    }

    public static TextDiff fromWords(String old, String new_) {
        return configure().diffWords(old, new_);
    }

    public static TextDiff fromChars(String old, String new_) {
        return configure().diffChars(old, new_);
    }

    public static TextDiff fromSlices(List<String> old, List<String> new_) {
        return configure().diffSlices(old, new_);
    }

    public Algorithm algorithm() {
        return algorithm;
    }

    public WhitespaceMode whitespaceMode() {
        return whitespaceMode;
    }

    public boolean newlineTerminated() {
        return newlineTerminated;
    }

    public int oldLen() {
        return old.size();
    }

    public int newLen() {
        return new_.size();
    }

    public String oldSlice(int index) {
        return index >= 0 && index < old.size() ? old.get(index) : null;
    }

    public String newSlice(int index) {
        return index >= 0 && index < new_.size() ? new_.get(index) : null;
    }

    public List<String> oldSlices() {
        return Collections.unmodifiableList(old);
    }

    public List<String> newSlices() {
        return Collections.unmodifiableList(new_);
    }

    public float ratio() {
        return Similar.diffRatio(ops, oldLen(), newLen());
    }

    public List<DiffOp> ops() {
        return Collections.unmodifiableList(ops);
    }

    public List<List<DiffOp>> groupedOps(int n) {
        return Similar.groupDiffOps(new ArrayList<>(ops), n);
    }

    public List<Change<String>> iterChanges(DiffOp op) {
        List<Change<String>> rv = new ArrayList<>();
        Range or = op.oldRange();
        Range nr = op.newRange();
        switch (op.tag()) {
            case EQUAL -> {
                for (int i = 0; i < or.len(); i++) {
                    String value = whitespaceMode == WhitespaceMode.EXACT ? side(old, or.start() + i)
                            : sideChecked(or.start() + i, nr.start() + i);
                    rv.add(new Change<>(ChangeTag.EQUAL, or.start() + i, nr.start() + i, value));
                }
            }
            case DELETE -> {
                for (int i = 0; i < or.len(); i++) {
                    rv.add(new Change<>(ChangeTag.DELETE, or.start() + i, null, side(old, or.start() + i)));
                }
            }
            case INSERT -> {
                for (int i = 0; i < nr.len(); i++) {
                    rv.add(new Change<>(ChangeTag.INSERT, null, nr.start() + i, side(new_, nr.start() + i)));
                }
            }
            case REPLACE -> {
                for (int i = 0; i < or.len(); i++) {
                    rv.add(new Change<>(ChangeTag.DELETE, or.start() + i, null, side(old, or.start() + i)));
                }
                for (int i = 0; i < nr.len(); i++) {
                    rv.add(new Change<>(ChangeTag.INSERT, null, nr.start() + i, side(new_, nr.start() + i)));
                }
            }
        }
        return rv;
    }

    private String sideChecked(int oldIndex, int newIndex) {
        side(old, oldIndex);
        return side(new_, newIndex);
    }

    private static String side(List<String> tokens, int index) {
        if (index < 0 || index >= tokens.size()) {
            throw new IndexOutOfBoundsException("diff operation range out of bounds");
        }
        return tokens.get(index);
    }

    public List<Change<String>> iterAllChanges() {
        List<Change<String>> rv = new ArrayList<>();
        for (DiffOp op : ops) {
            rv.addAll(iterChanges(op));
        }
        return rv;
    }

    public UnifiedDiff unifiedDiff() {
        return new UnifiedDiff(this);
    }

    public List<InlineChange> iterInlineChanges(DiffOp op) {
        return Inline.iterInlineChanges(this, op, new InlineChangeOptions());
    }

    public List<InlineChange> iterInlineChanges(DiffOp op, InlineChangeOptions options) {
        return Inline.iterInlineChanges(this, op, options);
    }

    public List<InlineChange> iterAllInlineChanges() {
        return iterAllInlineChanges(new InlineChangeOptions());
    }

    public List<InlineChange> iterAllInlineChanges(InlineChangeOptions options) {
        List<InlineChange> rv = new ArrayList<>();
        for (DiffOp op : ops) {
            rv.addAll(Inline.iterInlineChanges(this, op, options));
        }
        return rv;
    }
}
