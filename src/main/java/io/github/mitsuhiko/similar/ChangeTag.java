package io.github.mitsuhiko.similar;

public enum ChangeTag {
    EQUAL(" "),
    DELETE("-"),
    INSERT("+");

    private final String sign;

    ChangeTag(String sign) {
        this.sign = sign;
    }

    @Override
    public String toString() {
        return sign;
    }
}
