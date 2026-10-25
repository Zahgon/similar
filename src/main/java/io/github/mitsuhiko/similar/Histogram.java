package io.github.mitsuhiko.similar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class Histogram {
    private Histogram() {
    }

    private static final int MAX_CHAIN_LENGTH = 64;
    private static final int NONE = -1;
    private static final long DENSE_COVERED_MAX_RATIO = 8;

    static void diff(DiffHook d, Lookup old, int os, int oe, Lookup nw, int ns, int ne, boolean raw) {
        if (!raw && Preflight.maybeEmitReplaceFastPath(d, old, os, oe, nw, ns, ne)) {
            return;
        }
        int prefix = AlgoUtils.commonPrefixLen(old, os, oe, nw, ns, ne);
        int suffix = AlgoUtils.commonSuffixLen(old, os + prefix, oe, nw, ns + prefix, ne);
        int mos = os + prefix;
        int moe = oe - suffix;
        int mns = ns + prefix;
        int mne = ne - suffix;
        if (prefix > 0) {
            d.equal(os, ns, prefix);
        }
        if (!(mos < moe)) {
            if (mns < mne) {
                d.insert(mos, mns, mne - mns);
            }
        } else if (!(mns < mne)) {
            d.delete(mos, moe - mos, mns);
        } else {
            IdentifyDistinct h = new IdentifyDistinct(old, mos, moe, nw, mns, mne, true);
            Scratch scratch = new Scratch(h.oldDistinctCount(), mos, moe, mns, mne);
            diffImpl(new NoFinishHook<>(d), h.oldIds(), mos, moe, h.newIds(), mns, mne, raw, scratch);
        }
        if (suffix > 0) {
            d.equal(oe - suffix, ne - suffix, suffix);
        }
        d.finish();
    }

    private static final class Scratch {
        final int[] head;
        final int[] count;
        final int[] next;
        final int[] dense;
        final Map<Integer, Integer> sparse;
        final List<Integer> touched = new ArrayList<>();
        final int oldBase;
        final int newEnd;

        Scratch(int oldDistinct, int os, int oe, int ns, int ne) {
            long diagonals = (long) (oe - os) + (ne - ns) + 1;
            boolean isDense = diagonals <= DENSE_COVERED_MAX_RATIO * Math.min(oe - os, ne - ns);
            head = new int[oldDistinct];
            Arrays.fill(head, NONE);
            count = new int[oldDistinct];
            next = new int[oe - os];
            Arrays.fill(next, NONE);
            dense = isDense ? new int[(oe - os) + (ne - ns) + 1] : null;
            sparse = isDense ? null : new HashMap<>();
            oldBase = os;
            newEnd = ne;
        }

        int diagonal(int oldIdx, int newIdx) {
            return (oldIdx - oldBase) + (newEnd - newIdx);
        }

        int coveredEnd(int diagonal) {
            if (dense != null) {
                return dense[diagonal];
            }
            return sparse.getOrDefault(diagonal, 0);
        }

        void cover(int diagonal, int end) {
            int slot = coveredEnd(diagonal);
            if (sparse != null && !sparse.containsKey(diagonal)) {
                sparse.put(diagonal, 0);
            }
            if (slot == 0) {
                touched.add(diagonal);
            }
            int v = Math.max(slot, end + 1);
            if (dense != null) {
                dense[diagonal] = v;
            } else {
                sparse.put(diagonal, v);
            }
        }

        void clearCovered() {
            for (int diagonal : touched) {
                if (dense != null) {
                    dense[diagonal] = 0;
                } else {
                    sparse.remove(diagonal);
                }
            }
            touched.clear();
        }
    }

    private static final int RESULT_NONE = 0;
    private static final int RESULT_FALLBACK = 1;

    private static void diffImpl(DiffHook d, Lookup.IntLookup old, int os, int oe, Lookup.IntLookup nw, int ns,
            int ne, boolean raw, Scratch scratch) {
        if (!(os < oe) && !(ns < ne)) {
            return;
        }
        if (!(os < oe)) {
            d.insert(os, ns, ne - ns);
            return;
        }
        if (!(ns < ne)) {
            d.delete(os, oe - os, ns);
            return;
        }
        int prefix = AlgoUtils.commonPrefixLen(old, os, oe, nw, ns, ne);
        if (prefix > 0) {
            d.equal(os, ns, prefix);
            os += prefix;
            ns += prefix;
        }
        int suffix = AlgoUtils.commonSuffixLen(old, os, oe, nw, ns, ne);
        int suffixOld = oe - suffix;
        int suffixNew = ne - suffix;
        oe -= suffix;
        ne -= suffix;
        if (!(os < oe)) {
            if (ns < ne) {
                d.insert(os, ns, ne - ns);
            }
        } else if (!(ns < ne)) {
            d.delete(os, oe - os, ns);
        } else {
            Object result = findAnchor(old, os, oe, nw, ns, ne, scratch);
            if (result instanceof int[] anchor) {
                diffImpl(d, old, os, anchor[0], nw, ns, anchor[1], raw, scratch);
                d.equal(anchor[0], anchor[1], anchor[2]);
                diffImpl(d, old, anchor[0] + anchor[2], oe, nw, anchor[1] + anchor[2], ne, raw, scratch);
            } else if ((Integer) result == RESULT_NONE) {
                d.delete(os, oe - os, ns);
                d.insert(oe, ns, ne - ns);
            } else {
                NoFinishHook<DiffHook> hook = new NoFinishHook<>(d);
                if (raw) {
                    Myers.diffRaw(hook, old, os, oe, nw, ns, ne);
                } else {
                    Myers.diff(hook, old, os, oe, nw, ns, ne);
                }
            }
        }
        if (suffix > 0) {
            d.equal(suffixOld, suffixNew, suffix);
        }
    }

    private static Object findAnchor(Lookup.IntLookup old, int os, int oe, Lookup.IntLookup nw, int ns, int ne,
            Scratch s) {
        for (int oi = oe - 1; oi >= os; oi--) {
            int value = old.value(oi);
            s.next[oi - s.oldBase] = s.head[value];
            s.head[value] = oi;
            s.count[value]++;
        }
        Object result = findAnchorIndexed(old, os, oe, nw, ns, ne, s);
        for (int oi = os; oi < oe; oi++) {
            int value = old.value(oi);
            s.head[value] = NONE;
            s.count[value] = 0;
        }
        s.clearCovered();
        return result;
    }

    private static Object findAnchorIndexed(Lookup.IntLookup old, int os, int oe, Lookup.IntLookup nw, int ns,
            int ne, Scratch s) {
        boolean hasCommon = false;
        int[] best = null;
        int bestCount = Integer.MAX_VALUE;
        for (int ni = ns; ni < ne; ni++) {
            int value = nw.value(ni);
            if (value < 0 || value >= s.head.length) {
                continue;
            }
            int first = s.head[value];
            if (first == NONE) {
                continue;
            }
            hasCommon = true;
            int candidates = s.count[value];
            if (candidates > MAX_CHAIN_LENGTH) {
                continue;
            }
            int oi = first;
            while (oi != NONE) {
                int candidateOld = oi;
                oi = s.next[candidateOld - s.oldBase];
                int diagonal = s.diagonal(candidateOld, ni);
                if (s.coveredEnd(diagonal) > ni) {
                    continue;
                }
                int oldStart = candidateOld;
                int newStart = ni;
                int oldEnd = candidateOld;
                int newEnd = ni;
                int minCount = candidates;
                while (oldStart > os && newStart > ns && old.value(oldStart - 1) == nw.value(newStart - 1)) {
                    oldStart--;
                    newStart--;
                    int cnt = s.count[old.value(oldStart)];
                    if (cnt < minCount) {
                        minCount = cnt;
                    }
                }
                while (oldEnd + 1 < oe && newEnd + 1 < ne && old.value(oldEnd + 1) == nw.value(newEnd + 1)) {
                    oldEnd++;
                    newEnd++;
                    int cnt = s.count[old.value(oldEnd)];
                    if (cnt < minCount) {
                        minCount = cnt;
                    }
                }
                s.cover(diagonal, newEnd);
                int len = oldEnd - oldStart + 1;
                boolean isBetter;
                if (best == null) {
                    isBetter = true;
                } else {
                    isBetter = len > best[2] || (len == best[2] && (minCount < bestCount
                            || (minCount == bestCount && (oldStart < best[0]
                                    || (oldStart == best[0] && newStart < best[1])))));
                }
                if (isBetter) {
                    best = new int[] {oldStart, newStart, len};
                    bestCount = minCount;
                }
            }
        }
        if (best != null) {
            return best;
        }
        return hasCommon ? (Object) RESULT_FALLBACK : (Object) RESULT_NONE;
    }
}
