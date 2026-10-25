package io.github.mitsuhiko.similar;

import java.util.ArrayList;
import java.util.List;

final class TextTokens {
    private TextTokens() {
    }

    static List<String> lines(String s) {
        List<String> rv = new ArrayList<>();
        int last = 0;
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r') {
                int end = (c == '\r' && i + 1 < n && s.charAt(i + 1) == '\n') ? i + 2 : i + 1;
                rv.add(s.substring(last, end));
                last = end;
                i = end;
            } else {
                i++;
            }
        }
        if (last < n) {
            rv.add(s.substring(last));
        }
        return rv;
    }

    static List<String> linesAndNewlines(String s) {
        List<String> rv = new ArrayList<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            boolean nl = isNl(s.charAt(i));
            int start = i;
            i += Character.charCount(s.codePointAt(i));
            while (i < n && isNl(s.charAt(i)) == nl) {
                i += Character.charCount(s.codePointAt(i));
            }
            rv.add(s.substring(start, i));
        }
        return rv;
    }

    private static boolean isNl(char c) {
        return c == '\r' || c == '\n';
    }

    static boolean isRustWhitespace(int cp) {
        if (cp < 0x80) {
            return (cp >= 0x09 && cp <= 0x0d) || cp == 0x20;
        }
        return cp == 0x85 || cp == 0xa0 || cp == 0x1680 || (cp >= 0x2000 && cp <= 0x200a) || cp == 0x2028
                || cp == 0x2029 || cp == 0x202f || cp == 0x205f || cp == 0x3000;
    }

    static List<String> words(String s) {
        List<String> rv = new ArrayList<>();
        int start = 0;
        int pos = 0;
        boolean current = false;
        int n = s.length();
        while (pos < n) {
            int cp = s.codePointAt(pos);
            boolean ws = isRustWhitespace(cp);
            if (ws != current) {
                if (pos > start) {
                    rv.add(s.substring(start, pos));
                }
                start = pos;
                current = ws;
            }
            pos += Character.charCount(cp);
        }
        if (start < n) {
            rv.add(s.substring(start));
        }
        return rv;
    }

    static List<String> chars(String s) {
        List<String> rv = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            int w = Character.charCount(s.codePointAt(i));
            rv.add(s.substring(i, i + w));
            i += w;
        }
        return rv;
    }

    static boolean endsWithNewline(String s) {
        return s.endsWith("\r") || s.endsWith("\n");
    }

    private static boolean isGitWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n';
    }

    /** The comparison key Git-style whitespace modes compare lines by. */
    static String whitespaceKey(String s, WhitespaceMode mode) {
        switch (mode) {
            case EXACT:
                return s;
            case IGNORE_ALL: {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < s.length(); i++) {
                    char c = s.charAt(i);
                    if (!isGitWhitespace(c)) {
                        sb.append(c);
                    }
                }
                return sb.toString();
            }
            default: {
                StringBuilder sb = new StringBuilder();
                int i = 0;
                int n = s.length();
                while (i < n) {
                    char c = s.charAt(i++);
                    if (!isGitWhitespace(c)) {
                        sb.append(c);
                        continue;
                    }
                    while (i < n && isGitWhitespace(s.charAt(i))) {
                        i++;
                    }
                    if (i < n) {
                        sb.append(' ');
                    }
                }
                return sb.toString();
            }
        }
    }

    static List<String> keys(List<String> tokens, WhitespaceMode mode) {
        List<String> rv = new ArrayList<>(tokens.size());
        for (String t : tokens) {
            rv.add(whitespaceKey(t, mode));
        }
        return rv;
    }

    static int compareUtf8(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            int ca = a.codePointAt(i);
            int cb = b.codePointAt(j);
            if (ca != cb) {
                return Integer.compare(ca, cb);
            }
            i += Character.charCount(ca);
            j += Character.charCount(cb);
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }
}
