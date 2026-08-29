import java.util.Scanner;

public class Main {

    public static void main(String[] args) {

        try {
            CorpusLoader loader = new CorpusLoader("corpus");
            ProductDocument[] corpus = loader.load();
            SearchEngine searchEngine = new SearchEngine(corpus);
            Scanner scanner = new Scanner(System.in);

            System.out.println();
            System.out.println("========================================");
            System.out.println("       PRODUCT SEARCH SYSTEM");
            System.out.println("========================================");
            System.out.println("Corpus files loaded: " + corpus.length);
            System.out.println();
            System.out.println("Automatic algorithm selection enabled.");
            System.out.println("Type /test to benchmark KMP and Rabin-Karp.");
            System.out.println("Type exit to close the system.");

            /*
             * pendingQuery lets the user type the next search directly
             * at the product-number prompt. If the input is not a
             * number, SearchEngine returns it here and it is processed
             * as a new query/command.
             */
            String pendingQuery = "";

            while (true) {

                String query;

                if (pendingQuery.isEmpty()) {
                    System.out.println();
                    System.out.print("Enter search query: ");
                    query = scanner.nextLine().trim();
                } else {
                    query = pendingQuery;
                    pendingQuery = "";
                }

                if (query.equalsIgnoreCase("exit")) {
                    System.out.println();
                    System.out.println(
                            "Thank you for using the Product Search System."
                    );
                    break;
                }

                if (query.equalsIgnoreCase("/test")) {
                    searchEngine.showTestMenu(scanner);
                    continue;
                }

                pendingQuery = searchEngine.search(query, scanner);
            }

            scanner.close();

        } catch (Exception e) {
            System.out.println();
            System.out.println("Error: " + e.getMessage());
        }
    }
}