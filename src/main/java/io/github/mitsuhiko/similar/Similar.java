package io.github.mitsuhiko.similar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class Similar {
    private Similar() {
    }

    public static <T> List<DiffOp> captureDiff(Algorithm alg, List<T> old, int oldStart, int oldEnd, List<T> new_,
            int newStart, int newEnd) {
        return Algorithms.captureDiff(alg, Lookup.of(old), oldStart, oldEnd, Lookup.of(new_), newStart, newEnd);
    }

    public static <T> List<DiffOp> captureDiffSlices(Algorithm alg, List<T> old, List<T> new_) {
        return captureDiff(alg, old, 0, old.size(), new_, 0, new_.size());
    }

    public static <T, K> List<DiffOp> captureDiffSlicesByKey(Algorithm alg, List<T> old, List<T> new_,
            Function<? super T, ? extends K> key) {
        List<K> oldKeys = new ArrayList<>(old.size());
        for (T v : old) {
            oldKeys.add(key.apply(v));
        }
        List<K> newKeys = new ArrayList<>(new_.size());
        for (T v : new_) {
            newKeys.add(key.apply(v));
        }
        return captureDiff(alg, oldKeys, 0, oldKeys.size(), newKeys, 0, newKeys.size());
    }

    public static float diffRatio(List<DiffOp> ops, int oldLen, int newLen) {
        long matches = 0;
        for (DiffOp op : ops) {
            if (op.tag() == DiffTag.EQUAL) {
                matches += op.len();
            }
        }
        long len = (long) oldLen + newLen;
        if (len == 0) {
            return 1.0f;
        }
        return 2.0f * (float) matches / (float) len;
    }

    public static List<List<DiffOp>> groupDiffOps(List<DiffOp> input, int n) {
        List<List<DiffOp>> rv = new ArrayList<>();
        if (input.isEmpty()) {
            return rv;
        }
        List<DiffOp> ops = new ArrayList<>(input);
        DiffOp first = ops.get(0);
        if (first.tag() == DiffTag.EQUAL) {
            int offset = Math.max(0, first.len() - n);
            ops.set(0, DiffOp.equal(first.oldIndex() + offset, first.newIndex() + offset, first.len() - offset));
        }
        DiffOp last = ops.get(ops.size() - 1);
        if (last.tag() == DiffTag.EQUAL) {
            int cut = Math.max(0, last.len() - n);
            ops.set(ops.size() - 1, DiffOp.equal(last.oldIndex(), last.newIndex(), last.len() - cut));
        }
        List<DiffOp> pending = new ArrayList<>();
        for (DiffOp op : ops) {
            if (op.tag() == DiffTag.EQUAL && (long) op.len() > (long) n * 2) {
                pending.add(DiffOp.equal(op.oldIndex(), op.newIndex(), n));
                rv.add(pending);
                int offset = Math.max(0, op.len() - n);
                pending = new ArrayList<>();
                pending.add(DiffOp.equal(op.oldIndex() + offset, op.newIndex() + offset, op.len() - offset));
                continue;
            }
            pending.add(op);
        }
        if (!(pending.isEmpty() || (pending.size() == 1 && pending.get(0).tag() == DiffTag.EQUAL))) {
            rv.add(pending);
        }
        return rv;
    }

    public static List<String> getCloseMatches(String word, List<String> possibilities, int n, float cutoff) {
        List<String> seq1 = TextTokens.chars(word);
        Map<String, Integer> counts = new HashMap<>();
        int unique = 0;
        for (String w : seq1) {
            Integer c = counts.get(w);
            if (c == null) {
                counts.put(w, 1);
                unique++;
            } else {
                counts.put(w, c + 1);
            }
        }
        List<Object[]> matches = new ArrayList<>();
        for (String possibility : possibilities) {
            List<String> seq2 = TextTokens.chars(possibility);
            if (Inline.upperSeqRatio(seq1.size(), seq2.size()) < cutoff
                    || quickRatio(counts, unique, seq2) < cutoff) {
                continue;
            }
            float ratio = TextDiff.fromSlices(seq1, seq2).ratio();
            if (ratio >= cutoff) {
                float scaled = ratio * 4294967296.0f;
                long key;
                if (Float.isNaN(scaled) || scaled <= 0) {
                    key = 0;
                } else {
                    key = Math.min((long) scaled, 4294967295L);
                }
                matches.add(new Object[] {key, possibility});
            }
        }
        matches.sort((a, b) -> {
            int c = Long.compare((Long) b[0], (Long) a[0]);
            if (c != 0) {
                return c;
            }
            return TextTokens.compareUtf8((String) a[1], (String) b[1]);
        });
        List<String> rv = new ArrayList<>();
        for (int i = 0; i < n && i < matches.size(); i++) {
            rv.add((String) matches.get(i)[1]);
        }
        return rv;
    }

    private static float quickRatio(Map<String, Integer> counts, int unique, List<String> seq) {
        int n = unique + seq.size();
        if (n == 0) {
            return 1.0f;
        }
        Map<String, Integer> available = new HashMap<>();
        int matches = 0;
        for (String w : seq) {
            Integer x = available.get(w);
            if (x == null) {
                x = counts.getOrDefault(w, 0);
            }
            available.put(w, x - 1);
            if (x > 0) {
                matches++;
            }
        }
        return 2.0f * (float) matches / (float) n;
    }

    public static String unifiedDiff(Algorithm alg, String old, String new_, int n, String oldHeader,
            String newHeader) {
        UnifiedDiff u = TextDiff.configure().algorithm(alg).diffLines(old, new_).unifiedDiff().contextRadius(n);
        if (oldHeader != null || newHeader != null) {
            u.header(oldHeader == null ? "" : oldHeader, newHeader == null ? "" : newHeader);
        }
        return u.toString();
    }

    public static <T> List<TaggedSlice<List<T>>> diffSlices(Algorithm alg, List<T> old, List<T> new_) {
        List<TaggedSlice<List<T>>> rv = new ArrayList<>();
        for (DiffOp op : captureDiffSlices(alg, old, new_)) {
            rv.addAll(op.iterSlices(old, new_));
        }
        return rv;
    }

    public static List<TaggedSlice<String>> diffChars(Algorithm alg, String old, String new_) {
        return remapped(TextDiff.configure().algorithm(alg).diffChars(old, new_), old, new_);
    }

    public static List<TaggedSlice<String>> diffWords(Algorithm alg, String old, String new_) {
        return remapped(TextDiff.configure().algorithm(alg).diffWords(old, new_), old, new_);
    }

    public static List<TaggedSlice<String>> diffLines(Algorithm alg, String old, String new_) {
        List<String> o = TextTokens.lines(old);
        List<String> n = TextTokens.lines(new_);
        List<TaggedSlice<String>> rv = new ArrayList<>();
        for (DiffOp op : captureDiffSlices(alg, o, n)) {
            for (Change<String> c : op.<String>iterChanges(o, n)) {
                rv.add(new TaggedSlice<>(c.tag(), c.value()));
            }
        }
        return rv;
    }

    private static int[] offsets(List<String> tokens) {
        int[] rv = new int[tokens.size() + 1];
        for (int i = 0; i < tokens.size(); i++) {
            rv[i + 1] = rv[i] + tokens.get(i).length();
        }
        return rv;
    }

    private static List<TaggedSlice<String>> remapped(TextDiff diff, String old, String new_) {
        int[] oo = offsets(diff.oldSlices());
        int[] no = offsets(diff.newSlices());
        List<TaggedSlice<String>> rv = new ArrayList<>();
        for (DiffOp op : diff.ops()) {
            switch (op.tag()) {
                case EQUAL -> rv.add(new TaggedSlice<>(ChangeTag.EQUAL,
                        old.substring(oo[op.oldIndex()], oo[op.oldIndex() + op.len()])));
                case DELETE -> rv.add(new TaggedSlice<>(ChangeTag.DELETE,
                        old.substring(oo[op.oldIndex()], oo[op.oldIndex() + op.oldLen()])));
                case INSERT -> rv.add(new TaggedSlice<>(ChangeTag.INSERT,
                        new_.substring(no[op.newIndex()], no[op.newIndex() + op.newLen()])));
                case REPLACE -> {
                    rv.add(new TaggedSlice<>(ChangeTag.DELETE,
                            old.substring(oo[op.oldIndex()], oo[op.oldIndex() + op.oldLen()])));
                    rv.add(new TaggedSlice<>(ChangeTag.INSERT,
                            new_.substring(no[op.newIndex()], no[op.newIndex() + op.newLen()])));
                }
            }
        }
        return rv;
    }
}
