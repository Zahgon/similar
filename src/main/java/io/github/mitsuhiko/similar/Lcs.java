package io.github.mitsuhiko.similar;

final class Lcs {
    private Lcs() {
    }

    static void diff(DiffHook d, Lookup old, int os, int oe, Lookup nw, int ns, int ne, boolean raw) {
        if (!raw && Preflight.maybeEmitReplaceFastPath(d, old, os, oe, nw, ns, ne)) {
            return;
        }
        boolean oldEmpty = !(os < oe);
        boolean newEmpty = !(ns < ne);
        if (oldEmpty && newEmpty) {
            d.finish();
            return;
        } else if (newEmpty) {
            d.delete(os, oe - os, ns);
            d.finish();
            return;
        } else if (oldEmpty) {
            d.insert(os, ns, ne - ns);
            d.finish();
            return;
        }
        int prefix = AlgoUtils.commonPrefixLen(old, os, oe, nw, ns, ne);
        int suffix = AlgoUtils.commonSuffixLen(old, os + prefix, oe, nw, ns + prefix, ne);
        if (prefix == oe - os && oe - os == ne - ns) {
            d.equal(os, ns, oe - os);
            d.finish();
            return;
        }
        int tos = os + prefix;
        int toe = oe - suffix;
        int tns = ns + prefix;
        int tne = ne - suffix;
        int oldLen = toe - tos;
        int newLen = tne - tns;
        int[] table = null;
        int width = oldLen + 1;
        if (oldLen > 0 && newLen > 0) {
            table = new int[Math.multiplyExact(width, newLen + 1)];
            for (int i = newLen - 1; i >= 0; i--) {
                int row = i * width;
                int next = (i + 1) * width;
                for (int j = oldLen - 1; j >= 0; j--) {
                    table[row + j] = Lookup.eq(nw, tns + i, old, tos + j) ? table[next + j + 1] + 1
                            : Math.max(table[next + j], table[row + j + 1]);
                }
            }
        }
        int oldIdx = 0;
        int newIdx = 0;
        if (prefix > 0) {
            d.equal(os, ns, prefix);
        }
        if (table != null) {
            while (newIdx < newLen && oldIdx < oldLen) {
                int oo = tos + oldIdx;
                int no = tns + newIdx;
                if (Lookup.eq(nw, no, old, oo)) {
                    d.equal(oo, no, 1);
                    oldIdx++;
                    newIdx++;
                } else if (table[newIdx * width + oldIdx + 1] >= table[(newIdx + 1) * width + oldIdx]) {
                    d.delete(oo, 1, no);
                    oldIdx++;
                } else {
                    d.insert(oo, no, 1);
                    newIdx++;
                }
            }
        }
        if (oldIdx < oldLen) {
            d.delete(tos + oldIdx, oldLen - oldIdx, tns + newIdx);
            oldIdx = oldLen;
        }
        if (newIdx < newLen) {
            d.insert(tos + oldIdx, tns + newIdx, newLen - newIdx);
        }
        if (suffix > 0) {
            d.equal(os + oldLen + prefix, ns + newLen + prefix, suffix);
        }
        d.finish();
    }
}
