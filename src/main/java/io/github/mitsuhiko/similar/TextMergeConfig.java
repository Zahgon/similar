package io.github.mitsuhiko.similar;

public final class TextMergeConfig {
    private Algorithm algorithm = Algorithm.MYERS;
    private WhitespaceMode whitespaceMode = WhitespaceMode.EXACT;

    public TextMergeConfig() {
    }

    public TextMergeConfig algorithm(Algorithm algorithm) {
        this.algorithm = algorithm;
        return this;
    }

    public TextMergeConfig whitespaceMode(WhitespaceMode mode) {
        this.whitespaceMode = mode;
        return this;
    }

    public TextMerge mergeLines(String base, String ours, String theirs) {
        return new TextMerge(TextTokens.lines(base), TextTokens.lines(ours), TextTokens.lines(theirs), algorithm,
                whitespaceMode);
    }
}
