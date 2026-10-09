import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

/**
 * Prefix tree over lower-case words.
 *
 * insert(word)              O(L)
 * search / startsWith       O(L)
 * getWordsWithPrefix(p)     O(L + size of the matching subtree)
 * Space                     O(total characters stored) in the worst case.
 *
 * Children are kept in a TreeMap so completions are returned in
 * alphabetical order (a HashMap would return them in arbitrary order).
 */
public class Trie {

    private static class TrieNode {

        Map<Character, TrieNode> children;
        boolean isEndOfWord;

        TrieNode() {
            children = new TreeMap<>();   // sorted -> suggestions come out alphabetically
            isEndOfWord = false;
        }
    }

    private final TrieNode root;

    public Trie() {
        root = new TrieNode();
    }

    public void insert(String word) {

        if (word == null || word.trim().isEmpty()) {
            return;
        }

        word = word.toLowerCase().trim();

        TrieNode current = root;

        for (char ch : word.toCharArray()) {

            current.children.putIfAbsent(
                    ch,
                    new TrieNode()
            );

            current = current.children.get(ch);
        }

        current.isEndOfWord = true;
    }

    public boolean search(String word) {

        if (word == null || word.trim().isEmpty()) {
            return false;
        }

        TrieNode node = findNode(
                word.toLowerCase().trim()
        );

        return node != null && node.isEndOfWord;
    }

    public boolean startsWith(String prefix) {

        if (prefix == null || prefix.trim().isEmpty()) {
            return false;
        }

        return findNode(
                prefix.toLowerCase().trim()
        ) != null;
    }

    public ArrayList<String> getWordsWithPrefix(String prefix) {

        ArrayList<String> results =
                new ArrayList<>();

        if (prefix == null || prefix.trim().isEmpty()) {
            return results;
        }

        prefix = prefix.toLowerCase().trim();

        TrieNode node = findNode(prefix);

        if (node == null) {
            return results;
        }

        collectWords(
                node,
                new StringBuilder(prefix),
                results
        );

        return results;
    }

    private TrieNode findNode(String text) {

        TrieNode current = root;

        for (char ch : text.toCharArray()) {

            if (!current.children.containsKey(ch)) {
                return null;
            }

            current = current.children.get(ch);
        }

        return current;
    }

    private void collectWords(
            TrieNode node,
            StringBuilder current,
            ArrayList<String> results) {

        if (node.isEndOfWord) {
            results.add(current.toString());
        }

        for (Map.Entry<Character, TrieNode> entry
                : node.children.entrySet()) {

            current.append(entry.getKey());

            collectWords(
                    entry.getValue(),
                    current,
                    results
            );

            current.deleteCharAt(
                    current.length() - 1
            );
        }
    }
}