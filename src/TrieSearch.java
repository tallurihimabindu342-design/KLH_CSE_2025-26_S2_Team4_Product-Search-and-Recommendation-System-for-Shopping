import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * Prefix search over product names and each product's "Search Index" terms.
 *
 * A Trie holds every indexed word; termToProducts maps a word back to the
 * products that contain it.
 *
 * A multi-word prefix such as "samsung gal" is handled word by word: every
 * word is treated as a prefix, and only products matching ALL of them are
 * returned.
 */
public class TrieSearch {

    private final Trie trie;

    private final Map<String, LinkedHashSet<ProductDocument>>
            termToProducts;

    public TrieSearch() {

        trie = new Trie();

        termToProducts =
                new HashMap<>();
    }

    public void build(ProductDocument[] corpus) {

        if (corpus == null) {
            return;
        }

        for (ProductDocument product : corpus) {

            if (product == null) {
                continue;
            }

            addTerms(
                    product.getProductName(),
                    product
            );

            addTerms(
                    extractSearchIndex(product.getContent()),
                    product
            );
        }
    }

    private void addTerms(
            String text,
            ProductDocument product) {

        if (text == null || product == null) {
            return;
        }

        for (String word : tokenize(text)) {

            if (word.length() < 2) {
                continue;
            }

            trie.insert(word);

            // LinkedHashSet: O(1) duplicate check, keeps insertion order
            termToProducts
                    .computeIfAbsent(
                            word,
                            key -> new LinkedHashSet<>()
                    )
                    .add(product);
        }
    }

    public ArrayList<String> searchPrefix(String prefix) {

        return trie.getWordsWithPrefix(prefix);
    }

    public ArrayList<ProductDocument> searchProducts(String prefix) {

        // Same normalisation as indexing, so "Galaxy-S2", "S24+" etc. work.
        String[] prefixes = tokenize(prefix);

        if (prefixes.length == 0) {
            return new ArrayList<>();
        }

        LinkedHashSet<ProductDocument> matches = null;

        for (String part : prefixes) {

            LinkedHashSet<ProductDocument> forPart =
                    new LinkedHashSet<>();

            for (String term : trie.getWordsWithPrefix(part)) {

                LinkedHashSet<ProductDocument> products =
                        termToProducts.get(term);

                if (products != null) {
                    forPart.addAll(products);
                }
            }

            if (matches == null) {
                matches = forPart;
            } else {
                matches.retainAll(forPart);
            }

            if (matches.isEmpty()) {
                break;
            }
        }

        return new ArrayList<>(matches);
    }

    private static String[] tokenize(String text) {

        if (text == null) {
            return new String[0];
        }

        String cleaned =
                text.toLowerCase()
                        .replaceAll("[^a-z0-9]+", " ")
                        .trim();

        if (cleaned.isEmpty()) {
            return new String[0];
        }

        return cleaned.split("\\s+");
    }

    private String extractSearchIndex(String content) {

        if (content == null) {
            return "";
        }

        String[] lines =
                content.split("\\R");

        StringBuilder index =
                new StringBuilder();

        boolean insideIndex = false;

        for (String line : lines) {

            String trimmed =
                    line.trim();

            if (trimmed.equalsIgnoreCase("Search Index:")) {
                insideIndex = true;
                continue;
            }

            if (insideIndex
                    && trimmed.equalsIgnoreCase(
                            "Customer Search Queries:")) {

                break;
            }

            if (insideIndex) {
                index.append(line).append(" ");
            }
        }

        return index.toString();
    }
}