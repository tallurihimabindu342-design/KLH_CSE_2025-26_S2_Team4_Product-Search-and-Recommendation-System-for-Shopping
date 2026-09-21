import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Scanner;

public class SearchEngine {

    private final ProductDocument[] corpus;

    /*
     * Search modes:
     *
     * 0 = Automatic
     * 1 = KMP
     * 2 = Rabin-Karp
     */
    private int searchMode = 0;

    private static final int MAX_RESULTS = 10;

    public SearchEngine(ProductDocument[] corpus) {
        this.corpus = corpus;
    }

    // ============================================================
    // MAIN SEARCH
    // ============================================================

    public String search(
            String query,
            Scanner scanner) {

        if (query == null ||
                query.trim().isEmpty()) {

            System.out.println();
            System.out.println(
                    "Please enter a valid search query."
            );

            return "";
        }

        query = query.trim();

        String normalizedQuery =
                normalize(query);

        if (normalizedQuery.isEmpty()) {

            System.out.println();
            System.out.println(
                    "Please enter a valid search query."
            );

            return "";
        }

        String algorithm;

        if (searchMode == 1) {
            algorithm = "KMP";
        } else if (searchMode == 2) {
            algorithm = "Rabin-Karp";
        } else {
            algorithm = chooseAutomaticAlgorithm(normalizedQuery);
        }

        // --------------------------------------------------------
        // AUTOMATIC RECOMMENDATION ROUTING
        // --------------------------------------------------------
        // Max Flow is used for recommendation requests. KMP and
        // Rabin-Karp remain the automatic choices for exact pattern
        // searching, while Levenshtein is used as the fuzzy-search
        // fallback when an exact search finds nothing.
        if (algorithm.equals("Max Flow")) {
            MaxFlow.runDemo(normalizedQuery);
            return "";
        }

        String[] queryTerms =
                normalizedQuery.split("\\s+");

        ArrayList<SearchItem> results =
                new ArrayList<>();

        // --------------------------------------------------------
        // SEARCH EVERY PRODUCT
        // --------------------------------------------------------

        for (ProductDocument product : corpus) {

            if (product == null) {
                continue;
            }

            String content =
                    product.getContent();

            if (content == null ||
                    content.trim().isEmpty()) {

                continue;
            }

            String productName =
                    normalize(
                            product.getProductName()
                    );

            String searchableText =
                    getExactSearchText(content);

            // ----------------------------------------------------
            // EXACT PHRASE SEARCH
            // ----------------------------------------------------

            int occurrences;

            if (algorithm.equals("KMP")) {

                occurrences =
                        countOccurrencesKMP(
                                searchableText,
                                normalizedQuery
                        );

            } else {

                occurrences =
                        countOccurrencesRabinKarp(
                                searchableText,
                                normalizedQuery
                        );
            }

            // ----------------------------------------------------
            // QUERY TERM MATCHING
            // ----------------------------------------------------

            int nameMatchedTerms =
                    countMatchedTerms(
                            productName,
                            queryTerms
                    );

            int documentMatchedTerms =
                    countMatchedTerms(
                            searchableText,
                            queryTerms
                    );

            int matchedTerms =
                    Math.max(
                            nameMatchedTerms,
                            documentMatchedTerms
                    );

            // ----------------------------------------------------
            // NAME MATCHING
            // ----------------------------------------------------

            boolean exactNameMatch =
                    productName.equals(
                            normalizedQuery
                    );

            boolean phraseInName =
                    containsPhrase(
                            productName,
                            normalizedQuery
                    );

            // ----------------------------------------------------
            // RELEVANCE FILTER
            // ----------------------------------------------------

            boolean relevant;

            if (queryTerms.length == 1) {

                relevant =
                        matchedTerms >= 1;

            } else {

                relevant =
                        exactNameMatch
                        || phraseInName
                        || matchedTerms >= 2;
            }

            if (!relevant) {
                continue;
            }

            // ----------------------------------------------------
            // SCORE
            // ----------------------------------------------------

            int score =
                    calculateScore(
                            normalizedQuery,
                            occurrences,
                            nameMatchedTerms,
                            documentMatchedTerms,
                            exactNameMatch,
                            phraseInName
                    );

            results.add(
                    new SearchItem(
                            product,
                            score,
                            occurrences,
                            matchedTerms
                    )
            );
        }

        // --------------------------------------------------------
        // LEVENSHTEIN APPROXIMATE SEARCH FALLBACK
        // --------------------------------------------------------
        // If exact KMP / Rabin-Karp search finds no proper
        // result, try approximate matching using Edit Distance.
        if (results.isEmpty()) {

            ArrayList<LevenshteinSearch.Result> approximateResults =
                    LevenshteinSearch.search(corpus, query);

            if (!approximateResults.isEmpty()) {
                return displayLevenshteinResults(
                        query,
                        approximateResults,
                        scanner
                );
            }
        }

        // --------------------------------------------------------
        // SORT RESULTS
        // --------------------------------------------------------

        Collections.sort(
                results,
                new Comparator<SearchItem>() {

                    @Override
                    public int compare(
                            SearchItem a,
                            SearchItem b) {

                        int scoreCompare =
                                Integer.compare(
                                        b.score,
                                        a.score
                                );

                        if (scoreCompare != 0) {
                            return scoreCompare;
                        }

                        return a.product
                                .getProductName()
                                .compareToIgnoreCase(
                                        b.product
                                                .getProductName()
                                );
                    }
                }
        );

        return displayResults(
                query,
                algorithm,
                results,
                scanner
        );
    }

    // ============================================================
    // SEARCH RESULTS DISPLAY
    // ============================================================

    private String displayResults(
            String query,
            String algorithm,
            ArrayList<SearchItem> results,
            Scanner scanner) {

        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println(
                "           SEARCH RESULTS"
        );

        System.out.println(
                "========================================"
        );

        System.out.println(
                "Query     : " + query
        );

        System.out.println(
                "Algorithm : " + algorithm
        );

        System.out.println();

        if (results.isEmpty()) {

            System.out.println(
                    "No matching products found."
            );

            System.out.println(
                    "========================================"
            );

            return "";
        }

        int displayCount =
                Math.min(
                        results.size(),
                        MAX_RESULTS
                );

        for (int i = 0;
             i < displayCount;
             i++) {

            SearchItem item =
                    results.get(i);

            ProductDocument product =
                    item.product;

            String content =
                    product.getContent();

            String brand =
                    extractProductField(
                            content,
                            "Brand"
                    );

            String category =
                    extractProductField(
                            content,
                            "Category"
                    );

            String price =
                    extractPrice(content);

            String rating =
                    extractRating(content);

            String shortInfo =
                    getShortProductInfo(product);

            System.out.println(
                    (i + 1)
                    + ". "
                    + product.getProductName()
            );

            if (!price.isEmpty()) {

                System.out.println(
                        "   Price    : "
                        + cleanDisplay(price)
                );
            }

            if (!rating.isEmpty()) {

                System.out.println(
                        "   Rating   : "
                        + cleanDisplay(rating)
                );
            }

            if (!brand.isEmpty()) {

                System.out.println(
                        "   Brand    : "
                        + cleanDisplay(brand)
                );
            }

            if (!category.isEmpty()) {

                System.out.println(
                        "   Category : "
                        + cleanDisplay(category)
                );
            }

            if (!shortInfo.isEmpty()) {

                System.out.println(
                        "   Info     : "
                        + shortInfo
                );
            }

            System.out.println();
        }

        System.out.println(
                "========================================"
        );

        /*
         * Recommendation stage:
         * After a normal product search, ask whether the user
         * wants recommendations. This routes the actual top search
         * results dynamically into MaxFlow.runRecommendation().
         */
        System.out.println();
        System.out.print(
                "Would you like product recommendations? (Y/N): "
        );

        String recommendationChoice =
                scanner.nextLine().trim();

        if (recommendationChoice.equalsIgnoreCase("Y")) {
            System.out.println();
            System.out.println(
                    "========================================"
            );
            System.out.println(
                    "       PRODUCT RECOMMENDATIONS"
            );
            System.out.println(
                    "========================================"
            );
            System.out.println(
                    "Algorithm : Edmonds-Karp Max Flow"
            );
            System.out.println();

            int recommendationCount =
                    Math.min(displayCount, 6);

            String[] recommendationProducts =
                    new String[recommendationCount];

            String[] recommendationCategories =
                    new String[recommendationCount];

            for (int i = 0; i < recommendationCount; i++) {
                SearchItem recommendationItem =
                        results.get(i);

                recommendationProducts[i] =
                        recommendationItem.product.getProductName();

                recommendationCategories[i] =
                        extractProductField(
                                recommendationItem.product.getContent(),
                                "Category"
                        );

                if (recommendationCategories[i].isEmpty()) {
                    recommendationCategories[i] =
                            "Other Products";
                }
            }

            MaxFlow.runRecommendation(
                    query,
                    recommendationProducts,
                    recommendationCategories
            );
        }

        System.out.println();
        System.out.print(
                "Enter product number for details "
                + "or press ENTER for another search: "
        );

        String choice =
                scanner.nextLine().trim();

        if (choice.isEmpty()) {
            return "";
        }

        try {

            int selected =
                    Integer.parseInt(choice);

            if (selected >= 1 &&
                    selected <= displayCount) {

                SearchItem selectedItem =
                        results.get(
                                selected - 1
                        );

                showProductDetails(
                        selectedItem.product
                );

                return "";
            }

            System.out.println();
            System.out.println("Invalid product number.");
            return "";

        } catch (NumberFormatException e) {

            /*
             * If the user typed a normal search query instead of
             * a product number, pass it back to Main so it becomes
             * the next search. This also allows hidden commands
             * such as /test to work immediately after a result list.
             */
            return choice;
        }
    }

    // ============================================================
    // EXACT SEARCH TEXT CLEANUP
    // ============================================================

    /**
     * Returns text used by exact KMP / Rabin-Karp searching.
     * Normal product information and customer reviews remain
     * searchable, while typo/reference sections are removed so
     * that misspellings can be handled by Levenshtein.
     */
    private String getExactSearchText(String content) {

        if (content == null) {
            return "";
        }

        String text = content;

        // Remove Customer Search Queries, including typo queries.
        text = removeSection(
                text,
                "Customer Search Queries:",
                "Customer Questions & Answers:"
        );

        // Remove Common Misspellings from the Search Index.
        text = removeSection(
                text,
                "Common Misspellings:",
                "Search Phrases:"
        );

        return normalize(text);
    }

    /**
     * Removes text between two case-insensitive section headings.
     */
    private String removeSection(
            String text,
            String startHeading,
            String endHeading) {

        if (text == null) {
            return "";
        }

        String lowerText = text.toLowerCase();
        String start = startHeading.toLowerCase();
        String end = endHeading.toLowerCase();

        int startIndex = lowerText.indexOf(start);

        if (startIndex == -1) {
            return text;
        }

        int endIndex = lowerText.indexOf(
                end,
                startIndex + start.length()
        );

        if (endIndex == -1) {
            return text.substring(0, startIndex);
        }

        return text.substring(0, startIndex)
                + text.substring(endIndex);
    }

    // ============================================================
    // LEVENSHTEIN SEARCH RESULTS
    // ============================================================

    /**
     * Displays approximate-search results and supports the same
     * product-selection flow as normal KMP / Rabin-Karp search.
     */
    private String displayLevenshteinResults(
            String query,
            ArrayList<LevenshteinSearch.Result> results,
            Scanner scanner) {

        System.out.println();
        System.out.println("========================================");
        System.out.println("       APPROXIMATE SEARCH RESULTS");
        System.out.println("========================================");
        System.out.println("Query     : " + query);
        System.out.println("Algorithm : Levenshtein Edit Distance");
        System.out.println();

        int displayCount = Math.min(results.size(), MAX_RESULTS);

        for (int i = 0; i < displayCount; i++) {

            LevenshteinSearch.Result result = results.get(i);
            ProductDocument product = findProductByFileName(result.fileName);

            System.out.println((i + 1) + ". " + result.productName);

            if (product != null) {
                String content = product.getContent();

                String brand = extractProductField(content, "Brand");
                String category = extractProductField(content, "Category");
                String price = extractPrice(content);
                String rating = extractRating(content);
                String shortInfo = getShortProductInfo(product);

                if (!price.isEmpty()) {
                    System.out.println("   Price    : " + cleanDisplay(price));
                }

                if (!rating.isEmpty()) {
                    System.out.println("   Rating   : " + cleanDisplay(rating));
                }

                if (!brand.isEmpty()) {
                    System.out.println("   Brand    : " + cleanDisplay(brand));
                }

                if (!category.isEmpty()) {
                    System.out.println("   Category : " + cleanDisplay(category));
                }

                if (!shortInfo.isEmpty()) {
                    System.out.println("   Info     : " + shortInfo);
                }
            }

            System.out.println("   Matched Text       : " + result.matchedTerm);
            System.out.println("   Edit Distance      : " + result.distance);
            System.out.println(
                    "   Match Score        : "
                            + String.format("%.0f", result.matchScore)
                            + "%"
            );
            System.out.println();
        }

        System.out.println("========================================");
        System.out.println();
        System.out.print(
                "Enter product number for details "
                        + "or press ENTER for another search: "
        );

        String choice = scanner.nextLine().trim();

        if (choice.isEmpty()) {
            return "";
        }

        try {
            int selected = Integer.parseInt(choice);

            if (selected >= 1 && selected <= displayCount) {
                LevenshteinSearch.Result selectedResult =
                        results.get(selected - 1);

                ProductDocument selectedProduct =
                        findProductByFileName(selectedResult.fileName);

                if (selectedProduct != null) {
                    showProductDetails(selectedProduct);
                }

                return "";
            }

            System.out.println();
            System.out.println("Invalid product number.");
            return "";

        } catch (NumberFormatException e) {
            // Non-number input becomes the next search query.
            return choice;
        }
    }

    /**
     * Finds a corpus document from the file name stored by
     * LevenshteinSearch.Result.
     */
    private ProductDocument findProductByFileName(String fileName) {

        if (fileName == null) {
            return null;
        }

        for (ProductDocument product : corpus) {
            if (product != null && fileName.equals(product.getFileName())) {
                return product;
            }
        }

        return null;
    }

    // ============================================================
    // SHORT PRODUCT INFORMATION
    // ============================================================

    private String getShortProductInfo(
            ProductDocument product) {

        String content =
                product.getContent();

        String overview =
                extractOverview(content);

        if (overview.isEmpty()) {

            overview =
                    extractDescription(content);
        }

        if (overview.isEmpty()) {

            return "Product information available.";
        }

        overview =
                cleanDisplay(overview)
                        .replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();

        if (overview
                .toLowerCase()
                .startsWith("overview:")) {

            overview =
                    overview.substring(
                            "overview:".length()
                    ).trim();
        }

        /*
         * Keep search-result information concise.
         */
        final int MAX_LENGTH = 110;

        if (overview.length() <= MAX_LENGTH) {
            return overview;
        }

        String shortened =
                overview.substring(
                        0,
                        MAX_LENGTH
                );

        int lastSpace =
                shortened.lastIndexOf(" ");

        if (lastSpace > 50) {

            shortened =
                    shortened.substring(
                            0,
                            lastSpace
                    );
        }

        return shortened.trim() + "...";
    }

    // ============================================================
    // PRODUCT DETAILS
    // ============================================================

    private void showProductDetails(
            ProductDocument product) {

        String content =
                product.getContent();

        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println(
                "          PRODUCT DETAILS"
        );

        System.out.println(
                "========================================"
        );

        System.out.println();

        System.out.println(
                product.getProductName()
        );

        System.out.println();

        String price =
                extractPrice(content);

        String rating =
                extractRating(content);

        String brand =
                extractProductField(
                        content,
                        "Brand"
                );

        String category =
                extractProductField(
                        content,
                        "Category"
                );

        if (!price.isEmpty()) {

            System.out.println(
                    "Price       : "
                    + cleanDisplay(price)
            );
        }

        if (!rating.isEmpty()) {

            System.out.println(
                    "Rating      : "
                    + cleanDisplay(rating)
            );
        }

        if (!brand.isEmpty()) {

            System.out.println(
                    "Brand       : "
                    + cleanDisplay(brand)
            );
        }

        if (!category.isEmpty()) {

            System.out.println(
                    "Category    : "
                    + cleanDisplay(category)
            );
        }

        // --------------------------------------------------------
        // OVERVIEW
        // --------------------------------------------------------

        String overview =
                extractOverview(content);

        printSection(
                "Overview:",
                overview,
                false
        );

        // --------------------------------------------------------
        // KEY FEATURES
        // --------------------------------------------------------

        String features =
                extractFeatures(content);

        printSection(
                "Key Features:",
                features,
                true
        );

        // --------------------------------------------------------
        // BEST USE CASES
        // --------------------------------------------------------

        String useCases =
                extractBestUseCases(content);

        printSection(
                "Best Use Cases:",
                useCases,
                true
        );

        // --------------------------------------------------------
        // SPECIFICATIONS
        // --------------------------------------------------------

        String specifications =
                extractSpecifications(content);

        printSection(
                "Specifications:",
                specifications,
                true
        );

        // --------------------------------------------------------
        // QUICK HIGHLIGHTS
        // --------------------------------------------------------

        String highlights =
                extractQuickHighlights(content);

        printSection(
                "Quick Highlights:",
                highlights,
                true
        );

        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println();
    }

    // ============================================================
    // GENERIC SECTION PRINTER
    // ============================================================

    private void printSection(
            String title,
            String content,
            boolean preserveLines) {

        if (content == null ||
                content.trim().isEmpty()) {

            return;
        }

        System.out.println();

        System.out.println(title);

        System.out.println();

        String cleaned =
                cleanDisplay(content);

        if (preserveLines) {

            System.out.println(
                    cleaned
            );

        } else {

            System.out.println(
                    cleaned
                            .replaceAll(
                                    "\\s+",
                                    " "
                            )
                            .trim()
            );
        }
    }

    // ============================================================
    // OVERVIEW EXTRACTION
    // ============================================================

    private String extractOverview(
            String content) {

        if (content == null || content.trim().isEmpty()) {
            return "";
        }

        String[] lines = content.split("\\R");
        StringBuilder result = new StringBuilder();
        boolean readingOverview = false;

        for (String line : lines) {

            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                continue;
            }

            String lower = trimmed.toLowerCase();

            // Handles: Overview: The product description...
            if (lower.startsWith("overview:")) {
                readingOverview = true;
                String overviewText = trimmed.substring("overview:".length()).trim();
                if (!overviewText.isEmpty()) {
                    result.append(overviewText).append(" ");
                }
                continue;
            }

            // Handles Overview as a separate heading.
            if (lower.equals("overview")
                    || lower.equals("product overview")
                    || lower.equals("product overview:")) {
                readingOverview = true;
                continue;
            }

            if (!readingOverview) {
                continue;
            }

            // Stop at the next major section.
            if (lower.equals("key features:")
                    || lower.equals("key features")
                    || lower.equals("features:")
                    || lower.equals("features")
                    || lower.equals("best use cases:")
                    || lower.equals("best use cases (activities):")
                    || lower.equals("specifications:")
                    || lower.equals("technical specifications:")
                    || lower.equals("quick highlights:")
                    || lower.equals("customer search queries:")
                    || lower.equals("customer questions & answers:")
                    || lower.equals("customer reviews:")
                    || lower.equals("searchable attributes:")
                    || lower.equals("search index:")
                    || lower.startsWith("block ")) {
                break;
            }

            result.append(trimmed).append(" ");
        }

        String overview = result.toString()
                .replaceAll("\\s+", " ")
                .trim();

        // Remove any accidental duplicated Overview: prefix.
        while (overview.toLowerCase().startsWith("overview:")) {
            overview = overview.substring("overview:".length()).trim();
        }

        // Keep the expanded overview concise.
        final int MAX_OVERVIEW_LENGTH = 350;

        if (overview.length() > MAX_OVERVIEW_LENGTH) {
            String shortened = overview.substring(0, MAX_OVERVIEW_LENGTH);
            int lastSpace = shortened.lastIndexOf(" ");
            if (lastSpace > 200) {
                shortened = shortened.substring(0, lastSpace);
            }
            overview = shortened.trim() + "...";
        }

        return overview;
    }

    // ============================================================
    // DESCRIPTION EXTRACTION
    // ============================================================

    private String extractDescription(
            String content) {

        return extractSection(
                content,
                new String[] {
                        "description:",
                        "product description:"
                },
                new String[] {
                        "key features:",
                        "features:",
                        "best use cases (activities):",
                        "best use cases:",
                        "specifications:",
                        "technical specifications:",
                        "quick highlights:",
                        "customer reviews:",
                        "customer search queries:",
                        "customer questions & answers:",
                        "search index:",
                        "searchable attributes:"
                },
                false
        );
    }

    // ============================================================
    // KEY FEATURES EXTRACTION
    // ============================================================

    private String extractFeatures(
            String content) {

        return extractSection(
                content,
                new String[] {
                        "key features:",
                        "key features",
                        "features:",
                        "features"
                },
                new String[] {
                        "best use cases (activities):",
                        "best use cases:",
                        "specifications:",
                        "technical specifications:",
                        "quick highlights:",
                        "customer search queries:",
                        "customer questions & answers:",
                        "customer reviews:",
                        "searchable attributes:",
                        "search index:"
                },
                true
        );
    }

    // ============================================================
    // BEST USE CASES EXTRACTION
    // ============================================================

    private String extractBestUseCases(
            String content) {

        return extractSection(
                content,
                new String[] {
                        "best use cases (activities):",
                        "best use cases:"
                },
                new String[] {
                        "specifications:",
                        "technical specifications:",
                        "quick highlights:",
                        "key features:",
                        "customer search queries:",
                        "customer questions & answers:",
                        "customer reviews:",
                        "searchable attributes:",
                        "search index:"
                },
                true
        );
    }

    // ============================================================
    // SPECIFICATIONS EXTRACTION
    // ============================================================

    private String extractSpecifications(
            String content) {

        return extractSection(
                content,
                new String[] {
                        "specifications:",
                        "technical specifications:",
                        "specification:"
                },
                new String[] {
                        "quick highlights:",
                        "key features:",
                        "best use cases (activities):",
                        "best use cases:",
                        "customer search queries:",
                        "customer questions & answers:",
                        "customer reviews:",
                        "searchable attributes:",
                        "search index:"
                },
                true
        );
    }

    // ============================================================
    // QUICK HIGHLIGHTS EXTRACTION
    // ============================================================

    private String extractQuickHighlights(
            String content) {

        return extractSection(
                content,
                new String[] {
                        "quick highlights:"
                },
                new String[] {
                        "key features:",
                        "best use cases (activities):",
                        "best use cases:",
                        "specifications:",
                        "technical specifications:",
                        "customer search queries:",
                        "customer questions & answers:",
                        "customer reviews:",
                        "searchable attributes:",
                        "search index:"
                },
                true
        );
    }

    // ============================================================
    // GENERIC SECTION EXTRACTION
    // ============================================================

    private String extractSection(
            String content,
            String[] startHeadings,
            String[] endHeadings,
            boolean preserveLines) {

        if (content == null) {
            return "";
        }

        String[] lines =
                content.split("\\R");

        boolean reading = false;

        StringBuilder result =
                new StringBuilder();

        for (String line : lines) {

            String trimmed =
                    line.trim();

            if (trimmed.isEmpty()) {

                if (reading && preserveLines) {
                    result.append("\n");
                }

                continue;
            }

            String lower =
                    trimmed.toLowerCase();

            // ----------------------------------------------------
            // START SECTION
            // ----------------------------------------------------

            if (!reading) {

                if (matchesHeading(
                        lower,
                        startHeadings
                )) {

                    reading = true;
                }

                continue;
            }

            // ----------------------------------------------------
            // STOP AT MAJOR BLOCK
            // ----------------------------------------------------

            if (lower.startsWith("block ")) {
                break;
            }

            // ----------------------------------------------------
            // STOP AT NEXT SECTION
            // ----------------------------------------------------

            if (matchesHeading(
                    lower,
                    endHeadings
            )) {

                break;
            }

            // ----------------------------------------------------
            // REMOVE HEADING IF CAPTURED
            // ----------------------------------------------------

            if (matchesHeading(
                    lower,
                    startHeadings
            )) {

                continue;
            }

            // ----------------------------------------------------
            // ADD CONTENT
            // ----------------------------------------------------

            result.append(trimmed);

            if (preserveLines) {
                result.append("\n");
            } else {
                result.append(" ");
            }
        }

        return result
                .toString()
                .trim();
    }

    // ============================================================
    // HEADING MATCH
    // ============================================================

    private boolean matchesHeading(
            String line,
            String[] headings) {

        if (line == null) {
            return false;
        }

        String normalized =
                line.trim()
                        .toLowerCase();

        for (String heading : headings) {

            if (normalized.equals(
                    heading.toLowerCase()
            )) {

                return true;
            }
        }

        return false;
    }

    // ============================================================
    // PRODUCT FIELD
    // ============================================================

    private String extractProductField(
            String content,
            String fieldName) {

        if (content == null) {
            return "";
        }

        String[] lines =
                content.split("\\R");

        String target =
                fieldName
                        .trim()
                        .toLowerCase();

        for (String line : lines) {

            String trimmed =
                    line.trim();

            String lower =
                    trimmed.toLowerCase();

            // Example:
            // Brand: Samsung

            if (lower.startsWith(
                    target + ":"
            )) {

                return trimmed
                        .substring(
                                fieldName.length() + 1
                        )
                        .trim();
            }

            // Example:
            // Brand : Samsung

            if (lower.startsWith(
                    target + " :"
            )) {

                return trimmed
                        .substring(
                                fieldName.length()
                        )
                        .replaceFirst(
                                "^\\s*:\\s*",
                                ""
                        )
                        .trim();
            }
        }

        return "";
    }

    // ============================================================
    // PRICE
    // ============================================================

    private String extractPrice(
            String content) {

        String price =
                extractProductField(
                        content,
                        "Selling Price"
                );

        if (!price.isEmpty()) {
            return price;
        }

        price =
                extractProductField(
                        content,
                        "Price"
                );

        if (!price.isEmpty()) {
            return price;
        }

        return extractProductField(
                content,
                "Product Price"
        );
    }

    // ============================================================
    // RATING
    // ============================================================

    private String extractRating(
            String content) {

        String rating =
                extractProductField(
                        content,
                        "Average Rating"
                );

        if (!rating.isEmpty()) {
            return rating;
        }

        rating =
                extractProductField(
                        content,
                        "Rating"
                );

        if (!rating.isEmpty()) {
            return rating;
        }

        return extractProductField(
                content,
                "Customer Rating"
        );
    }

    // ============================================================
    // SEARCHABLE CONTENT
    // ============================================================

    private String getSearchableText(
            String content) {

        if (content == null) {
            return "";
        }

        /*
         * Reuse ProductDocument's clean searchable
         * content so search does not get polluted by
         * customer queries, reviews and recommendation
         * sections.
         */
        ProductDocument temporary =
                new ProductDocument(
                        "temporary.txt",
                        content
                );

        return normalize(
                temporary.getSearchableContent()
        );
    }

    // ============================================================
    // MATCHED QUERY TERMS
    // ============================================================

    private int countMatchedTerms(
            String text,
            String[] queryTerms) {

        if (text == null ||
                text.isEmpty()) {

            return 0;
        }

        String normalizedText =
                normalize(text);

        String[] words =
                normalizedText.split("\\s+");

        int matched = 0;

        for (String queryTerm :
                queryTerms) {

            if (queryTerm.isEmpty()) {
                continue;
            }

            for (String word : words) {

                if (word.equals(queryTerm)) {

                    matched++;
                    break;
                }
            }
        }

        return matched;
    }

    // ============================================================
    // PHRASE MATCH
    // ============================================================

    private boolean containsPhrase(
            String text,
            String phrase) {

        if (text == null ||
                phrase == null) {

            return false;
        }

        String normalizedText =
                normalize(text);

        String normalizedPhrase =
                normalize(phrase);

        if (normalizedText.isEmpty()
                || normalizedPhrase.isEmpty()) {

            return false;
        }

        return normalizedText.contains(
                normalizedPhrase
        );
    }

    // ============================================================
    // SCORE
    // ============================================================

    private int calculateScore(
            String query,
            int occurrences,
            int nameMatchedTerms,
            int documentMatchedTerms,
            boolean exactNameMatch,
            boolean phraseInName) {

        int score = 0;

        /*
         * Exact product name:
         * strongest match.
         */
        if (exactNameMatch) {

            score += 1000;

        } else if (phraseInName) {

            /*
             * Full query phrase in product name.
             */
            score += 700;
        }

        /*
         * Query words appearing in product name.
         */
        score +=
                nameMatchedTerms * 150;

        /*
         * Query words appearing in searchable
         * product content.
         */
        score +=
                documentMatchedTerms * 50;

        /*
         * Full query phrase appears in document.
         */
        if (occurrences > 0) {

            score += 75;
        }

        /*
         * Small bonus for repeated exact matches.
         */
        score += Math.min(
                occurrences * 5,
                50
        );

        return score;
    }

    // ============================================================
    // NORMALIZATION
    // ============================================================

    private String normalize(
            String text) {

        if (text == null) {
            return "";
        }

        return text
                .toLowerCase()
                .replaceAll(
                        "[^a-z0-9]+",
                        " "
                )
                .trim()
                .replaceAll(
                        "\\s+",
                        " "
                );
    }

    // ============================================================
    // DISPLAY CLEANUP
    // ============================================================

    private String cleanDisplay(
            String text) {

        if (text == null) {
            return "";
        }

        return text
                .replace("₹", "INR ")
                .replace("✓", "[+]")
                .replace("✔", "[+]");
    }

    // ============================================================
    // AUTOMATIC ALGORITHM SELECTION
    // ============================================================

    private String chooseAutomaticAlgorithm(
            String normalizedQuery) {

        // Recommendation requests use the graph-based Max Flow
        // algorithm instead of treating the request as text search.
        if (isRecommendationQuery(normalizedQuery)) {
            return "Max Flow";
        }

        long kmpTime =
                benchmarkAlgorithm(
                        normalizedQuery,
                        true
                );

        long rabinKarpTime =
                benchmarkAlgorithm(
                        normalizedQuery,
                        false
                );

        return kmpTime <= rabinKarpTime
                ? "KMP"
                : "Rabin-Karp";
    }

    /**
     * Detects an explicit product-recommendation intent.
     *
     * Normal product searches such as "samsung", "wireless" or
     * "gaming laptop" continue through KMP/Rabin-Karp. Only clear
     * recommendation requests are routed to Max Flow.
     */
    private boolean isRecommendationQuery(
            String normalizedQuery) {

        if (normalizedQuery == null ||
                normalizedQuery.isEmpty()) {
            return false;
        }

        return normalizedQuery.equals("recommend")
                || normalizedQuery.equals("recommendation")
                || normalizedQuery.equals("recommendations")
                || normalizedQuery.equals("suggest products")
                || normalizedQuery.equals("product recommendation")
                || normalizedQuery.equals("product recommendations")
                || normalizedQuery.equals("recommend products")
                || normalizedQuery.equals("recommend me products")
                || normalizedQuery.equals("what should i buy")
                || normalizedQuery.equals("what should i purchase")
                || normalizedQuery.startsWith("recommend ")
                || normalizedQuery.startsWith("suggest products ");
    }

    /**
     * Measures the actual pattern-matching time on the current
     * query and the current product corpus.
     *
     * A couple of warm-up runs reduce JVM JIT effects. The median
     * of the measured runs is used so that one unusually slow run
     * does not decide the algorithm.
     */
    private long benchmarkAlgorithm(
            String pattern,
            boolean useKMP) {

        final int WARMUP_RUNS = 2;
        final int MEASURED_RUNS = 5;

        for (int run = 0; run < WARMUP_RUNS; run++) {
            runPatternMatchAcrossCorpus(pattern, useKMP);
        }

        long[] times = new long[MEASURED_RUNS];

        for (int run = 0; run < MEASURED_RUNS; run++) {

            long start = System.nanoTime();

            runPatternMatchAcrossCorpus(pattern, useKMP);

            long end = System.nanoTime();
            times[run] = end - start;
        }

        java.util.Arrays.sort(times);

        return times[MEASURED_RUNS / 2];
    }

    private void runPatternMatchAcrossCorpus(
            String pattern,
            boolean useKMP) {

        int total = 0;

        for (ProductDocument product : corpus) {

            if (product == null) {
                continue;
            }

            String content = product.getContent();

            if (content == null || content.trim().isEmpty()) {
                continue;
            }

            String searchableText =
                    normalize(content);

            if (useKMP) {
                total += countOccurrencesKMP(
                        searchableText, pattern);
            } else {
                total += countOccurrencesRabinKarp(
                        searchableText, pattern);
            }
        }

        // Prevent the JVM from treating the benchmark as dead work.
        if (total == Integer.MIN_VALUE) {
            System.out.print("");
        }
    }

    // ============================================================
    // KMP
    // ============================================================

    private int countOccurrencesKMP(
            String text,
            String pattern) {

        if (pattern.isEmpty()
                || text.length()
                < pattern.length()) {

            return 0;
        }

        int[] lps =
                buildLPS(pattern);

        int i = 0;
        int j = 0;
        int count = 0;

        while (i < text.length()) {

            if (text.charAt(i)
                    == pattern.charAt(j)) {

                i++;
                j++;

                if (j == pattern.length()) {

                    count++;

                    /*
                     * Continue searching for
                     * overlapping matches.
                     */
                    j =
                            lps[j - 1];
                }

            } else {

                if (j != 0) {

                    j =
                            lps[j - 1];

                } else {

                    i++;
                }
            }
        }

        return count;
    }

    // ============================================================
    // KMP LPS TABLE
    // ============================================================

    private int[] buildLPS(
            String pattern) {

        int[] lps =
                new int[
                        pattern.length()
                ];

        int length = 0;
        int i = 1;

        while (i < pattern.length()) {

            if (pattern.charAt(i)
                    == pattern.charAt(length)) {

                length++;

                lps[i] =
                        length;

                i++;

            } else {

                if (length != 0) {

                    length =
                            lps[length - 1];

                } else {

                    lps[i] = 0;

                    i++;
                }
            }
        }

        return lps;
    }

    // ============================================================
    // RABIN-KARP
    // ============================================================

    private int countOccurrencesRabinKarp(
            String text,
            String pattern) {

        if (pattern.isEmpty()
                || text.length()
                < pattern.length()) {

            return 0;
        }

        final int BASE = 256;
        final int PRIME = 101;

        int m =
                pattern.length();

        int n =
                text.length();

        long patternHash = 0;
        long textHash = 0;
        long highestPower = 1;

        /*
         * BASE^(m-1) % PRIME
         */
        for (int i = 0;
             i < m - 1;
             i++) {

            highestPower =
                    (highestPower * BASE)
                            % PRIME;
        }

        /*
         * Initial hash.
         */
        for (int i = 0;
             i < m;
             i++) {

            patternHash =
                    (
                            BASE * patternHash
                            + pattern.charAt(i)
                    ) % PRIME;

            textHash =
                    (
                            BASE * textHash
                            + text.charAt(i)
                    ) % PRIME;
        }

        int count = 0;

        // --------------------------------------------------------
        // ROLLING HASH
        // --------------------------------------------------------

        for (int i = 0;
             i <= n - m;
             i++) {

            if (patternHash == textHash) {

                /*
                 * Verify actual characters.
                 * This avoids false positives due to
                 * hash collisions.
                 */
                boolean match = true;

                for (int j = 0;
                     j < m;
                     j++) {

                    if (text.charAt(i + j)
                            != pattern.charAt(j)) {

                        match = false;
                        break;
                    }
                }

                if (match) {
                    count++;
                }
            }

            if (i < n - m) {

                textHash =
                        (
                                BASE
                                * (
                                        textHash
                                        - text.charAt(i)
                                        * highestPower
                                )
                                + text.charAt(i + m)
                        ) % PRIME;

                if (textHash < 0) {
                    textHash += PRIME;
                }
            }
        }

        return count;
    }

    // ============================================================
    // HIDDEN ALGORITHM TEST MODE
    // ============================================================

    /**
     * Opens the benchmark menu only when the user explicitly types
     * /test. Normal product searches remain clean and automatic.
     */
    public void showTestMenu(Scanner scanner) {

        while (true) {

            System.out.println();
            System.out.println("========================================");
            System.out.println("       ALGORITHM TEST MODE");
            System.out.println("========================================");
            System.out.println("1. Test KMP");
            System.out.println("2. Test Rabin-Karp");
            System.out.println("3. Compare KMP vs Rabin-Karp");
            System.out.println("4. Test Max Flow");
            System.out.println("5. Exit Test Mode");
            System.out.println("========================================");
            System.out.print("Enter choice: ");

            String choice = scanner.nextLine().trim();

            switch (choice) {

                case "1":
                    runSingleAlgorithmTest(scanner, true);
                    break;

                case "2":
                    runSingleAlgorithmTest(scanner, false);
                    break;

                case "3":
                    runComparisonTest(scanner);
                    break;

                case "4":
                    MaxFlow.runDemo();
                    break;

                case "5":
                    return;

                default:
                    System.out.println("Invalid choice. Please select 1-5.");
            }
        }
    }

    private void runSingleAlgorithmTest(
            Scanner scanner,
            boolean useKMP) {

        System.out.println();
        System.out.print("Enter test query: ");
        String query = scanner.nextLine().trim();

        String normalizedQuery = normalize(query);

        if (normalizedQuery.isEmpty()) {
            System.out.println("Please enter a valid test query.");
            return;
        }

        long time = benchmarkAlgorithm(
                normalizedQuery,
                useKMP
        );

        System.out.println();
        System.out.println("----------------------------------------");
        System.out.println(
                useKMP ? "KMP TEST" : "RABIN-KARP TEST"
        );
        System.out.println("Query            : " + query);

        if (useKMP) {
            System.out.println("Time Complexity  : O(n + m)");
        } else {
            System.out.println("Average Complexity: O(n + m)");
            System.out.println("Worst-case       : O(nm)");
        }

        System.out.println("Execution Time   : " + formatTime(time));
        System.out.println("----------------------------------------");
    }

    private void runComparisonTest(Scanner scanner) {

        System.out.println();
        System.out.print("Enter test query: ");
        String query = scanner.nextLine().trim();

        String normalizedQuery = normalize(query);

        if (normalizedQuery.isEmpty()) {
            System.out.println("Please enter a valid test query.");
            return;
        }

        long kmpTime = benchmarkAlgorithm(
                normalizedQuery, true
        );

        long rabinKarpTime = benchmarkAlgorithm(
                normalizedQuery, false
        );

        String selected =
                kmpTime <= rabinKarpTime
                        ? "KMP"
                        : "Rabin-Karp";

        System.out.println();
        System.out.println("========================================");
        System.out.println("       ALGORITHM COMPARISON");
        System.out.println("========================================");
        System.out.println("Query: " + query);
        System.out.println();

        System.out.println("KMP");
        System.out.println("Time Complexity : O(n + m)");
        System.out.println("Execution Time  : " + formatTime(kmpTime));
        System.out.println();

        System.out.println("Rabin-Karp");
        System.out.println("Average         : O(n + m)");
        System.out.println("Worst-case      : O(nm)");
        System.out.println("Execution Time  : " + formatTime(rabinKarpTime));
        System.out.println();

        System.out.println("----------------------------------------");
        System.out.println("Faster Algorithm: " + selected);
        System.out.println("Reason: Lower measured execution time for this query.");
        System.out.println("========================================");
    }

    /**
     * Converts nanoseconds to milliseconds for a more readable
     * benchmark display. The benchmark itself is still measured
     * using System.nanoTime() for precision.
     */
    private String formatTime(long nanoseconds) {

        double milliseconds = nanoseconds / 1_000_000.0;

        return String.format("%.3f ms", milliseconds);
    }

    // ============================================================
    // SEARCH RESULT OBJECT
    // ============================================================

    private static class SearchItem {

        ProductDocument product;

        int score;

        int occurrences;

        int matchedTerms;

        SearchItem(
                ProductDocument product,
                int score,
                int occurrences,
                int matchedTerms) {

            this.product =
                    product;

            this.score =
                    score;

            this.occurrences =
                    occurrences;

            this.matchedTerms =
                    matchedTerms;
        }
    }
}