package io.github.mitsuhiko.similar;

public interface DiffHook {
    default void equal(int oldIndex, int newIndex, int len) {
    }

    default void delete(int oldIndex, int oldLen, int newIndex) {
    }

    default void insert(int oldIndex, int newIndex, int newLen) {
    }

    default void replace(int oldIndex, int oldLen, int newIndex, int newLen) {
        delete(oldIndex, oldLen, newIndex);
        insert(oldIndex, newIndex, newLen);
    }

    default void finish() {
    }
}
