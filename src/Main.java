import java.util.ArrayList;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {

        try {

            // Optional first argument: corpus directory (default "corpus").
            String corpusDirectory =
                    args.length > 0 ? args[0] : "corpus";

            CorpusLoader loader =
                    new CorpusLoader(corpusDirectory);

            ProductDocument[] corpus =
                    loader.load();

            SearchEngine searchEngine =
                    new SearchEngine(corpus);

            Scanner scanner =
                    new Scanner(System.in);

            System.out.println();
            System.out.println(
                    "========================================"
            );
            System.out.println(
                    "   PRODUCT SEARCH & RECOMMENDATION"
            );
            System.out.println(
                    "========================================"
            );
            System.out.println(
                    "Corpus files loaded: "
                            + corpus.length
            );

            System.out.println();
            System.out.println(
                    "KMP / Rabin-Karp for pattern matching."
            );
            System.out.println(
                    "Levenshtein for spelling recovery."
            );
            System.out.println(
                    "Edmonds-Karp for recommendations."
            );

            System.out.println();
            System.out.println(
                    "Type exit to close the system."
            );

            while (true) {

                System.out.println();
                System.out.print(
                        "Enter search query: "
                );

                if (!scanner.hasNextLine()) {
                    break;
                }

                String query =
                        scanner.nextLine().trim();

                if (query.equalsIgnoreCase("exit")) {

                    System.out.println();
                    System.out.println(
                            "Thank you for using "
                                    + "the Product Search System."
                    );

                    break;
                }

                if (query.equalsIgnoreCase("/time")) {

                    runTimingTest(
                            corpus,
                            scanner
                    );

                    continue;
                }

                if (query.equalsIgnoreCase("/trie")) {

                    runTriePrefixSearch(
                            corpus,
                            scanner
                    );

                    continue;
                }

                if (query.isEmpty()) {

                    System.out.println(
                            "Please enter a valid search query."
                    );

                    continue;
                }

                searchEngine.search(
                        query,
                        scanner
                );
            }

            scanner.close();

        } catch (java.io.IOException e) {

            // Typical cause: program started outside the project folder.
            System.out.println();
            System.out.println(
                    "Error: "
                            + e.getMessage()
            );
            System.out.println(
                    "Run the program from the project root (the folder that "
                            + "contains 'corpus'), or pass the corpus folder as "
                            + "the first argument."
            );

        } catch (Exception e) {

            System.out.println();
            System.out.println(
                    "Error: "
                            + e.getMessage()
            );

            e.printStackTrace();
        }
    }

    private static void runTimingTest(
            ProductDocument[] corpus,
            Scanner scanner) {

        final int WARMUP_ROUNDS = 3;
        final int MEASURED_ROUNDS = 10;
        final double WINNER_MARGIN_PERCENT = 5.0;

        System.out.println();
        System.out.println("========================================");
        System.out.println("  KMP vs RABIN-KARP AVERAGED BENCHMARK");
        System.out.println("========================================");
        System.out.print("Enter pattern/query: ");

        if (!scanner.hasNextLine()) {
            return;
        }

        String pattern = normalize(scanner.nextLine());
        if (pattern.isEmpty()) {
            System.out.println("Please enter a valid pattern.");
            return;
        }

        ArrayList<String> searchableTexts = buildSearchableTexts(corpus);
        if (searchableTexts.isEmpty()) {
            System.out.println("No searchable product text is available.");
            return;
        }

        int expectedMatches = -1;
        int lastKmpMatches = -1;
        int lastRabinMatches = -1;

        // Warm up both implementations so early JVM/JIT work is less likely
        // to dominate the measured timings.
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            int kmpMatches = countWithKMP(searchableTexts, pattern);
            int rabinMatches = countWithRabinKarp(searchableTexts, pattern);
            if (kmpMatches != rabinMatches) {
                System.out.println("Match-count consistency: FAIL during warm-up.");
                System.out.println("KMP=" + kmpMatches + ", Rabin-Karp=" + rabinMatches);
                return;
            }
            expectedMatches = kmpMatches;
        }

        long kmpTotalNanos = 0L;
        long rabinTotalNanos = 0L;
        boolean countsConsistent = true;

        // Alternate execution order to reduce systematic first-run/order bias.
        for (int i = 0; i < MEASURED_ROUNDS; i++) {
            int kmpMatches;
            int rabinMatches;
            long kmpNanos;
            long rabinNanos;

            if (i % 2 == 0) {
                long startKmp = System.nanoTime();
                kmpMatches = countWithKMP(searchableTexts, pattern);
                kmpNanos = System.nanoTime() - startKmp;

                long startRabin = System.nanoTime();
                rabinMatches = countWithRabinKarp(searchableTexts, pattern);
                rabinNanos = System.nanoTime() - startRabin;
            } else {
                long startRabin = System.nanoTime();
                rabinMatches = countWithRabinKarp(searchableTexts, pattern);
                rabinNanos = System.nanoTime() - startRabin;

                long startKmp = System.nanoTime();
                kmpMatches = countWithKMP(searchableTexts, pattern);
                kmpNanos = System.nanoTime() - startKmp;
            }

            if (kmpMatches != rabinMatches || kmpMatches != expectedMatches) {
                countsConsistent = false;
            }
            lastKmpMatches = kmpMatches;
            lastRabinMatches = rabinMatches;
            kmpTotalNanos += kmpNanos;
            rabinTotalNanos += rabinNanos;
        }

        if (!countsConsistent) {
            System.out.println("Match-count consistency: FAIL");
            System.out.println("KMP and Rabin-Karp did not return the same count in every run.");
            return;
        }

        double kmpAverageMillis = kmpTotalNanos / 1_000_000.0 / MEASURED_ROUNDS;
        double rabinAverageMillis = rabinTotalNanos / 1_000_000.0 / MEASURED_ROUNDS;

        System.out.println();
        System.out.println("Corpus products scanned : " + searchableTexts.size());
        System.out.println("Warm-up rounds / algorithm: " + WARMUP_ROUNDS);
        System.out.println("Measured rounds / algorithm: " + MEASURED_ROUNDS);
        System.out.println("Pattern                 : " + pattern);
        System.out.println();
        System.out.println("KMP:");
        System.out.println("  Matches found : " + lastKmpMatches);
        System.out.println("  Average time  : " + String.format("%.4f ms", kmpAverageMillis));
        System.out.println();
        System.out.println("Rabin-Karp:");
        System.out.println("  Matches found : " + lastRabinMatches);
        System.out.println("  Average time  : " + String.format("%.4f ms", rabinAverageMillis));
        System.out.println();
        System.out.println("Match-count consistency: PASS");

        double slowerAverage = Math.max(kmpAverageMillis, rabinAverageMillis);
        double differencePercent = slowerAverage == 0.0
                ? 0.0
                : Math.abs(kmpAverageMillis - rabinAverageMillis) / slowerAverage * 100.0;

        if (differencePercent < WINNER_MARGIN_PERCENT) {
            System.out.println("Timing observation: No clear winner (difference under "
                    + String.format("%.0f%%", WINNER_MARGIN_PERCENT) + ").");
        } else if (kmpAverageMillis < rabinAverageMillis) {
            System.out.println("Timing observation: KMP was faster in this benchmark run.");
        } else {
            System.out.println("Timing observation: Rabin-Karp was faster in this benchmark run.");
        }
        System.out.println("Note: timings depend on JVM warm-up, hardware, and system load;");
        System.out.println("      a small timing difference is not proof that one algorithm is always faster.");
        System.out.println("========================================");
    }

    private static ArrayList<String> buildSearchableTexts(
            ProductDocument[] corpus) {

        ArrayList<String> searchableTexts =
                new ArrayList<>();

        if (corpus == null) {
            return searchableTexts;
        }

        for (ProductDocument product : corpus) {

            if (product == null) {
                continue;
            }

            String text =
                    product.getProductName()
                            + " "
                            + product.getBrand()
                            + " "
                            + product.getCategory()
                            + " "
                            + product.getSearchableContent();

            searchableTexts.add(
                    normalize(text)
            );
        }

        return searchableTexts;
    }

    private static int countWithKMP(
            ArrayList<String> texts,
            String pattern) {
        int matches = 0;
        for (String text : texts) {
            matches += PatternMatching.countKMP(text, pattern);
        }
        return matches;
    }

    private static int countWithRabinKarp(
            ArrayList<String> texts,
            String pattern) {
        int matches = 0;
        for (String text : texts) {
            matches += PatternMatching.countRabinKarp(text, pattern);
        }
        return matches;
    }

    private static void runTriePrefixSearch(
            ProductDocument[] corpus,
            Scanner scanner) {

        TrieSearch trieSearch =
                new TrieSearch();

        trieSearch.build(corpus);

        System.out.println();
        System.out.println(
                "========================================"
        );
        System.out.println(
                "         TRIE PREFIX SEARCH"
        );
        System.out.println(
                "========================================"
        );

        System.out.print(
                "Enter prefix: "
        );

        if (!scanner.hasNextLine()) {
            return;
        }

        String prefix =
                scanner.nextLine().trim();

        if (prefix.isEmpty()) {

            System.out.println(
                    "Please enter a valid prefix."
            );

            return;
        }

        ArrayList<ProductDocument> results =
                trieSearch.searchProducts(prefix);

        System.out.println();
        System.out.println(
                "Prefix: " + prefix
        );

        if (results.isEmpty()) {

            System.out.println(
                    "No matching products found."
            );

        } else {

            final int MAX_SHOWN = 10;

            int count = 1;

            for (ProductDocument product : results) {

                System.out.println();
                System.out.println(
                        count + ". "
                                + product.getProductName()
                );
                System.out.println(
                        "   File : "
                                + product.getFileName()
                );

                count++;

                if (count > MAX_SHOWN) {
                    break;
                }
            }

            if (results.size() > MAX_SHOWN) {
                System.out.println();
                System.out.println(
                        "Showing " + MAX_SHOWN + " of "
                                + results.size()
                                + " matching products."
                );
            }
        }

        System.out.println(
                "========================================"
        );
    }

    private static String normalize(String text) {

        if (text == null) {
            return "";
        }

        return text.toLowerCase()
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}