package io.github.mitsuhiko.similar;

import java.util.ArrayList;
import java.util.List;

public final class Capture implements DiffHook {
    private final List<DiffOp> ops = new ArrayList<>();

    public Capture() {
    }

    public List<DiffOp> ops() {
        return ops;
    }

    public List<DiffOp> intoOps() {
        return new ArrayList<>(ops);
    }

    public List<List<DiffOp>> intoGroupedOps(int n) {
        return Similar.groupDiffOps(intoOps(), n);
    }

    @Override
    public void equal(int oldIndex, int newIndex, int len) {
        ops.add(DiffOp.equal(oldIndex, newIndex, len));
    }

    @Override
    public void delete(int oldIndex, int oldLen, int newIndex) {
        ops.add(DiffOp.delete(oldIndex, oldLen, newIndex));
    }

    @Override
    public void insert(int oldIndex, int newIndex, int newLen) {
        ops.add(DiffOp.insert(oldIndex, newIndex, newLen));
    }

    @Override
    public void replace(int oldIndex, int oldLen, int newIndex, int newLen) {
        ops.add(DiffOp.replace(oldIndex, oldLen, newIndex, newLen));
    }
}
