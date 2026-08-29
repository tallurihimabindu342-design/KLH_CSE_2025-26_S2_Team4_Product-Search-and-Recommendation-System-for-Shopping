public class RabinKarp {
    private static final long BASE = 256;
    private static final long MOD = 1_000_000_007L;

    private RabinKarp() {}

    public static MatchResult search(String text, String pattern) {
        if (pattern == null || pattern.isEmpty()) return new MatchResult(true, 0, 0);
        if (text == null || pattern.length() > text.length()) return new MatchResult(false, -1, 0);

        int n = text.length();
        int m = pattern.length();
        long patternHash = 0;
        long windowHash = 0;
        long highPower = 1;
        long checks = 0;

        for (int i = 0; i < m; i++) {
            patternHash = (patternHash * BASE + normalized(pattern.charAt(i))) % MOD;
            windowHash = (windowHash * BASE + normalized(text.charAt(i))) % MOD;
            if (i < m - 1) highPower = (highPower * BASE) % MOD;
        }

        for (int start = 0; start <= n - m; start++) {
            if (patternHash == windowHash) {
                boolean equal = true;
                for (int j = 0; j < m; j++) {
                    checks++;
                    if (normalized(text.charAt(start + j)) != normalized(pattern.charAt(j))) {
                        equal = false;
                        break;
                    }
                }
                if (equal) return new MatchResult(true, start, checks);
            } else {
                checks++;
            }

            if (start < n - m) {
                long outgoing = (normalized(text.charAt(start)) * highPower) % MOD;
                windowHash = (windowHash - outgoing + MOD) % MOD;
                windowHash = (windowHash * BASE + normalized(text.charAt(start + m))) % MOD;
            }
        }
        return new MatchResult(false, -1, checks);
    }

    private static long normalized(char c) {
        return Character.toLowerCase(c);
    }
}
