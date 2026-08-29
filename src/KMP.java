public class KMP {
    private KMP() {}

    public static int[] buildLPS(String pattern) {
        int[] lps = new int[pattern.length()];
        int length = 0;
        int i = 1;

        while (i < pattern.length()) {
            if (equalsIgnoreCase(pattern.charAt(i), pattern.charAt(length))) {
                lps[i] = ++length;
                i++;
            } else if (length > 0) {
                length = lps[length - 1];
            } else {
                lps[i] = 0;
                i++;
            }
        }
        return lps;
    }

    public static MatchResult search(String text, String pattern) {
        if (pattern == null || pattern.isEmpty()) return new MatchResult(true, 0, 0);
        if (text == null || pattern.length() > text.length()) return new MatchResult(false, -1, 0);

        int[] lps = buildLPS(pattern);
        int i = 0, j = 0;
        long comparisons = 0;

        while (i < text.length()) {
            comparisons++;
            if (equalsIgnoreCase(text.charAt(i), pattern.charAt(j))) {
                i++;
                j++;
                if (j == pattern.length()) {
                    return new MatchResult(true, i - j, comparisons);
                }
            } else if (j > 0) {
                j = lps[j - 1];
            } else {
                i++;
            }
        }
        return new MatchResult(false, -1, comparisons);
    }

    private static boolean equalsIgnoreCase(char a, char b) {
        return Character.toLowerCase(a) == Character.toLowerCase(b);
    }
}
