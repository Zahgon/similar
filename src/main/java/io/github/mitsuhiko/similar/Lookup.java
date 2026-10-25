package io.github.mitsuhiko.similar;

import java.util.List;
import java.util.Objects;

/** Random access over one side of a diff; equality follows Rust's {@code new[i] == old[j]}. */
abstract class Lookup {
    abstract Object at(int index);

    static Lookup of(List<?> list) {
        return new ListLookup(list);
    }

    static boolean eq(Lookup newSide, int newIndex, Lookup oldSide, int oldIndex) {
        if (newSide instanceof IntLookup n && oldSide instanceof IntLookup o) {
            return n.value(newIndex) == o.value(oldIndex);
        }
        return Objects.equals(newSide.at(newIndex), oldSide.at(oldIndex));
    }

    static final class ListLookup extends Lookup {
        private final List<?> list;

        ListLookup(List<?> list) {
            this.list = list;
        }

        @Override
        Object at(int index) {
            return list.get(index);
        }
    }

    /** Integer ids starting at {@code offset}. */
    static final class IntLookup extends Lookup {
        final int offset;
        final int[] values;

        IntLookup(int offset, int[] values) {
            this.offset = offset;
            this.values = values;
        }

        int value(int index) {
            return values[index - offset];
        }

        @Override
        Object at(int index) {
            return values[index - offset];
        }
    }
}
