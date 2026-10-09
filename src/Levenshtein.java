/**
 * Levenshtein edit distance (insertion, deletion, substitution; each cost 1).
 *
 * Time  : O(m * n) for strings of length m and n.
 * Space : O(min(m, n)) - only two rows of the dynamic-programming table are
 *         kept, because row i depends only on row i - 1. The final distance is
 *         the same as with the full (m + 1) x (n + 1) table.
 *
 * Comparison is case-insensitive. A null argument is treated as the empty
 * string, so distance(null, "abc") == 3 instead of throwing.
 */
public class Levenshtein {

    public static int distance(String a, String b) {

        a = (a == null) ? "" : a.toLowerCase();
        b = (b == null) ? "" : b.toLowerCase();

        // Keep the shorter string in b so the rows are as small as possible.
        if (b.length() > a.length()) {
            String swap = a;
            a = b;
            b = swap;
        }

        int m = a.length();
        int n = b.length();

        // previous[j] = distance(a[0..i-1], b[0..j])
        int[] previous = new int[n + 1];
        int[] current = new int[n + 1];

        // Row 0: transform the empty prefix of a into b[0..j]
        for (int j = 0; j <= n; j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= m; i++) {

            // Column 0: transform a[0..i] into the empty string
            current[0] = i;

            for (int j = 1; j <= n; j++) {

                if (a.charAt(i - 1) == b.charAt(j - 1)) {

                    // Characters are equal: no edit needed
                    current[j] = previous[j - 1];

                } else {

                    int insertion = current[j - 1];
                    int deletion = previous[j];
                    int replacement = previous[j - 1];

                    current[j] = 1 + Math.min(
                            insertion,
                            Math.min(deletion, replacement)
                    );
                }
            }

            int[] swap = previous;
            previous = current;
            current = swap;
        }

        return previous[n];
    }
}