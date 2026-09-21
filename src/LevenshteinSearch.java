import java.util.ArrayList;

// ============================================================
// REVIEW 3 - LEVENSHTEIN APPROXIMATE PRODUCT SEARCH
// Integrates Edit Distance with product searching
// ============================================================
public class LevenshteinSearch {

    public static class Result {

        String productName;
        String fileName;
        String matchedTerm;

        int distance;
        int matchedTerms;
        int totalTerms;

        double matchScore;

        Result(
            String productName,
            String fileName,
            String matchedTerm,
            int distance,
            int matchedTerms,
            int totalTerms,
            double matchScore
        ) {
            this.productName = productName;
            this.fileName = fileName;
            this.matchedTerm = matchedTerm;
            this.distance = distance;
            this.matchedTerms = matchedTerms;
            this.totalTerms = totalTerms;
            this.matchScore = matchScore;
        }
    }

    public static ArrayList<Result> search(
            ProductDocument[] corpus,
            String query) {

        ArrayList<Result> results =
            new ArrayList<>();

        String[] queryTerms =
            tokenize(query);

        if (queryTerms.length == 0) {
            return results;
        }

        for (ProductDocument product : corpus) {

            String productName =
                product.getProductName();

            String productNameText =
                productName.toLowerCase();

            /*
             * Product information used as a
             * secondary source for approximate matching.
             */
            String searchableText =
                product.getSearchableContent()
                       .toLowerCase();

            searchableText =
                removeCommonMisspellings(searchableText);

            String[] productNameTerms =
                tokenize(productNameText);

            String[] searchableTerms =
                tokenize(searchableText);

            int matchedTerms = 0;
            int totalDistance = 0;

            StringBuilder matched =
                new StringBuilder();

            for (String queryTerm : queryTerms) {

                int nameBestDistance =
                    Integer.MAX_VALUE;

                String nameBestMatch =
                    "";

                /*
                 * STEP 1:
                 * PRODUCT NAME APPROXIMATE MATCHING.(Fuzzy Matching)
                 */
                for (String productTerm :
                        productNameTerms) {

                    int distance =
                        Levenshtein.distance(
                            queryTerm,
                            productTerm
                        );

                    if (distance < nameBestDistance) {
                        nameBestDistance = distance;
                        nameBestMatch = productTerm;
                    }
                }
                //Fuzzy Matching 

                int threshold =
                    getThreshold(queryTerm.length());

                /*
                 * If the product name contains a
                 * valid close match, use it.
                 *
                 * This prevents unrelated corpus
                 * terms from replacing a product-name
                 * typo match.
                 */
                if (nameBestDistance <= threshold) {

                    matchedTerms++;
                    totalDistance += nameBestDistance;

                    if (matched.length() > 0) {
                        matched.append(", ");
                    }

                    matched.append(nameBestMatch);

                    continue;
                }

                /*
                 * STEP 2:
                 *
                 * For SINGLE-WORD queries, do not
                 * search the entire product content.
                 *
                 * This prevents a query such as
                 * "wireles" from matching unrelated
                 * products simply because the word
                 * "wireless" appears in their features,
                 * description, or search terms.
                 */
                if (queryTerms.length == 1) {
                    continue;
                }

                /*
                 * For MULTI-WORD queries, searchable
                 * product information can still be
                 * used as a secondary source.
                 */
                int contentBestDistance =
                    Integer.MAX_VALUE;

                String contentBestMatch =
                    "";

                for (String searchableTerm :
                        searchableTerms) {

                    int distance =
                        Levenshtein.distance(
                            queryTerm,
                            searchableTerm
                        );

                    if (distance < contentBestDistance) {
                        contentBestDistance = distance;
                        contentBestMatch = searchableTerm;
                    }
                }

                if (contentBestDistance <= threshold) {

                    matchedTerms++;
                    totalDistance += contentBestDistance;

                    if (matched.length() > 0) {
                        matched.append(", ");
                    }

                    matched.append(contentBestMatch);
                }
            }

            int minimumMatches;

            if (queryTerms.length >= 3) {
                minimumMatches = 2;
            } else {
                minimumMatches = 1;
            }

            if (matchedTerms >= minimumMatches) {

                /*
                 * Base score:
                 * percentage of query terms matched.
                 */
                double termScore =
                    ((double) matchedTerms
                    / queryTerms.length) * 100.0;

                /*
                 * Distance penalty.
                 */
                double distancePenalty =
                    totalDistance * 5.0;

                double matchScore =
                    termScore - distancePenalty;

                /*
                 * Exact product-name query bonus.
                 */
                if (productNameText.contains(
                        query.toLowerCase())) {

                    matchScore += 30.0;
                }

                if (matchScore > 100.0) {
                    matchScore = 100.0;
                }

                if (matchScore < 0.0) {
                    matchScore = 0.0;
                }

                results.add(
                    new Result(
                        productName,
                        product.getFileName(),
                        matched.toString(),
                        totalDistance,
                        matchedTerms,
                        queryTerms.length,
                        matchScore
                    )
                );
            }
        }

        /*
         * Highest score first.
         */
        results.sort((a, b) ->
            Double.compare(
                b.matchScore,
                a.matchScore
            )
        );

        return results;
    }

    /*
     * Removes the Common Misspellings field
     * from Levenshtein searchable content.
     *
     * This prevents a stored typo such as
     * "samsng" from matching itself with
     * edit distance 0.
     */
    private static String removeCommonMisspellings(
            String text) {

        String marker =
            "common misspellings:";

        int start =
            text.indexOf(marker);

        if (start == -1) {
            return text;
        }

        String[] nextSections = {
            "search phrases:",
            "search intent keywords:",
            "related search terms:"
        };

        int end = text.length();

        for (String section : nextSections) {

            int position =
                text.indexOf(
                    section,
                    start
                );

            if (position != -1 && position < end) {
                end = position;
            }
        }

        return text.substring(0, start)
            + text.substring(end);
    }

    private static String[] tokenize(
            String text) {

        String cleaned =
            text.toLowerCase()
                .replaceAll(
                    "[^a-z0-9]+",
                    " "
                )
                .trim();

        if (cleaned.isEmpty()) {
            return new String[0];
        }

        return cleaned.split("\\s+");
    }

    /*
     * Maximum allowed edit distance.
     */
    private static int getThreshold(
            int length) {

        if (length <= 4) {
            return 1;
        }

        if (length <= 8) {
            return 1;
        }

        return 2;
    }
}