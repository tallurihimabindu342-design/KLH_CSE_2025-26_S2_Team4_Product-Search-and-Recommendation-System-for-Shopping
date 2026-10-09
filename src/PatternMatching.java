/**
 * Shared exact-pattern matching algorithms used by the search engine and benchmark.
 */
public final class PatternMatching {
    private PatternMatching() { }

    /** Counts overlapping occurrences using Knuth-Morris-Pratt. */
    public static int countKMP(String text, String pattern) {
        if (text == null || pattern == null || pattern.isEmpty()
                || text.length() < pattern.length()) {
            return 0;
        }

        int[] lps = buildLPS(pattern);
        int i = 0;
        int j = 0;
        int count = 0;

        while (i < text.length()) {
            if (text.charAt(i) == pattern.charAt(j)) {
                i++;
                j++;
                if (j == pattern.length()) {
                    count++;
                    j = lps[j - 1];
                }
            } else if (j > 0) {
                j = lps[j - 1];
            } else {
                i++;
            }
        }
        return count;
    }

    /** Counts overlapping occurrences using Rabin-Karp with hash verification. */
    public static int countRabinKarp(String text, String pattern) {
        if (text == null || pattern == null || pattern.isEmpty()
                || text.length() < pattern.length()) {
            return 0;
        }

        final long base = 256L;
        final long mod = 1_000_000_007L;
        int m = pattern.length();
        int n = text.length();
        long patternHash = 0L;
        long windowHash = 0L;
        long highPower = 1L;

        for (int i = 0; i < m - 1; i++) {
            highPower = (highPower * base) % mod;
        }
        for (int i = 0; i < m; i++) {
            patternHash = (patternHash * base + pattern.charAt(i)) % mod;
            windowHash = (windowHash * base + text.charAt(i)) % mod;
        }

        int count = 0;
        for (int i = 0; i <= n - m; i++) {
            if (patternHash == windowHash && text.regionMatches(i, pattern, 0, m)) {
                count++;
            }
            if (i < n - m) {
                windowHash = (windowHash - text.charAt(i) * highPower) % mod;
                if (windowHash < 0) {
                    windowHash += mod;
                }
                windowHash = (windowHash * base + text.charAt(i + m)) % mod;
            }
        }
        return count;
    }

    private static int[] buildLPS(String pattern) {
        int[] lps = new int[pattern.length()];
        int length = 0;
        int i = 1;
        while (i < pattern.length()) {
            if (pattern.charAt(i) == pattern.charAt(length)) {
                lps[i++] = ++length;
            } else if (length > 0) {
                length = lps[length - 1];
            } else {
                lps[i++] = 0;
            }
        }
        return lps;
    }
}