import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ================================================================
 * SEARCH ENGINE
 * ================================================================
 *
 * Architecture (for viva explanation):
 *
 *                   PRODUCT SEARCH SYSTEM
 *                           |
 *               +-----------+-----------+
 *               |                       |
 *          SEARCH ENGINE          RECOMMENDATION
 *               |                       |
 *       KMP / Rabin-Karp          Edmonds-Karp
 *       Levenshtein               Max Flow
 *       Relevance Score           Category limits
 *       Ranking                   Product limits
 *
 * KMP and Rabin-Karp are used for efficient exact-phrase pattern
 * matching inside a product's text. Levenshtein distance is used
 * for spelling recovery, applied to each query word BEFORE
 * retrieval so a misspelled query is corrected first and then
 * searched normally. A weighted, field-based relevance-ranking
 * model (not the pattern-matching algorithms themselves) decides
 * the order of search results. Edmonds-Karp / Max Flow is used
 * separately, only for the recommendation step, subject to
 * category and product capacity constraints.
 */
public class SearchEngine {

    private final ProductDocument[] corpus;

    /** Pre-computed, query-independent view of every product (built once). */
    private final ProductIndex[] index;

    /**
     * Word -> number of products containing it. Used for spelling recovery:
     * a word that appears in the corpus is never "corrected", and when
     * several corrections are equally close the most common word wins.
     */
    private final Map<String, Integer> wordFrequency;

    private static final int MAX_RESULTS = 10;
    private static final int RECOMMENDATION_POOL = 30;

    // ----------------------------------------------------------
    // RELEVANCE SCORE WEIGHTS
    // ----------------------------------------------------------
    // Match location                          Score
    // Exact product name                      +300
    // Complete query phrase inside name        +220
    // Product name contains word               +100  (per matched word)
    // Brand match                              +80   (per matched word)
    // Category / product type match            +180  (per matched word)
    // Description match                        +10   (per matched word)
    // Both/all query terms matched strongly    +200
    // All terms matched, some only in text     +150
    // Query category intent matches product    +250
    // Query category intent WRONG for product  -180
    // Exact full query phrase found            +50
    // Rating                                   tie-breaker only
    //
    // "Matched" always means a WHOLE-WORD match (see tokenMatches): "pro"
    // does not match "proart" and a one-letter query does not match every
    // product. A word found only in the description is a weak match: it
    // qualifies a product (so feature queries such as "waterproof" work)
    // but scores far below a name / brand / category match.
    //
    // The category-intent block is what makes "samsung phone" push
    // Samsung smartphones to the top and push Samsung earbuds/watches/
    // SSDs down - a brand match alone is no longer enough to rank well
    // if the product is clearly the wrong kind of thing.
    // ----------------------------------------------------------

    // ----------------------------------------------------------
    // CATEGORY SYNONYMS (search-term understanding)
    // ----------------------------------------------------------

    private static final Map<String, Set<String>> CATEGORY_SYNONYMS = buildSynonyms();

    /** Query word -> product category it clearly asks for. */
    private static final Map<String, String> CATEGORY_INTENT = buildIntent();

    /** The product taxonomy used in the corpus (README: "Other" = unknown). */
    private static final String[] CANONICAL_CATEGORIES = {
            "Smartphone", "Laptop", "Audio", "Monitor", "Camera", "Storage",
            "Wearable", "Tablet", "Networking", "Accessories", "Printer"
    };

    public SearchEngine(ProductDocument[] corpus) {

        this.corpus = corpus == null ? new ProductDocument[0] : corpus;

        ArrayList<ProductIndex> built = new ArrayList<>();

        for (ProductDocument product : this.corpus) {

            if (product != null) {
                built.add(new ProductIndex(product));
            }
        }

        this.index = built.toArray(new ProductIndex[0]);
        this.wordFrequency = buildWordFrequency(this.index);
    }

    // ================================================================
    // PER-PRODUCT INDEX
    // ================================================================

    private final class ProductIndex {

        final ProductDocument product;

        final String name;               // normalised product name
        final Set<String> nameTokens;
        final Set<String> brandTokens;
        final String category;           // taxonomy value, e.g. "Smartphone"
        final String categoryNormalized; // lower case
        final Set<String> descriptionTokens;
        final String phraseText;         // " name brand category description "
        final double rating;

        ProductIndex(ProductDocument product) {

            this.product = product;

            this.name = normalize(product.getProductName());
            this.nameTokens = tokens(name);
            this.brandTokens = tokens(normalize(product.getBrand()));
            this.category = getRecommendationCategory(product);
            this.categoryNormalized = normalize(category);
            this.rating = parseRating(product.getRating());

            // Broad curated text: overview, features, technical specs and
            // the search index. Reviews, Q&A and customer queries are
            // already excluded by getSearchableContent(); the stored
            // "Common Misspellings" lines are removed as well so a typo that
            // is listed in the corpus cannot count as a real word.
            String description = normalize(
                    product.getSearchableContentWithoutMisspellings()
            );

            this.descriptionTokens = tokens(description);

            this.phraseText = " " + normalize(
                    product.getProductName() + " "
                            + product.getBrand() + " "
                            + category + " "
                            + description
            ) + " ";
        }
    }

    private static Set<String> tokens(String normalized) {

        Set<String> set = new HashSet<>();

        if (!normalized.isEmpty()) {
            set.addAll(Arrays.asList(normalized.split("\\s+")));
        }

        return set;
    }

    // ================================================================
    // MAIN SEARCH
    // ================================================================

    /** One ranked search hit. */
    public static final class Ranked {

        public final ProductDocument product;
        public final int score;

        Ranked(ProductDocument product, int score) {
            this.product = product;
            this.score = score;
        }
    }

    /** Everything the ranking step produces for one query. */
    public static final class Outcome {

        public final String normalizedQuery;
        public final String correctedQuery;
        public final String algorithm;
        public final List<Ranked> results;

        Outcome(String normalizedQuery,
                String correctedQuery,
                String algorithm,
                List<Ranked> results) {

            this.normalizedQuery = normalizedQuery;
            this.correctedQuery = correctedQuery;
            this.algorithm = algorithm;
            this.results = results;
        }
    }

    /**
     * Spelling recovery + scoring + sorting, without any printing.
     * Returns null when the query contains no searchable word.
     * (search() below is the console front-end for this method.)
     */
    public Outcome rank(String query) {

        String normalizedQuery = normalize(query);

        if (normalizedQuery.isEmpty()) {
            return null;
        }

        // --------------------------------------------------------
        // SPELLING RECOVERY BEFORE RETRIEVAL
        // --------------------------------------------------------

        String[] rawTerms = normalizedQuery.split("\\s+");

        String[] terms = correctSpelling(rawTerms);

        String correctedQuery = String.join(" ", terms);

        // a repeated word ("phone phone") must not be counted twice
        String[] uniqueTerms = new LinkedHashSet<>(Arrays.asList(terms))
                .toArray(new String[0]);

        // phrase used for scoring: the unique words, so "phone phone"
        // scores exactly like "phone"
        String scoringQuery = String.join(" ", uniqueTerms);

        String algorithm = chooseAlgorithm(scoringQuery);

        String queryCategory = detectQueryCategory(uniqueTerms);

        List<ScoredIndex> scored = new ArrayList<>();

        // ============================================================
        // SCORE EVERY CANDIDATE IN THE ENTIRE CORPUS
        // (no early cut-off: every product gets scored, then we sort)
        // ============================================================

        for (ProductIndex entry : index) {

            int score = scoreProduct(
                    entry, uniqueTerms, scoringQuery, algorithm, queryCategory
            );

            if (score > 0) {
                scored.add(new ScoredIndex(entry, score));
            }
        }

        // ============================================================
        // SORT: SCORE DESC -> RATING DESC (TIE-BREAKER) -> NAME ASC
        // ============================================================

        scored.sort((a, b) -> {

            int scoreCompare = Integer.compare(b.score, a.score);

            if (scoreCompare != 0) {
                return scoreCompare;
            }

            int ratingCompare = Double.compare(b.entry.rating, a.entry.rating);

            if (ratingCompare != 0) {
                return ratingCompare;
            }

            return a.entry.product.getProductName()
                    .compareToIgnoreCase(b.entry.product.getProductName());
        });

        List<Ranked> results = new ArrayList<>();

        for (ScoredIndex item : scored) {
            results.add(new Ranked(item.entry.product, item.score));
        }

        return new Outcome(normalizedQuery, correctedQuery, algorithm, results);
    }

    public void search(String query, Scanner scanner) {

        if (query == null || query.trim().isEmpty()) {
            System.out.println("Please enter a valid search query.");
            return;
        }

        query = query.trim();

        Outcome outcome = rank(query);

        if (outcome == null) {
            System.out.println("Please enter a valid search query.");
            return;
        }

        // ============================================================
        // NO RELEVANT CANDIDATES -> FALL BACK TO PRODUCT-LEVEL LEVENSHTEIN
        // ============================================================

        if (outcome.results.isEmpty()) {

            displayLevenshtein(query);

            return;
        }

        // ============================================================
        // DISPLAY SEARCH RESULTS
        // ============================================================

        displayResults(
                query,
                outcome.normalizedQuery,
                outcome.correctedQuery,
                outcome.algorithm,
                outcome.results
        );

        // ============================================================
        // RECOMMENDATION OPTION (Edmonds-Karp / Max Flow, kept separate)
        // ============================================================

        System.out.print("Would you like product recommendations? (Y/N): ");

        // Input may end here (piped input, Ctrl+D): treat as "no".
        if (!scanner.hasNextLine()) {
            System.out.println();
            return;
        }

        String answer = scanner.nextLine().trim();

        if (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes")) {

            runRecommendations(query, outcome.results);
        }
    }

    // ================================================================
    // FIELD-WEIGHTED RELEVANCE SCORING
    // ================================================================

    private int scoreProduct(
            ProductIndex p,
            String[] terms,
            String correctedQuery,
            String algorithm,
            String queryCategory) {

        int score = 0;

        // ============================================================
        // 1. EXACT PRODUCT NAME
        // ============================================================

        if (p.name.equals(correctedQuery)) {
            score += 300;
        }

        // ============================================================
        // 2. COMPLETE QUERY INSIDE PRODUCT NAME (as whole words)
        // ============================================================

        else if ((" " + p.name + " ").contains(" " + correctedQuery + " ")) {

            score += 220;
        }

        // ============================================================
        // 3. QUERY TERM MATCHING
        //
        // strongTerms = terms found in name / brand / category
        // weakTerms   = terms found only in the description text
        // Each term is counted once, however many fields it matches.
        // ============================================================

        int strongTerms = 0;
        int weakTerms = 0;

        for (String term : terms) {

            if (term.isEmpty()) {
                continue;
            }

            boolean strong = false;

            // PRODUCT NAME MATCH
            if (nameMatches(p.nameTokens, term)) {
                score += 100;
                strong = true;
            }

            // BRAND MATCH
            if (p.brandTokens.contains(term)) {
                score += 80;
                strong = true;
            }

            // CATEGORY / PRODUCT TYPE MATCH
            if (matchesCategory(term, p.categoryNormalized)) {
                score += 180;
                strong = true;
            }

            // DESCRIPTION MATCH
            if (descriptionMatches(p.descriptionTokens, term)) {
                score += 10;

                if (!strong) {
                    weakTerms++;
                }
            }

            if (strong) {
                strongTerms++;
            }
        }

        // ============================================================
        // 4. MULTI-WORD QUERY BONUS
        //
        // Example:
        // "samsung phone"
        //
        // A product matching BOTH samsung and phone should receive
        // a large bonus.
        // ============================================================

        if (terms.length > 1) {

            if (strongTerms == terms.length) {
                score += 200;
            } else if (strongTerms + weakTerms == terms.length) {
                // every word matched, some only in the description
                // ("stylus phone" -> a phone whose text mentions a stylus)
                score += 150;
            }
        }

        // ============================================================
        // 5. QUERY CATEGORY INTENT
        //
        // If the user searches "phone", a phone should strongly
        // outrank earbuds, watches, monitors, etc.
        // ============================================================

        if (!queryCategory.isEmpty()) {

            if (p.category.equalsIgnoreCase(queryCategory)) {
                score += 250;
            } else {
                // Wrong product category.
                //
                // Example:
                // Query = Samsung phone
                // Product = Samsung Galaxy Buds
                //
                // Samsung matches, but Buds are NOT phones.
                score -= 180;
            }
        }

        // ============================================================
        // 6. FULL QUERY PHRASE MATCH (KMP / Rabin-Karp)
        //
        // Searches only the curated text (name, brand, category and
        // description), NOT reviews or the raw document. The phrase is
        // padded with spaces so it only matches whole words.
        // ============================================================

        String paddedQuery = " " + correctedQuery + " ";

        int occurrences;

        if (algorithm.equals("KMP")) {
            occurrences = PatternMatching.countKMP(p.phraseText, paddedQuery);
        } else {
            occurrences = PatternMatching.countRabinKarp(p.phraseText, paddedQuery);
        }

        if (occurrences > 0) {
            score += 50;
        }

        // ============================================================
        // 7. REQUIRE AT LEAST ONE REAL MATCH
        // ============================================================

        if (strongTerms == 0 && weakTerms == 0 && queryCategory.isEmpty()) {
            return 0;
        }

        return Math.max(score, 0);
    }

    // ================================================================
    // WHOLE-WORD MATCHING
    // ================================================================

    /**
     * Does one product-name word match one query word?
     *
     * Exact match, a trailing model number ("buds" -> "buds3",
     * "watch" -> "watch7"), a simple plural ("laptops" <-> "laptop"),
     * or a compound word ending in the query ("phone" -> "iphone",
     * "book" -> "macbook"). It never matches in the middle of a word and
     * never matches a query shorter than 3 characters by anything but
     * equality, so "pro" does not match "proart" and "n" matches nothing.
     */
    private static boolean tokenMatches(String token, String term) {

        if (token.equals(term)) {
            return true;
        }

        if (term.length() >= 3
                && token.startsWith(term)
                && isDigits(token.substring(term.length()))) {

            return true;
        }

        if (term.length() >= 4) {

            if (token.equals(term + "s") || term.equals(token + "s")) {
                return true;
            }

            if (token.endsWith(term)) {
                return true;
            }
        }

        return false;
    }

    private static boolean nameMatches(Set<String> nameTokens, String term) {

        if (nameTokens.contains(term)) {
            return true;
        }

        for (String token : nameTokens) {

            if (tokenMatches(token, term)) {
                return true;
            }
        }

        return false;
    }

    /** Description text is long, so only exact words and plurals are tried. */
    private static boolean descriptionMatches(Set<String> tokens, String term) {

        if (tokens.contains(term)) {
            return true;
        }

        if (term.length() >= 4) {

            if (tokens.contains(term + "s")) {
                return true;
            }

            if (term.endsWith("s") && tokens.contains(term.substring(0, term.length() - 1))) {
                return true;
            }
        }

        return false;
    }

    private static boolean isDigits(String text) {

        if (text.isEmpty()) {
            return false;
        }

        for (int i = 0; i < text.length(); i++) {

            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    // ================================================================
    // QUERY CATEGORY INTENT DETECTION
    // ================================================================

    private String detectQueryCategory(String[] terms) {

        for (String term : terms) {

            String category = CATEGORY_INTENT.get(term);

            if (category != null) {
                return category;
            }
        }

        return "";
    }

    private static Map<String, String> buildIntent() {

        Map<String, String> map = new HashMap<>();

        for (String w : new String[] {"phone", "phones", "smartphone", "smartphones",
                "mobile", "mobiles"}) {
            map.put(w, "Smartphone");
        }

        for (String w : new String[] {"earbud", "earbuds", "bud", "buds", "earphone",
                "earphones", "headphone", "headphones", "headset", "headsets",
                "speaker", "speakers", "audio"}) {
            map.put(w, "Audio");
        }

        for (String w : new String[] {"laptop", "laptops", "notebook", "notebooks",
                "macbook"}) {
            map.put(w, "Laptop");
        }

        for (String w : new String[] {"watch", "watches", "smartwatch", "smartwatches",
                "wearable", "wearables"}) {
            map.put(w, "Wearable");
        }

        for (String w : new String[] {"camera", "cameras", "dslr", "mirrorless"}) {
            map.put(w, "Camera");
        }

        for (String w : new String[] {"monitor", "monitors", "display", "displays"}) {
            map.put(w, "Monitor");
        }

        for (String w : new String[] {"ssd", "hdd", "storage"}) {
            map.put(w, "Storage");
        }

        for (String w : new String[] {"tablet", "tablets", "ipad"}) {
            map.put(w, "Tablet");
        }

        for (String w : new String[] {"keyboard", "keyboards", "mouse", "mice",
                "accessory", "accessories"}) {
            map.put(w, "Accessories");
        }

        for (String w : new String[] {"router", "routers", "mesh", "networking"}) {
            map.put(w, "Networking");
        }

        for (String w : new String[] {"printer", "printers"}) {
            map.put(w, "Printer");
        }

        return map;
    }

    // ================================================================
    // CATEGORY SYNONYM MATCHING
    // ================================================================

    private boolean matchesCategory(String term, String categoryNormalized) {

        if (categoryNormalized.isEmpty()) {
            return false;
        }

        // Direct category match (whole word, simple plurals allowed)
        if (categoryNormalized.equals(term)
                || (term.length() >= 4
                    && (categoryNormalized.equals(term + "s")
                        || term.equals(categoryNormalized + "s")))) {
            return true;
        }

        // Synonym match
        Set<String> synonyms = CATEGORY_SYNONYMS.get(term);

        if (synonyms != null) {
            return synonyms.contains(categoryNormalized);
        }

        return false;
    }

    private static Map<String, Set<String>> buildSynonyms() {

        Map<String, Set<String>> map = new HashMap<>();

        map.put("phone", setOf("smartphone"));
        map.put("phones", setOf("smartphone"));
        map.put("smartphones", setOf("smartphone"));
        map.put("mobile", setOf("smartphone"));
        map.put("mobiles", setOf("smartphone"));

        for (String w : new String[] {"earbuds", "earbud", "buds", "bud", "earphone",
                "earphones", "headphone", "headphones", "headset", "headsets",
                "speaker", "speakers"}) {
            map.put(w, setOf("audio"));
        }

        map.put("notebook", setOf("laptop"));
        map.put("notebooks", setOf("laptop"));
        map.put("macbook", setOf("laptop"));

        for (String w : new String[] {"watch", "watches", "smartwatch", "smartwatches",
                "wearables"}) {
            map.put(w, setOf("wearable"));
        }

        map.put("cameras", setOf("camera"));
        map.put("dslr", setOf("camera"));
        map.put("mirrorless", setOf("camera"));

        map.put("display", setOf("monitor"));
        map.put("displays", setOf("monitor"));

        map.put("ssd", setOf("storage"));
        map.put("hdd", setOf("storage"));

        map.put("ipad", setOf("tablet"));

        for (String w : new String[] {"keyboard", "keyboards", "mouse", "mice",
                "accessory"}) {
            map.put(w, setOf("accessories"));
        }

        for (String w : new String[] {"router", "routers", "mesh", "wifi"}) {
            map.put(w, setOf("networking"));
        }

        map.put("printers", setOf("printer"));

        return map;
    }

    private static Set<String> setOf(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    // ================================================================
    // SPELLING RECOVERY (LEVENSHTEIN, APPLIED BEFORE RETRIEVAL)
    // ================================================================

    private String[] correctSpelling(String[] terms) {

        String[] corrected = new String[terms.length];

        for (int i = 0; i < terms.length; i++) {
            corrected[i] = correctTerm(terms[i]);
        }

        return corrected;
    }

    private String correctTerm(String term) {

        if (term.length() < 3) {
            // Too short to safely correct ("tv", "pc", ...).
            return term;
        }

        if (containsDigit(term)) {
            // Model numbers / capacities ("s24", "128gb", "5g") are not
            // typos of other model numbers, so they are never rewritten.
            return term;
        }

        if (wordFrequency.containsKey(term)) {
            // Already a word that exists in the corpus.
            return term;
        }

        String best = null;

        int bestDistance = Integer.MAX_VALUE;
        int bestFrequency = 0;

        for (Map.Entry<String, Integer> entry : wordFrequency.entrySet()) {

            String word = entry.getKey();

            // Quick prune: word lengths too far apart can't be close matches.
            if (Math.abs(word.length() - term.length()) > 2) {
                continue;
            }

            int distance = Levenshtein.distance(term, word);

            // Closest word wins; ties go to the word found in more products,
            // then alphabetically, so the suggestion never depends on hash
            // order ("ultr" -> "ultra", not the rarer "ult").
            if (distance < bestDistance
                    || (distance == bestDistance
                        && (entry.getValue() > bestFrequency
                            || (entry.getValue() == bestFrequency
                                && word.compareTo(best) < 0)))) {

                bestDistance = distance;
                bestFrequency = entry.getValue();
                best = word;
            }
        }

        int threshold = term.length() <= 4 ? 1 : 2;

        if (best != null && bestDistance <= threshold) {
            return best;
        }

        // No close-enough match in the vocabulary -> keep the original
        // term as-is (it may be a legitimate word just not in the corpus).
        return term;
    }

    private static boolean containsDigit(String text) {

        for (int i = 0; i < text.length(); i++) {

            if (Character.isDigit(text.charAt(i))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Builds word -> number of products containing it, from product names,
     * brands, categories and searchable text (without the stored
     * misspellings), plus the category synonyms. Words shorter than 3
     * characters or containing digits are not spelling targets.
     */
    private Map<String, Integer> buildWordFrequency(ProductIndex[] products) {

        Map<String, Integer> frequency = new HashMap<>();

        for (ProductIndex p : products) {

            Set<String> words = new HashSet<>();

            words.addAll(p.nameTokens);
            words.addAll(p.brandTokens);
            words.addAll(tokens(p.categoryNormalized));
            words.addAll(p.descriptionTokens);

            for (String word : words) {

                if (word.length() >= 3 && !containsDigit(word)) {
                    frequency.merge(word, 1, Integer::sum);
                }
            }
        }

        for (Map.Entry<String, Set<String>> entry : CATEGORY_SYNONYMS.entrySet()) {

            frequency.merge(entry.getKey(), 1, Integer::sum);

            for (String synonym : entry.getValue()) {
                frequency.merge(synonym, 1, Integer::sum);
            }
        }

        return frequency;
    }

    // ================================================================
    // SEARCH RESULT DISPLAY
    // ================================================================

    private void displayResults(
            String originalQuery,
            String normalizedQuery,
            String correctedQuery,
            String algorithm,
            List<Ranked> results) {

        System.out.println();

        System.out.println("========================================");
        System.out.println("           SEARCH RESULTS");
        System.out.println("========================================");

        System.out.println("Query     : " + originalQuery);

        if (!correctedQuery.equals(normalizedQuery)) {
            System.out.println("Did you mean : " + correctedQuery + "?");

            // show which words were corrected and how far apart they were
            String[] wordsBefore = normalizedQuery.split("\\s+");
            String[] wordsAfter = correctedQuery.split("\\s+");

            for (int w = 0; w < wordsBefore.length && w < wordsAfter.length; w++) {

                if (!wordsBefore[w].equals(wordsAfter[w])) {
                    System.out.println("Spelling     : " + wordsBefore[w] + " -> "
                            + wordsAfter[w] + "  (Levenshtein edit distance = "
                            + Levenshtein.distance(wordsBefore[w], wordsAfter[w]) + ")");
                }
            }
        }

        if (!correctedQuery.equals(normalizedQuery)) {
            System.out.println("Algorithm    : Levenshtein (spelling) -> "
                    + algorithm + " (matching)");
        } else {
            System.out.println("Algorithm    : " + algorithm + " (matching)");
        }

        System.out.println();

        int count = Math.min(MAX_RESULTS, results.size());

        for (int i = 0; i < count; i++) {

            ProductDocument product = results.get(i).product;

            System.out.println((i + 1) + ". " + product.getProductName());

            System.out.println("   Score    : " + results.get(i).score);

            System.out.println("   Price    : " + product.getPrice());

            System.out.println("   Rating   : " + product.getRating());

            System.out.println("   Brand    : " + product.getBrand());

            System.out.println("   Category : " + getRecommendationCategory(product));

            System.out.println("   Info     : " + shortInfo(product));

            System.out.println();
        }

        if (results.size() > count) {
            System.out.println("Showing top " + count + " of "
                    + results.size() + " matching products.");
        }

        System.out.println("========================================");
        System.out.println();
    }

    // ================================================================
    // SEND TOP 30 CANDIDATES TO MAX FLOW
    // ================================================================

    private void runRecommendations(String query, List<Ranked> results) {

        int count = Math.min(RECOMMENDATION_POOL, results.size());

        String[] products = new String[count];

        String[] categories = new String[count];

        String[] brands = new String[count];

        int[] scores = new int[count];

        for (int i = 0; i < count; i++) {

            ProductDocument product = results.get(i).product;

            products[i] = product.getProductName();

            categories[i] = getRecommendationCategory(product);

            brands[i] = product.getBrand();

            scores[i] = results.get(i).score;
        }

        MaxFlow.runRecommendation(query, products, categories, brands, scores);
    }

    // ================================================================
    // CATEGORY CLASSIFICATION
    // ================================================================

    /**
     * The product's own "Category:" field is the source of truth. The
     * keyword rules below are only a fallback for records whose category
     * is missing, "Other" or a legacy/broad label - they must never
     * override a valid category (that used to turn the "ASUS ZenBook 14
     * OLED" laptop into a monitor because its name contains "OLED").
     */
    private String getRecommendationCategory(ProductDocument product) {

        String field = product.getCategory().trim();

        for (String canonical : CANONICAL_CATEGORIES) {

            if (canonical.equalsIgnoreCase(field)) {
                return canonical;
            }
        }

        String name = normalize(product.getProductName());

        if (containsAny(name, "earbuds", "buds", "headphone", "headset", "speaker")) {
            return "Audio";
        }

        if (containsAny(name, "watch", "smartwatch", "galaxy fit", "fitness band", "fitness tracker")) {
            return "Wearable";
        }

        if (containsAny(name, "ssd", "hdd", "hard drive", "flash drive", "portable drive")) {
            return "Storage";
        }

        if (containsAny(name, "laptop", "notebook", "macbook", "chromebook", "ultrabook")) {
            return "Laptop";
        }

        if (containsAny(name, "monitor", "display", "oled")) {
            return "Monitor";
        }

        if (containsAny(name, "camera", "eos", "alpha", "mirrorless", "dslr")) {
            return "Camera";
        }

        if (containsAny(name, "tablet", "ipad", "galaxy tab")) {
            return "Tablet";
        }

        if (containsAny(name, "phone", "smartphone", "galaxy s", "galaxy a", "galaxy z", "iphone", "pixel")) {
            return "Smartphone";
        }

        String normalizedCategory = normalize(field);

        if (containsAny(normalizedCategory, "audio", "earbuds", "headphone")) {
            return "Audio";
        }

        if (containsAny(normalizedCategory, "wearable", "watch")) {
            return "Wearable";
        }

        if (containsAny(normalizedCategory, "storage")) {
            return "Storage";
        }

        if (containsAny(normalizedCategory, "monitor", "display")) {
            return "Monitor";
        }

        if (containsAny(normalizedCategory, "laptop", "computer")) {
            return "Laptop";
        }

        if (containsAny(normalizedCategory, "camera")) {
            return "Camera";
        }

        if (containsAny(normalizedCategory, "tablet")) {
            return "Tablet";
        }

        if (containsAny(normalizedCategory, "mobile", "phone", "smartphone")) {
            return "Smartphone";
        }

        // product.getCategory() already defaults to "Other" itself
        // when no Category field is found.
        return field.isEmpty() ? "Other" : field;
    }

    // ================================================================
    // AUTOMATIC ALGORITHM SELECTION (KMP vs Rabin-Karp)
    // ================================================================

    private String chooseAlgorithm(String query) {

        /*
         * KMP is preferred for short queries because it guarantees
         * linear-time pattern matching.
         *
         * Rabin-Karp is used for longer multi-word queries to
         * demonstrate rolling-hash based pattern matching.
         * Both return identical counts (checked by TestRunner); the
         * choice only changes which algorithm is exercised.
         */

        if (query.length() <= 8 || query.split("\\s+").length <= 2) {
            return "KMP";
        }

        return "Rabin-Karp";
    }

    // ================================================================
    // PRODUCT-LEVEL LEVENSHTEIN FALLBACK (when nothing scores > 0)
    // ================================================================

    private void displayLevenshtein(String query) {

        ArrayList<LevenshteinSearch.Result> approximate =
                LevenshteinSearch.search(corpus, query);

        System.out.println();
        System.out.println("========================================");
        System.out.println("      APPROXIMATE SEARCH RESULTS");
        System.out.println("========================================");

        if (approximate.isEmpty()) {
            System.out.println("No matching products found.");
            return;
        }

        int count = Math.min(MAX_RESULTS, approximate.size());

        for (int i = 0; i < count; i++) {

            LevenshteinSearch.Result result = approximate.get(i);

            System.out.println((i + 1) + ". " + result.productName);
            System.out.println("   Matched : " + result.matchedTerm);
            System.out.println("   Distance: " + result.distance);
            System.out.println("   Score   : " + String.format("%.2f", result.matchScore));
            System.out.println();
        }

        System.out.println("Search recovered using Levenshtein Edit Distance.");
    }

    // ================================================================
    // FIELD EXTRACTION
    // ================================================================

    private static String extractField(String content, String field) {

        if (content == null) {
            return "";
        }

        String[] lines = content.split("\\R");

        String target = field.toLowerCase();

        for (String line : lines) {

            String trimmed = line.trim();

            // Strip common bullet / markdown prefixes so "- Price:" or
            // "**Price:**" still resolve to the "price" key.
            trimmed = trimmed.replaceFirst("^[\\-\\*\\u2022\\s]+", "");
            trimmed = trimmed.replaceAll("\\*", "");

            int colon = trimmed.indexOf(':');

            if (colon >= 0) {

                String key = trimmed.substring(0, colon).trim().toLowerCase();

                if (key.equals(target)) {
                    return trimmed.substring(colon + 1).trim();
                }

                continue;
            }

            int dash = trimmed.indexOf('-');

            if (dash > 0) {

                String key = trimmed.substring(0, dash).trim().toLowerCase();

                if (key.equals(target)) {
                    return trimmed.substring(dash + 1).trim();
                }
            }
        }

        return "";
    }

    private static final Pattern RATING_NUMBER = Pattern.compile("[0-9]+(\\.[0-9]+)?");

    private static double parseRating(String ratingText) {

        if (ratingText == null || ratingText.isEmpty()) {
            return 0.0;
        }

        Matcher matcher = RATING_NUMBER.matcher(ratingText);

        if (matcher.find()) {

            try {
                return Double.parseDouble(matcher.group());
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }

        return 0.0;
    }

    // ================================================================
    // SHORT PRODUCT DESCRIPTION
    // ================================================================

    /**
     * One-line summary for the result list: the product's own Overview
     * sentence. (The old code printed the first "Description:" line found,
     * which is the first KEY FEATURE's text - often generic filler such as
     * "Tuned for responsive operation..." - and not a product description.)
     */
    private String shortInfo(ProductDocument product) {

        String info = product.getOverview();

        String content = product.getContent();

        if (info.isEmpty()) {
            info = extractField(content, "Description");
        }

        if (info.isEmpty()) {
            info = extractField(content, "Info");
        }

        if (info.isEmpty()) {
            info = extractField(content, "Product Description");
        }

        if (info.length() > 120) {
            info = info.substring(0, 120) + "...";
        }

        return info;
    }

    // ================================================================
    // NORMALIZATION
    // ================================================================

    private static String normalize(String text) {

        if (text == null) {
            return "";
        }

        return text
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    // ================================================================
    // KEYWORD TEST
    // ================================================================

    private static boolean containsAny(String text, String... values) {

        for (String value : values) {

            if (text.contains(value)) {
                return true;
            }
        }

        return false;
    }

    // ================================================================
    // SORTING HELPER
    // ================================================================

    private static final class ScoredIndex {

        final ProductIndex entry;
        final int score;

        ScoredIndex(ProductIndex entry, int score) {
            this.entry = entry;
            this.score = score;
        }
    }
}