public class Levenshtein {

    /**
     * Calculates the Levenshtein Edit Distance
     * between two strings.
     *
     * Allowed operations:
     * 1. Insertion
     * 2. Deletion
     * 3. Replacement
     */
    public static int distance(String a, String b) {

        // Case-insensitive comparison
        a = a.toLowerCase();
        b = b.toLowerCase();

        int m = a.length();
        int n = b.length();

        // DP table
        int[][] dp = new int[m + 1][n + 1];

        // Transform empty string into b
        for (int j = 0; j <= n; j++) {
            dp[0][j] = j;
        }

        // Transform a into empty string
        for (int i = 0; i <= m; i++) {
            dp[i][0] = i;
        }

        // Fill DP table
        for (int i = 1; i <= m; i++) {

            for (int j = 1; j <= n; j++) {

                if (a.charAt(i - 1) == b.charAt(j - 1)) {

                    // Characters are equal
                    dp[i][j] = dp[i - 1][j - 1];

                } else {

                    int insertion = dp[i][j - 1];
                    int deletion = dp[i - 1][j];
                    int replacement = dp[i - 1][j - 1];

                    dp[i][j] = 1 + Math.min(
                        insertion,
                        Math.min(deletion, replacement)
                    );
                }
            }
        }

        return dp[m][n];
    }
}