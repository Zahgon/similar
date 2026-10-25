package io.github.mitsuhiko.similar;

import java.util.Objects;

public final class MergeRegion {
    private final MergeResolution resolution;
    private final Range baseRange;
    private final Range oursRange;
    private final Range theirsRange;

    MergeRegion(MergeResolution resolution, Range baseRange, Range oursRange, Range theirsRange) {
        this.resolution = resolution;
        this.baseRange = baseRange;
        this.oursRange = oursRange;
        this.theirsRange = theirsRange;
    }

    public MergeResolution resolution() {
        return resolution;
    }

    public Range baseRange() {
        return baseRange;
    }

    public Range oursRange() {
        return oursRange;
    }

    public Range theirsRange() {
        return theirsRange;
    }

    public boolean isConflict() {
        return resolution == MergeResolution.CONFLICT;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MergeRegion other)) {
            return false;
        }
        return resolution == other.resolution && baseRange.equals(other.baseRange)
                && oursRange.equals(other.oursRange) && theirsRange.equals(other.theirsRange);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resolution, baseRange, oursRange, theirsRange);
    }
}
