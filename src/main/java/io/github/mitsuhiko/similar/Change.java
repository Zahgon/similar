package io.github.mitsuhiko.similar;

import java.util.Objects;

public final class Change<T> {
    private final ChangeTag tag;
    private final Integer oldIndex;
    private final Integer newIndex;
    private final T value;

    Change(ChangeTag tag, Integer oldIndex, Integer newIndex, T value) {
        this.tag = tag;
        this.oldIndex = oldIndex;
        this.newIndex = newIndex;
        this.value = value;
    }

    public ChangeTag tag() {
        return tag;
    }

    public Integer oldIndex() {
        return oldIndex;
    }

    public Integer newIndex() {
        return newIndex;
    }

    public T value() {
        return value;
    }

    public boolean missingNewline() {
        String s = String.valueOf(value);
        return !(s.endsWith("\r") || s.endsWith("\n"));
    }

    @Override
    public String toString() {
        String s = String.valueOf(value);
        return missingNewline() ? s + "\n" : s;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Change<?> other)) {
            return false;
        }
        return tag == other.tag && Objects.equals(oldIndex, other.oldIndex)
                && Objects.equals(newIndex, other.newIndex) && Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tag, oldIndex, newIndex, value);
    }
}
