import java.util.ArrayList;
import java.util.Arrays;

/**
 * Small self-checking Trie demo (prints each result and PASS/FAIL).
 * The full Trie tests, including randomised ones, are in TestRunner.
 */
public class TrieTest {

    private static int failures = 0;

    private static void expect(String label, Object actual, Object expected) {

        boolean ok = actual.equals(expected);

        if (!ok) {
            failures++;
        }

        System.out.println((ok ? "PASS  " : "FAIL  ") + label
                + " -> " + actual
                + (ok ? "" : "   (expected " + expected + ")"));
    }

    public static void main(String[] args) {

        Trie trie = new Trie();

        trie.insert("samsung");
        trie.insert("samsonite");
        trie.insert("sony");
        trie.insert("gaming");
        trie.insert("google");

        expect("Prefix 'sam'", trie.getWordsWithPrefix("sam"),
                new ArrayList<>(Arrays.asList("samsonite", "samsung")));
        expect("Prefix 'so'", trie.getWordsWithPrefix("so"),
                new ArrayList<>(Arrays.asList("sony")));
        expect("Prefix 'ga'", trie.getWordsWithPrefix("ga"),
                new ArrayList<>(Arrays.asList("gaming")));
        expect("Prefix 'goo'", trie.getWordsWithPrefix("goo"),
                new ArrayList<>(Arrays.asList("google")));
        expect("Prefix 'xyz'", trie.getWordsWithPrefix("xyz"),
                new ArrayList<String>());

        System.out.println();

        expect("search('sony')", trie.search("sony"), true);
        expect("search('son')", trie.search("son"), false);
        expect("startsWith('son')", trie.startsWith("son"), true);

        System.out.println();
        System.out.println(failures == 0 ? "TrieTest: ALL PASSED" : "TrieTest: " + failures + " FAILED");

        System.exit(failures == 0 ? 0 : 1);
    }
}