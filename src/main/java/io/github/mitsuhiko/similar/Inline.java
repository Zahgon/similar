package io.github.mitsuhiko.similar;

import java.util.ArrayList;
import java.util.List;

final class Inline {
    private Inline() {
    }

    private static List<String> tokenize(String s, InlineChangeMode mode) {
        return switch (mode) {
            case CHARS -> TextTokens.chars(s);
            default -> TextTokens.words(s);
        };
    }

    /** Tokens of several strings with the string index and offset each came from. */
    private static final class MultiLookup extends Lookup {
        final List<String> strings;
        final List<String> tokens = new ArrayList<>();
        final List<int[]> origin = new ArrayList<>();

        MultiLookup(List<String> strings, InlineChangeMode mode) {
            this.strings = strings;
            for (int si = 0; si < strings.size(); si++) {
                int offset = 0;
                for (String token : tokenize(strings.get(si), mode)) {
                    tokens.add(token);
                    origin.add(new int[] {si, offset});
                    offset += token.length();
                }
            }
        }

        int len() {
            return tokens.size();
        }

        @Override
        Object at(int index) {
            return tokens.get(index);
        }

        String token(int index) {
            return tokens.get(index);
        }

        List<Object[]> originalSlices(int idx, int len) {
            List<Object[]> rv = new ArrayList<>();
            int lastStr = -1;
            int start = 0;
            int lastLen = 0;
            for (int off = 0; off < len; off++) {
                String s = tokens.get(idx + off);
                int[] o = origin.get(idx + off);
                if (lastStr < 0) {
                    lastStr = o[0];
                    start = o[1];
                    lastLen = s.length();
                } else if (lastStr == o[0]) {
                    lastLen += s.length();
                } else {
                    rv.add(new Object[] {lastStr, strings.get(lastStr).substring(start, start + lastLen)});
                    lastStr = o[0];
                    start = o[1];
                    lastLen = s.length();
                }
            }
            if (lastStr >= 0) {
                rv.add(new Object[] {lastStr, strings.get(lastStr).substring(start, start + lastLen)});
            }
            return rv;
        }
    }

    private static void pushValues(List<List<InlineValue>> v, int idx, boolean emphasized, String s) {
        while (v.size() < idx + 1) {
            v.add(new ArrayList<>());
        }
        if (emphasized) {
            for (String seg : TextTokens.linesAndNewlines(s)) {
                v.get(idx).add(new InlineValue(!TextTokens.endsWithNewline(seg), seg));
            }
        } else {
            v.get(idx).add(new InlineValue(false, s));
        }
    }

    private static InlineChange fromChange(Change<String> c) {
        List<InlineValue> values = new ArrayList<>();
        values.add(new InlineValue(false, c.value()));
        return new InlineChange(c.tag(), c.oldIndex(), c.newIndex(), values);
    }

    private static List<InlineChange> plain(TextDiff diff, DiffOp op) {
        List<InlineChange> rv = new ArrayList<>();
        for (Change<String> c : diff.iterChanges(op)) {
            rv.add(fromChange(c));
        }
        return rv;
    }

    static float upperSeqRatio(int a, int b) {
        int n = a + b;
        if (n == 0) {
            return 1.0f;
        }
        return 2.0f * (float) Math.min(a, b) / (float) n;
    }

    static List<InlineChange> iterInlineChanges(TextDiff diff, DiffOp op, InlineChangeOptions options) {
        if (op.tag() != DiffTag.REPLACE) {
            return plain(diff, op);
        }
        Range or = op.oldRange();
        Range nr = op.newRange();
        int oldIndex = or.start();
        int newIndex = nr.start();
        List<String> oldSlices = new ArrayList<>();
        for (int i = or.start(); i < or.end(); i++) {
            oldSlices.add(require(diff.oldSlice(i)));
        }
        List<String> newSlices = new ArrayList<>();
        for (int i = nr.start(); i < nr.end(); i++) {
            newSlices.add(require(diff.newSlice(i)));
        }
        float minRatio = options.minimumRatio();
        if (upperSeqRatio(oldSlices.size(), newSlices.size()) < minRatio) {
            return plain(diff, op);
        }
        MultiLookup oldLookup = new MultiLookup(oldSlices, options.refinementMode());
        MultiLookup newLookup = new MultiLookup(newSlices, options.refinementMode());
        List<DiffOp> ops = Algorithms.captureDiff(options.refinementAlgorithm(), oldLookup, 0, oldLookup.len(),
                newLookup, 0, newLookup.len());
        if (Similar.diffRatio(ops, oldLookup.len(), newLookup.len()) < minRatio) {
            return plain(diff, op);
        }
        if (options.semanticCleanupEnabled()) {
            ops = cleanupSemantic(oldLookup, newLookup, ops);
        }
        List<List<InlineValue>> oldValues = new ArrayList<>();
        List<List<InlineValue>> newValues = new ArrayList<>();
        for (DiffOp o : ops) {
            switch (o.tag()) {
                case EQUAL -> {
                    for (Object[] s : oldLookup.originalSlices(o.oldIndex(), o.len())) {
                        pushValues(oldValues, (Integer) s[0], false, (String) s[1]);
                    }
                    for (Object[] s : newLookup.originalSlices(o.newIndex(), o.len())) {
                        pushValues(newValues, (Integer) s[0], false, (String) s[1]);
                    }
                }
                case DELETE -> {
                    for (Object[] s : oldLookup.originalSlices(o.oldIndex(), o.oldLen())) {
                        pushValues(oldValues, (Integer) s[0], true, (String) s[1]);
                    }
                }
                case INSERT -> {
                    for (Object[] s : newLookup.originalSlices(o.newIndex(), o.newLen())) {
                        pushValues(newValues, (Integer) s[0], true, (String) s[1]);
                    }
                }
                case REPLACE -> {
                    for (Object[] s : oldLookup.originalSlices(o.oldIndex(), o.oldLen())) {
                        pushValues(oldValues, (Integer) s[0], true, (String) s[1]);
                    }
                    for (Object[] s : newLookup.originalSlices(o.newIndex(), o.newLen())) {
                        pushValues(newValues, (Integer) s[0], true, (String) s[1]);
                    }
                }
            }
        }
        List<InlineChange> rv = new ArrayList<>();
        for (List<InlineValue> values : oldValues) {
            rv.add(new InlineChange(ChangeTag.DELETE, oldIndex, null, values));
            oldIndex++;
        }
        for (List<InlineValue> values : newValues) {
            rv.add(new InlineChange(ChangeTag.INSERT, null, newIndex, values));
            newIndex++;
        }
        return rv;
    }

    private static String require(String s) {
        if (s == null) {
            throw new IndexOutOfBoundsException("slice out of bounds");
        }
        return s;
    }

    private static List<DiffOp> expandReplaceOps(List<DiffOp> ops) {
        List<DiffOp> rv = new ArrayList<>(ops.size() + 4);
        for (DiffOp op : ops) {
            if (op.tag() == DiffTag.REPLACE) {
                if (op.oldLen() > 0) {
                    rv.add(DiffOp.delete(op.oldIndex(), op.oldLen(), op.newIndex()));
                }
                if (op.newLen() > 0) {
                    rv.add(DiffOp.insert(op.oldIndex(), op.newIndex(), op.newLen()));
                }
            } else {
                rv.add(op);
            }
        }
        return rv;
    }

    private static boolean canMerge(DiffOp a, DiffOp b) {
        if (a.tag() == DiffTag.EQUAL && b.tag() == DiffTag.EQUAL) {
            return a.oldIndex() + a.len() == b.oldIndex() && a.newIndex() + a.len() == b.newIndex();
        }
        if (a.tag() == DiffTag.DELETE && b.tag() == DiffTag.DELETE) {
            return a.oldIndex() + a.oldLen() == b.oldIndex() && a.newIndex() == b.newIndex();
        }
        if (a.tag() == DiffTag.INSERT && b.tag() == DiffTag.INSERT) {
            return a.oldIndex() == b.oldIndex() && a.newIndex() + a.newLen() == b.newIndex();
        }
        return false;
    }

    private static List<DiffOp> mergeInlineOps(List<DiffOp> ops) {
        List<DiffOp> merged = new ArrayList<>(ops.size());
        for (DiffOp op : ops) {
            if (op.isEmptyOp()) {
                continue;
            }
            if (!merged.isEmpty()) {
                DiffOp last = merged.get(merged.size() - 1);
                if (canMerge(last, op)) {
                    int grow = switch (op.tag()) {
                        case EQUAL -> op.len();
                        case DELETE -> op.oldLen();
                        default -> op.newLen();
                    };
                    merged.set(merged.size() - 1, last.growRight(grow));
                    continue;
                }
            }
            merged.add(op);
        }
        return merged;
    }

    private static int commonPrefix(MultiLookup a, int as, int al, MultiLookup b, int bs, int bl) {
        int matched = 0;
        int max = Math.min(al, bl);
        while (matched < max && a.token(as + matched).equals(b.token(bs + matched))) {
            matched++;
        }
        return matched;
    }

    private static int commonSuffix(MultiLookup a, int as, int al, MultiLookup b, int bs, int bl) {
        int matched = 0;
        int max = Math.min(al, bl);
        while (matched < max && a.token(as + al - 1 - matched).equals(b.token(bs + bl - 1 - matched))) {
            matched++;
        }
        return matched;
    }

    private static boolean isAsciiAlnum(int c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isAsciiWhitespace(int c) {
        return c == ' ' || c == '\t' || c == '\n' || c == 0x0c || c == '\r';
    }

    private static boolean isAsciiControl(int c) {
        return c < 0x20 || c == 0x7f;
    }

    private static int boundaryScore(MultiLookup seq, int leftStart, int leftLen, int rightStart, int rightLen) {
        if (leftLen == 0 || rightLen == 0) {
            return 6;
        }
        String l = seq.token(leftStart + leftLen - 1);
        String r = seq.token(rightStart);
        if (l.isEmpty() || r.isEmpty()) {
            return 0;
        }
        int c1 = l.codePointBefore(l.length());
        int c2 = r.codePointAt(0);
        boolean na1 = !isAsciiAlnum(c1);
        boolean na2 = !isAsciiAlnum(c2);
        boolean ws1 = na1 && isAsciiWhitespace(c1);
        boolean ws2 = na2 && isAsciiWhitespace(c2);
        boolean lb1 = ws1 && isAsciiControl(c1);
        boolean lb2 = ws2 && isAsciiControl(c2);
        if (lb1 || lb2) {
            return 4;
        } else if (na1 && !ws1 && ws2) {
            return 3;
        } else if (ws1 || ws2) {
            return 2;
        } else if (na1 || na2) {
            return 1;
        }
        return 0;
    }

    private static int pairScore(MultiLookup seq, Range a, Range b, Range c) {
        return boundaryScore(seq, a.start(), a.len(), b.start(), b.len())
                + boundaryScore(seq, b.start(), b.len(), c.start(), c.len());
    }

    private static List<DiffOp> cleanupSemanticLossless(MultiLookup old, MultiLookup nw, List<DiffOp> ops) {
        int pointer = 1;
        while (pointer + 1 < ops.size()) {
            DiffOp prev = ops.get(pointer - 1);
            DiffOp edit = ops.get(pointer);
            DiffOp next = ops.get(pointer + 1);
            boolean changed = false;
            boolean insertCase = prev.tag() == DiffTag.EQUAL && edit.tag() == DiffTag.INSERT
                    && next.tag() == DiffTag.EQUAL;
            boolean deleteCase = prev.tag() == DiffTag.EQUAL && edit.tag() == DiffTag.DELETE
                    && next.tag() == DiffTag.EQUAL;
            if (insertCase || deleteCase) {
                MultiLookup seq = insertCase ? nw : old;
                DiffOp oPrev = prev;
                DiffOp oEdit = edit;
                DiffOp oNext = next;
                Range pr = insertCase ? prev.newRange() : prev.oldRange();
                Range er = insertCase ? edit.newRange() : edit.oldRange();
                int common = commonSuffix(seq, pr.start(), pr.len(), seq, er.start(), er.len());
                if (common > 0) {
                    prev = prev.shrinkLeft(common);
                    edit = edit.shiftLeft(common);
                    next = next.growLeft(common);
                }
                DiffOp bestPrev = prev;
                DiffOp bestEdit = edit;
                DiffOp bestNext = next;
                int bestScore = pairScore(seq, side(prev, insertCase), side(edit, insertCase), side(next, insertCase));
                while (!side(edit, insertCase).isEmpty() && !side(next, insertCase).isEmpty()
                        && seq.token(side(edit, insertCase).start()).equals(seq.token(side(next, insertCase).start()))) {
                    prev = prev.growRight(1);
                    edit = edit.shiftRight(1);
                    next = next.shiftRight(1);
                    next = next.shrinkLeft(1);
                    int score = pairScore(seq, side(prev, insertCase), side(edit, insertCase), side(next, insertCase));
                    if (score >= bestScore) {
                        bestScore = score;
                        bestPrev = prev;
                        bestEdit = edit;
                        bestNext = next;
                    }
                }
                ops.set(pointer - 1, bestPrev);
                ops.set(pointer, bestEdit);
                ops.set(pointer + 1, bestNext);
                changed = !(bestPrev.equals(oPrev) && bestEdit.equals(oEdit) && bestNext.equals(oNext));
            }
            if (changed) {
                ops = mergeInlineOps(ops);
                if (pointer > 1) {
                    pointer--;
                }
            } else {
                pointer++;
            }
        }
        return ops;
    }

    private static Range side(DiffOp op, boolean newSide) {
        return newSide ? op.newRange() : op.oldRange();
    }

    private static List<DiffOp> cleanupOverlaps(MultiLookup old, MultiLookup nw, List<DiffOp> ops) {
        int pointer = 0;
        while (pointer + 1 < ops.size()) {
            if (!(ops.get(pointer).tag() == DiffTag.DELETE && ops.get(pointer + 1).tag() == DiffTag.INSERT)) {
                pointer++;
                continue;
            }
            DiffOp del = ops.get(pointer);
            DiffOp ins = ops.get(pointer + 1);
            Range delOld = del.oldRange();
            Range insNew = ins.newRange();
            int prefix = commonPrefix(old, delOld.start(), delOld.len(), nw, insNew.start(), insNew.len());
            if (prefix > 0) {
                int oldStart = del.oldRange().start();
                int newStart = ins.newRange().start();
                del = del.shiftRight(prefix).shrinkLeft(prefix);
                ins = ins.shiftRight(prefix).shrinkLeft(prefix);
                DiffOp eq = DiffOp.equal(oldStart, newStart, prefix);
                if (pointer > 0 && ops.get(pointer - 1).tag() == DiffTag.EQUAL) {
                    DiffOp prev = ops.get(pointer - 1);
                    if (prev.oldRange().end() == oldStart && prev.newRange().end() == newStart) {
                        ops.set(pointer - 1, prev.growRight(prefix));
                    } else {
                        ops.add(pointer, eq);
                        pointer++;
                    }
                } else {
                    ops.add(pointer, eq);
                    pointer++;
                }
                ops.set(pointer, del);
                ops.set(pointer + 1, ins);
            }
            delOld = ops.get(pointer).oldRange();
            insNew = ops.get(pointer + 1).newRange();
            int suffix = commonSuffix(old, delOld.start(), delOld.len(), nw, insNew.start(), insNew.len());
            if (suffix > 0) {
                ops.set(pointer, ops.get(pointer).shrinkLeft(suffix));
                ops.set(pointer + 1, ops.get(pointer + 1).shrinkLeft(suffix));
                int oldStart = ops.get(pointer).oldRange().end();
                int newStart = ops.get(pointer + 1).newRange().end();
                ops.add(pointer + 2, DiffOp.equal(oldStart, newStart, suffix));
            }
            pointer++;
        }
        return mergeInlineOps(ops);
    }

    private static List<DiffOp> cleanupSemantic(MultiLookup old, MultiLookup nw, List<DiffOp> ops) {
        List<DiffOp> expanded = mergeInlineOps(expandReplaceOps(ops));
        expanded = new ArrayList<>(cleanupOverlaps(old, nw, expanded));
        expanded = cleanupSemanticLossless(old, nw, expanded);
        return mergeInlineOps(expanded);
    }
}
