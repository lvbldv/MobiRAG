package com.mobirag;

import java.util.*;
import java.util.stream.Collectors;

public class TfIdfKeywordExtractor {

    private final Map<String, Double> idfScores;
    private final int totalDocs;

    public TfIdfKeywordExtractor(Map<String, Integer> dfMap, int totalDocs) {
        this.totalDocs = totalDocs;
        this.idfScores = new HashMap<>();
        for (Map.Entry<String, Integer> entry : dfMap.entrySet()) {
            double idf = Math.log((double) totalDocs / (1 + entry.getValue()));
            idfScores.put(entry.getKey(), idf);
        }
    }

//    private void buildIdfScores(List<String> documents) {
//        Map<String, Integer> docFreq = new HashMap<>();
//
//        for (String doc : documents) {
//            Set<String> uniqueTokens = KeywordExtractor.tokenize(doc);
//            for (String token : uniqueTokens) {
//                docFreq.put(token, docFreq.getOrDefault(token, 0) + 1);
//            }
//        }
//
//        for (Map.Entry<String, Integer> entry : docFreq.entrySet()) {
//            double idf = Math.log((double) totalDocs / (1 + entry.getValue()));
//            idfScores.put(entry.getKey(), idf);
//        }
//    }

    public List<String> getTopKeywords(String text, int topN) {
        Map<String, Double> tfidf = new HashMap<>();
        List<String> tokens = KeywordExtractor.tokenizeWithDuplicates(text);

        Map<String, Long> freqMap = tokens.stream()
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        for (String token : freqMap.keySet()) {
            double tf = freqMap.get(token) / (double) tokens.size();
            double idf = idfScores.getOrDefault(token, Math.log((double) totalDocs));
            tfidf.put(token, tf * idf);
        }

        return tfidf.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
