import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;

public class CorpusLoader {
    private final String directoryPath;

    public CorpusLoader(String directoryPath) {
        this.directoryPath = directoryPath;
    }

    public ProductDocument[] load() throws IOException {
        File directory = new File(directoryPath);
        if (!directory.isDirectory()) {
            throw new IOException("Corpus directory not found: " + directory.getAbsolutePath());
        }

        File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".txt"));
        if (files == null) throw new IOException("Unable to read corpus directory.");

        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        ProductDocument[] documents = new ProductDocument[files.length];

        for (int i = 0; i < files.length; i++) {
            documents[i] = new ProductDocument(files[i].getName(), readFile(files[i]));
        }
        return documents;
    }

    private String readFile(File file) throws IOException {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append('\n');
            }
        }
        return text.toString();
    }
}
