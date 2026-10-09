public class ProductDocument {

    private final String fileName;
    private final String content;

    // Parsed values are computed once on first use. The document is
    // immutable, and parsing re-scans the whole file, so without this every
    // search would re-parse every product several times.
    private String cachedName;
    private String cachedPrice;
    private String cachedRating;
    private String cachedBrand;
    private String cachedCategory;
    private String cachedSearchable;
    private String cachedSearchableClean;

    public ProductDocument(String fileName, String content) {
        this.fileName = fileName;
        this.content = content;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContent() {
        return content;
    }

    // ---------------------------------------------------------
    // PRODUCT NAME
    // ---------------------------------------------------------
    // 1. The labelled "Product Name:" field (proper capitalisation,
    //    e.g. "Samsung Galaxy S24 Ultra").
    // 2. Otherwise the first non-empty line, UNLESS that line itself
    //    looks like a metadata field (Price / Rating / Brand /
    //    Category / MRP) - in which case it keeps looking for the
    //    real title line below it.
    // ---------------------------------------------------------
    public String getProductName() {

        if (cachedName == null) {
            cachedName = computeProductName();
        }

        return cachedName;
    }

    private String computeProductName() {

        String[] lines = content.split("\\R");

        for (String line : lines) {

            String trimmed = line.trim();
            int colon = trimmed.indexOf(':');

            if (colon > 0
                    && trimmed.substring(0, colon).trim()
                            .equalsIgnoreCase("Product Name")) {

                String value = trimmed.substring(colon + 1).trim();

                if (!value.isEmpty()) {
                    return value;
                }
            }
        }

        for (String line : lines) {

            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                continue;
            }

            String name = trimmed.replaceFirst("^#+\\s*", "").trim();

            String lower = name.toLowerCase();

            boolean looksLikeMetadata =
                    lower.startsWith("price")
                    || lower.startsWith("mrp")
                    || lower.startsWith("rating")
                    || lower.startsWith("brand")
                    || lower.startsWith("category");

            if (!looksLikeMetadata) {
                return name;
            }
        }

        return fileName;
    }

    // ---------------------------------------------------------
    // PRICE
    // ---------------------------------------------------------
    // Matches "Price:", "Selling Price:", "MRP:", "Cost:",
    // "Price = ...", "Price - ...", "Price INR 49,999" etc.
    // ---------------------------------------------------------
    private static final String[] PRICE_KEY_HINTS = {
        "price", "mrp", "cost", "amount"
    };

    public String getPrice() {

        if (cachedPrice == null) {
            cachedPrice = normalizePrice(
                    extractFieldByHints(PRICE_KEY_HINTS)
            );
        }

        return cachedPrice;
    }

    private String normalizePrice(String price) {

        if (price == null) {
            return "";
        }

        String cleaned = price.trim();

        if (cleaned.isEmpty()) {
            return "";
        }

        if (cleaned.startsWith("\u20B9")
                || cleaned.startsWith("?")) {

            cleaned = cleaned.substring(1).trim();
        }

        if (cleaned.toLowerCase().startsWith("rs.")) {
            cleaned = cleaned.substring(3).trim();
        } else if (cleaned.toLowerCase().startsWith("rs")) {
            cleaned = cleaned.substring(2).trim();
        }

        if (cleaned.toLowerCase().startsWith("inr")) {
            cleaned = cleaned.substring(3).trim();
        }

        return "INR " + groupIndian(cleaned);
    }

    // ---------------------------------------------------------
    // The corpus mixes western grouping (129,999) and Indian
    // grouping (1,29,999) for the same currency. Plain numbers are
    // re-grouped the Indian way so every price prints consistently;
    // anything that is not a plain number is left untouched.
    // ---------------------------------------------------------
    static String groupIndian(String amount) {

        if (!amount.matches("[0-9][0-9,]*")) {
            return amount;
        }

        String digits = amount.replace(",", "");

        if (digits.length() <= 3) {
            return digits;
        }

        String lastThree = digits.substring(digits.length() - 3);
        String rest = digits.substring(0, digits.length() - 3);

        StringBuilder grouped = new StringBuilder();

        while (rest.length() > 2) {
            grouped.insert(0, "," + rest.substring(rest.length() - 2));
            rest = rest.substring(0, rest.length() - 2);
        }

        grouped.insert(0, rest);

        return grouped + "," + lastThree;
    }

    // ---------------------------------------------------------
    // RATING
    // ---------------------------------------------------------
    // Matches "Rating:", "Average Rating:", "Customer Rating:",
    // "Star Rating:", "Rating = 4.7", "Rating - 4.7/5" etc.
    // ---------------------------------------------------------
    private static final String[] RATING_KEY_HINTS = {
        "rating", "stars"
    };

    public String getRating() {

        if (cachedRating == null) {
            cachedRating = extractFieldByHints(RATING_KEY_HINTS);
        }

        return cachedRating;
    }

    // ---------------------------------------------------------
    // BRAND
    // ---------------------------------------------------------
    // Matches "Brand:", "Manufacturer:", "Make:" etc. Falls back
    // to guessing the brand from the product name if no labelled
    // field is found at all.
    // ---------------------------------------------------------
    private static final String[] BRAND_KEY_HINTS = {
        "brand", "manufacturer", "make"
    };

    public String getBrand() {

        if (cachedBrand == null) {

            String value = extractFieldByHints(BRAND_KEY_HINTS);

            cachedBrand = value.isEmpty()
                    ? extractBrandFromName()
                    : value;
        }

        return cachedBrand;
    }

    // ---------------------------------------------------------
    // CATEGORY
    // ---------------------------------------------------------
    // Deliberately does NOT use a generic hint like "type" here:
    // spec sheets for this kind of corpus commonly contain lines
    // like "Display Type:", "Connector Type:", "Battery Type:",
    // "SIM Type:" - matching on "type" would silently grab one of
    // those instead of the real category.
    // ---------------------------------------------------------
    private static final String[] CATEGORY_KEY_HINTS = {
        "category", "product category"
    };

    public String getCategory() {

        if (cachedCategory == null) {

            String value = extractFieldByHints(CATEGORY_KEY_HINTS);

            cachedCategory = value.isEmpty() ? "Other" : value;
        }

        return cachedCategory;
    }

    // ---------------------------------------------------------
    // SHARED "LABEL CONTAINS HINT" FIELD EXTRACTION
    // ---------------------------------------------------------
    // For each line, picks a SINGLE separator - whichever of
    // ':', '=', '-' appears first after position 0 (so a leading
    // "- " bullet isn't mistaken for the separator). Everything
    // before that separator is the label; if the label contains
    // one of the hint words, everything after it is the value.
    //
    // If a line has no separator at all, it also tries the plain
    // "Price INR 49,999" space-separated form.
    // ---------------------------------------------------------
    private String extractFieldByHints(String[] hints) {

        String[] lines = content.split("\\R");

        for (String line : lines) {

            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                continue;
            }

            int colon = trimmed.indexOf(':');
            int equals = trimmed.indexOf('=');
            int dash = trimmed.indexOf('-');

            int separator = earliestPositive(colon, equals, dash);

            if (separator > 0) {

                String key = trimmed.substring(0, separator).trim().toLowerCase();

                String value = trimmed.substring(separator + 1).trim();

                if (!value.isEmpty() && keyMatchesAny(key, hints)) {
                    return value;
                }

                continue;
            }

            // No ':' / '=' / '-' on this line - try "Price INR 49,999".
            String lower = trimmed.toLowerCase();

            for (String hint : hints) {

                if (lower.startsWith(hint + " ")) {

                    String value = trimmed.substring(hint.length()).trim();

                    if (!value.isEmpty()) {
                        return value;
                    }
                }
            }
        }

        return "";
    }

    private int earliestPositive(int... positions) {

        int best = -1;

        for (int position : positions) {

            if (position > 0 && (best == -1 || position < best)) {
                best = position;
            }
        }

        return best;
    }

    private boolean keyMatchesAny(String key, String[] hints) {

        for (String hint : hints) {

            if (key.equals(hint) || key.contains(hint)) {
                return true;
            }
        }

        return false;
    }

    // ---------------------------------------------------------
    // BRAND FALLBACK (guess from the product name)
    // ---------------------------------------------------------
    private String extractBrandFromName() {

        String name = getProductName().toLowerCase();

        if (name.contains("samsung"))
            return "Samsung";

        if (name.contains("apple") || name.contains("macbook") ||
            name.contains("iphone") || name.contains("ipad"))
            return "Apple";

        if (name.contains("sony"))
            return "Sony";

        if (name.contains("canon"))
            return "Canon";

        if (name.contains("nikon"))
            return "Nikon";

        if (name.contains("dell"))
            return "Dell";

        if (name.contains("lenovo"))
            return "Lenovo";

        if (name.contains("oneplus"))
            return "OnePlus";

        if (name.contains("google") || name.contains("pixel"))
            return "Google";

        if (name.contains("asus"))
            return "ASUS";

        if (name.contains("hp"))
            return "HP";

        if (name.contains("xiaomi") || name.contains("redmi"))
            return "Xiaomi";

        if (name.contains("realme"))
            return "Realme";

        if (name.contains("oppo"))
            return "OPPO";

        if (name.contains("vivo"))
            return "Vivo";

        if (name.contains("logitech"))
            return "Logitech";

        if (name.contains("jbl"))
            return "JBL";

        return "";
    }

    // ---------------------------------------------------------
    // SEARCHABLE CONTENT
    // ---------------------------------------------------------
    public String getSearchableContent() {

        if (cachedSearchable == null) {
            cachedSearchable = computeSearchableContent(true);
        }

        return cachedSearchable;
    }

    // ---------------------------------------------------------
    // Text for WORD matching. Same as getSearchableContent() but
    //  - without the stored "Common Misspellings:" lines, which
    //    deliberately contain wrong spellings and must not count as
    //    real words, and
    //  - without list numbering ("3: Gamers", "[2] Feature Name: ..."),
    //    otherwise a query such as "7" would match every product that
    //    has a seventh list item.
    // ---------------------------------------------------------
    public String getSearchableContentWithoutMisspellings() {

        if (cachedSearchableClean == null) {
            cachedSearchableClean = computeSearchableContent(false);
        }

        return cachedSearchableClean;
    }

    // ---------------------------------------------------------
    // OVERVIEW (one-line product summary shown in search results)
    // ---------------------------------------------------------
    public String getOverview() {

        for (String line : content.split("\\R")) {

            String trimmed = line.trim();
            int colon = trimmed.indexOf(':');

            if (colon > 0
                    && trimmed.substring(0, colon).trim()
                            .equalsIgnoreCase("Overview")) {

                return trimmed.substring(colon + 1).trim();
            }
        }

        return "";
    }

    private String computeSearchableContent(boolean includeMisspellings) {

        StringBuilder searchable = new StringBuilder();

        String[] lines = content.split("\\R");

        boolean skipSection = false;

        for (String line : lines) {

            String trimmed = line.trim();

            // Stop at recommendation data
            // Source files use an em dash ("BLOCK 3 - Recommendation
            // Data"); matching only the "BLOCK 3" prefix keeps this
            // independent of the dash character and of the compiler's
            // source encoding.
            if (trimmed.toUpperCase().startsWith("BLOCK 3")) {

                break;
            }

            // Ignore customer reviews
            if (trimmed.equalsIgnoreCase(
                    "Customer Reviews:")) {

                skipSection = true;
                continue;
            }

            // Ignore customer search queries
            if (trimmed.equalsIgnoreCase(
                    "Customer Search Queries:")) {

                skipSection = true;
                continue;
            }

            // Ignore customer Q&A
            if (trimmed.equalsIgnoreCase(
                    "Customer Questions & Answers:")) {

                skipSection = true;
                continue;
            }

            // Resume at searchable attributes
            if (trimmed.equalsIgnoreCase(
                    "Searchable Attributes:")) {

                skipSection = false;
            }

            // Resume at search index
            if (trimmed.equalsIgnoreCase(
                    "Search Index:")) {

                skipSection = false;
            }

            if (!includeMisspellings
                    && trimmed.toLowerCase().startsWith("common misspellings:")) {

                continue;
            }

            if (!skipSection) {
                searchable
                        .append(includeMisspellings
                                ? line
                                : trimmed.replaceFirst("^(\\[[0-9]+\\]|[0-9]+:)\\s*", ""))
                        .append(" ");
            }
        }

        return searchable.toString();
    }

    // ---------------------------------------------------------
    // FULL TEXT FOR SEARCH
    // ---------------------------------------------------------
    public String getSearchText() {

        return (
            getProductName() + " " +
            getBrand() + " " +
            getCategory() + " " +
            getPrice() + " " +
            getRating() + " " +
            getSearchableContent()
        ).toLowerCase();
    }
}