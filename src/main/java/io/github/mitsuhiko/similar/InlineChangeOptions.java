package io.github.mitsuhiko.similar;

public final class InlineChangeOptions {
    private Algorithm algorithm = Algorithm.PATIENCE;
    private InlineChangeMode mode = InlineChangeMode.AUTO;
    private float minRatio = 0.5f;
    private boolean semanticCleanup = false;

    public InlineChangeOptions() {
    }

    public InlineChangeOptions algorithm(Algorithm alg) {
        this.algorithm = alg;
        return this;
    }

    public InlineChangeOptions mode(InlineChangeMode mode) {
        this.mode = mode;
        return this;
    }

    public InlineChangeOptions minRatio(float minRatio) {
        this.minRatio = minRatio;
        return this;
    }

    public InlineChangeOptions semanticCleanup(boolean yes) {
        this.semanticCleanup = yes;
        return this;
    }

    public Algorithm refinementAlgorithm() {
        return algorithm;
    }

    public InlineChangeMode refinementMode() {
        return mode;
    }

    public float minimumRatio() {
        return minRatio;
    }

    public boolean semanticCleanupEnabled() {
        return semanticCleanup;
    }
}
