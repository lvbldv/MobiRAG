package com.mobirag;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class KeywordExtractor {

    // You can customize this stopword list further
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "i", "me", "my", "myself", "we", "our", "ours", "ourselves",
            "you", "your", "yours", "yourself", "yourselves", "he", "him", "his",
            "himself", "she", "her", "hers", "herself", "it", "its", "itself",
            "they", "them", "their", "theirs", "themselves", "what", "which",
            "who", "whom", "this", "that", "these", "those", "am", "is", "are",
            "was", "were", "be", "been", "being", "have", "has", "had", "having",
            "do", "does", "did", "doing", "a", "an", "the", "and", "but", "if",
            "or", "because", "as", "until", "while", "of", "at", "by", "for",
            "with", "about", "against", "between", "into", "through", "during",
            "before", "after", "above", "below", "to", "from", "up", "down", "in",
            "out", "on", "off", "over", "under", "again", "further", "then",
            "once", "here", "there", "when", "where", "why", "how", "all", "any",
            "both", "each", "few", "more", "most", "other", "some", "such", "no",
            "nor", "not", "only", "own", "same", "so", "than", "too", "very", "s",
            "t", "can", "will", "just", "don", "should", "now", "hello", "hi"
    ));

    public static Set<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase().split("\\W+"))
                .filter(token -> token.length() > 2 && !token.matches("\\d+"))
                .filter(token -> !STOPWORDS.contains(token))
                .collect(Collectors.toSet());
    }

    public static List<String> tokenizeWithDuplicates(String text) {
        return Arrays.stream(text.toLowerCase().split("\\W+"))
                .filter(token -> token.length() > 2 && !token.matches("\\d+"))
                .filter(token -> !STOPWORDS.contains(token))
                .collect(Collectors.toList());
    }
}