# Product Search and Recommendation System

**Course:** Data Structures and Algorithms – 3  
**Course Code:** 25CS2103E  
**Team:** 4  
**Team Member:** Talluri HimaBindu Sree  
**Roll Number:** 2520030484  
**Supervisor:** Dr. S. Vinay Kumar, Associate Professor, Department of Computer Science and Engineering

---

## 1. Abstract

The Product Search and Recommendation System is a Java-based application developed to demonstrate the practical application of Data Structures and Algorithms in an e-commerce environment. The system searches product information stored in a corpus of 300 text files and returns relevant results based on user queries.

The system implements the Knuth–Morris–Pratt (KMP) and Rabin–Karp algorithms for pattern matching. A Trie data structure supports prefix-based searching, while Levenshtein Edit Distance helps identify spelling mistakes in search queries. The recommendation module uses the Edmonds–Karp maximum-flow algorithm to select products while satisfying defined recommendation constraints.

The application also provides performance benchmarking to compare KMP and Rabin–Karp. These features demonstrate the practical use of string matching, dynamic programming, tree-based searching, graph algorithms, and corpus processing.

## 2. Problem Statement

Searching for relevant products in a collection of product documents requires efficient pattern matching, handling of spelling errors, and suitable result selection.

The objective is to develop a Java-based system that searches product information, supports prefix queries, assists with spelling mistakes, compares string-matching algorithms, and generates recommendations using defined constraints.

## 3. Objectives

- Implement KMP and Rabin–Karp pattern-matching algorithms.
- Search products from a structured text corpus.
- Support prefix searching using a Trie.
- Handle spelling mistakes using Levenshtein Edit Distance.
- Rank and display relevant search results.
- Generate constrained product recommendations using maximum flow.
- Compare the performance of KMP and Rabin–Karp.
- Demonstrate practical applications of DSA in product search.

## 4. Technologies Used

- **Programming language:** Java
- **Data source:** Text-based product corpus
- **Data structures:** Trie and graph-based flow network
- **Algorithms:** KMP, Rabin–Karp, Levenshtein Edit Distance, Edmonds–Karp
- **Version control:** Git and GitHub

## 5. Algorithms and Data Structures

### 5.1 Knuth–Morris–Pratt (KMP)

KMP searches for a pattern using a Longest Prefix Suffix (LPS) array. The array helps avoid repeating unnecessary comparisons after a mismatch.

**Time complexity:** O(n + m)

### 5.2 Rabin–Karp

Rabin–Karp uses hashing and a rolling hash technique to locate pattern matches in text.

**Expected time complexity:** O(n + m), under suitable hashing assumptions.  
**Worst-case time complexity:** O(nm).

### 5.3 Levenshtein Edit Distance

Levenshtein Edit Distance calculates the minimum number of insertions, deletions, and substitutions needed to transform one string into another. It helps identify likely corrections for misspelled queries.

**Time complexity:** O(nm) for the standard dynamic-programming implementation.

### 5.4 Trie

A Trie stores searchable terms in a tree-like structure. It supports prefix-based searching and helps retrieve terms beginning with a given prefix.

### 5.5 Edmonds–Karp Maximum Flow

Edmonds–Karp is a graph algorithm that calculates maximum flow using breadth-first search (BFS) to find augmenting paths. In this project, it is used by the recommendation module to select products while satisfying defined recommendation constraints.

**Time complexity:** O(VE²), where V is the number of vertices and E is the number of edges.

### 5.6 Corpus Processing and Relevance Ranking

The corpus loader reads product documents, the search engine processes user queries, and relevance ranking helps present suitable results.

## 6. System Workflow

```text
              Product Corpus
                    |
                    v
               Corpus Loader
                    |
                    v
              Product Records
                    |
                    v
                User Query
                    |
          +---------+----------+
          |         |          |
          v         v          v
         KMP    Rabin-Karp   Trie Search
          |         |          |
          +---------+----------+
                    |
                    v
             Matching Products
                    |
                    v
             Relevance Ranking
                    |
                    v
              Search Results
                    |
          +---------+----------+
          |                    |
          v                    v
    Product Details      Recommendations
                               |
                               v
                     Edmonds–Karp Max Flow
```

## 7. Product Corpus

The project uses **300 product text files** stored in the `corpus/` directory.

The corpus provides the product information used by the application. The corpus loader reads the files at runtime, and the application displays the number of files loaded.

The `corpus/` directory must remain in the project root for the application and tests to locate the product documents.

## 8. Getting Started

### Prerequisites

Install:

- Java Development Kit (JDK)
- Git, if you plan to clone the repository

Verify the installations:

```powershell
java -version
javac -version
git --version
```

### Clone the Repository

Copy the HTTPS repository URL from GitHub using **Code → HTTPS**.

```powershell
git clone <YOUR_GITHUB_REPOSITORY_URL>
cd Product_Search_Recommendation_System
```

Replace the placeholder with your actual GitHub repository URL.

### Compile the Project

Run this command from the project root:

```powershell
javac -encoding UTF-8 -d out src/*.java
```

### Run the Application

```powershell
java -cp out Main
```

The application loads the product corpus and displays the number of product files loaded before accepting search queries.

## 9. How to Use the Application

### 9.1 Product Search

Enter a product name or keyword to retrieve matching products.

Example:

```text
phone
```

The system displays relevant matching products.

### 9.2 Search by Feature

Example queries include:

```text
wireless charging
120hz display
samsung
apple
```

The results depend on the product information available in the corpus.

### 9.3 Spelling-Error Recovery

Enter a query containing a simple spelling mistake.

Example:

```text
samsng
```

The system can identify the likely correction:

```text
samsung
```

Spelling correction uses Levenshtein Edit Distance and is separate from KMP and Rabin–Karp pattern matching.

### 9.4 Prefix Search

Enter:

```text
/trie
```

Follow the prompt to enter a prefix.

Example:

```text
app
```

The Trie returns matching indexed terms. If no terms match the prefix, no matching terms are displayed.

### 9.5 Performance Benchmarking

Enter:

```text
/time
```

Provide a search query when prompted. The application compares KMP and Rabin–Karp using the same query and reports their match counts and measured execution times.

Benchmark timings may vary depending on the computer and runtime conditions.

### 9.6 Product Recommendations

Search for a product or keyword to explore the recommendation functionality. The recommendation module uses Edmonds–Karp maximum flow to select products while respecting the implemented constraints.

### 9.7 Exit

Enter:

```text
exit
```

to terminate the application.

## 10. Algorithm Complexity

Let n represent the text length and m represent the pattern length.

| Algorithm | Average / Expected Time | Worst-Case Time |
|---|---|---|
| KMP | O(n + m) | O(n + m) |
| Rabin–Karp | O(n + m), under suitable hashing assumptions | O(nm) |
| Levenshtein Edit Distance | O(nm) | O(nm) |
| Edmonds–Karp | O(VE²) | O(VE²) |

The complexities for Levenshtein Edit Distance refer to the standard dynamic-programming implementation. Actual execution time also depends on the input size and implementation details.

## 11. Testing

The project includes test programs for validating its algorithms and functionality.

### Compile

```powershell
javac -encoding UTF-8 -d out src/*.java
```

### Run the Full Test Suite

```powershell
java -cp out TestRunner
```

### Run Trie Tests

```powershell
java -cp out TrieTest
```

Run these commands from the project root. Ensure the corpus is available when required by the tests.

## 12. Project Structure

```text
Product_Search_Recommendation_System/
│
├── corpus/
│   └── 300 product text files
│
├── src/
│   ├── Main.java
│   ├── SearchEngine.java
│   ├── CorpusLoader.java
│   ├── ProductDocument.java
│   ├── PatternMatching.java
│   ├── Levenshtein.java
│   ├── LevenshteinSearch.java
│   ├── Trie.java
│   ├── TrieSearch.java
│   ├── MaxFlow.java
│   ├── TestRunner.java
│   ├── TrieTest.java
│   └── ...
│
├── README.md
├── ABSTRACT.md
└── Project_Report.pdf
```

This is an illustrative structure. Retain the actual filenames and test files in your repository. Add `ABSTRACT.md` and `Project_Report.pdf` after creating those documents.

## 13. Troubleshooting

### Corpus Directory Not Found

Ensure that `corpus/` exists in the project root and contains the product text files.

```text
Product_Search_Recommendation_System/
├── corpus/
├── src/
└── README.md
```

### Compilation Error

Run the compilation command from the project root:

```powershell
javac -encoding UTF-8 -d out src/*.java
```

### No Search Results

Try a simpler keyword or a product term known to exist in the corpus. Check the spelling of your query.

### Benchmark Times Differ

Execution times vary with system load, runtime optimization, and input data. Compare both algorithms using the same query and corpus.

## 14. Future Enhancements

- Category and price filtering.
- Improved relevance ranking.
- Enhanced autocomplete.
- Expansion of the product corpus.
- Additional performance analysis.
- Further refinement of product recommendations.

## 15. Conclusion

The Product Search and Recommendation System demonstrates the practical application of Data Structures and Algorithms in an e-commerce search environment.

By combining KMP, Rabin–Karp, Levenshtein Edit Distance, Trie-based prefix searching, relevance ranking, and Edmonds–Karp maximum flow, the project supports product search, spelling-error recovery, performance comparison, and constrained product recommendations through a Java-based console application.

## 16. Project Status

**Status:** Final implementation and demonstration preparation.

Implemented components include:

- Product corpus loading and processing.
- KMP pattern matching.
- Rabin–Karp pattern matching.
- Levenshtein Edit Distance.
- Trie-based prefix searching.
- Product relevance ranking.
- Edmonds–Karp maximum-flow-based recommendations.
- Algorithm performance benchmarking.
- Automated test programs.
