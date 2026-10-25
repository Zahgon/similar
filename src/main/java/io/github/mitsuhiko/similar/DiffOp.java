package io.github.mitsuhiko.similar;

import java.util.ArrayList;
import java.util.List;

public final class DiffOp {
    private final DiffTag tag;
    private final int oldIndex;
    private final int newIndex;
    private final int oldLen;
    private final int newLen;

    private DiffOp(DiffTag tag, int oldIndex, int oldLen, int newIndex, int newLen) {
        this.tag = tag;
        this.oldIndex = oldIndex;
        this.oldLen = oldLen;
        this.newIndex = newIndex;
        this.newLen = newLen;
    }

    public static DiffOp equal(int oldIndex, int newIndex, int len) {
        return new DiffOp(DiffTag.EQUAL, oldIndex, len, newIndex, len);
    }

    public static DiffOp delete(int oldIndex, int oldLen, int newIndex) {
        return new DiffOp(DiffTag.DELETE, oldIndex, oldLen, newIndex, 0);
    }

    public static DiffOp insert(int oldIndex, int newIndex, int newLen) {
        return new DiffOp(DiffTag.INSERT, oldIndex, 0, newIndex, newLen);
    }

    public static DiffOp replace(int oldIndex, int oldLen, int newIndex, int newLen) {
        return new DiffOp(DiffTag.REPLACE, oldIndex, oldLen, newIndex, newLen);
    }

    public DiffTag tag() {
        return tag;
    }

    public int oldIndex() {
        return oldIndex;
    }

    public int newIndex() {
        return newIndex;
    }

    public int oldLen() {
        return oldLen;
    }

    public int newLen() {
        return newLen;
    }

    public int len() {
        return tag == DiffTag.EQUAL ? oldLen : 0;
    }

    public Range oldRange() {
        return new Range(oldIndex, oldIndex + oldLen);
    }

    public Range newRange() {
        return new Range(newIndex, newIndex + newLen);
    }

    public void applyToHook(DiffHook d) {
        switch (tag) {
            case EQUAL -> d.equal(oldIndex, newIndex, oldLen);
            case DELETE -> d.delete(oldIndex, oldLen, newIndex);
            case INSERT -> d.insert(oldIndex, newIndex, newLen);
            case REPLACE -> d.replace(oldIndex, oldLen, newIndex, newLen);
        }
    }

    public <T> List<Change<T>> iterChanges(List<? extends T> old, List<? extends T> new_) {
        List<Change<T>> rv = new ArrayList<>();
        switch (tag) {
            case EQUAL -> {
                for (int i = 0; i < oldLen; i++) {
                    rv.add(new Change<>(ChangeTag.EQUAL, oldIndex + i, newIndex + i, old.get(oldIndex + i)));
                }
            }
            case DELETE -> {
                for (int i = 0; i < oldLen; i++) {
                    rv.add(new Change<>(ChangeTag.DELETE, oldIndex + i, null, old.get(oldIndex + i)));
                }
            }
            case INSERT -> {
                for (int i = 0; i < newLen; i++) {
                    rv.add(new Change<>(ChangeTag.INSERT, null, newIndex + i, new_.get(newIndex + i)));
                }
            }
            case REPLACE -> {
                for (int i = 0; i < oldLen; i++) {
                    rv.add(new Change<>(ChangeTag.DELETE, oldIndex + i, null, old.get(oldIndex + i)));
                }
                for (int i = 0; i < newLen; i++) {
                    rv.add(new Change<>(ChangeTag.INSERT, null, newIndex + i, new_.get(newIndex + i)));
                }
            }
        }
        return rv;
    }

    public <T> List<TaggedSlice<List<T>>> iterSlices(List<T> old, List<T> new_) {
        List<TaggedSlice<List<T>>> rv = new ArrayList<>();
        switch (tag) {
            case EQUAL -> rv.add(new TaggedSlice<>(ChangeTag.EQUAL, old.subList(oldIndex, oldIndex + oldLen)));
            case INSERT -> rv.add(new TaggedSlice<>(ChangeTag.INSERT, new_.subList(newIndex, newIndex + newLen)));
            case DELETE -> rv.add(new TaggedSlice<>(ChangeTag.DELETE, old.subList(oldIndex, oldIndex + oldLen)));
            case REPLACE -> {
                rv.add(new TaggedSlice<>(ChangeTag.DELETE, old.subList(oldIndex, oldIndex + oldLen)));
                rv.add(new TaggedSlice<>(ChangeTag.INSERT, new_.subList(newIndex, newIndex + newLen)));
            }
        }
        return rv;
    }

    boolean isEmptyOp() {
        return oldLen == 0 && newLen == 0;
    }

    DiffOp shiftLeft(int adjust) {
        return adjust(-adjust, 0);
    }

    DiffOp shiftRight(int adjust) {
        return adjust(adjust, 0);
    }

    DiffOp growLeft(int adjust) {
        return adjust(-adjust, adjust);
    }

    DiffOp growRight(int adjust) {
        return adjust(0, adjust);
    }

    DiffOp shrinkLeft(int adjust) {
        return adjust(0, -adjust);
    }

    DiffOp shrinkRight(int adjust) {
        return adjust(adjust, -adjust);
    }

    private DiffOp adjust(int offset, int len) {
        return switch (tag) {
            case EQUAL -> equal(oldIndex + offset, newIndex + offset, oldLen + len);
            case DELETE -> delete(oldIndex + offset, oldLen + len, newIndex + offset);
            case INSERT -> insert(oldIndex + offset, newIndex + offset, newLen + len);
            case REPLACE -> replace(oldIndex + offset, oldLen + len, newIndex + offset, newLen + len);
        };
    }

    DiffOp withOldIndex(int value) {
        return new DiffOp(tag, value, oldLen, newIndex, newLen);
    }

    DiffOp withNewIndex(int value) {
        return new DiffOp(tag, oldIndex, oldLen, value, newLen);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DiffOp other)) {
            return false;
        }
        return tag == other.tag && oldIndex == other.oldIndex && newIndex == other.newIndex
                && oldLen == other.oldLen && newLen == other.newLen;
    }

    @Override
    public int hashCode() {
        return ((((tag.hashCode() * 31) + oldIndex) * 31 + newIndex) * 31 + oldLen) * 31 + newLen;
    }

    @Override
    public String toString() {
        return switch (tag) {
            case EQUAL -> "Equal { old_index: " + oldIndex + ", new_index: " + newIndex + ", len: " + oldLen + " }";
            case DELETE -> "Delete { old_index: " + oldIndex + ", old_len: " + oldLen + ", new_index: " + newIndex + " }";
            case INSERT -> "Insert { old_index: " + oldIndex + ", new_index: " + newIndex + ", new_len: " + newLen + " }";
            case REPLACE -> "Replace { old_index: " + oldIndex + ", old_len: " + oldLen + ", new_index: " + newIndex
                    + ", new_len: " + newLen + " }";
        };
    }
}
