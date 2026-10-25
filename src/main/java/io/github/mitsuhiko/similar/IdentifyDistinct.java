package io.github.mitsuhiko.similar;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class IdentifyDistinct {
    private final Lookup.IntLookup old;
    private final Lookup.IntLookup new_;
    private final int oldDistinct;

    public <T> IdentifyDistinct(List<T> old, int oldStart, int oldEnd, List<T> new_, int newStart, int newEnd) {
        this(Lookup.of(old), oldStart, oldEnd, Lookup.of(new_), newStart, newEnd, false);
    }

    IdentifyDistinct(Lookup old, int oldStart, int oldEnd, Lookup new_, int newStart, int newEnd,
            boolean collapseNewOnly) {
        Map<Object, Integer> map = new HashMap<>();
        int[] oldSeq = new int[Math.max(0, oldEnd - oldStart)];
        int[] newSeq = new int[Math.max(0, newEnd - newStart)];
        int nextId = 0;
        int distinct = 0;
        for (int idx = oldStart; idx < oldEnd; idx++) {
            Object value = old.at(idx);
            Integer id = map.get(value);
            if (id == null) {
                id = nextId++;
                distinct++;
                map.put(value, id);
            }
            oldSeq[idx - oldStart] = id;
        }
        int newOnlyId = nextId;
        if (collapseNewOnly) {
            nextId++;
        }
        for (int idx = newStart; idx < newEnd; idx++) {
            Object value = new_.at(idx);
            Integer id = map.get(value);
            if (id == null) {
                if (collapseNewOnly) {
                    id = newOnlyId;
                } else {
                    id = nextId++;
                    map.put(value, id);
                }
            }
            newSeq[idx - newStart] = id;
        }
        this.old = new Lookup.IntLookup(oldStart, oldSeq);
        this.new_ = new Lookup.IntLookup(newStart, newSeq);
        this.oldDistinct = distinct;
    }

    int oldDistinctCount() {
        return oldDistinct;
    }

    Lookup.IntLookup oldIds() {
        return old;
    }

    Lookup.IntLookup newIds() {
        return new_;
    }

    public int oldId(int index) {
        return old.value(index);
    }

    public int newId(int index) {
        return new_.value(index);
    }

    public Range oldRange() {
        return new Range(old.offset, old.offset + old.values.length);
    }

    public Range newRange() {
        return new Range(new_.offset, new_.offset + new_.values.length);
    }
}
