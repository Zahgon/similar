package io.github.mitsuhiko.similar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class Hunt {
    private Hunt() {
    }

    private static final long MAX_MATCH_PAIRS_PER_INPUT_ITEM = 64;
    private static final long MAX_MATCH_PAIRS = 1_000_000;

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
            diffInt(new NoFinishHook<>(d), h.oldIds(), mos, moe, h.newIds(), mns, mne, h.oldDistinctCount(), raw);
        }
        if (suffix > 0) {
            d.equal(oe - suffix, ne - suffix, suffix);
        }
        d.finish();
    }

    private static void myers(DiffHook d, Lookup old, int os, int oe, Lookup nw, int ns, int ne, boolean raw) {
        if (raw) {
            Myers.diffRaw(d, old, os, oe, nw, ns, ne);
        } else {
            Myers.diff(d, old, os, oe, nw, ns, ne);
        }
    }

    private static void diffInt(DiffHook d, Lookup.IntLookup old, int os, int oe, Lookup.IntLookup nw, int ns,
            int ne, int oldDistinct, boolean raw) {
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
        int mos = os + prefix;
        int moe = Math.max(0, oe - suffix);
        int mns = ns + prefix;
        int mne = Math.max(0, ne - suffix);
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
            Map<Integer, List<Integer>> matchList = new HashMap<>();
            for (int ni = mns; ni < mne; ni++) {
                int v = nw.value(ni);
                if (v >= oldDistinct) {
                    continue;
                }
                matchList.computeIfAbsent(v, k -> new ArrayList<>()).add(ni);
            }
            List<int[]> anchors = huntAnchors(old, mos, moe, mne - mns, matchList);
            if (anchors != null) {
                emitAnchoredScript(d, mos, moe, mns, mne, anchors);
            } else {
                myers(new NoFinishHook<>(d), old, mos, moe, nw, mns, mne, raw);
            }
        }
        if (suffix > 0) {
            d.equal(oe - suffix, ne - suffix, suffix);
        }
        d.finish();
    }

    private static int lowerBound(List<Integer> slice, int value) {
        int lo = 0;
        int hi = slice.size();
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (slice.get(mid) < value) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private static List<int[]> huntAnchors(Lookup.IntLookup old, int os, int oe, int newLen,
            Map<Integer, List<Integer>> matchList) {
        long maxPairs = Math.min(((long) (oe - os) + newLen) * MAX_MATCH_PAIRS_PER_INPUT_ITEM, MAX_MATCH_PAIRS);
        long pairs = 0;
        for (int oi = os; oi < oe; oi++) {
            List<Integer> l = matchList.get(old.value(oi));
            if (l != null) {
                pairs += l.size();
                if (pairs > maxPairs) {
                    return null;
                }
            }
        }
        List<Integer> thresh = new ArrayList<>();
        List<Integer> links = new ArrayList<>();
        List<int[]> candidates = new ArrayList<>();
        long lastK = -1;
        for (int oi = os; oi < oe; oi++) {
            List<Integer> l = matchList.get(old.value(oi));
            if (l == null) {
                continue;
            }
            for (int idx = l.size() - 1; idx >= 0; idx--) {
                int ni = l.get(idx);
                long guess = lastK + 1;
                int k;
                if (guess <= thresh.size() && (guess == 0 || thresh.get((int) guess - 1) < ni)
                        && (guess == thresh.size() || ni <= thresh.get((int) guess))) {
                    k = (int) guess;
                } else {
                    k = lowerBound(thresh, ni);
                }
                lastK = k;
                if (k == thresh.size() || ni < thresh.get(k)) {
                    int prev = k > 0 ? links.get(k - 1) : -1;
                    int ci = candidates.size();
                    candidates.add(new int[] {oi, ni, prev});
                    if (k == thresh.size()) {
                        thresh.add(ni);
                        links.add(ci);
                    } else {
                        thresh.set(k, ni);
                        links.set(k, ci);
                    }
                }
            }
        }
        List<int[]> anchors = new ArrayList<>();
        if (links.isEmpty()) {
            return anchors;
        }
        int ptr = links.get(links.size() - 1);
        while (true) {
            int[] c = candidates.get(ptr);
            anchors.add(new int[] {c[0], c[1]});
            if (c[2] == -1) {
                break;
            }
            ptr = c[2];
        }
        java.util.Collections.reverse(anchors);
        return anchors;
    }

    private static void emitAnchoredScript(DiffHook d, int oldStart, int oldEnd, int newStart, int newEnd,
            List<int[]> anchors) {
        int oldIndex = oldStart;
        int newIndex = newStart;
        int ptr = 0;
        while (ptr < anchors.size()) {
            int runOld = anchors.get(ptr)[0];
            int runNew = anchors.get(ptr)[1];
            if (oldIndex < runOld) {
                d.delete(oldIndex, runOld - oldIndex, newIndex);
                oldIndex = runOld;
            }
            if (newIndex < runNew) {
                d.insert(oldIndex, newIndex, runNew - newIndex);
            }
            int run = 1;
            ptr++;
            while (ptr < anchors.size() && anchors.get(ptr)[0] == runOld + run && anchors.get(ptr)[1] == runNew + run) {
                run++;
                ptr++;
            }
            d.equal(runOld, runNew, run);
            oldIndex = runOld + run;
            newIndex = runNew + run;
        }
        if (oldIndex < oldEnd) {
            d.delete(oldIndex, oldEnd - oldIndex, newIndex);
            oldIndex = oldEnd;
        }
        if (newIndex < newEnd) {
            d.insert(oldIndex, newIndex, newEnd - newIndex);
        }
    }
}
