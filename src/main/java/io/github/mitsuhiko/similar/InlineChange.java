package io.github.mitsuhiko.similar;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class InlineChange {
    private final ChangeTag tag;
    private final Integer oldIndex;
    private final Integer newIndex;
    private final List<InlineValue> values;

    InlineChange(ChangeTag tag, Integer oldIndex, Integer newIndex, List<InlineValue> values) {
        this.tag = tag;
        this.oldIndex = oldIndex;
        this.newIndex = newIndex;
        this.values = values;
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

    public List<InlineValue> values() {
        return Collections.unmodifiableList(values);
    }

    public boolean missingNewline() {
        if (values.isEmpty()) {
            return false;
        }
        return !TextTokens.endsWithNewline(values.get(values.size() - 1).value());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (InlineValue v : values) {
            String marker = "";
            if (v.emphasized()) {
                if (tag == ChangeTag.DELETE) {
                    marker = "-";
                } else if (tag == ChangeTag.INSERT) {
                    marker = "+";
                }
            }
            sb.append(marker).append(v.value()).append(marker);
        }
        if (missingNewline()) {
            sb.append('\n');
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof InlineChange other)) {
            return false;
        }
        return tag == other.tag && Objects.equals(oldIndex, other.oldIndex) && Objects.equals(newIndex, other.newIndex)
                && values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tag, oldIndex, newIndex, values);
    }
}
