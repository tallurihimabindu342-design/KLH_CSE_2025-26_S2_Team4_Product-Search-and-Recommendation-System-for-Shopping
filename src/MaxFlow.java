import java.util.*;

/**
 * ============================================================
 * MAX FLOW RECOMMENDATION SYSTEM
 * Algorithm: Edmonds-Karp (Ford-Fulkerson with BFS augmenting paths)
 *
 * Network built by runRecommendation (one unit of flow = one
 * recommended product):
 *
 *   SOURCE --[max total]--> GLOBAL LIMIT
 *   GLOBAL LIMIT --[max per category]--> CATEGORY node
 *   CATEGORY --[1 per product]--> BRAND node     (each product is an EDGE)
 *   BRAND --[max per brand]--> SINK
 *
 * A unit of flow can only pass if the global limit, the product's
 * category limit AND its brand limit all still have room, so all
 * three limits are enforced by edge capacities alone.
 *
 * Complexity: Edmonds-Karp is O(V * E^2) in general. Here the total
 * flow is at most 6, so each max-flow run needs at most 6 BFS
 * searches of O(V + E). The selection step re-runs max flow once per
 * candidate (at most 30), so one recommendation costs
 * O(candidates * 6 * (V + E)).
 *
 * runDemo() is a small stand-alone example network (not used by the
 * console program).
 * ============================================================
 */
public class MaxFlow {

    private static class Edge {
        int to;
        int reverse;
        int capacity;

        Edge(int to, int reverse, int capacity) {
            this.to = to;
            this.reverse = reverse;
            this.capacity = capacity;
        }
    }

    private final ArrayList<Edge>[] graph;

    // Optional: when set, every augmenting path that is found is printed.
    private String[] nodeLabels = null;
    private IdentityHashMap<Edge, String> edgeLabels = null;

    @SuppressWarnings("unchecked")
    public MaxFlow(int vertices) {
        graph = (ArrayList<Edge>[]) new ArrayList<?>[vertices];

        for (int i = 0; i < vertices; i++) {
            graph[i] = new ArrayList<>();
        }
    }

    public void addEdge(int from, int to, int capacity) {

        if (capacity < 0) {
            throw new IllegalArgumentException(
                    "Capacity cannot be negative."
            );
        }

        Edge forward = new Edge(
                to,
                graph[to].size(),
                capacity
        );

        Edge reverse = new Edge(
                from,
                graph[from].size(),
                0
        );

        graph[from].add(forward);
        graph[to].add(reverse);
    }

    /**
     * Edmonds-Karp algorithm.
     *
     * Edmonds-Karp is Ford-Fulkerson where BFS is used
     * to find augmenting paths.
     *
     * Time Complexity:
     * O(VE^2)
     */
    public int calculateMaxFlow(int source, int sink) {

        int maxFlow = 0;
        int vertices = graph.length;
        int pathNumber = 0;

        while (true) {

            int[] parentVertex = new int[vertices];
            int[] parentEdge = new int[vertices];

            Arrays.fill(parentVertex, -1);
            Arrays.fill(parentEdge, -1);

            Queue<Integer> queue = new ArrayDeque<>();

            queue.add(source);
            parentVertex[source] = source;

            // BFS to find augmenting path
            while (!queue.isEmpty()
                    && parentVertex[sink] == -1) {

                int current = queue.poll();

                for (int i = 0;
                     i < graph[current].size();
                     i++) {

                    Edge edge = graph[current].get(i);

                    if (edge.capacity > 0
                            && parentVertex[edge.to] == -1) {

                        parentVertex[edge.to] = current;
                        parentEdge[edge.to] = i;

                        queue.add(edge.to);

                        if (edge.to == sink) {
                            break;
                        }
                    }
                }
            }

            // No augmenting path remains
            if (parentVertex[sink] == -1) {
                break;
            }

            // Find bottleneck capacity
            int pathFlow = Integer.MAX_VALUE;

            for (int v = sink;
                 v != source;
                 v = parentVertex[v]) {

                int u = parentVertex[v];

                Edge edge =
                        graph[u].get(parentEdge[v]);

                pathFlow =
                        Math.min(pathFlow, edge.capacity);
            }

            // (display only) show the augmenting path that was found
            if (nodeLabels != null) {
                pathNumber++;
                printPath(pathNumber, source, sink,
                        parentVertex, parentEdge, pathFlow);
            }

            // Update residual graph
            for (int v = sink;
                 v != source;
                 v = parentVertex[v]) {

                int u = parentVertex[v];

                Edge edge =
                        graph[u].get(parentEdge[v]);

                Edge reverse =
                        graph[v].get(edge.reverse);

                edge.capacity -= pathFlow;
                reverse.capacity += pathFlow;
            }

            maxFlow += pathFlow;
        }

        return maxFlow;
    }

    /**
     * Prints one augmenting path, e.g.
     * Path 1: SOURCE -> LIMIT -> Laptop -> Apple [Apple MacBook Pro] -> SINK  (+1)
     * (display only - does not change the algorithm)
     */
    private void printPath(
            int number,
            int source,
            int sink,
            int[] parentVertex,
            int[] parentEdge,
            int pathFlow) {

        ArrayList<String> parts = new ArrayList<>();

        for (int v = sink; v != source; v = parentVertex[v]) {

            int u = parentVertex[v];
            Edge edge = graph[u].get(parentEdge[v]);

            String part = nodeLabels[v];

            if (edgeLabels != null && edgeLabels.containsKey(edge)) {
                part += " [" + edgeLabels.get(edge) + "]";
            }

            parts.add(part);
        }

        parts.add(nodeLabels[source]);
        Collections.reverse(parts);

        System.out.println("  Path " + number + ": "
                + String.join(" -> ", parts) + "   (+" + pathFlow + ")");
    }

    /** true = also print the full flow network (useful if an examiner asks how it is built). */
    private static final boolean SHOW_FLOW_NETWORK = false;

    /**
     * Amazon-style "diverse recommendations" using max flow.
     *
     * Real stores do not want six near-identical results, so the
     * recommendation list must obey THREE limits at the same time:
     *
     *   1. at most MAX_RECOMMENDATIONS products in total
     *   2. at most MAX_PER_CATEGORY products from one category
     *   3. at most MAX_PER_BRAND products from one brand
     *
     * Network (every PRODUCT is an EDGE from its category to its brand):
     *
     *   SOURCE --[6]--> GLOBAL --[2]--> CATEGORY --[1] product--> BRAND --[2]--> SINK
     *
     * One unit of flow = one recommended product. The flow can only pass
     * if the product's category AND its brand AND the global limit all
     * still have room, so all three limits are enforced by the capacities.
     *
     * Because two limits overlap (category and brand), a simple
     * "take the best product if it fits" loop can get stuck and recommend
     * FEWER products than are actually possible. Max flow always finds
     * the largest valid set.
     *
     * productNames[i], categories[i], brands[i] and scores[i] describe the
     * same product, and the arrays must be in relevance-ranked order.
     * (scores is only displayed; the flow itself never uses it.)
     */
    public static void runRecommendation(
            String query,
            String[] productNames,
            String[] categories,
            String[] brands,
            int[] scores) {

        final int MAX_RECOMMENDATIONS = 6;
        // default limits (they are only relaxed when they cannot fill the list)
        final int DEFAULT_PER_CATEGORY = 6;
        final int DEFAULT_PER_BRAND = 2;

        int MAX_PER_CATEGORY = DEFAULT_PER_CATEGORY;
        int MAX_PER_BRAND = DEFAULT_PER_BRAND;

        System.out.println();
        System.out.println("========================================");
        System.out.println("        RECOMMENDATION ENGINE");
        System.out.println("========================================");
        System.out.println("Query     : " + query);
        System.out.println("Algorithm : Edmonds-Karp (Max Flow)");
        System.out.println();

        if (productNames == null || productNames.length == 0) {
            System.out.println("No products available for recommendation.");
            return;
        }

        /*
         * STEP 1: clean the input and remove duplicate product names
         * (rank order is kept).
         */
        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> categoryList = new ArrayList<>();
        ArrayList<String> brandList = new ArrayList<>();
        ArrayList<Integer> scoreList = new ArrayList<>();
        HashSet<String> seenNames = new HashSet<>();

        for (int i = 0; i < productNames.length; i++) {

            if (productNames[i] == null || productNames[i].trim().isEmpty()) {
                continue;
            }

            String name = productNames[i].trim();

            if (!seenNames.add(name.toLowerCase())) {
                continue;
            }

            names.add(name);
            categoryList.add(cleanValue(categories, i, "Other Products"));
            brandList.add(cleanValue(brands, i, ""));
            scoreList.add(scores != null && i < scores.length ? scores[i] : -1);
        }

        if (names.isEmpty()) {
            System.out.println("No valid products available.");
            return;
        }

        int productCount = names.size();

        /*
         * STEP 2: give every category and every brand a node number.
         * Matching ignores upper/lower case, so "Audio" and "audio"
         * are the same category. A product with no brand gets a brand
         * of its own, so a missing brand never limits anything.
         */
        LinkedHashMap<String, Integer> categoryIds = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> brandIds = new LinkedHashMap<>();
        ArrayList<String> categoryLabels = new ArrayList<>();
        ArrayList<String> brandLabels = new ArrayList<>();
        ArrayList<Integer> categorySizes = new ArrayList<>();
        ArrayList<Integer> brandSizes = new ArrayList<>();

        int[] categoryOf = new int[productCount];
        int[] brandOf = new int[productCount];

        for (int i = 0; i < productCount; i++) {

            String categoryKey = categoryList.get(i).toLowerCase();

            Integer categoryId = categoryIds.get(categoryKey);

            if (categoryId == null) {
                categoryId = categoryLabels.size();
                categoryIds.put(categoryKey, categoryId);
                categoryLabels.add(categoryList.get(i));
                categorySizes.add(0);
            }

            categoryOf[i] = categoryId;
            categorySizes.set(categoryId, categorySizes.get(categoryId) + 1);

            String brand = brandList.get(i);

            String brandKey = brand.isEmpty()
                    ? "?unknown-brand?" + i
                    : brand.toLowerCase();

            Integer brandId = brandIds.get(brandKey);

            if (brandId == null) {
                brandId = brandLabels.size();
                brandIds.put(brandKey, brandId);
                brandLabels.add(brand.isEmpty() ? "Unknown" : brand);
                brandSizes.add(0);
            }

            brandOf[i] = brandId;
            brandSizes.set(brandId, brandSizes.get(brandId) + 1);
        }

        int categoryCount = categoryLabels.size();
        int brandCount = brandLabels.size();

        /*
         * STEP 3: run Edmonds-Karp on the full network.
         * The result is the LARGEST number of products that can be
         * recommended without breaking any limit.
         */
        int[] categorySize = new int[categoryCount];
        int[] brandSize = new int[brandCount];

        for (int c = 0; c < categoryCount; c++) {
            categorySize[c] = categorySizes.get(c);
        }

        for (int b = 0; b < brandCount; b++) {
            brandSize[b] = brandSizes.get(b);
        }

        boolean[] removed = new boolean[productCount];

        /*
         * The default limits (category 6, brand 2) are tried first.
         * If they cannot fill the list (for example a brand search such
         * as "apple" where every candidate has the same brand), the
         * limits are adjusted so the list can still be filled:
         *
         *   tier 1: default limits
         *   tier 2: swap the limits  (diversify by category instead of brand)
         *   tier 3: both limits relaxed
         *
         * The first tier that fills the list is used. If none can, the
         * tier with the largest flow is used.
         */
        int target = Math.min(MAX_RECOMMENDATIONS, productCount);

        int[][] tiers = {
                {DEFAULT_PER_CATEGORY, DEFAULT_PER_BRAND},
                {DEFAULT_PER_BRAND, DEFAULT_PER_CATEGORY},
                {MAX_RECOMMENDATIONS, MAX_RECOMMENDATIONS}
        };

        int maximumFlow = -1;
        int usedTier = 0;
        int defaultFlow = 0;

        for (int k = 0; k < tiers.length; k++) {

            int tierFlow = buildAndRun(
                    categoryCount, brandCount, categoryOf, brandOf,
                    categorySize, brandSize, removed, null,
                    MAX_RECOMMENDATIONS, tiers[k][0], tiers[k][1],
                    null, null
            );

            if (k == 0) {
                defaultFlow = tierFlow;
            }

            if (tierFlow > maximumFlow) {
                maximumFlow = tierFlow;
                usedTier = k;
            }

            if (tierFlow >= target) {
                break;
            }
        }

        MAX_PER_CATEGORY = tiers[usedTier][0];
        MAX_PER_BRAND = tiers[usedTier][1];

        /*
         * STEP 4: decide WHICH products to recommend.
         *
         * Max flow only guarantees the COUNT. To keep the best-ranked
         * products, go from the lowest-ranked candidate upwards and try
         * to drop each one. A product is dropped only if the max flow
         * stays the same without it. What remains is exactly
         * "maximumFlow" products, and the highest-ranked ones are kept
         * whenever the limits allow.
         */
        for (int i = productCount - 1; i >= 0; i--) {

            removed[i] = true;

            int flowWithout = buildAndRun(
                    categoryCount, brandCount, categoryOf, brandOf,
                    categorySize, brandSize, removed, null,
                    MAX_RECOMMENDATIONS, MAX_PER_CATEGORY, MAX_PER_BRAND,
                    null, null
            );

            if (flowWithout < maximumFlow) {
                removed[i] = false;   // this product is needed, keep it
            }
        }

        /*
         * STEP 5: the products still present are the recommendations.
         */
        boolean[] selected = new boolean[productCount];
        int[] pickedPerCategory = new int[categoryCount];
        int[] pickedPerBrand = new int[brandCount];
        int selectedCount = 0;

        for (int i = 0; i < productCount; i++) {

            selected[i] = !removed[i];

            if (selected[i]) {
                selectedCount++;
                pickedPerCategory[categoryOf[i]]++;
                pickedPerBrand[brandOf[i]]++;
            }
        }

        /*
         * DISPLAY: constraints and max flow result
         */
        System.out.println("Constraints");
        System.out.println("----------------------------------------");
        System.out.println("Maximum recommendations : " + MAX_RECOMMENDATIONS);
        System.out.println("Max products / category : " + MAX_PER_CATEGORY);
        System.out.println("Max products / brand    : " + MAX_PER_BRAND);

        if (usedTier != 0) {
            System.out.println("Note: the default limits (category " + DEFAULT_PER_CATEGORY
                    + ", brand " + DEFAULT_PER_BRAND + ") could fill only "
                    + defaultFlow + " of " + target + " slots,");
            System.out.println("      so the limits were adjusted to fill the list.");
        }

        System.out.println();
        System.out.println("Candidates considered   : " + productCount);
        System.out.println("Network : SOURCE -> GLOBAL LIMIT -> CATEGORY -> BRAND -> SINK");
        System.out.println("          (each product is a CATEGORY -> BRAND edge of capacity 1)");
        System.out.println();
        System.out.println("Running Max Flow on " + productCount + " candidates...");
        System.out.println("Maximum Flow = " + maximumFlow);
        System.out.println();
        System.out.println("Augmenting paths (BFS, shortest first) on the selected set:");

        // node names used when printing paths
        String[] pathLabels = new String[2 + categoryCount + brandCount + 1];
        pathLabels[0] = "SOURCE";
        pathLabels[1] = "LIMIT";

        for (int c = 0; c < categoryCount; c++) {
            pathLabels[2 + c] = categoryLabels.get(c);
        }

        for (int b = 0; b < brandCount; b++) {
            pathLabels[2 + categoryCount + b] = brandLabels.get(b);
        }

        pathLabels[pathLabels.length - 1] = "SINK";

        // replay Edmonds-Karp on the final network so each path is visible
        boolean[] notSelected = new boolean[productCount];

        for (int i = 0; i < productCount; i++) {
            notSelected[i] = !selected[i];
        }

        buildAndRun(
                categoryCount, brandCount, categoryOf, brandOf,
                categorySize, brandSize, notSelected, null,
                MAX_RECOMMENDATIONS, MAX_PER_CATEGORY, MAX_PER_BRAND,
                pathLabels, names.toArray(new String[0])
        );

        System.out.println();

        if (SHOW_FLOW_NETWORK) {
            /*
             * DISPLAY: the flow network
             */
            System.out.println("========================================");
            System.out.println("      CAPACITY-CONSTRAINED FLOW");
            System.out.println("========================================");
            System.out.println("Maximum recommendations : " + MAX_RECOMMENDATIONS);
            System.out.println("Maximum per category    : " + MAX_PER_CATEGORY);
            System.out.println("Maximum per brand       : " + MAX_PER_BRAND);
            System.out.println();
            System.out.println("Flow Network (each product is a CATEGORY -> BRAND edge):");
            System.out.println("SOURCE --[" + MAX_RECOMMENDATIONS + "]--> GLOBAL LIMIT");

            for (int c = 0; c < categoryCount; c++) {

                System.out.println(
                        "  |--[" + Math.min(MAX_PER_CATEGORY, categorySizes.get(c))
                                + "] CATEGORY: " + categoryLabels.get(c)
                );

                for (int i = 0; i < productCount; i++) {

                    if (categoryOf[i] != c) {
                        continue;
                    }

                    System.out.println(
                            "        |--[1] " + names.get(i)
                                    + "  -->  BRAND: " + brandLabels.get(brandOf[i])
                    );
                }
            }

            System.out.println();
            System.out.println("BRAND --> SINK limits:");

            for (int b = 0; b < brandCount; b++) {

                // brands that only exist because a product had no brand are not shown
                if (brandLabels.get(b).equals("Unknown")) {
                    continue;
                }

                System.out.println(
                        "  [" + Math.min(MAX_PER_BRAND, brandSizes.get(b))
                                + "] " + brandLabels.get(b) + " --> SINK"
                );
            }

            System.out.println();
            System.out.println("----------------------------------------");
            System.out.println("Candidate Products : " + productCount);
            System.out.println("Categories         : " + categoryCount);
            System.out.println("Brands             : " + brandCount);
            System.out.println("Maximum Flow       : " + maximumFlow);
            System.out.println("Complexity         : O(VE^2) in general;");
            System.out.println("                     here flow <= " + MAX_RECOMMENDATIONS
                    + ", so at most " + MAX_RECOMMENDATIONS + " BFS searches");
            System.out.println("----------------------------------------");
            System.out.println("How to read this:");
            System.out.println("One unit of flow = one recommended product.");
            System.out.println("A unit can only pass if the global limit, the");
            System.out.println("category limit and the brand limit all have room.");
            System.out.println("1) Edmonds-Karp finds the largest valid number");
            System.out.println("   of recommendations (the maximum flow).");
            System.out.println("2) Lowest-ranked candidates are then dropped one");
            System.out.println("   by one while the maximum flow stays the same,");
            System.out.println("   so the best-ranked products are kept.");
            System.out.println("========================================");

        }

        /*
         * DISPLAY: recommendations as a table (plain ASCII so it also
         * looks right in the Windows console), in relevance order.
         */
        int nameWidth = 7;       // "Product"
        int categoryWidth = 8;   // "Category"
        int brandWidth = 5;      // "Brand"
        int scoreWidth = 5;      // "Score"

        for (int i = 0; i < productCount; i++) {

            if (!selected[i]) {
                continue;
            }

            nameWidth = Math.max(nameWidth, Math.min(40, names.get(i).length()));
            categoryWidth = Math.max(categoryWidth, categoryLabels.get(categoryOf[i]).length());
            brandWidth = Math.max(brandWidth, brandLabels.get(brandOf[i]).length());
            scoreWidth = Math.max(scoreWidth, String.valueOf(scoreList.get(i)).length());
        }

        String border = "+----+-" + dashes(nameWidth) + "-+-" + dashes(categoryWidth)
                + "-+-" + dashes(brandWidth) + "-+-" + dashes(scoreWidth) + "-+";

        System.out.println("FINAL RECOMMENDATIONS");
        System.out.println(border);
        System.out.println("| #  | " + pad("Product", nameWidth) + " | "
                + pad("Category", categoryWidth) + " | "
                + pad("Brand", brandWidth) + " | "
                + pad("Score", scoreWidth) + " |");
        System.out.println(border);

        int shown = 0;

        for (int i = 0; i < productCount; i++) {

            if (!selected[i]) {
                continue;
            }

            shown++;

            String brandText = brandLabels.get(brandOf[i]).equals("Unknown")
                    ? "-" : brandLabels.get(brandOf[i]);

            System.out.println("| " + pad(String.valueOf(shown), 2) + " | "
                    + pad(names.get(i), nameWidth) + " | "
                    + pad(categoryLabels.get(categoryOf[i]), categoryWidth) + " | "
                    + pad(brandText, brandWidth) + " | "
                    + pad(scoreList.get(i) < 0 ? "-" : String.valueOf(scoreList.get(i)), scoreWidth) + " |");
        }

        if (shown == 0) {
            System.out.println("| No products selected.");
        }

        System.out.println(border);
        System.out.println();

        /*
         * DISPLAY: why were the best-ranked products left out?
         *
         * If a skipped product had room in its category, its brand and
         * the global limit, a path would still exist and the flow would
         * not be maximal. So every skipped product is blocked by one of
         * the three limits.
         */
        System.out.println("TOP CANDIDATES NOT SELECTED");

        int explained = 0;

        for (int i = 0; i < productCount && explained < 5; i++) {

            if (selected[i]) {
                continue;
            }

            String reason;

            if (MAX_PER_CATEGORY < MAX_RECOMMENDATIONS
                    && pickedPerCategory[categoryOf[i]] >= MAX_PER_CATEGORY) {
                reason = "category limit reached ("
                        + categoryLabels.get(categoryOf[i]) + ")";
            } else if (MAX_PER_BRAND < MAX_RECOMMENDATIONS
                    && pickedPerBrand[brandOf[i]] >= MAX_PER_BRAND) {
                reason = "brand limit reached ("
                        + brandLabels.get(brandOf[i]) + ")";
            } else {
                reason = "all " + MAX_RECOMMENDATIONS
                        + " recommendation slots already used";
            }

            String scoreNote = scoreList.get(i) < 0 ? "" : "  (score " + scoreList.get(i) + ")";

            // not truncated: this list is not limited to the table's column width
            System.out.println("  - " + pad(names.get(i), Math.max(nameWidth, names.get(i).length()))
                    + "  " + reason + scoreNote);

            explained++;
        }

        if (explained == 0) {
            System.out.println("  (every candidate was selected)");
        }

        System.out.println();

        /*
         * DISPLAY: constraint check, computed from the selected set
         * (not printed blindly: a violated limit would show [FAIL]).
         */
        ArrayList<String> checkLabels = new ArrayList<>();
        ArrayList<Integer> checkUsed = new ArrayList<>();
        ArrayList<Integer> checkAllowed = new ArrayList<>();

        checkLabels.add("Total recommendations");
        checkUsed.add(selectedCount);
        checkAllowed.add(MAX_RECOMMENDATIONS);

        for (int c = 0; c < categoryCount; c++) {

            if (pickedPerCategory[c] == 0) {
                continue;
            }

            checkLabels.add("Category: " + categoryLabels.get(c));
            checkUsed.add(pickedPerCategory[c]);
            checkAllowed.add(MAX_PER_CATEGORY);
        }

        for (int b = 0; b < brandCount; b++) {

            if (pickedPerBrand[b] == 0 || brandLabels.get(b).equals("Unknown")) {
                continue;
            }

            checkLabels.add("Brand: " + brandLabels.get(b));
            checkUsed.add(pickedPerBrand[b]);
            checkAllowed.add(MAX_PER_BRAND);
        }

        int labelWidth = 5;

        for (String label : checkLabels) {
            labelWidth = Math.max(labelWidth, label.length());
        }

        System.out.println("CONSTRAINT CHECK");
        System.out.println("  " + pad("Limit", labelWidth) + "  Used/Max  Status");
        System.out.println("  " + dashes(labelWidth) + "  --------  ------");

        boolean allSatisfied = true;

        for (int k = 0; k < checkLabels.size(); k++) {

            int used = checkUsed.get(k);
            int allowed = checkAllowed.get(k);
            boolean ok = used <= allowed;

            if (!ok) {
                allSatisfied = false;
            }

            System.out.println("  " + pad(checkLabels.get(k), labelWidth) + "  "
                    + pad(used + " / " + allowed, 8) + "  "
                    + (ok ? "OK" : "FAIL"));
        }

        System.out.println();
        System.out.println(allSatisfied
                ? "All constraints satisfied."
                : "Warning: a constraint was violated.");
        System.out.println("Final Recommendation Count : " + selectedCount);

        if (selectedCount != maximumFlow) {
            System.out.println(
                    "Warning: selected product count differs from calculated flow."
            );
        }

        System.out.println("========================================");
    }

    /**
     * Older version: category limit + global limit only (no brand data).
     * Kept so existing calls still compile.
     */
    public static void runRecommendation(
            String query,
            String[] productNames,
            String[] categories) {

        runRecommendation(query, productNames, categories, null, null);
    }

    /**
     * Version without scores (kept so older calls still compile).
     */
    public static void runRecommendation(
            String query,
            String[] productNames,
            String[] categories,
            String[] brands) {

        runRecommendation(query, productNames, categories, brands, null);
    }

    /**
     * Builds the flow network and runs Edmonds-Karp.
     *
     * Products marked in "removed" are left out of the network.
     * If productEdgeOut is not null it receives the product edges.
     *
     * SOURCE -> GLOBAL -> CATEGORY -> (product edge) -> BRAND -> SINK
     */
    private static int buildAndRun(
            int categoryCount,
            int brandCount,
            int[] categoryOf,
            int[] brandOf,
            int[] categorySize,
            int[] brandSize,
            boolean[] removed,
            Edge[] productEdgeOut,
            int maxRecommendations,
            int maxPerCategory,
            int maxPerBrand,
            String[] nodeLabels,
            String[] productLabels) {

        int source = 0;
        int globalNode = 1;
        int categoryStart = 2;
        int brandStart = categoryStart + categoryCount;
        int sink = brandStart + brandCount;

        MaxFlow flow = new MaxFlow(sink + 1);

        if (nodeLabels != null) {
            flow.nodeLabels = nodeLabels;
            flow.edgeLabels = new IdentityHashMap<>();
        }

        // SOURCE -> GLOBAL : total number of recommendations
        flow.addEdge(source, globalNode, maxRecommendations);

        // GLOBAL -> CATEGORY : products allowed from each category
        for (int c = 0; c < categoryCount; c++) {
            flow.addEdge(
                    globalNode,
                    categoryStart + c,
                    Math.min(maxPerCategory, categorySize[c])
            );
        }

        // BRAND -> SINK : products allowed from each brand
        for (int b = 0; b < brandCount; b++) {
            flow.addEdge(
                    brandStart + b,
                    sink,
                    Math.min(maxPerBrand, brandSize[b])
            );
        }

        // CATEGORY -> BRAND : one edge per product, capacity 1
        // (capacity 1 means a product can be picked at most once)
        for (int i = 0; i < categoryOf.length; i++) {

            if (removed[i]) {
                continue;
            }

            int from = categoryStart + categoryOf[i];
            int position = flow.graph[from].size();

            flow.addEdge(from, brandStart + brandOf[i], 1);

            if (productEdgeOut != null) {
                productEdgeOut[i] = flow.graph[from].get(position);
            }

            if (nodeLabels != null) {
                flow.edgeLabels.put(flow.graph[from].get(position), productLabels[i]);
            }
        }

        return flow.calculateMaxFlow(source, sink);
    }

    /** Pads (or shortens with "...") text to exactly the given width. */
    private static String pad(String text, int width) {

        if (text.length() > width) {
            text = width > 3 ? text.substring(0, width - 3) + "..." : text.substring(0, width);
        }

        StringBuilder sb = new StringBuilder(text);

        while (sb.length() < width) {
            sb.append(' ');
        }

        return sb.toString();
    }

    /** A line of dashes of the given length. */
    private static String dashes(int length) {

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++) {
            sb.append('-');
        }

        return sb.toString();
    }

    /**
     * Returns arr[index] trimmed, or the fallback if it is missing or blank.
     */
    private static String cleanValue(String[] arr, int index, String fallback) {

        if (arr == null
                || index >= arr.length
                || arr[index] == null
                || arr[index].trim().isEmpty()) {

            return fallback;
        }

        return arr[index].trim();
    }

    /**
     * Demo method.
     */
    public static void runDemo() {
        runDemo("demo recommendation");
    }

    /**
     * Edmonds-Karp demonstration.
     */
    public static void runDemo(String query) {

        System.out.println();
        System.out.println("========================================");
        System.out.println("        MAX FLOW ALGORITHM TEST");
        System.out.println("========================================");

        System.out.println(
                "Query     : " + query
        );

        System.out.println(
                "Algorithm : Edmonds-Karp"
        );

        System.out.println();

        /*
         * Demo network:
         *
         * SOURCE
         *   |
         *   +-- Category A [2]
         *   |      +-- Product A1
         *   |      +-- Product A2
         *   |
         *   +-- Category B [1]
         *          +-- Product B1
         *                   |
         *                   +-- Recommendation Pool
         *                            |
         *                            +-- SINK [3]
         *
         * Maximum flow = 3
         */

        int source = 0;

        int categoryA = 1;
        int categoryB = 2;

        int productA1 = 3;
        int productA2 = 4;
        int productB1 = 5;

        int pool = 6;
        int sink = 7;

        MaxFlow flow =
                new MaxFlow(8);

        // SOURCE -> CATEGORY
        flow.addEdge(
                source,
                categoryA,
                2
        );

        flow.addEdge(
                source,
                categoryB,
                1
        );

        // CATEGORY -> PRODUCT
        flow.addEdge(
                categoryA,
                productA1,
                1
        );

        flow.addEdge(
                categoryA,
                productA2,
                1
        );

        flow.addEdge(
                categoryB,
                productB1,
                1
        );

        // PRODUCT -> POOL
        flow.addEdge(
                productA1,
                pool,
                1
        );

        flow.addEdge(
                productA2,
                pool,
                1
        );

        flow.addEdge(
                productB1,
                pool,
                1
        );

        // POOL -> SINK
        flow.addEdge(
                pool,
                sink,
                3
        );

        System.out.println("Flow Network:");

        System.out.println("SOURCE");

        System.out.println(
                "  |--[2] Category A"
        );

        System.out.println(
                "  |      |--[1] Product A1"
        );

        System.out.println(
                "  |      |--[1] Product A2"
        );

        System.out.println(
                "  |"
        );

        System.out.println(
                "  |--[1] Category B"
        );

        System.out.println(
                "         |--[1] Product B1"
        );

        System.out.println(
                "                 |"
        );

        System.out.println(
                "                 +--[1] "
                        + "Recommendation Pool"
        );

        System.out.println(
                "                         |"
        );

        System.out.println(
                "                         +--[3] SINK"
        );

        int maximumFlow =
                flow.calculateMaxFlow(
                        source,
                        sink
                );

        System.out.println();

        System.out.println(
                "----------------------------------------"
        );

        System.out.println(
                "Maximum Flow : "
                        + maximumFlow
        );

        System.out.println(
                "Complexity   : O(VE^2)"
        );

        System.out.println(
                "----------------------------------------"
        );

        System.out.println(
                "Interpretation:"
        );

        System.out.println(
                "BFS repeatedly finds an augmenting"
        );

        System.out.println(
                "path in the residual graph."
        );

        System.out.println(
                "The bottleneck capacity determines"
        );

        System.out.println(
                "how much additional flow can be sent."
        );

        System.out.println(
                "========================================"
        );
    }
}