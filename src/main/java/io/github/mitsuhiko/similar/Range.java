package io.github.mitsuhiko.similar;

public record Range(int start, int end) {
    public int len() {
        return start < end ? end - start : 0;
    }

    public boolean isEmpty() {
        return !(start < end);
    }
}
