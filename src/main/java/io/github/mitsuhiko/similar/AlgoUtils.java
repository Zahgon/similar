package io.github.mitsuhiko.similar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class AlgoUtils {
    private AlgoUtils() {
    }

    static int commonPrefixLen(Lookup old, int os, int oe, Lookup nw, int ns, int ne) {
        if (!(os < oe) || !(ns < ne)) {
            return 0;
        }
        int count = 0;
        int n = ns;
        int o = os;
        while (n < ne && o < oe && Lookup.eq(nw, n, old, o)) {
            n++;
            o++;
            count++;
        }
        return count;
    }

    static int commonSuffixLen(Lookup old, int os, int oe, Lookup nw, int ns, int ne) {
        if (!(os < oe) || !(ns < ne)) {
            return 0;
        }
        int count = 0;
        int n = ne - 1;
        int o = oe - 1;
        while (n >= ns && o >= os && Lookup.eq(nw, n, old, o)) {
            n--;
            o--;
            count++;
        }
        return count;
    }

    /** Values unique within the range, as a lookup plus their original indexes. */
    static final class Unique extends Lookup {
        final Lookup source;
        final int[] indexes;

        Unique(Lookup source, int[] indexes) {
            this.source = source;
            this.indexes = indexes;
        }

        int len() {
            return indexes.length;
        }

        int originalIndex(int i) {
            return indexes[i];
        }

        @Override
        Object at(int index) {
            return source.at(indexes[index]);
        }
    }

    static Unique unique(Lookup lookup, int start, int end) {
        Map<Object, int[]> seen = new HashMap<>();
        List<int[]> order = new ArrayList<>();
        for (int index = start; index < end; index++) {
            Object value = lookup.at(index);
            int[] entry = seen.get(value);
            if (entry == null) {
                entry = new int[] {index, 1};
                seen.put(value, entry);
                order.add(entry);
            } else {
                entry[1] = 0;
            }
        }
        int count = 0;
        for (int[] e : order) {
            if (e[1] == 1) {
                count++;
            }
        }
        int[] rv = new int[count];
        int i = 0;
        for (int[] e : order) {
            if (e[1] == 1) {
                rv[i++] = e[0];
            }
        }
        return new Unique(lookup, rv);
    }
}
