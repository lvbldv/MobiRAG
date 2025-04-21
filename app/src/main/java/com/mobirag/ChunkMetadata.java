package com.mobirag;

import java.util.List;

public class ChunkMetadata {
    private String pdfFilePath; // Path to the original PDF
    private int pageNumber;     // Page number in the PDF
    private int startOffset;    // Start offset of the chunk in characters
    private int endOffset;      // End offset of the chunk in characters
    private List<String> keywords;

    public int getEndOffset() {
        return endOffset;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public String getPdfFilePath() {
        return pdfFilePath;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    public ChunkMetadata(String pdfFilePath, int pageNumber, int startOffset, int endOffset) {
        this.pdfFilePath = pdfFilePath;
        this.pageNumber = pageNumber;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.keywords = null;
    }

}

