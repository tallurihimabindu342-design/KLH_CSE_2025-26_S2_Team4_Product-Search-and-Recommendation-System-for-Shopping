# Product Search and Recommendation System

**Course:** Data Structures and Algorithms – 3  
**Course Code:** 25CS2103E  
**Team:** 4  
**Team Member:** Talluri HimaBindu Sree – 2520030484  
**Supervisor:** Dr. S. Vinay Kumar, Associate Professor, Department of Computer Science and Engineering  
**Current Phase:** Pattern/String Matching Implementation – Review 2

---

## Student Details

| S. No. | Student Name | Roll Number |
|-------:|--------------|-------------|
| 1 | Talluri HimaBindu Sree | 2520030484 |

---

## Abstract

The Product Search and Recommendation System is a data-structures-and-algorithms-based project designed to provide efficient search and retrieval of products from a structured e-commerce product corpus.

The system processes a collection of product documents and applies advanced string-matching algorithms to support efficient pattern and keyword searching across product information such as product names, features, specifications, prices, ratings, and other searchable attributes.

For pattern matching, the system implements the Knuth-Morris-Pratt (KMP) and Rabin-Karp algorithms. KMP uses the Longest Proper Prefix which is also Suffix (LPS) array to avoid unnecessary comparisons, while Rabin-Karp uses a rolling hash to identify candidate matches efficiently.

The system also incorporates Levenshtein Edit Distance for spelling-error recovery and relevance-based ranking to return the most relevant products. The project is designed to demonstrate the practical application of advanced string algorithms and data structures in a real-world e-commerce search scenario.

---

## Objectives

1. Implement efficient pattern and string-matching algorithms.
2. Search product information stored in the project's product corpus.
3. Implement Knuth-Morris-Pratt (KMP) pattern matching.
4. Implement Rabin-Karp pattern matching using rolling hash.
5. Compare KMP and Rabin-Karp for the same search queries.
6. Verify consistency of matching results produced by different algorithms.
7. Measure and compare algorithm execution performance.
8. Handle spelling errors using Levenshtein Edit Distance.
9. Rank products according to their relevance to the user's search query.
10. Provide concise product search results and detailed product information.
11. Demonstrate practical applications of Data Structures and Algorithms in an e-commerce search scenario.

---

## Algorithms and Data Structures

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
       Algorithm Comparison
                 |
                 v
       Performance Analysis

       
## Product Corpus

The system uses a structured product corpus containing **20 product text files**.

Each product document contains information such as:

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

└── product_p020_samsung_t9_portable_ssd_2tb.txt
