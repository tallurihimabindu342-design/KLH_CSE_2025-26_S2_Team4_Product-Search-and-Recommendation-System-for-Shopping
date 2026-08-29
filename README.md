# Product Search and Recommendation System

**Course:** Data Structures and Algorithms – 3  
**Course Code:** 25CS2103E  
**Team:** 4  
**Team Member:** Talluri HimaBindu Sree – 2520030484  
**Supervisor:** Dr. S. Vinay Kumar, Associate Professor, Department of Computer Science and Engineering  
**Current Phase:** Pattern/String Matching – Review 2  

---

## Student Details

| S. No. | Student Name | Roll Number |
|-------:|--------------|-------------|
| 1 | Talluri HimaBindu Sree | 2520030484 |

---

## Abstract

The **Product Search and Recommendation System** is a Data Structures and Algorithms-based project designed to provide efficient search and retrieval of products from a structured e-commerce product corpus.

The system uses **Knuth-Morris-Pratt (KMP)** and **Rabin-Karp** algorithms for pattern matching. KMP uses the **LPS array** to avoid unnecessary comparisons, while Rabin-Karp uses a **rolling hash** for efficient matching.

The system also uses **Levenshtein Edit Distance** for spelling-error recovery and relevance-based ranking to return suitable products.

---

## Objectives

- Implement efficient string-matching algorithms.
- Search products from a structured corpus.
- Implement KMP and Rabin-Karp pattern matching.
- Compare algorithm performance.
- Handle spelling errors using Levenshtein Edit Distance.
- Rank and display relevant products.
- Demonstrate practical applications of DSA in e-commerce search.

---

## Algorithms and Data Structures

- **KMP** – Pattern matching using LPS array
- **Rabin-Karp** – Pattern matching using rolling hash
- **Levenshtein Edit Distance** – Spelling-error recovery
- **Trie** – Prefix searching/autocomplete
- Product corpus loading and processing
- Relevance-based result ranking
- Algorithm performance benchmarking

---

## Project Flow

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
      +-------------------+
      |                   |
      v                   v
     KMP             Rabin-Karp
      |                   |
      +---------+---------+
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
                v
        Product Details
```

---

## Product Corpus

The system currently contains **20 product text files**.

Each product document contains:

- Product name
- Brand
- Category
- Price
- Rating
- Overview
- Key features
- Specifications
- Best use cases
- Searchable attributes

# Getting Started

## Prerequisites

Install:

- **Java JDK**
- **Git**

Check installation:

```bash
java -version
javac -version
git --version
```

---

## Clone the Repository

Copy the repository URL from GitHub using **Code → HTTPS**.

Then run:

```bash
git clone <YOUR_GITHUB_REPOSITORY_URL>
cd Product_Search_Recommendation_System
```

---

## Compile

From the project root:

```powershell
javac -encoding UTF-8 -d out src\*.java
```

---

## Run

```powershell
java -cp out Main
```

The application will display:

```text
========================================
       PRODUCT SEARCH SYSTEM
========================================
Corpus files loaded: 20

Automatic algorithm selection enabled.
Type /test to benchmark KMP and Rabin-Karp.
Type exit to close the system.

Enter search query:
```

---

# How to Use

### 1. Search for a Product

Enter a product name:

```text
Samsung Galaxy S24 Ultra
```

The system displays the relevant products with:

- Product name
- Price
- Rating
- Brand
- Category
- Short description

### 2. Search by Feature

Example queries:

```text
wireless charging
```

```text
120hz display
```

```text
phone with s pen
```

```text
200mp camera
```

### 3. Spelling Errors

The system can recover from simple spelling mistakes using **Levenshtein Edit Distance**.

Example:

```text
samsng
```

can match:

```text
samsung
```

> KMP and Rabin-Karp perform pattern matching; spelling correction is handled separately using Levenshtein Edit Distance.

### 4. View Product Details

After search results appear, enter the product number:

```text
Enter product number for details or press ENTER for another search: 1
```

The system displays detailed product information and specifications.

---

# Algorithm Testing

Normal searches use **automatic algorithm selection**.

To manually test the algorithms, enter:

```text
/test
```

Options:

```text
1. Test KMP
2. Test Rabin-Karp
3. Compare KMP vs Rabin-Karp
4. Exit Test Mode
```

For example, selecting **3** compares both algorithms using the same query and displays their measured execution times.

---

## Time Complexity

| Algorithm | Average | Worst Case |
|-----------|---------|------------|
| **KMP** | O(n + m) | O(n + m) |
| **Rabin-Karp** | O(n + m) | O(nm) |
| **Levenshtein Distance** | O(mn) | O(mn) |

Where:

- `n` = text length
- `m` = pattern/string length

---

# Project Structure

```text
Product_Search_Recommendation_System/
│
├── corpus/
├── src/
│   ├── Main.java
│   ├── SearchEngine.java
│   ├── KMP.java
│   ├── RabinKarp.java
│   ├── Levenshtein.java
│   ├── Trie.java
│   ├── ProductDocument.java
│   ├── CorpusLoader.java
│   └── ...
│
├── out/
└── README.md
```

---

# Troubleshooting

### Corpus Not Found

Make sure `corpus/` is in the project root:

```text
Product_Search_Recommendation_System/
├── corpus/
├── src/
└── out/
```

### Compilation Error

Run:

```powershell
javac -encoding UTF-8 -d out src\*.java
```

### No Results

Try a product name or searchable feature such as:

```text
Samsung Galaxy S24 Ultra
```

```text
wireless charging
```

```text
120hz display
```

---

# Quick Start

```bash
git clone <YOUR_GITHUB_REPOSITORY_URL>
cd Product_Search_Recommendation_System
javac -encoding UTF-8 -d out src\*.java
java -cp out Main
```

Then enter a search query.

For algorithm testing:

```text
/test
```

To exit:

```text
exit
```

---

# Future Enhancements

- Advanced product recommendations
- Improved relevance ranking
- Category and price filtering
- Enhanced autocomplete
- Larger product corpus
- Additional DSA algorithms
- Advanced performance analysis

---

# Conclusion

The **Product Search and Recommendation System** demonstrates the practical application of Data Structures and Algorithms in an e-commerce search environment.

By combining **KMP, Rabin-Karp, Levenshtein Edit Distance, Trie-based searching, corpus processing, and relevance ranking**, the system provides efficient product search while allowing algorithm performance to be analyzed and compared.

---

## Project Status

**Current Phase:** Pattern/String Matching – Review 2

Current implementation includes:

- Product corpus loading
- KMP pattern matching
- Rabin-Karp pattern matching
- Levenshtein Edit Distance
- Automatic algorithm selection
- Product relevance ranking
- Product detail retrieval
- Algorithm benchmarking
