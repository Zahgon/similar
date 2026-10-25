package io.github.mitsuhiko.similar;

public final class Replace<D extends DiffHook> implements DiffHook {
    private final D d;
    private int[] del;
    private int[] ins;
    private int[] eq;

    public Replace(D d) {
        this.d = d;
    }

    public D intoInner() {
        return d;
    }

    private void flushEq() {
        if (eq != null) {
            int[] e = eq;
            eq = null;
            d.equal(e[0], e[1], e[2]);
        }
    }

    private void flushDelIns() {
        if (del != null) {
            int[] dl = del;
            del = null;
            if (ins != null) {
                int[] in = ins;
                ins = null;
                d.replace(dl[0], dl[1], in[1], in[2]);
            } else {
                d.delete(dl[0], dl[1], dl[2]);
            }
        } else if (ins != null) {
            int[] in = ins;
            ins = null;
            d.insert(in[0], in[1], in[2]);
        }
    }

    @Override
    public void equal(int oldIndex, int newIndex, int len) {
        flushDelIns();
        if (eq != null) {
            eq = new int[] {eq[0], eq[1], eq[2] + len};
        } else {
            eq = new int[] {oldIndex, newIndex, len};
        }
    }

    @Override
    public void delete(int oldIndex, int oldLen, int newIndex) {
        flushEq();
        if (del != null) {
            del = new int[] {del[0], del[1] + oldLen, del[2]};
        } else {
            del = new int[] {oldIndex, oldLen, newIndex};
        }
    }

    @Override
    public void insert(int oldIndex, int newIndex, int newLen) {
        flushEq();
        if (ins != null) {
            ins = new int[] {ins[0], ins[1], newLen + ins[2]};
        } else {
            ins = new int[] {oldIndex, newIndex, newLen};
        }
    }

    @Override
    public void replace(int oldIndex, int oldLen, int newIndex, int newLen) {
        flushEq();
        d.replace(oldIndex, oldLen, newIndex, newLen);
    }

    @Override
    public void finish() {
        flushEq();
        flushDelIns();
        d.finish();
    }
}
