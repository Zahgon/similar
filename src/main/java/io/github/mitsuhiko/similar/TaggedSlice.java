package io.github.mitsuhiko.similar;

public record TaggedSlice<T>(ChangeTag tag, T value) {
}
