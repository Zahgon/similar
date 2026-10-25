package io.github.mitsuhiko.similar;

import java.util.ArrayList;
import java.util.List;

public final class Compact<D extends DiffHook> implements DiffHook {
    private final D d;
    private final List<DiffOp> ops = new ArrayList<>();
    private final Lookup old;
    private final Lookup nw;

    public Compact(D d, List<?> old, List<?> new_) {
        this(d, Lookup.of(old), Lookup.of(new_));
    }

    Compact(D d, Lookup old, Lookup nw) {
        this.d = d;
        this.old = old;
        this.nw = nw;
    }

    public D intoInner() {
        return d;
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
    public void finish() {
        cleanupDiffOps(old, nw, ops);
        for (DiffOp op : ops) {
            op.applyToHook(d);
        }
        d.finish();
    }

    static void cleanupDiffOps(Lookup old, Lookup nw, List<DiffOp> ops) {
        shiftAll(ops, old, nw, DiffTag.DELETE);
        shiftAll(ops, old, nw, DiffTag.INSERT);
        normalizeCursors(ops);
    }

    private static void shiftAll(List<DiffOp> ops, Lookup old, Lookup nw, DiffTag tag) {
        int pointer = 0;
        while (pointer < ops.size()) {
            if (ops.get(pointer).tag() == tag) {
                pointer = shiftUp(ops, old, nw, pointer);
                pointer = shiftDown(ops, old, nw, pointer);
            }
            pointer++;
        }
    }

    private static void normalizeCursors(List<DiffOp> ops) {
        if (ops.isEmpty()) {
            return;
        }
        int oldCursor = ops.get(0).oldRange().start();
        int newCursor = ops.get(0).newRange().start();
        for (int i = 0; i < ops.size(); i++) {
            DiffOp op = ops.get(i);
            switch (op.tag()) {
                case EQUAL, REPLACE -> {
                    oldCursor = op.oldIndex() + op.oldLen();
                    newCursor = op.newIndex() + op.newLen();
                }
                case DELETE -> {
                    op = op.withNewIndex(newCursor);
                    ops.set(i, op);
                    oldCursor = op.oldIndex() + op.oldLen();
                }
                case INSERT -> {
                    op = op.withOldIndex(oldCursor);
                    ops.set(i, op);
                    newCursor = op.newIndex() + op.newLen();
                }
            }
        }
    }

    private static void swapAdjacent(List<DiffOp> ops, int left) {
        int right = left + 1;
        int oldStart = ops.get(left).oldRange().start();
        int oldEnd = ops.get(right).oldRange().end();
        int newStart = ops.get(left).newRange().start();
        int newEnd = ops.get(right).newRange().end();
        DiffOp a = ops.get(right);
        DiffOp b = ops.get(left);
        a = a.tag() == DiffTag.INSERT ? a.withOldIndex(oldStart) : a.withNewIndex(newStart);
        b = b.tag() == DiffTag.INSERT ? b.withOldIndex(oldEnd) : b.withNewIndex(newEnd);
        ops.set(left, a);
        ops.set(right, b);
    }

    private static int shiftUp(List<DiffOp> ops, Lookup old, Lookup nw, int pointer) {
        while (pointer >= 1 && pointer - 1 < ops.size()) {
            DiffOp prev = ops.get(pointer - 1);
            DiffOp cur = ops.get(pointer);
            DiffTag t = cur.tag();
            DiffTag p = prev.tag();
            if (t == DiffTag.INSERT && p == DiffTag.EQUAL) {
                Range po = prev.oldRange();
                Range cn = cur.newRange();
                int suffix = AlgoUtils.commonSuffixLen(old, po.start(), po.end(), nw, cn.start(), cn.end());
                if (suffix > 0) {
                    if (pointer + 1 < ops.size() && ops.get(pointer + 1).tag() == DiffTag.EQUAL) {
                        ops.set(pointer + 1, ops.get(pointer + 1).growLeft(suffix));
                    } else {
                        ops.add(pointer + 1, DiffOp.equal(po.end() - suffix, cn.end() - suffix, suffix));
                    }
                    ops.set(pointer, ops.get(pointer).shiftLeft(suffix));
                    ops.set(pointer - 1, ops.get(pointer - 1).shrinkLeft(suffix));
                    if (ops.get(pointer - 1).isEmptyOp()) {
                        ops.remove(pointer - 1);
                        pointer--;
                    }
                } else if (ops.get(pointer - 1).isEmptyOp()) {
                    ops.remove(pointer - 1);
                    pointer--;
                } else {
                    break;
                }
            } else if (t == DiffTag.DELETE && p == DiffTag.EQUAL) {
                boolean canMerge = pointer >= 2 && ops.get(pointer - 2).tag() == DiffTag.DELETE;
                if (!canMerge) {
                    break;
                }
                Range co = cur.oldRange();
                Range pn = prev.newRange();
                int suffix = AlgoUtils.commonSuffixLen(old, co.start(), co.end(), nw, pn.start(), pn.end());
                if (suffix != 0) {
                    if (pointer + 1 < ops.size() && ops.get(pointer + 1).tag() == DiffTag.EQUAL) {
                        ops.set(pointer + 1, ops.get(pointer + 1).growLeft(suffix));
                    } else {
                        ops.add(pointer + 1, DiffOp.equal(co.end() - suffix, pn.end() - suffix, suffix));
                    }
                    ops.set(pointer, ops.get(pointer).shiftLeft(suffix));
                    ops.set(pointer - 1, ops.get(pointer - 1).shrinkLeft(suffix));
                    if (ops.get(pointer - 1).isEmptyOp()) {
                        ops.remove(pointer - 1);
                        pointer--;
                    }
                } else if (ops.get(pointer - 1).isEmptyOp()) {
                    ops.remove(pointer - 1);
                    pointer--;
                } else {
                    break;
                }
            } else if ((t == DiffTag.INSERT && p == DiffTag.DELETE) || (t == DiffTag.DELETE && p == DiffTag.INSERT)) {
                swapAdjacent(ops, pointer - 1);
                pointer--;
            } else if (t == DiffTag.INSERT && p == DiffTag.INSERT) {
                ops.set(pointer - 1, ops.get(pointer - 1).growRight(cur.newRange().len()));
                ops.remove(pointer);
                pointer--;
            } else if (t == DiffTag.DELETE && p == DiffTag.DELETE) {
                ops.set(pointer - 1, ops.get(pointer - 1).growRight(cur.oldRange().len()));
                ops.remove(pointer);
                pointer--;
            } else {
                throw new IllegalStateException("unexpected tag");
            }
        }
        return pointer;
    }

    private static int shiftDown(List<DiffOp> ops, Lookup old, Lookup nw, int pointer) {
        while (pointer + 1 < ops.size()) {
            DiffOp next = ops.get(pointer + 1);
            DiffOp cur = ops.get(pointer);
            DiffTag t = cur.tag();
            DiffTag nt = next.tag();
            if (t == DiffTag.INSERT && nt == DiffTag.EQUAL) {
                Range no = next.oldRange();
                Range cn = cur.newRange();
                int prefix = AlgoUtils.commonPrefixLen(old, no.start(), no.end(), nw, cn.start(), cn.end());
                if (prefix > 0) {
                    if (pointer >= 1 && ops.get(pointer - 1).tag() == DiffTag.EQUAL) {
                        ops.set(pointer - 1, ops.get(pointer - 1).growRight(prefix));
                    } else {
                        ops.add(pointer, DiffOp.equal(no.start(), cn.start(), prefix));
                        pointer++;
                    }
                    ops.set(pointer, ops.get(pointer).shiftRight(prefix));
                    ops.set(pointer + 1, ops.get(pointer + 1).shrinkRight(prefix));
                    if (ops.get(pointer + 1).isEmptyOp()) {
                        ops.remove(pointer + 1);
                    }
                } else if (ops.get(pointer + 1).isEmptyOp()) {
                    ops.remove(pointer + 1);
                } else {
                    break;
                }
            } else if (t == DiffTag.DELETE && nt == DiffTag.EQUAL) {
                boolean canMerge = pointer + 2 < ops.size() && ops.get(pointer + 2).tag() == DiffTag.DELETE;
                if (!canMerge) {
                    break;
                }
                Range co = cur.oldRange();
                Range nn = next.newRange();
                int prefix = AlgoUtils.commonPrefixLen(old, co.start(), co.end(), nw, nn.start(), nn.end());
                if (prefix > 0) {
                    if (pointer >= 1 && ops.get(pointer - 1).tag() == DiffTag.EQUAL) {
                        ops.set(pointer - 1, ops.get(pointer - 1).growRight(prefix));
                    } else {
                        ops.add(pointer, DiffOp.equal(co.start(), cur.newRange().start(), prefix));
                        pointer++;
                    }
                    ops.set(pointer, ops.get(pointer).shiftRight(prefix));
                    ops.set(pointer + 1, ops.get(pointer + 1).shrinkRight(prefix));
                    if (ops.get(pointer + 1).isEmptyOp()) {
                        ops.remove(pointer + 1);
                    }
                } else if (ops.get(pointer + 1).isEmptyOp()) {
                    ops.remove(pointer + 1);
                } else {
                    break;
                }
            } else if ((t == DiffTag.INSERT && nt == DiffTag.DELETE) || (t == DiffTag.DELETE && nt == DiffTag.INSERT)) {
                swapAdjacent(ops, pointer);
                pointer++;
            } else if (t == DiffTag.INSERT && nt == DiffTag.INSERT) {
                ops.set(pointer, ops.get(pointer).growRight(next.newRange().len()));
                ops.remove(pointer + 1);
            } else if (t == DiffTag.DELETE && nt == DiffTag.DELETE) {
                ops.set(pointer, ops.get(pointer).growRight(next.oldRange().len()));
                ops.remove(pointer + 1);
            } else {
                throw new IllegalStateException("unexpected tag");
            }
        }
        return pointer;
    }
}
