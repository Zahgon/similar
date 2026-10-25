package io.github.mitsuhiko.similar;

/** Myers' middle-snake diff with the bounded heuristic search and its raw variant. */
final class Myers {
    private Myers() {
    }

    private static final int SMALL_SIDE_EXACT_MAX = 64;
    private static final int SMALL_SIDE_EXACT_MIN_LARGE = 512;
    private static final long SMALL_SIDE_EXACT_MAX_WORK = 64_000_000L;
    private static final int HEURISTIC_MIN_COST = 256;
    private static final int HEURISTIC_MIN_SNAKE = 20;
    private static final int HEURISTIC_MIN_MAX_COST = 256;
    private static final int HEURISTIC_PROGRESS_FACTOR = 4;
    private static final int MAX_SKIP = 4;
    private static final int MIN_ANCHOR_COMMON = 96;

    static void diff(DiffHook d, Lookup old, int os, int oe, Lookup nw, int ns, int ne) {
        Object pre = Preflight.reduceForMyers(old, os, oe, nw, ns, ne);
        if (pre instanceof Preflight.Reduced r) {
            diffReduced(d, r);
        } else {
            diffPretrimmed(d, old, nw, (Preflight.Trimmed) pre, true);
        }
    }

    static void diffRaw(DiffHook d, Lookup old, int os, int oe, Lookup nw, int ns, int ne) {
        diffImpl(d, old, os, oe, nw, ns, ne, false);
    }

    private static final class ReducedHook implements DiffHook {
        final DiffHook d;
        final int[] oldIndices;
        final int[] newIndices;
        int oldCursor;
        int newCursor;
        final int oldEnd;
        final int newEnd;

        ReducedHook(DiffHook d, int[] oldIndices, int[] newIndices, int oldCursor, int newCursor, int oldEnd,
                int newEnd) {
            this.d = d;
            this.oldIndices = oldIndices;
            this.newIndices = newIndices;
            this.oldCursor = oldCursor;
            this.newCursor = newCursor;
            this.oldEnd = oldEnd;
            this.newEnd = newEnd;
        }

        void emitGaps(int oldAnchor, int newAnchor) {
            if (oldCursor < oldAnchor) {
                d.delete(oldCursor, oldAnchor - oldCursor, newCursor);
                oldCursor = oldAnchor;
            }
            if (newCursor < newAnchor) {
                d.insert(oldCursor, newCursor, newAnchor - newCursor);
                newCursor = newAnchor;
            }
        }

        @Override
        public void equal(int oldIndex, int newIndex, int len) {
            int offset = 0;
            while (offset < len) {
                int oldAnchor = oldIndices[oldIndex + offset];
                int newAnchor = newIndices[newIndex + offset];
                emitGaps(oldAnchor, newAnchor);
                int run = 1;
                while (offset + run < len && oldIndices[oldIndex + offset + run] == oldAnchor + run
                        && newIndices[newIndex + offset + run] == newAnchor + run) {
                    run++;
                }
                d.equal(oldAnchor, newAnchor, run);
                oldCursor = oldAnchor + run;
                newCursor = newAnchor + run;
                offset += run;
            }
        }

        @Override
        public void delete(int oldIndex, int oldLen, int newIndex) {
        }

        @Override
        public void insert(int oldIndex, int newIndex, int newLen) {
        }

        @Override
        public void replace(int oldIndex, int oldLen, int newIndex, int newLen) {
        }

        @Override
        public void finish() {
            emitGaps(oldEnd, newEnd);
        }
    }

    private static void diffReduced(DiffHook d, Preflight.Reduced r) {
        Preflight.Trimmed t = r.trimmed;
        if (t.prefixLen > 0) {
            d.equal(t.origOldStart, t.origNewStart, t.prefixLen);
        }
        ReducedHook hook = new ReducedHook(d, r.oldIndices, r.newIndices, t.oldStart, t.newStart, t.oldEnd, t.newEnd);
        diffImpl(hook, new Lookup.IntLookup(0, r.oldValues), 0, r.oldValues.length,
                new Lookup.IntLookup(0, r.newValues), 0, r.newValues.length, true);
        if (t.suffixLen > 0) {
            d.equal(t.origOldEnd - t.suffixLen, t.origNewEnd - t.suffixLen, t.suffixLen);
        }
        d.finish();
    }

    private static void diffImpl(DiffHook d, Lookup old, int os, int oe, Lookup nw, int ns, int ne,
            boolean heuristic) {
        int prefix = AlgoUtils.commonPrefixLen(old, os, oe, nw, ns, ne);
        int aos = os + prefix;
        int ans = ns + prefix;
        int suffix = AlgoUtils.commonSuffixLen(old, aos, oe, nw, ans, ne);
        Preflight.Trimmed t = new Preflight.Trimmed();
        t.oldStart = aos;
        t.oldEnd = oe - suffix;
        t.newStart = ans;
        t.newEnd = ne - suffix;
        t.origOldStart = os;
        t.origOldEnd = oe;
        t.origNewStart = ns;
        t.origNewEnd = ne;
        t.prefixLen = prefix;
        t.suffixLen = suffix;
        diffPretrimmed(d, old, nw, t, heuristic);
    }

    private static void diffPretrimmed(DiffHook d, Lookup old, Lookup nw, Preflight.Trimmed t, boolean heuristic) {
        if (t.prefixLen > 0) {
            d.equal(t.origOldStart, t.origNewStart, t.prefixLen);
        }
        boolean oldEmpty = !(t.oldStart < t.oldEnd);
        boolean newEmpty = !(t.newStart < t.newEnd);
        if (oldEmpty && newEmpty) {
            // nothing
        } else if (oldEmpty) {
            d.insert(t.oldStart, t.newStart, t.newEnd - t.newStart);
        } else if (newEmpty) {
            d.delete(t.oldStart, t.oldEnd - t.oldStart, t.newStart);
        } else {
            int maxD = maxD(t.oldEnd - t.oldStart, t.newEnd - t.newStart);
            V vb = new V(maxD);
            V vf = new V(maxD);
            conquer(d, old, t.oldStart, t.oldEnd, nw, t.newStart, t.newEnd, vf, vb, heuristic);
        }
        if (t.suffixLen > 0) {
            d.equal(t.origOldEnd - t.suffixLen, t.origNewEnd - t.suffixLen, t.suffixLen);
        }
        d.finish();
    }

    private static final class V {
        final long offset;
        final long[] v;

        V(int maxD) {
            offset = maxD;
            v = new long[2 * maxD];
        }

        int len() {
            return v.length;
        }

        long get(long k) {
            return v[(int) (k + offset)];
        }

        void set(long k, long value) {
            v[(int) (k + offset)] = value;
        }
    }

    private static int maxD(int len1, int len2) {
        long sum = (long) len1 + len2;
        return (int) ((sum + 1) / 2 + 1);
    }

    private static int commonPrefixLenAt(Lookup old, int oldStart, int oldEnd, Lookup nw, int newStart, int newEnd) {
        int max = Math.min(oldEnd - oldStart, newEnd - newStart);
        int matched = 0;
        while (matched < max && Lookup.eq(nw, newStart + matched, old, oldStart + matched)) {
            matched++;
        }
        return matched;
    }

    private static int commonSuffixLenAt(Lookup old, int oldStart, int oldEnd, Lookup nw, int newStart, int newEnd) {
        int max = Math.min(oldEnd - oldStart, newEnd - newStart);
        int matched = 0;
        while (matched < max && Lookup.eq(nw, newEnd - 1 - matched, old, oldEnd - 1 - matched)) {
            matched++;
        }
        return matched;
    }

    private static boolean maybeEmitSmallSideExact(DiffHook d, Lookup old, int os, int oe, Lookup nw, int ns,
            int ne) {
        int oldLen = oe - os;
        int newLen = ne - ns;
        int small = Math.min(oldLen, newLen);
        int large = Math.max(oldLen, newLen);
        long work = (long) oldLen * newLen;
        if (small == 0 || small > SMALL_SIDE_EXACT_MAX || large < SMALL_SIDE_EXACT_MIN_LARGE
                || work > SMALL_SIDE_EXACT_MAX_WORK) {
            return false;
        }
        if (oldLen <= newLen) {
            emitSmallOldExact(d, old, os, oe, nw, ns, ne);
        } else {
            emitSmallNewExact(d, old, os, oe, nw, ns, ne);
        }
        return true;
    }

    private static void emitSmallOldExact(DiffHook d, Lookup old, int os, int oe, Lookup nw, int ns, int ne) {
        int n = oe - os;
        int m = ne - ns;
        int width = m + 1;
        int[] dp = new int[(n + 1) * width];
        for (int i = n - 1; i >= 0; i--) {
            int row = i * width;
            int next = (i + 1) * width;
            for (int j = m - 1; j >= 0; j--) {
                dp[row + j] = Lookup.eq(nw, ns + j, old, os + i) ? dp[next + j + 1] + 1
                        : Math.max(dp[next + j], dp[row + j + 1]);
            }
        }
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            int row = i * width;
            if (Lookup.eq(nw, ns + j, old, os + i) && dp[row + j] == dp[(i + 1) * width + j + 1] + 1) {
                int si = i;
                int sj = j;
                while (i < n && j < m) {
                    int r = i * width;
                    if (Lookup.eq(nw, ns + j, old, os + i) && dp[r + j] == dp[(i + 1) * width + j + 1] + 1) {
                        i++;
                        j++;
                    } else {
                        break;
                    }
                }
                d.equal(os + si, ns + sj, i - si);
            } else if (dp[(i + 1) * width + j] >= dp[row + j + 1]) {
                int si = i;
                int newIdx = ns + j;
                while (i < n) {
                    if (j >= m) {
                        i = n;
                        break;
                    }
                    int r = i * width;
                    if (Lookup.eq(nw, ns + j, old, os + i) && dp[r + j] == dp[(i + 1) * width + j + 1] + 1) {
                        break;
                    }
                    if (dp[(i + 1) * width + j] >= dp[r + j + 1]) {
                        i++;
                    } else {
                        break;
                    }
                }
                d.delete(os + si, i - si, newIdx);
            } else {
                int sj = j;
                int oldIdx = os + i;
                while (j < m) {
                    if (i >= n) {
                        j = m;
                        break;
                    }
                    int r = i * width;
                    if (Lookup.eq(nw, ns + j, old, os + i) && dp[r + j] == dp[(i + 1) * width + j + 1] + 1) {
                        break;
                    }
                    if (dp[(i + 1) * width + j] < dp[r + j + 1]) {
                        j++;
                    } else {
                        break;
                    }
                }
                d.insert(oldIdx, ns + sj, j - sj);
            }
        }
        if (i < n) {
            d.delete(os + i, n - i, ns + j);
        }
        if (j < m) {
            d.insert(os + i, ns + j, m - j);
        }
    }

    private static void emitSmallNewExact(DiffHook d, Lookup old, int os, int oe, Lookup nw, int ns, int ne) {
        int n = oe - os;
        int m = ne - ns;
        int width = n + 1;
        int[] dp = new int[(m + 1) * width];
        for (int i = m - 1; i >= 0; i--) {
            int row = i * width;
            int next = (i + 1) * width;
            for (int j = n - 1; j >= 0; j--) {
                dp[row + j] = Lookup.eq(nw, ns + i, old, os + j) ? dp[next + j + 1] + 1
                        : Math.max(dp[next + j], dp[row + j + 1]);
            }
        }
        int i = 0;
        int j = 0;
        while (i < m && j < n) {
            int row = i * width;
            if (Lookup.eq(nw, ns + i, old, os + j) && dp[row + j] == dp[(i + 1) * width + j + 1] + 1) {
                int si = i;
                int sj = j;
                while (i < m && j < n) {
                    int r = i * width;
                    if (Lookup.eq(nw, ns + i, old, os + j) && dp[r + j] == dp[(i + 1) * width + j + 1] + 1) {
                        i++;
                        j++;
                    } else {
                        break;
                    }
                }
                d.equal(os + sj, ns + si, j - sj);
            } else if (dp[(i + 1) * width + j] >= dp[row + j + 1]) {
                int si = i;
                int oldIdx = os + j;
                while (i < m) {
                    if (j >= n) {
                        i = m;
                        break;
                    }
                    int r = i * width;
                    if (Lookup.eq(nw, ns + i, old, os + j) && dp[r + j] == dp[(i + 1) * width + j + 1] + 1) {
                        break;
                    }
                    if (dp[(i + 1) * width + j] >= dp[r + j + 1]) {
                        i++;
                    } else {
                        break;
                    }
                }
                d.insert(oldIdx, ns + si, i - si);
            } else {
                int sj = j;
                int newIdx = ns + i;
                while (j < n) {
                    if (i >= m) {
                        j = n;
                        break;
                    }
                    int r = i * width;
                    if (Lookup.eq(nw, ns + i, old, os + j) && dp[r + j] == dp[(i + 1) * width + j + 1] + 1) {
                        break;
                    }
                    if (dp[(i + 1) * width + j] < dp[r + j + 1]) {
                        j++;
                    } else {
                        break;
                    }
                }
                d.delete(os + sj, j - sj, newIdx);
            }
        }
        if (j < n) {
            d.delete(os + j, n - j, ns + i);
        }
        if (i < m) {
            d.insert(os + j, ns + i, m - i);
        }
    }

    /** Returns {oldStart, newStart} after the anchor, or null when nothing was emitted. */
    private static int[] tryEmitFrontAnchor(DiffHook d, Lookup old, int os, int oe, Lookup nw, int ns, int ne) {
        int oldLen = oe - os;
        int newLen = ne - ns;
        if (oldLen <= MIN_ANCHOR_COMMON || newLen <= MIN_ANCHOR_COMMON) {
            return null;
        }
        int small = Math.min(oldLen, newLen);
        int large = Math.max(oldLen, newLen);
        if ((long) large < (long) small * 2) {
            return null;
        }
        int maxOldSkip = Math.min(oldLen - 1, MAX_SKIP);
        int maxNewSkip = Math.min(newLen - 1, MAX_SKIP);
        boolean preferInsert = newLen > oldLen;
        boolean preferDelete = oldLen > newLen;
        int[] best = null;
        search:
        for (int oldSkip = 0; oldSkip <= maxOldSkip; oldSkip++) {
            for (int newSkip = 0; newSkip <= maxNewSkip; newSkip++) {
                if (oldSkip == 0 && newSkip == 0) {
                    continue;
                }
                if (oldSkip != 0 && newSkip != 0) {
                    continue;
                }
                if (preferInsert && oldSkip != 0) {
                    continue;
                }
                if (preferDelete && newSkip != 0) {
                    continue;
                }
                if (!Lookup.eq(nw, ns + newSkip, old, os + oldSkip)) {
                    continue;
                }
                int common = commonPrefixLenAt(old, os + oldSkip, oe, nw, ns + newSkip, ne);
                if (common >= MIN_ANCHOR_COMMON) {
                    if (common >= MIN_ANCHOR_COMMON * 8) {
                        best = new int[] {oldSkip, newSkip, common};
                        break search;
                    }
                    boolean better;
                    if (best == null) {
                        better = true;
                    } else {
                        int span = Math.max(oldSkip, newSkip);
                        int bestSpan = Math.max(best[0], best[1]);
                        if (common != best[2]) {
                            better = common > best[2];
                        } else if (span != bestSpan) {
                            better = span < bestSpan;
                        } else if (oldSkip != best[0]) {
                            better = oldSkip > best[0];
                        } else {
                            better = newSkip > best[1];
                        }
                    }
                    if (better) {
                        best = new int[] {oldSkip, newSkip, common};
                    }
                }
            }
        }
        if (best == null) {
            return null;
        }
        int oldSkip = best[0];
        int newSkip = best[1];
        int common = best[2];
        if (oldSkip == 0 && newSkip == 0) {
            // nothing
        } else if (oldSkip == 0) {
            d.insert(os, ns, newSkip);
        } else if (newSkip == 0) {
            d.delete(os, oldSkip, ns);
        } else {
            d.replace(os, oldSkip, ns, newSkip);
        }
        d.equal(os + oldSkip, ns + newSkip, common);
        return new int[] {os + oldSkip + common, ns + newSkip + common};
    }

    private static final class Split {
        long oldIndex;
        long newIndex;
        long progress;
        boolean forward;

        Split(long oldIndex, long newIndex, long progress, boolean forward) {
            this.oldIndex = oldIndex;
            this.newIndex = newIndex;
            this.progress = progress;
            this.forward = forward;
        }
    }

    /** Split point with the modes of the left and right halves. */
    private static final class MiddleSnake {
        final int oldIndex;
        final int newIndex;
        final boolean leftHeuristic;
        final boolean rightHeuristic;

        MiddleSnake(int oldIndex, int newIndex, boolean leftHeuristic, boolean rightHeuristic) {
            this.oldIndex = oldIndex;
            this.newIndex = newIndex;
            this.leftHeuristic = leftHeuristic;
            this.rightHeuristic = rightHeuristic;
        }

        static MiddleSnake exact(long oldIndex, long newIndex) {
            return new MiddleSnake((int) oldIndex, (int) newIndex, false, false);
        }

        static MiddleSnake heuristic(Split split, int oldStart, int newStart) {
            return new MiddleSnake((int) (oldStart + split.oldIndex), (int) (newStart + split.newIndex),
                    !split.forward, split.forward);
        }
    }

    private static Split better(Split best, Split candidate) {
        if (best == null || candidate.progress > best.progress) {
            return candidate;
        }
        return best;
    }

    private static boolean isInteriorSplit(long oldIndex, long newIndex, long oldLen, long newLen) {
        return oldIndex <= oldLen && newIndex <= newLen && (oldIndex != 0 || newIndex != 0)
                && (oldIndex != oldLen || newIndex != newLen);
    }

    private static long[] clampDiagonalPoint(long oldIndex, long diagonal, long oldLen, long newLen) {
        if (diagonal < 0 && -diagonal > newLen) {
            return null;
        }
        long minOld = Math.max(diagonal, 0);
        long maxOld = diagonal >= 0 ? Math.min(oldLen, newLen + diagonal)
                : Math.min(oldLen, Math.max(0, newLen - (-diagonal)));
        if (minOld > maxOld) {
            return null;
        }
        long o = Math.max(minOld, Math.min(maxOld, oldIndex));
        return new long[] {o, o - diagonal};
    }

    private static long heuristicMaxCost(long oldLen, long newLen) {
        return Math.max(Preflight.isqrt(oldLen + newLen), HEURISTIC_MIN_MAX_COST);
    }

    private static MiddleSnake findMiddleSnake(Lookup old, int os, int oe, Lookup nw, int ns, int ne, V vf, V vb,
            boolean heuristic) {
        long n = oe - os;
        long m = ne - ns;
        long delta = n - m;
        boolean odd = (delta & 1) == 1;
        vf.set(1, 0);
        vb.set(1, 0);
        long dMax = maxD((int) n, (int) m);
        if (vf.len() < dMax || vb.len() < dMax) {
            throw new IllegalStateException("assertion failed");
        }
        long maxCost = heuristic ? heuristicMaxCost(n, m) : Long.MAX_VALUE;
        for (long d = 0; d < dMax; d++) {
            Split good = null;
            Split forward = null;
            Split backward = null;
            boolean trackLimit = heuristic && d >= maxCost;
            boolean trackGood = heuristic && d > HEURISTIC_MIN_COST;
            for (long k = d; k >= -d; k -= 2) {
                long x;
                if (k == -d || (k != d && vf.get(k - 1) < vf.get(k + 1))) {
                    x = vf.get(k + 1);
                } else {
                    x = vf.get(k - 1) + 1;
                }
                long y = x - k;
                long x0 = x;
                long y0 = y;
                boolean yValid = y >= 0;
                if (x < n && yValid && y < m) {
                    x += commonPrefixLenAt(old, (int) (os + x), oe, nw, (int) (ns + y), ne);
                }
                long snake = x - x0;
                vf.set(k, x);
                if (odd && Math.abs(k - delta) <= d - 1 && vf.get(k) + vb.get(-(k - delta)) >= n) {
                    return MiddleSnake.exact(x0 + os, y0 + ns);
                }
                if (trackLimit) {
                    long[] p = clampDiagonalPoint(x, k, n, m);
                    if (p != null && isInteriorSplit(p[0], p[1], n, m)) {
                        forward = better(forward, new Split(p[0], p[1], p[0] + p[1], true));
                    }
                }
                if (trackGood && snake >= HEURISTIC_MIN_SNAKE && x < n && yValid && y < m) {
                    long useful = Math.max(0, x + y - Math.abs(k));
                    if (useful > HEURISTIC_PROGRESS_FACTOR * d && isInteriorSplit(x0, y0, n, m)) {
                        good = better(good, new Split(x0, y0, useful, true));
                    }
                }
            }
            for (long k = d; k >= -d; k -= 2) {
                long x;
                if (k == -d || (k != d && vb.get(k - 1) < vb.get(k + 1))) {
                    x = vb.get(k + 1);
                } else {
                    x = vb.get(k - 1) + 1;
                }
                long y = x - k;
                long x0 = x;
                boolean yValid = y >= 0;
                if (x < n && yValid && y < m) {
                    int advance = commonSuffixLenAt(old, os, (int) (os + n - x), nw, ns, (int) (ns + m - y));
                    x += advance;
                    y += advance;
                }
                long snake = x - x0;
                vb.set(k, x);
                if (!odd && Math.abs(k - delta) <= d && vb.get(k) + vf.get(-(k - delta)) >= n) {
                    return MiddleSnake.exact(n - x + os, m - y + ns);
                }
                if (trackLimit) {
                    long[] p = clampDiagonalPoint(x, k, n, m);
                    if (p != null) {
                        long so = n - p[0];
                        long sn = m - p[1];
                        if (isInteriorSplit(so, sn, n, m)) {
                            backward = better(backward, new Split(so, sn, p[0] + p[1], false));
                        }
                    }
                }
                if (trackGood && snake >= HEURISTIC_MIN_SNAKE && x <= n && yValid && y <= m) {
                    long so = n - x;
                    long sn = m - y;
                    long useful = Math.max(0, x + y - Math.abs(k));
                    if (so > 0 && sn > 0 && useful > HEURISTIC_PROGRESS_FACTOR * d) {
                        good = better(good, new Split(so, sn, useful, false));
                    }
                }
            }
            if (good != null) {
                return MiddleSnake.heuristic(good, os, ns);
            }
            if (d >= maxCost) {
                Split split;
                if (forward != null && backward != null) {
                    split = forward.progress > backward.progress ? forward : backward;
                } else if (forward != null) {
                    split = forward;
                } else if (backward != null) {
                    split = backward;
                } else {
                    break;
                }
                return MiddleSnake.heuristic(split, os, ns);
            }
        }
        return null;
    }

    private static void conquer(DiffHook d, Lookup old, int os, int oe, Lookup nw, int ns, int ne, V vf, V vb,
            boolean heuristic) {
        int prefix = AlgoUtils.commonPrefixLen(old, os, oe, nw, ns, ne);
        if (prefix > 0) {
            d.equal(os, ns, prefix);
        }
        os += prefix;
        ns += prefix;
        int suffix = AlgoUtils.commonSuffixLen(old, os, oe, nw, ns, ne);
        int suffixOld = oe - suffix;
        int suffixNew = ne - suffix;
        oe -= suffix;
        ne -= suffix;
        while (os < oe && ns < ne) {
            int[] moved = tryEmitFrontAnchor(d, old, os, oe, nw, ns, ne);
            if (moved == null) {
                break;
            }
            os = moved[0];
            ns = moved[1];
        }
        boolean oldEmpty = !(os < oe);
        boolean newEmpty = !(ns < ne);
        if (oldEmpty && newEmpty) {
            // nothing
        } else if (newEmpty) {
            d.delete(os, oe - os, ns);
        } else if (oldEmpty) {
            d.insert(os, ns, ne - ns);
        } else if (maybeEmitSmallSideExact(d, old, os, oe, nw, ns, ne)) {
            // emitted
        } else {
            MiddleSnake split = findMiddleSnake(old, os, oe, nw, ns, ne, vf, vb, heuristic);
            if (split != null) {
                conquer(d, old, os, split.oldIndex, nw, ns, split.newIndex, vf, vb, split.leftHeuristic);
                conquer(d, old, split.oldIndex, oe, nw, split.newIndex, ne, vf, vb, split.rightHeuristic);
            } else {
                d.delete(os, oe - os, ns);
                d.insert(os, ns, ne - ns);
            }
        }
        if (suffix > 0) {
            d.equal(suffixOld, suffixNew, suffix);
        }
    }
}
