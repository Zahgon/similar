package io.github.mitsuhiko.similar;

final class Patience {
    private Patience() {
    }

    static void diff(DiffHook d, Lookup old, int os, int oe, Lookup nw, int ns, int ne, boolean raw) {
        if (!raw && Preflight.maybeEmitReplaceFastPath(d, old, os, oe, nw, ns, ne)) {
            return;
        }
        if (!(os < oe) || !(ns < ne)) {
            if (ns < ne) {
                d.insert(os, ns, ne - ns);
            } else if (os < oe) {
                d.delete(os, oe - os, ns);
            }
            d.finish();
            return;
        }
        int prefix = AlgoUtils.commonPrefixLen(old, os, oe, nw, ns, ne);
        if (oe - os == ne - ns && prefix == oe - os) {
            if (prefix > 0) {
                d.equal(os, ns, prefix);
            }
            d.finish();
            return;
        }
        AlgoUtils.Unique oldIndexes = AlgoUtils.unique(old, os, oe);
        AlgoUtils.Unique newIndexes = AlgoUtils.unique(nw, ns, ne);
        Hook hook = new Hook(d, old, os, oe, oldIndexes, nw, ns, ne, newIndexes, raw);
        Replace<Hook> patienceD = new Replace<>(hook);
        if (raw) {
            Myers.diffRaw(patienceD, oldIndexes, 0, oldIndexes.len(), newIndexes, 0, newIndexes.len());
        } else {
            Myers.diff(patienceD, oldIndexes, 0, oldIndexes.len(), newIndexes, 0, newIndexes.len());
        }
    }

    private static final class Hook implements DiffHook {
        final DiffHook d;
        final Lookup old;
        int oldCurrent;
        final int oldEnd;
        final AlgoUtils.Unique oldIndexes;
        final Lookup nw;
        int newCurrent;
        final int newEnd;
        final AlgoUtils.Unique newIndexes;
        final boolean raw;

        Hook(DiffHook d, Lookup old, int os, int oe, AlgoUtils.Unique oldIndexes, Lookup nw, int ns, int ne,
                AlgoUtils.Unique newIndexes, boolean raw) {
            this.d = d;
            this.old = old;
            this.oldCurrent = os;
            this.oldEnd = oe;
            this.oldIndexes = oldIndexes;
            this.nw = nw;
            this.newCurrent = ns;
            this.newEnd = ne;
            this.newIndexes = newIndexes;
            this.raw = raw;
        }

        private void myers(DiffHook hook, int os, int oe, int ns, int ne) {
            if (raw) {
                Myers.diffRaw(hook, old, os, oe, nw, ns, ne);
            } else {
                Myers.diff(hook, old, os, oe, nw, ns, ne);
            }
        }

        @Override
        public void equal(int o, int n, int len) {
            for (int i = 0; i < len; i++) {
                int oi = o + i;
                int ni = n + i;
                int a0 = oldCurrent;
                int b0 = newCurrent;
                int oldTarget = oldIndexes.originalIndex(oi);
                int newTarget = newIndexes.originalIndex(ni);
                while (oldCurrent < oldTarget && newCurrent < newTarget
                        && Lookup.eq(nw, newCurrent, old, oldCurrent)) {
                    oldCurrent++;
                    newCurrent++;
                }
                if (oldCurrent > a0) {
                    d.equal(a0, b0, oldCurrent - a0);
                }
                myers(new NoFinishHook<>(d), oldCurrent, oldTarget, newCurrent, newTarget);
                oldCurrent = oldTarget;
                newCurrent = newTarget;
            }
        }

        @Override
        public void finish() {
            myers(d, oldCurrent, oldEnd, newCurrent, newEnd);
        }
    }
}
