import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;

/**
 * Automated, dependency-free test suite for the whole project.
 *
 *   javac -d out src/*.java
 *   java -cp out TestRunner            (run from the project root)
 *   java -cp out TestRunner path/to/corpus
 *
 * Exit code 0 = every check passed, 1 = at least one check failed.
 * Every randomised test uses a fixed seed, so a failure is reproducible.
 */
public class TestRunner {

    private static int passed = 0;
    private static int failed = 0;
    private static String group = "";

    private static void group(String name) {
        group = name;
        System.out.println();
        System.out.println("== " + name);
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
        } else {
            failed++;
            System.out.println("  FAIL [" + group + "] " + name);
        }
    }

    private static void checkEq(String name, Object actual, Object expected) {
        boolean ok = actual == null ? expected == null : actual.equals(expected);
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("  FAIL [" + group + "] " + name
                    + "  expected=<" + expected + "> actual=<" + actual + ">");
        }
    }

    public static void main(String[] args) throws Exception {

        String corpusDir = args.length > 0 ? args[0] : "corpus";

        testPatternMatching();
        testLevenshtein();
        testTrie();
        testMaxFlow();
        testRecommendationPolicy();

        ProductDocument[] corpus = new CorpusLoader(corpusDir).load();

        testCorpusConsistency(corpus);
        testProductParsing(corpus);
        testSearchEngine(corpus);
        testConsoleFlow(corpus);
        testTrieSearch(corpus);
        testLevenshteinFallback(corpus);
        testBenchmarkCounts(corpus);

        System.out.println();
        System.out.println("========================================");
        System.out.println("Checks passed: " + passed);
        System.out.println("Checks failed: " + failed);
        System.out.println(failed == 0 ? "RESULT: ALL TESTS PASSED" : "RESULT: FAILURES PRESENT");
        System.out.println("========================================");

        System.exit(failed == 0 ? 0 : 1);
    }

    // ================================================================
    // KMP / RABIN-KARP
    // ================================================================

    /** Obviously-correct reference: counts overlapping occurrences with indexOf. */
    private static int naiveCount(String text, String pattern) {
        if (pattern.isEmpty()) {
            return 0;
        }
        int count = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(pattern, from);
            if (at < 0) {
                return count;
            }
            count++;
            from = at + 1;
        }
    }

    private static void testPatternMatching() {

        group("KMP / Rabin-Karp");

        String[][] known = {
                {"aaaaa", "aa", "4"},
                {"abababa", "aba", "3"},
                {"product", "xyz", "0"},
                {"abc", "abc", "1"},
                {"ab", "abc", "0"},
                {"", "a", "0"},
                {"mississippi", "issi", "2"},
                {"aabaacaadaabaaba", "aaba", "3"},
        };

        for (String[] k : known) {
            int expected = Integer.parseInt(k[2]);
            checkEq("KMP '" + k[0] + "' / '" + k[1] + "'",
                    PatternMatching.countKMP(k[0], k[1]), expected);
            checkEq("RabinKarp '" + k[0] + "' / '" + k[1] + "'",
                    PatternMatching.countRabinKarp(k[0], k[1]), expected);
        }

        checkEq("KMP empty pattern", PatternMatching.countKMP("abc", ""), 0);
        checkEq("RK empty pattern", PatternMatching.countRabinKarp("abc", ""), 0);
        checkEq("KMP null text", PatternMatching.countKMP(null, "a"), 0);
        checkEq("RK null text", PatternMatching.countRabinKarp(null, "a"), 0);
        checkEq("KMP null pattern", PatternMatching.countKMP("a", null), 0);
        checkEq("RK null pattern", PatternMatching.countRabinKarp("a", null), 0);

        // non-ASCII characters (Rabin-Karp base is 256, chars can be larger)
        checkEq("KMP unicode", PatternMatching.countKMP("é日本é日", "é日"), 2);
        checkEq("RK unicode", PatternMatching.countRabinKarp("é日本é日", "é日"), 2);

        Random random = new Random(20261009L);
        String[] alphabets = {"ab", "abc", "a", "ab ", "abcdefghij 0123456789"};
        int mismatches = 0;

        for (int i = 0; i < 20000; i++) {
            String alphabet = alphabets[random.nextInt(alphabets.length)];
            String text = randomString(random, alphabet, random.nextInt(41));
            String pattern = randomString(random, alphabet, 1 + random.nextInt(7));
            int expected = naiveCount(text, pattern);

            if (PatternMatching.countKMP(text, pattern) != expected
                    || PatternMatching.countRabinKarp(text, pattern) != expected) {
                mismatches++;
            }
        }

        checkEq("20000 random cases: KMP and Rabin-Karp equal indexOf reference", mismatches, 0);

        // worst-case-style input must finish quickly and stay correct
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 300000; i++) {
            text.append('a');
        }
        String pattern = repeat('a', 2000) + "b";
        long start = System.nanoTime();
        int kmp = PatternMatching.countKMP(text.toString(), pattern);
        int rabin = PatternMatching.countRabinKarp(text.toString(), pattern);
        long millis = (System.nanoTime() - start) / 1_000_000L;
        check("a^300000 vs a^2000 b: both 0 and finishes in under 5 s (" + millis + " ms)",
                kmp == 0 && rabin == 0 && millis < 5000);
    }

    private static String randomString(Random random, String alphabet, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    // ================================================================
    // LEVENSHTEIN
    // ================================================================

    /** Independent top-down reference (memoised recursion, not the DP table). */
    private static int levReference(String a, String b, int i, int j, int[][] memo) {
        if (i == 0) {
            return j;
        }
        if (j == 0) {
            return i;
        }
        if (memo[i][j] >= 0) {
            return memo[i][j];
        }
        int result;
        if (a.charAt(i - 1) == b.charAt(j - 1)) {
            result = levReference(a, b, i - 1, j - 1, memo);
        } else {
            result = 1 + Math.min(levReference(a, b, i - 1, j, memo),
                    Math.min(levReference(a, b, i, j - 1, memo),
                            levReference(a, b, i - 1, j - 1, memo)));
        }
        memo[i][j] = result;
        return result;
    }

    private static void testLevenshtein() {

        group("Levenshtein");

        checkEq("kitten/sitting", Levenshtein.distance("kitten", "sitting"), 3);
        checkEq("flaw/lawn", Levenshtein.distance("flaw", "lawn"), 2);
        checkEq("intention/execution", Levenshtein.distance("intention", "execution"), 5);
        checkEq("empty/abc", Levenshtein.distance("", "abc"), 3);
        checkEq("abc/empty", Levenshtein.distance("abc", ""), 3);
        checkEq("empty/empty", Levenshtein.distance("", ""), 0);
        checkEq("identical", Levenshtein.distance("samsung", "samsung"), 0);
        checkEq("samsng/samsung", Levenshtein.distance("samsng", "samsung"), 1);
        checkEq("iphnoe/iphone (transposition = 2 edits)", Levenshtein.distance("iphnoe", "iphone"), 2);
        checkEq("case-insensitive", Levenshtein.distance("ABC", "abc"), 0);
        checkEq("null treated as empty", Levenshtein.distance(null, "abc"), 3);
        checkEq("both null", Levenshtein.distance(null, null), 0);

        Random random = new Random(11L);
        int mismatches = 0;
        int asymmetric = 0;
        int triangle = 0;

        for (int i = 0; i < 5000; i++) {
            String alphabet = random.nextBoolean() ? "ab" : "abcxyz";
            String a = randomString(random, alphabet, random.nextInt(13));
            String b = randomString(random, alphabet, random.nextInt(13));
            String c = randomString(random, alphabet, random.nextInt(13));

            int expected = levReference(a, b, a.length(), b.length(),
                    filled(a.length() + 1, b.length() + 1));

            if (Levenshtein.distance(a, b) != expected) {
                mismatches++;
            }
            if (Levenshtein.distance(a, b) != Levenshtein.distance(b, a)) {
                asymmetric++;
            }
            if (Levenshtein.distance(a, c)
                    > Levenshtein.distance(a, b) + Levenshtein.distance(b, c)) {
                triangle++;
            }
        }

        checkEq("5000 random pairs equal the independent recursive reference", mismatches, 0);
        checkEq("symmetry d(a,b) == d(b,a)", asymmetric, 0);
        checkEq("triangle inequality", triangle, 0);

        // two-row implementation must also work for long inputs
        String longA = repeat('a', 3000);
        String longB = repeat('a', 2990) + repeat('b', 10);
        checkEq("long strings (3000 chars)", Levenshtein.distance(longA, longB), 10);
    }

    private static int[][] filled(int rows, int cols) {
        int[][] memo = new int[rows][cols];
        for (int[] row : memo) {
            Arrays.fill(row, -1);
        }
        return memo;
    }

    // ================================================================
    // TRIE
    // ================================================================

    private static void testTrie() {

        group("Trie");

        Trie trie = new Trie();
        trie.insert("samsung");
        trie.insert("samsonite");
        trie.insert("sony");
        trie.insert("gaming");
        trie.insert("google");

        checkEq("prefix 'sam' (alphabetical)", trie.getWordsWithPrefix("sam"),
                new ArrayList<>(Arrays.asList("samsonite", "samsung")));
        checkEq("prefix 'so'", trie.getWordsWithPrefix("so"),
                new ArrayList<>(Arrays.asList("sony")));
        checkEq("prefix 'xyz' empty", trie.getWordsWithPrefix("xyz"), new ArrayList<String>());
        check("search('sony')", trie.search("sony"));
        check("search('son') is false (prefix only)", !trie.search("son"));
        check("startsWith('son')", trie.startsWith("son"));
        check("search is case-insensitive", trie.search("SONY"));
        check("search ignores surrounding blanks", trie.search("  sony "));
        check("search(null) false", !trie.search(null));
        check("search('') false", !trie.search(""));
        check("startsWith('') false", !trie.startsWith(""));
        checkEq("prefix(null) empty", trie.getWordsWithPrefix(null), new ArrayList<String>());

        Trie blank = new Trie();
        blank.insert(null);
        blank.insert("");
        blank.insert("   ");
        check("null / blank words are ignored", !blank.startsWith("a") && blank.getWordsWithPrefix("a").isEmpty());

        // duplicates are stored once
        Trie dup = new Trie();
        dup.insert("apple");
        dup.insert("APPLE");
        dup.insert("apple");
        checkEq("duplicate insert stored once", dup.getWordsWithPrefix("app").size(), 1);

        // randomised comparison with a TreeSet
        Random random = new Random(7L);
        int mismatches = 0;

        for (int round = 0; round < 300; round++) {
            Trie t = new Trie();
            TreeSet<String> reference = new TreeSet<>();

            for (int i = 0; i < random.nextInt(40); i++) {
                String word = randomString(random, "abc", 1 + random.nextInt(6));
                t.insert(word);
                reference.add(word);
            }

            for (int q = 0; q < 30; q++) {
                String prefix = randomString(random, "abc", 1 + random.nextInt(5));
                ArrayList<String> expected = new ArrayList<>();
                for (String w : reference) {
                    if (w.startsWith(prefix)) {
                        expected.add(w);
                    }
                }
                if (!t.getWordsWithPrefix(prefix).equals(expected)
                        || t.search(prefix) != reference.contains(prefix)
                        || t.startsWith(prefix) != !expected.isEmpty()) {
                    mismatches++;
                }
            }
        }

        checkEq("9000 random prefix queries equal TreeSet reference (order included)", mismatches, 0);
    }

    // ================================================================
    // EDMONDS-KARP
    // ================================================================

    /** Exact max flow via minimum cut: tries every source-side vertex subset. */
    private static int bruteForceMinCut(int n, int[][] capacity, int s, int t) {
        int best = Integer.MAX_VALUE;
        for (int mask = 0; mask < (1 << n); mask++) {
            if ((mask >> s & 1) == 0 || (mask >> t & 1) == 1) {
                continue;
            }
            int cut = 0;
            for (int u = 0; u < n; u++) {
                for (int v = 0; v < n; v++) {
                    if ((mask >> u & 1) == 1 && (mask >> v & 1) == 0) {
                        cut += capacity[u][v];
                    }
                }
            }
            best = Math.min(best, cut);
        }
        return best;
    }

    private static void testMaxFlow() {

        group("Edmonds-Karp max flow");

        // textbook network (CLRS 26.1): maximum flow = 23
        MaxFlow clrs = new MaxFlow(6);
        clrs.addEdge(0, 1, 16);
        clrs.addEdge(0, 2, 13);
        clrs.addEdge(1, 2, 10);
        clrs.addEdge(2, 1, 4);
        clrs.addEdge(1, 3, 12);
        clrs.addEdge(3, 2, 9);
        clrs.addEdge(2, 4, 14);
        clrs.addEdge(4, 3, 7);
        clrs.addEdge(3, 5, 20);
        clrs.addEdge(4, 5, 4);
        checkEq("CLRS example network", clrs.calculateMaxFlow(0, 5), 23);

        MaxFlow disconnected = new MaxFlow(3);
        disconnected.addEdge(0, 1, 5);
        checkEq("sink unreachable -> 0", disconnected.calculateMaxFlow(0, 2), 0);

        MaxFlow parallel = new MaxFlow(2);
        parallel.addEdge(0, 1, 3);
        parallel.addEdge(0, 1, 4);
        checkEq("parallel edges add up", parallel.calculateMaxFlow(0, 1), 7);

        MaxFlow zero = new MaxFlow(2);
        zero.addEdge(0, 1, 0);
        checkEq("zero capacity -> 0", zero.calculateMaxFlow(0, 1), 0);

        MaxFlow bottleneck = new MaxFlow(4);
        bottleneck.addEdge(0, 1, 100);
        bottleneck.addEdge(1, 2, 1);
        bottleneck.addEdge(2, 3, 100);
        checkEq("bottleneck edge", bottleneck.calculateMaxFlow(0, 3), 1);

        boolean rejected = false;
        try {
            new MaxFlow(2).addEdge(0, 1, -1);
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        check("negative capacity rejected", rejected);

        // flow needs a residual (reverse) edge to reach the optimum
        MaxFlow residual = new MaxFlow(4);
        residual.addEdge(0, 1, 1);
        residual.addEdge(0, 2, 1);
        residual.addEdge(1, 2, 1);
        residual.addEdge(1, 3, 1);
        residual.addEdge(2, 3, 1);
        checkEq("residual-edge network", residual.calculateMaxFlow(0, 3), 2);

        Random random = new Random(5L);
        int mismatches = 0;

        for (int round = 0; round < 3000; round++) {
            int n = 2 + random.nextInt(7);
            int[][] capacity = new int[n][n];
            MaxFlow flow = new MaxFlow(n);

            for (int e = 0; e < 1 + random.nextInt(20); e++) {
                int u = random.nextInt(n);
                int v = random.nextInt(n);
                if (u == v) {
                    continue;
                }
                int c = random.nextInt(9);
                capacity[u][v] += c;
                flow.addEdge(u, v, c);
            }

            if (flow.calculateMaxFlow(0, n - 1) != bruteForceMinCut(n, capacity, 0, n - 1)) {
                mismatches++;
            }
        }

        checkEq("3000 random graphs: max flow == brute-force min cut (max-flow min-cut theorem)",
                mismatches, 0);
    }

    // ================================================================
    // RECOMMENDATION POLICY (max flow with category / brand / total limits)
    // ================================================================

    private static String captureRecommendation(String[] names, String[] categories,
                                                String[] brands, int[] scores) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer));
        try {
            MaxFlow.runRecommendation("test", names, categories, brands, scores);
        } finally {
            System.setOut(original);
        }
        return buffer.toString();
    }

    private static int parseLimit(String output, String label) {
        for (String line : output.split("\n")) {
            if (line.startsWith(label)) {
                return Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
            }
        }
        return -1;
    }

    private static List<Integer> parseSelected(String output) {
        List<Integer> selected = new ArrayList<>();
        boolean inTable = false;
        for (String line : output.split("\n")) {
            if (line.startsWith("FINAL RECOMMENDATIONS")) {
                inTable = true;
            } else if (line.startsWith("TOP CANDIDATES")) {
                break;
            } else if (inTable && line.startsWith("| ") && !line.startsWith("| #")) {
                String name = line.split("\\|")[2].trim();
                if (name.startsWith("p")) {
                    selected.add(Integer.parseInt(name.substring(1)));
                }
            }
        }
        return selected;
    }

    private static boolean feasible(List<Integer> set, String[] cat, String[] brand,
                                    int perCategory, int perBrand) {
        if (set.size() > 6) {
            return false;
        }
        Map<String, Integer> c = new HashMap<>();
        Map<String, Integer> b = new HashMap<>();
        for (int i : set) {
            if (c.merge(cat[i].toLowerCase(), 1, Integer::sum) > perCategory) {
                return false;
            }
            if (b.merge(brand[i].toLowerCase(), 1, Integer::sum) > perBrand) {
                return false;
            }
        }
        return true;
    }

    private static int bestSize(int n, String[] cat, String[] brand, int perCategory, int perBrand) {
        int best = 0;
        for (int mask = 0; mask < (1 << n); mask++) {
            if (Integer.bitCount(mask) <= best || Integer.bitCount(mask) > 6) {
                continue;
            }
            List<Integer> set = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if ((mask >> i & 1) == 1) {
                    set.add(i);
                }
            }
            if (feasible(set, cat, brand, perCategory, perBrand)) {
                best = Integer.bitCount(mask);
            }
        }
        return best;
    }

    private static void testRecommendationPolicy() {

        group("Recommendation policy (max flow)");

        Random random = new Random(42L);
        String[] categories = {"Audio", "Laptop", "Smartphone", "Wearable"};
        String[] brandNames = {"Apple", "Sony", "ASUS", "Bose", "Dell"};

        int sizeWrong = 0;
        int limitsWrong = 0;
        int defaultsIgnored = 0;
        int notBest = 0;
        int tooMany = 0;

        for (int round = 0; round < 500; round++) {
            int n = 1 + random.nextInt(13);
            String[] names = new String[n];
            String[] cat = new String[n];
            String[] brand = new String[n];
            int[] scores = new int[n];
            int categoryPool = 1 + random.nextInt(4);
            int brandPool = 1 + random.nextInt(5);

            for (int i = 0; i < n; i++) {
                names[i] = "p" + i;
                cat[i] = categories[random.nextInt(categoryPool)];
                brand[i] = brandNames[random.nextInt(brandPool)];
                scores[i] = 1000 - i;
            }

            String out = captureRecommendation(names, cat, brand, scores);
            List<Integer> selected = parseSelected(out);
            int perCategory = parseLimit(out, "Max products / category");
            int perBrand = parseLimit(out, "Max products / brand");
            int target = Math.min(6, n);

            if (selected.size() > 6) {
                tooMany++;
            }
            if (!feasible(selected, cat, brand, perCategory, perBrand)) {
                limitsWrong++;
            }
            if (selected.size() != bestSize(n, cat, brand, perCategory, perBrand)) {
                sizeWrong++;
            }
            // the default limits (6 per category, 2 per brand) must be used whenever they can fill the list
            if (bestSize(n, cat, brand, 6, 2) == target && (perCategory != 6 || perBrand != 2)) {
                defaultsIgnored++;
            }
            // the best-ranked products must be kept: p0 is always selectable on its own
            if (!selected.isEmpty() && selected.get(0) != 0) {
                notBest++;
            }
        }

        checkEq("never more than 6 recommendations", tooMany, 0);
        checkEq("reported category / brand limits are respected", limitsWrong, 0);
        checkEq("returns the largest feasible number of products", sizeWrong, 0);
        checkEq("default limits (6 / category, 2 / brand) used whenever they can fill the list", defaultsIgnored, 0);
        checkEq("top-ranked candidate is always kept", notBest, 0);

        // fewer candidates than the limit
        String out = captureRecommendation(new String[] {"p0", "p1"},
                new String[] {"Audio", "Audio"}, new String[] {"Sony", "Bose"}, new int[] {10, 9});
        checkEq("2 candidates -> 2 recommendations", parseSelected(out).size(), 2);

        // empty / invalid input must not crash
        out = captureRecommendation(new String[0], new String[0], new String[0], new int[0]);
        check("empty input handled", out.contains("No products available"));
        out = captureRecommendation(new String[] {null, "  "}, new String[2], new String[2], new int[2]);
        check("blank names handled", out.contains("No valid products"));

        // duplicate names appear once; missing brand/category must not crash
        out = captureRecommendation(new String[] {"p0", "P0", "p1"}, new String[] {null, null, "Audio"},
                new String[] {null, null, ""}, null);
        checkEq("duplicate names collapsed, missing brand/category ok", parseSelected(out).size(), 2);

        // everything from one brand: limits must be relaxed and the note shown
        String[] names = new String[10];
        String[] cat = new String[10];
        String[] brand = new String[10];
        for (int i = 0; i < 10; i++) {
            names[i] = "p" + i;
            cat[i] = "Cat" + (i % 5);
            brand[i] = "Apple";
        }
        out = captureRecommendation(names, cat, brand, null);
        checkEq("single-brand candidates still fill 6 slots", parseSelected(out).size(), 6);
        check("adjusted-limits note printed", out.contains("limits were adjusted"));
        check("constraint check reports success", out.contains("All constraints satisfied."));
    }

    // ================================================================
    // CORPUS / DATA CONSISTENCY
    // ================================================================

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static void testCorpusConsistency(ProductDocument[] corpus) {

        group("Corpus consistency");

        check("corpus is not empty", corpus.length > 0);

        Set<String> taxonomy = new HashSet<>(Arrays.asList(
                "Smartphone", "Laptop", "Audio", "Monitor", "Camera", "Storage",
                "Wearable", "Tablet", "Networking", "Accessories", "Printer", "Other"));

        Set<String> names = new HashSet<>();
        Set<String> ids = new HashSet<>();
        Map<String, Set<String>> brandSpellings = new HashMap<>();
        int badCategory = 0;
        int badRating = 0;
        int badPrice = 0;
        int missingBrand = 0;
        int duplicateName = 0;
        int duplicateId = 0;
        int categoryVsSubcategory = 0;

        for (ProductDocument product : corpus) {

            if (!taxonomy.contains(product.getCategory())) {
                badCategory++;
            }

            if (!product.getRating().matches("[0-5](\\.[0-9])?/5")) {
                badRating++;
            }

            if (!product.getPrice().matches("INR [0-9]{1,3}(,[0-9]{2})*(,[0-9]{3})?")) {
                badPrice++;
            }

            if (product.getBrand().isEmpty()) {
                missingBrand++;
            }

            if (!names.add(normalize(product.getProductName()))) {
                duplicateName++;
            }

            String id = fieldOf(product.getContent(), "Product ID");
            if (!id.isEmpty() && !ids.add(id)) {
                duplicateId++;
            }

            brandSpellings
                    .computeIfAbsent(product.getBrand().toLowerCase(), k -> new HashSet<>())
                    .add(product.getBrand());

            // Category must agree with the product's own Subcategory
            String sub = normalize(fieldOf(product.getContent(), "Subcategory"));
            String cat = product.getCategory();

            boolean audioSub = sub.contains("headphone") || sub.contains("earbud")
                    || sub.contains("speaker") || sub.equals("audio devices");
            boolean watchSub = sub.contains("watch") || sub.contains("fitness");
            boolean phoneSub = sub.contains("phone") && !sub.contains("headphone");

            if ((audioSub && !cat.equals("Audio"))
                    || (watchSub && !cat.equals("Wearable"))
                    || (phoneSub && !cat.equals("Smartphone"))
                    || (cat.equals("Smartphone") && (audioSub || watchSub))) {
                categoryVsSubcategory++;
            }
        }

        checkEq("every Category is in the documented taxonomy", badCategory, 0);
        checkEq("every rating looks like 4.6/5", badRating, 0);
        checkEq("every price is formatted 'INR 1,29,999' (one grouping style)", badPrice, 0);
        checkEq("every product has a brand", missingBrand, 0);
        checkEq("no two products share a normalised name", duplicateName, 0);
        checkEq("no repeated Product ID", duplicateId, 0);
        checkEq("Category agrees with Subcategory (no headphones/watches filed as Smartphone)",
                categoryVsSubcategory, 0);

        int brandVariants = 0;
        for (Set<String> spellings : brandSpellings.values()) {
            if (spellings.size() > 1) {
                brandVariants++;
            }
        }
        checkEq("brand spelled one way only (no ASUS / Asus split)", brandVariants, 0);

        // pairs that used to be the same product listed under two titles
        String[][] formerDuplicates = {
                {"apple airpods pro 2", "apple airpods pro 2nd gen usb c"},
                {"sony wh 1000xm6", "sony wh 1000xm6 wireless headphones"},
                {"dji pocket 3", "dji osmo pocket 3"},
                {"sennheiser momentum 4", "sennheiser momentum 4 wireless"},
        };

        int stillDuplicated = 0;

        for (String[] pair : formerDuplicates) {

            int found = 0;

            for (ProductDocument product : corpus) {

                String n = normalize(product.getProductName());

                if (n.equals(pair[0]) || n.equals(pair[1])) {
                    found++;
                }
            }

            if (found > 1) {
                stillDuplicated++;
            }
        }

        checkEq("known same-product duplicates (AirPods Pro 2, WH-1000XM6, Pocket 3, Momentum 4) are gone",
                stillDuplicated, 0);
    }

    private static String fieldOf(String content, String key) {
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            int colon = trimmed.indexOf(':');
            if (colon > 0 && trimmed.substring(0, colon).trim().equalsIgnoreCase(key)) {
                return trimmed.substring(colon + 1).trim();
            }
        }
        return "";
    }

    // ================================================================
    // PRODUCT PARSING
    // ================================================================

    private static void testProductParsing(ProductDocument[] corpus) {

        group("ProductDocument parsing");

        ProductDocument sample = new ProductDocument("x.txt",
                "SAMPLE TITLE\nProduct Information:\n  Product Name: Sample Phone X\n"
                        + "  Brand: Acme\n  Category: Smartphone\n  Selling Price: ₹129,999\n"
                        + "  Average Rating: 4.5/5\n\nProduct Overview:\n  Overview: A sample.\n"
                        + "BLOCK 3 - Recommendation Data\n  Similar: secret-marker\n");

        checkEq("name from Product Name field", sample.getProductName(), "Sample Phone X");
        checkEq("brand", sample.getBrand(), "Acme");
        checkEq("category", sample.getCategory(), "Smartphone");
        checkEq("price re-grouped", sample.getPrice(), "INR 1,29,999");
        checkEq("rating", sample.getRating(), "4.5/5");
        checkEq("overview", sample.getOverview(), "A sample.");
        check("BLOCK 3 text excluded from searchable content",
                !sample.getSearchableContent().contains("secret-marker"));

        ProductDocument empty = new ProductDocument("empty.txt", "");
        checkEq("empty file: name falls back to file name", empty.getProductName(), "empty.txt");
        checkEq("empty file: category", empty.getCategory(), "Other");
        checkEq("empty file: price", empty.getPrice(), "");

        ProductDocument noName = new ProductDocument("n.txt", "Category: Laptop\nPrice: 5000\nMy Laptop 5\n");
        checkEq("metadata-looking first lines skipped for the title", noName.getProductName(), "My Laptop 5");

        checkEq("price grouping 129999", ProductDocument.groupIndian("129,999"), "1,29,999");
        checkEq("price grouping 1,19,999 unchanged", ProductDocument.groupIndian("1,19,999"), "1,19,999");
        checkEq("price grouping 999", ProductDocument.groupIndian("999"), "999");
        checkEq("price grouping 10000000", ProductDocument.groupIndian("10000000"), "1,00,00,000");
        checkEq("price grouping non-numeric untouched", ProductDocument.groupIndian("Call us"), "Call us");

        ProductDocument crlf = new ProductDocument("c.txt", "Product Name: A B\r\nBrand: Z\r\n");
        checkEq("CRLF line endings", crlf.getBrand(), "Z");
    }

    // ================================================================
    // SEARCH ENGINE (ranking, spelling recovery, edge cases)
    // ================================================================

    private static List<String> topNames(SearchEngine.Outcome outcome, int limit) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, outcome.results.size()); i++) {
            names.add(outcome.results.get(i).product.getProductName());
        }
        return names;
    }

    private static boolean allHaveCategory(SearchEngine.Outcome outcome, int limit, String category) {
        for (int i = 0; i < Math.min(limit, outcome.results.size()); i++) {
            if (!outcome.results.get(i).product.getCategory().equals(category)) {
                return false;
            }
        }
        return outcome.results.size() > 0;
    }

    private static void testSearchEngine(ProductDocument[] corpus) {

        group("Search engine");

        SearchEngine engine = new SearchEngine(corpus);

        check("empty query -> null", engine.rank("") == null);
        check("blank query -> null", engine.rank("    ") == null);
        check("punctuation-only query -> null", engine.rank("!!! ???") == null);
        check("null query -> null", engine.rank(null) == null);

        SearchEngine.Outcome none = engine.rank("xyznonexistent123");
        check("no match -> empty result list", none != null && none.results.isEmpty());
        check("random letters -> empty", engine.rank("zzzz").results.isEmpty());
        check("single letter does not match everything", engine.rank("n").results.isEmpty());
        check("short number does not match everything", engine.rank("7").results.size() < corpus.length / 2);

        // misspellings
        SearchEngine.Outcome typo = engine.rank("samsng phone");
        checkEq("'samsng phone' corrected", typo.correctedQuery, "samsung phone");
        boolean samsungPhones = true;
        for (int i = 0; i < 3; i++) {
            ProductDocument p = typo.results.get(i).product;
            samsungPhones &= p.getBrand().equals("Samsung") && p.getCategory().equals("Smartphone");
        }
        check("top 3 for 'samsng phone' are Samsung smartphones", samsungPhones);

        checkEq("'iphnoe' -> iphone", engine.rank("iphnoe").correctedQuery, "iphone");
        checkEq("'wireles headphnes' corrected", engine.rank("wireles headphnes").correctedQuery, "wireless headphones");
        checkEq("'galaxy s24 ultr' -> ultra (not the rarer 'ult')", engine.rank("galaxy s24 ultr").correctedQuery, "galaxy s24 ultra");
        checkEq("top hit for 'galaxy s24 ultr'", topNames(engine.rank("galaxy s24 ultr"), 1).get(0), "Samsung Galaxy S24 Ultra");

        // valid words and model numbers are never rewritten
        checkEq("'amoled' is a real word, not 'oled'", engine.rank("amoled").correctedQuery, "amoled");
        checkEq("'128' not rewritten", engine.rank("128").correctedQuery, "128");
        checkEq("'s24' not rewritten", engine.rank("s24 ultra").correctedQuery, "s24 ultra");
        checkEq("'tv' (too short) not rewritten", engine.rank("tv").correctedQuery, "tv");

        // exact product
        SearchEngine.Outcome exact = engine.rank("Samsung Galaxy S24 Ultra");
        checkEq("exact name ranks first", topNames(exact, 1).get(0), "Samsung Galaxy S24 Ultra");
        checkEq("upper-case query same top hit", topNames(engine.rank("SAMSUNG GALAXY S24 ULTRA"), 1).get(0),
                "Samsung Galaxy S24 Ultra");

        // category intent
        check("'phone' -> top 10 all Smartphone", allHaveCategory(engine.rank("phone"), 10, "Smartphone"));
        check("'headphones' -> top 10 all Audio", allHaveCategory(engine.rank("headphones"), 10, "Audio"));
        check("'laptop' -> top 10 all Laptop", allHaveCategory(engine.rank("laptop"), 10, "Laptop"));
        check("'watch' -> top 10 all Wearable", allHaveCategory(engine.rank("watch"), 10, "Wearable"));
        check("'audio' -> top 10 all Audio", allHaveCategory(engine.rank("audio"), 10, "Audio"));
        check("'wearable' -> top 10 all Wearable", allHaveCategory(engine.rank("wearable"), 10, "Wearable"));
        check("'printer' -> the printer", allHaveCategory(engine.rank("printer"), 1, "Printer"));
        check("'keyboard' -> Accessories", allHaveCategory(engine.rank("keyboard"), 5, "Accessories"));
        check("'router' -> Networking", allHaveCategory(engine.rank("router"), 5, "Networking"));
        check("'monitor' -> top 10 all Monitor", allHaveCategory(engine.rank("monitor"), 10, "Monitor"));
        check("'tablet' -> Tablet", allHaveCategory(engine.rank("tablet"), 5, "Tablet"));
        check("'ipad' -> Tablet", allHaveCategory(engine.rank("ipad"), 3, "Tablet"));

        // a laptop whose name contains "OLED" is still a laptop
        boolean zenbookFound = false;
        for (SearchEngine.Ranked r : engine.rank("laptop").results) {
            if (normalize(r.product.getProductName()).equals("asus zenbook 14 oled")) {
                zenbookFound = r.score >= 400 && r.product.getCategory().equals("Laptop");
            }
        }
        check("ASUS ZenBook 14 OLED is ranked as a laptop (not penalised as a monitor)", zenbookFound);

        // brand-only matches must not be dropped
        int appleProducts = 0;
        for (ProductDocument p : corpus) {
            if (p.getBrand().equalsIgnoreCase("apple")) {
                appleProducts++;
            }
        }
        SearchEngine.Outcome apple = engine.rank("apple");
        int appleReturned = 0;
        for (SearchEngine.Ranked r : apple.results) {
            if (r.product.getBrand().equalsIgnoreCase("apple")) {
                appleReturned++;
            }
        }
        checkEq("'apple' returns every Apple-brand product (incl. MacBooks without 'Apple' in the title)",
                appleReturned, appleProducts);
        boolean macbookInTop = false;
        for (String n : topNames(apple, apple.results.size())) {
            macbookInTop |= normalize(n).startsWith("macbook");
        }
        check("MacBook Pro 14 M4 found by brand query", macbookInTop);

        // whole-word matching
        boolean allWholeWord = true;
        for (String n : topNames(engine.rank("pro"), 10)) {
            allWholeWord &= (" " + normalize(n) + " ").contains(" pro ");
        }
        check("'pro' top 10 contain the word 'pro' (not 'proart')", allWholeWord);

        // description-only (feature) queries
        check("'waterproof' finds products through their description text",
                !engine.rank("waterproof").results.isEmpty());
        checkEq("'stylus phone' puts the S-Pen phone first", topNames(engine.rank("stylus phone"), 1).get(0),
                "Samsung Galaxy S24 Ultra");

        // repeated words / long / unusual input
        checkEq("'phone phone' ranks like 'phone'",
                topNames(engine.rank("phone phone"), 10), topNames(engine.rank("phone"), 10));
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            huge.append("samsung phone ");
        }
        check("very long query does not crash", engine.rank(huge.toString()) != null);
        check("unicode query does not crash", engine.rank("café 日本 phone") != null);
        check("tabs / extra spaces", engine.rank("  samsung \t  phone ").correctedQuery.equals("samsung phone"));

        // ranking order is non-increasing and deterministic
        SearchEngine.Outcome ordered = engine.rank("samsung");
        boolean sorted = true;
        for (int i = 1; i < ordered.results.size(); i++) {
            sorted &= ordered.results.get(i - 1).score >= ordered.results.get(i).score;
        }
        check("results sorted by score descending", sorted);
        checkEq("repeating a query gives the identical ranking",
                topNames(engine.rank("samsung"), 50), topNames(engine.rank("samsung"), 50));

        // engine on an empty corpus
        SearchEngine emptyEngine = new SearchEngine(new ProductDocument[0]);
        check("empty corpus: no results, no crash", emptyEngine.rank("phone").results.isEmpty());
        SearchEngine nullEngine = new SearchEngine(null);
        check("null corpus: no results, no crash", nullEngine.rank("phone").results.isEmpty());
    }

    // ================================================================
    // END-TO-END CONSOLE FLOW (search + recommendation prompt)
    // ================================================================

    private static String runSearchConsole(SearchEngine engine, String query, String input) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer));
        try {
            engine.search(query, new Scanner(new ByteArrayInputStream(input.getBytes())));
        } finally {
            System.setOut(original);
        }
        return buffer.toString();
    }

    private static void testConsoleFlow(ProductDocument[] corpus) {

        group("Console flow (search + recommendations)");

        SearchEngine engine = new SearchEngine(corpus);

        String out = runSearchConsole(engine, "phone", "Y\n");
        check("search results printed", out.contains("SEARCH RESULTS"));
        check("recommendations printed after Y", out.contains("RECOMMENDATION ENGINE"));
        check("at most 6 recommendations", out.contains("Final Recommendation Count : 6"));
        check("limits: 6 total / 2 per brand", out.contains("Maximum recommendations : 6")
                && out.contains("Max products / brand    : 2"));
        check("constraint table all OK", out.contains("All constraints satisfied.") && !out.contains("FAIL"));

        out = runSearchConsole(engine, "phone", "n\n");
        check("N skips recommendations", !out.contains("RECOMMENDATION ENGINE"));

        out = runSearchConsole(engine, "phone", "yes\n");
        check("'yes' accepted", out.contains("RECOMMENDATION ENGINE"));

        // the old code crashed with NoSuchElementException here
        boolean crashed = false;
        try {
            runSearchConsole(engine, "laptop", "");
        } catch (RuntimeException e) {
            crashed = true;
        }
        check("input ending at the Y/N prompt does not crash", !crashed);

        out = runSearchConsole(engine, "apple", "Y\n");
        check("brand query shows adjusted-limits note", out.contains("limits were adjusted"));
        check("brand query still yields 6 recommendations", out.contains("Final Recommendation Count : 6"));

        out = runSearchConsole(engine, "xyznonexistent123", "Y\n");
        check("no match: friendly message", out.contains("No matching products found."));
        check("no match: no recommendation prompt", !out.contains("Would you like product recommendations"));

        out = runSearchConsole(engine, "   ", "");
        check("blank query message", out.contains("Please enter a valid search query."));

        out = runSearchConsole(engine, "samsng phone", "N\n");
        check("'Did you mean' shown", out.contains("Did you mean : samsung phone?"));
        check("edit distance shown", out.contains("Levenshtein edit distance = 1"));

        out = runSearchConsole(engine, "phone", "N\n");
        check("result count note when more than 10 match", out.contains("Showing top 10 of"));
        check("price printed in one format", !out.contains("INR 119,999") && out.contains("INR 1,19,999"));
    }

    // ================================================================
    // TRIE PREFIX SEARCH OVER THE CORPUS
    // ================================================================

    private static void testTrieSearch(ProductDocument[] corpus) {

        group("Trie prefix search (corpus)");

        TrieSearch search = new TrieSearch();
        search.build(corpus);

        ArrayList<ProductDocument> sam = search.searchProducts("sam");
        check("'sam' finds products", !sam.isEmpty());
        boolean samsungPresent = false;
        for (ProductDocument p : sam) {
            samsungPresent |= p.getBrand().equals("Samsung");
        }
        check("'sam' includes Samsung products", samsungPresent);
        check("'xyz' finds nothing", search.searchProducts("xyz").isEmpty());
        check("empty prefix finds nothing", search.searchProducts("").isEmpty());
        check("blank prefix finds nothing", search.searchProducts("   ").isEmpty());
        check("null prefix finds nothing", search.searchProducts(null).isEmpty());
        check("prefix is case-insensitive",
                search.searchProducts("SAM").size() == sam.size());

        ArrayList<ProductDocument> multi = search.searchProducts("samsung gal");
        boolean allGalaxy = !multi.isEmpty();
        for (ProductDocument p : multi) {
            allGalaxy &= p.getBrand().equals("Samsung")
                    && normalize(p.getContent()).contains("galaxy");
        }
        check("multi-word prefix 'samsung gal' -> Samsung Galaxy products only", allGalaxy);
        check("punctuation in prefix ('galaxy-s2') is normalised", !search.searchProducts("galaxy-s2").isEmpty());

        Set<ProductDocument> unique = new HashSet<>(sam);
        checkEq("no duplicate products in results", unique.size(), sam.size());
    }

    // ================================================================
    // LEVENSHTEIN PRODUCT-LEVEL FALLBACK
    // ================================================================

    private static void testLevenshteinFallback(ProductDocument[] corpus) {

        group("Levenshtein fallback search");

        check("no match -> empty", LevenshteinSearch.search(corpus, "xyznonexistent123").isEmpty());
        check("empty query -> empty", LevenshteinSearch.search(corpus, "").isEmpty());
        check("1-letter query -> empty (no noise)", LevenshteinSearch.search(corpus, "n").isEmpty());
        check("2-letter query -> empty (no noise)", LevenshteinSearch.search(corpus, "tv").isEmpty());
        check("number is not fuzzy-matched", LevenshteinSearch.search(corpus, "128").isEmpty());

        ArrayList<LevenshteinSearch.Result> typo = LevenshteinSearch.search(corpus, "headphnes");
        check("'headphnes' recovers headphone products", !typo.isEmpty()
                && normalize(typo.get(0).productName).contains("headphone"));
    }

    // ================================================================
    // KMP vs RABIN-KARP OVER THE REAL CORPUS (what /time compares)
    // ================================================================

    private static void testBenchmarkCounts(ProductDocument[] corpus) {

        group("Benchmark counts (/time)");

        List<String> texts = new ArrayList<>();
        for (ProductDocument p : corpus) {
            texts.add(normalize(p.getProductName() + " " + p.getBrand() + " "
                    + p.getCategory() + " " + p.getSearchableContent()));
        }

        String[] patterns = {"wireless charging", "120hz", "usb c", "bluetooth", "gaming",
                "camera", "fast charging", "5g", "xyznonexistent123", "a", "samsung"};

        int disagreements = 0;

        for (String pattern : patterns) {
            int kmp = 0;
            int rabin = 0;
            int reference = 0;
            for (String text : texts) {
                kmp += PatternMatching.countKMP(text, pattern);
                rabin += PatternMatching.countRabinKarp(text, pattern);
                reference += naiveCount(text, pattern);
            }
            if (kmp != rabin || kmp != reference) {
                disagreements++;
                System.out.println("  mismatch for '" + pattern + "': kmp=" + kmp
                        + " rk=" + rabin + " reference=" + reference);
            }
        }

        checkEq("KMP == Rabin-Karp == indexOf reference for every benchmark pattern", disagreements, 0);
    }
}