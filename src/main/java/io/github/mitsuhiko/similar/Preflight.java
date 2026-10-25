package io.github.mitsuhiko.similar;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class Preflight {
    private Preflight() {
    }

    static final int DISJOINT_FAST_PATH_MIN_LEN = 512;
    static final long DISJOINT_FAST_PATH_MIN_WORK = 128L * 1024;
    static final int DISJOINT_FAST_PATH_BOUNDARY_PROBE = 8;
    static final int NEAR_DISJOINT_COMMON_ITEM_DIVISOR = 1024;
    static final int NEAR_DISJOINT_MAX_COMMON_ITEMS = 64;
    static final int CONFUSING_RECORD_SCAN_WINDOW = 100;
    static final int CONFUSING_RECORD_RATIO = 4;
    static final int MAX_MATCH_FREQUENCY_THRESHOLD = 1024;
    static final int NO_MATCH = -1;

    static final byte DISCARD = 0;
    static final byte KEEP = 1;
    static final byte INVESTIGATE = 2;

    static final class Trimmed {
        int oldStart;
        int oldEnd;
        int newStart;
        int newEnd;
        int origOldStart;
        int origOldEnd;
        int origNewStart;
        int origNewEnd;
        int prefixLen;
        int suffixLen;
    }

    static final class Reduced {
        int[] oldValues;
        int[] oldIndices;
        int[] newValues;
        int[] newIndices;
        Trimmed trimmed;
    }

    static boolean shouldScan(Lookup old, int os, int oe, Lookup nw, int ns, int ne) {
        int oldLen = oe - os;
        int newLen = ne - ns;
        if (oldLen < DISJOINT_FAST_PATH_MIN_LEN || newLen < DISJOINT_FAST_PATH_MIN_LEN
                || (long) oldLen * newLen < DISJOINT_FAST_PATH_MIN_WORK) {
            return false;
        }
        if (Lookup.eq(nw, ns, old, os) || Lookup.eq(nw, ne - 1, old, oe - 1)) {
            return false;
        }
        int probeLen = Math.min(Math.min(oldLen, newLen), DISJOINT_FAST_PATH_BOUNDARY_PROBE + 1);
        for (int skip = 1; skip < probeLen; skip++) {
            if (Lookup.eq(nw, ns + skip, old, os) || Lookup.eq(nw, ns, old, os + skip)
                    || Lookup.eq(nw, ne - 1 - skip, old, oe - 1)
                    || Lookup.eq(nw, ne - 1, old, oe - 1 - skip)) {
                return false;
            }
        }
        return true;
    }

    static boolean maybeEmitReplaceFastPath(DiffHook d, Lookup old, int os, int oe, Lookup nw, int ns, int ne) {
        if (!shouldScan(old, os, oe, nw, ns, ne)) {
            return false;
        }
        int oldLen = oe - os;
        int newLen = ne - ns;
        int budget = Math.max(1, Math.min(NEAR_DISJOINT_MAX_COMMON_ITEMS,
                Math.min(oldLen, newLen) / NEAR_DISJOINT_COMMON_ITEM_DIVISOR));
        if (hasMoreThanCommonItems(old, os, oe, nw, ns, ne, budget)) {
            return false;
        }
        d.delete(os, oldLen, ns);
        d.insert(os, ns, newLen);
        d.finish();
        return true;
    }

    static boolean hasMoreThanCommonItems(Lookup old, int os, int oe, Lookup nw, int ns, int ne, int budget) {
        Set<Object> values = new HashSet<>();
        for (int i = os; i < oe; i++) {
            values.add(old.at(i));
        }
        int common = 0;
        for (int i = ns; i < ne; i++) {
            if (values.contains(nw.at(i))) {
                common++;
                if (common > budget) {
                    return true;
                }
            }
        }
        return false;
    }

    static int isqrt(long v) {
        long r = (long) Math.sqrt((double) v);
        while (r * r > v) {
            r--;
        }
        while ((r + 1) * (r + 1) <= v) {
            r++;
        }
        return (int) r;
    }

    private static byte[] recordActions(int[] ids, int[] oppositeCounts) {
        int limit = Math.max(1, Math.min(MAX_MATCH_FREQUENCY_THRESHOLD, isqrt(ids.length)));
        byte[] rv = new byte[ids.length];
        for (int i = 0; i < ids.length; i++) {
            int id = ids[i];
            if (id == NO_MATCH) {
                rv[i] = DISCARD;
            } else {
                int count = oppositeCounts[id];
                rv[i] = count == 0 ? DISCARD : count < limit ? KEEP : INVESTIGATE;
            }
        }
        return rv;
    }

    private static boolean discardConfusingRecord(byte[] actions, int index) {
        int leftDiscards = 0;
        int leftInvestigates = 0;
        int taken = 0;
        for (int i = index - 1; i >= 0 && taken < CONFUSING_RECORD_SCAN_WINDOW; i--, taken++) {
            byte a = actions[i];
            if (a == DISCARD) {
                leftDiscards++;
            } else if (a == INVESTIGATE) {
                leftInvestigates++;
            } else {
                break;
            }
        }
        if (leftDiscards == 0) {
            return false;
        }
        int rightDiscards = 0;
        int rightInvestigates = 0;
        taken = 0;
        for (int i = index + 1; i < actions.length && taken < CONFUSING_RECORD_SCAN_WINDOW; i++, taken++) {
            byte a = actions[i];
            if (a == DISCARD) {
                rightDiscards++;
            } else if (a == INVESTIGATE) {
                rightInvestigates++;
            } else {
                break;
            }
        }
        if (rightDiscards == 0) {
            return false;
        }
        long discards = leftDiscards + rightDiscards;
        long investigates = leftInvestigates + rightInvestigates + 1;
        return investigates * CONFUSING_RECORD_RATIO < investigates + discards;
    }

    private static boolean keepRecord(byte[] actions, int index) {
        byte a = actions[index];
        if (a == DISCARD) {
            return false;
        }
        if (a == KEEP) {
            return true;
        }
        return !discardConfusingRecord(actions, index);
    }

    /** Returns a {@link Trimmed} or a {@link Reduced} input. */
    static Object reduceForMyers(Lookup old, int os, int oe, Lookup nw, int ns, int ne) {
        int prefix = AlgoUtils.commonPrefixLen(old, os, oe, nw, ns, ne);
        int suffix = AlgoUtils.commonSuffixLen(old, os + prefix, oe, nw, ns + prefix, ne);
        Trimmed t = new Trimmed();
        t.oldStart = os + prefix;
        t.oldEnd = oe - suffix;
        t.newStart = ns + prefix;
        t.newEnd = ne - suffix;
        t.origOldStart = os;
        t.origOldEnd = oe;
        t.origNewStart = ns;
        t.origNewEnd = ne;
        t.prefixLen = prefix;
        t.suffixLen = suffix;
        if (!shouldScan(old, t.oldStart, t.oldEnd, nw, t.newStart, t.newEnd)) {
            return t;
        }
        Map<Object, Integer> ids = new HashMap<>();
        int oldLen = t.oldEnd - t.oldStart;
        int newLen = t.newEnd - t.newStart;
        int[] oldValues = new int[oldLen];
        int[] newValues = new int[newLen];
        int[] oldCounts = new int[oldLen];
        int[] newCounts = new int[oldLen];
        int distinct = 0;
        for (int off = 0; off < oldLen; off++) {
            Object value = old.at(t.oldStart + off);
            Integer id = ids.get(value);
            if (id == null) {
                id = distinct++;
                ids.put(value, id);
            }
            oldCounts[id]++;
            oldValues[off] = id;
        }
        for (int off = 0; off < newLen; off++) {
            Integer id = ids.get(nw.at(t.newStart + off));
            if (id != null) {
                newCounts[id]++;
                newValues[off] = id;
            } else {
                newValues[off] = NO_MATCH;
            }
        }
        byte[] oldActions = recordActions(oldValues, newCounts);
        int[] keptOld = new int[oldLen];
        int[] oldIndices = new int[oldLen];
        int ko = 0;
        for (int off = 0; off < oldLen; off++) {
            if (keepRecord(oldActions, off)) {
                keptOld[ko] = oldValues[off];
                oldIndices[ko] = t.oldStart + off;
                ko++;
            }
        }
        byte[] newActions = recordActions(newValues, oldCounts);
        int[] keptNew = new int[newLen];
        int[] newIndices = new int[newLen];
        int kn = 0;
        for (int off = 0; off < newLen; off++) {
            if (keepRecord(newActions, off)) {
                keptNew[kn] = newValues[off];
                newIndices[kn] = t.newStart + off;
                kn++;
            }
        }
        if (ko == oldLen && kn == newLen) {
            return t;
        }
        Reduced r = new Reduced();
        r.oldValues = java.util.Arrays.copyOf(keptOld, ko);
        r.oldIndices = java.util.Arrays.copyOf(oldIndices, ko);
        r.newValues = java.util.Arrays.copyOf(keptNew, kn);
        r.newIndices = java.util.Arrays.copyOf(newIndices, kn);
        r.trimmed = t;
        return r;
    }
}
