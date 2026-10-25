package io.github.mitsuhiko.similar;

public final class NoFinishHook<D extends DiffHook> implements DiffHook {
    private final D d;

    public NoFinishHook(D d) {
        this.d = d;
    }

    public D intoInner() {
        return d;
    }

    @Override
    public void equal(int oldIndex, int newIndex, int len) {
        d.equal(oldIndex, newIndex, len);
    }

    @Override
    public void delete(int oldIndex, int oldLen, int newIndex) {
        d.delete(oldIndex, oldLen, newIndex);
    }

    @Override
    public void insert(int oldIndex, int newIndex, int newLen) {
        d.insert(oldIndex, newIndex, newLen);
    }

    @Override
    public void replace(int oldIndex, int oldLen, int newIndex, int newLen) {
        d.replace(oldIndex, oldLen, newIndex, newLen);
    }

    @Override
    public void finish() {
    }
}
