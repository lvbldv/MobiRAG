// ChatActivity.java
package com.mobirag;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import mobirag.R;

public class ChatActivity extends AppCompatActivity {

    EditText editQuery;
    Button btnAsk;
    ProgressBar progressBar;
    TextView tvLiveStatus;
    TextView tvEmptyHint;
    private static final String TAG = "SentenceEmbedding";

    SentenceEmbeddingWrapper sentenceEmbeddingWrapper;
    LlamaCppWrapper llamaCppWrapper;

    RecyclerView chatRecyclerView;
    ChatAdapter chatAdapter;
    List<ChatMessage> chatMessages = new ArrayList<>();
    LinearLayoutManager layoutManager;

    private Handler thinkingHandler = new Handler(Looper.getMainLooper());
    private Runnable thinkingRunnable;
    private int dotCount = 0;
    private boolean isThinking = false;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        PDFBoxResourceLoader.init(getApplicationContext());

        editQuery = findViewById(R.id.editQuery);
        btnAsk = findViewById(R.id.btnAsk);
        //tvChatAnswer = findViewById(R.id.tvChatAnswer);
        progressBar = findViewById(R.id.progressBarChat);
        tvLiveStatus = findViewById(R.id.tvLiveStatus);
        tvLiveStatus.setVisibility(View.GONE);

        chatRecyclerView = findViewById(R.id.chatRecyclerView);
        chatAdapter = new ChatAdapter(chatMessages);
        chatRecyclerView.setAdapter(chatAdapter);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        sentenceEmbeddingWrapper = new SentenceEmbeddingWrapper();
        llamaCppWrapper = new LlamaCppWrapper();

        tvEmptyHint = findViewById(R.id.tvEmptyHint);

        progressBar.setVisibility(View.VISIBLE);

        layoutManager = (LinearLayoutManager) chatRecyclerView.getLayoutManager();

        // Load ONNX model
        new Thread(() -> {
            try {
                File modelFile = new File(getFilesDir(), "all-minilm-l6-v2/model.onnx");
                File tokenizerFile = new File(getFilesDir(), "all-minilm-l6-v2/tokenizer.json");
                sentenceEmbeddingWrapper.initSync(modelFile.getAbsolutePath(),
                        getBytesFromFile(tokenizerFile),
                        true, "sentence_embedding", true);
                runOnUiThread(() -> Toast.makeText(this, "Embedding model loaded", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Embedding model failed to load", Toast.LENGTH_LONG).show());
                Log.e(TAG, "onCreate: "+ e.getMessage());
            }
        }).start();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {

            // Load LLM (Qwen)
            File modelPath = new File(getExternalFilesDir(null), "qwen2.5-0.5b-instruct-q4_k_m.gguf");
            llamaCppWrapper.loadModel(modelPath.getAbsolutePath());

            // heavy stuff
            runOnUiThread(() -> {
                progressBar.setVisibility(View.INVISIBLE);
                //tvChatAnswer.setText("Initialized Model");
                // UI update
            });
        });



        // Load PQ index
        File dir = new File(getExternalFilesDir(null), "faiss_data");
        String indexPath = dir.getAbsolutePath() + "/embeddings_pq.index";
        List<ChunkMetadata> metadataList = loadMetadataFromDisk();
        Map<Integer, ChunkMetadata> indexToMetadata = new HashMap<>();
        for (int i = 0; i < metadataList.size(); i++) {
            indexToMetadata.put(i, metadataList.get(i));
        }

        btnAsk.setOnClickListener(v -> processQuery(indexPath, indexToMetadata));
    }

    private void processQuery(String indexPath, Map<Integer, ChunkMetadata> indexToMetadata) {
        String query = editQuery.getText().toString().trim();

        if (query.isEmpty()) {
            Toast.makeText(this, "Please enter a query", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        //tvChatAnswer.setText("Thinking...");

        new Thread(() -> {
            try {
                final int[] aiIndexHolder = new int[1];
                Handler uiHandler = new Handler(Looper.getMainLooper());
                List<Runnable> pendingStatusRunnables = new ArrayList<>();

                runOnUiThread(() -> {
                    // Add user message

                    tvEmptyHint.setVisibility(View.GONE);

                    editQuery.setText("");
                    chatMessages.add(new ChatMessage(query, true));
                    chatAdapter.notifyItemInserted(chatMessages.size() - 1);

                    // Add placeholder AI response
                    chatMessages.add(new ChatMessage("Thinking", false));
                    chatAdapter.notifyItemInserted(chatMessages.size() - 1);

                    aiIndexHolder[0] = chatMessages.size() - 1;

                    //chatRecyclerView.scrollToPositionWithOffSet(aiIndexHolder[0]);
                    chatRecyclerView.post(() -> {
                        layoutManager.scrollToPositionWithOffset(chatMessages.size() - 1, Integer.MIN_VALUE);
                    });

                    isThinking= true;
                    thinkingRunnable = new Runnable() {
                        @Override
                        public void run() {
                            if (!isThinking) return;
                            dotCount = (dotCount + 1) % 4; // cycles from 0..3
                            String dots = new String(new char[dotCount]).replace("\0", ".");
                            chatMessages.get(aiIndexHolder[0]).text = "Thinking" + dots;
                            chatAdapter.notifyItemChanged(aiIndexHolder[0]);
                            thinkingHandler.postDelayed(this, 500); // repeat every 500ms
                        }
                    };
                    thinkingHandler.post(thinkingRunnable);
                });

                float[] queryEmbedding = sentenceEmbeddingWrapper.encodeSync(query);
                String resultJson = SentenceEmbeddingActivity.searchIndexNative(queryEmbedding, 5, indexPath);
                List<Integer> topIndices = new Gson().fromJson(resultJson, new TypeToken<List<Integer>>() {}.getType());

                Set<String> queryKeywords = KeywordExtractor.tokenize(query);
                Map<Integer, Double> keywordScores = new HashMap<>();
                double keywordThreshold = 0.2;
                for (Map.Entry<Integer, ChunkMetadata> entry : indexToMetadata.entrySet()) {
                    int index = entry.getKey();
                    ChunkMetadata meta = entry.getValue();
                    List<String> chunkKeywords = meta.getKeywords();

                    if (chunkKeywords == null || chunkKeywords.isEmpty()) continue;

                    long matchCount = queryKeywords.stream().filter(chunkKeywords::contains).count();
                    double score = matchCount / (double) chunkKeywords.size();
                    if (score >= keywordThreshold) keywordScores.put(index, score);
                }

                // --- Normalize semantic scores ---
//                Map<Integer, Double> semanticRawScores = new HashMap<>();
//                int maxSemanticScore = topIndices.size();
//
//                for (int i = 0; i < indexToMetadata.size(); i++) {
//                    if (topIndices.contains(i)) {
//                        // Higher rank = higher score
//                        double semanticScore = maxSemanticScore - topIndices.indexOf(i);
//                        semanticRawScores.put(i, semanticScore);
//                    }
//                }
//
//                // Scale to [0, 1]
//                double maxSem = semanticRawScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
//                Map<Integer, Double> normalizedSemanticScores = new HashMap<>();
//                for (Map.Entry<Integer, Double> entry : semanticRawScores.entrySet()) {
//                    normalizedSemanticScores.put(entry.getKey(), entry.getValue() / maxSem);
//                }
//
//                // --- Normalize keyword scores ---
//                double maxKeywordScore = keywordScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
//                Map<Integer, Double> normalizedKeywordScores = new HashMap<>();
//                for (Map.Entry<Integer, Double> entry : keywordScores.entrySet()) {
//                    normalizedKeywordScores.put(entry.getKey(), entry.getValue() / maxKeywordScore);
//                }
//
//                // Step 4: Combine semantic and keyword scores
//                Map<Integer, Double> hybridScores = new HashMap<>();
//                double alpha = 0.5; // Weight for semantic score
//
//                Set<Integer> allIndices = new HashSet<>();
//                allIndices.addAll(normalizedSemanticScores.keySet());
//                allIndices.addAll(normalizedKeywordScores.keySet());
//
//                for (int i : allIndices) {
//                    double semScore = normalizedSemanticScores.getOrDefault(i, 0.0);
//                    double keyScore = normalizedKeywordScores.getOrDefault(i, 0.0);
//                    double hybridScore = alpha * semScore + (1 - alpha) * keyScore;
//                    if (hybridScore > 0) hybridScores.put(i, hybridScore);
//                }
//
//                // Step 6: Sort by hybrid score
//                List<Map.Entry<Integer, Double>> sorted = new ArrayList<>(hybridScores.entrySet());
//                sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
//
//                List<Integer> topHybridIndices = sorted.stream()
//                        .map(Map.Entry::getKey)
//                        .limit(5)
//                        .collect(Collectors.toList());

                List<Integer> topKeyword = keywordScores.entrySet().stream()
                        .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                        .limit(2).map(Map.Entry::getKey).collect(Collectors.toList());

                List<Integer> topSemanticLimited = topIndices.stream().limit(2).collect(Collectors.toList());
                Set<Integer> combinedTop = new LinkedHashSet<>();
                combinedTop.addAll(topSemanticLimited);
                combinedTop.addAll(topKeyword);


                StringBuilder contextBuilder = new StringBuilder();
                Set<String> seenPages = new HashSet<>();
                Set<String> sourceFiles = new HashSet<>();
                int totalFiles = combinedTop.size();
                long[] lastUpdate = {0};

                for (Integer index : combinedTop) {
                    if (indexToMetadata.containsKey(index)) {
                        ChunkMetadata m = indexToMetadata.get(index);
                        String fileUri = m.getPdfFilePath();
                        Uri uri = Uri.parse(fileUri);
                        String fileName = getFileNameFromUri(uri);
                        sourceFiles.add(fileName);

                        String pageKey = m.getPdfFilePath() + "::" + m.getPageNumber();

                        if (seenPages.contains(pageKey)) {
                            continue; // skip duplicate page
                        }
                        seenPages.add(pageKey);

                        String statustext = "Reading PDF " + fileName + " (Page " + m.getPageNumber() + ")...";

//                        runOnUiThread(() -> {
//                            tvLiveStatus.setText("Reading PDF " + fileName + " (Page " + m.getPageNumber() + ")...");
//                            tvLiveStatus.setVisibility(View.VISIBLE);
//                        });
                        long now = System.currentTimeMillis();
                        long delay = Math.max(0, 5000 - (now - lastUpdate[0]));
                        lastUpdate[0] = now + delay;

                        Runnable updateStatus = () -> {
                            tvLiveStatus.setText(statustext);
                            tvLiveStatus.setVisibility(View.VISIBLE);
                        };
                        pendingStatusRunnables.add(updateStatus);
                        uiHandler.postDelayed(updateStatus, delay);

                        String snippet = extractTextFromPdf(
                                Uri.parse(m.getPdfFilePath()), m.getPageNumber(), m.getStartOffset(), m.getEndOffset());

                        contextBuilder.append("PDF: ").append(m.getPdfFilePath())
                                .append(" (Page ").append(m.getPageNumber()).append(")\n")
                                .append(snippet).append("\n\n");

                    }
                }

                if (contextBuilder.toString().isEmpty()) {
                    contextBuilder.append("No relevant context found.");
                }
                else{
                    long now = System.currentTimeMillis();
                    long delay = Math.max(0, 5000 - (now - lastUpdate[0]));
                    lastUpdate[0] = now + delay;

                    Runnable updateStatus = () -> {
                        tvLiveStatus.setText("Analysing the content...");
                        tvLiveStatus.setVisibility(View.VISIBLE);
                    };
                    pendingStatusRunnables.add(updateStatus);
                    uiHandler.postDelayed(updateStatus, delay);
                }

                // Generate LLM response
                String finalPrompt = "You are an AI assistant for the user. Your job is to politely respond and answer the user's question." +
                        "Keep your response simple and concise. " +
                        "You may refer the given context information to reply to the user's message." +
                        "If the context information provided is not related to the user's message, you should ignore it completely." +
                        "The context information is as follows:\n\n"
                        + contextBuilder.toString()
                        + "\n\nUser's message: " + query + "\nYour response: \n\n";

//                String answer = llamaCppWrapper.runInference(finalPrompt);
//
//                String correction_prompt = "Reformat the below content, remove duplicates, remove unnecessary information, and make it accurate. " +
//                        "Only return the formatted content. If the content is already formatted, just return the same content."
//                        + "\nThe content is as follows:\n\n" + answer;
//
//                runOnUiThread(() -> {
//                    progressBar.setVisibility(View.INVISIBLE);
//                    tvChatAnswer.setText(answer);
//                });
//                long now = System.currentTimeMillis();
//                long delay = Math.max(0, 9000 - (now - lastUpdate[0]));
//                lastUpdate[0] = now + delay;
//
//                new Handler(Looper.getMainLooper()).postDelayed(() -> {
//                    tvLiveStatus.setText("Generating answer... ");
//                    tvLiveStatus.setVisibility(View.VISIBLE);
//                }, delay);

                // Now safe to use:
                llamaCppWrapper.runInferenceStream(finalPrompt, new InferenceListener() {
                    @Override
                    public void onTokenGenerated(String token) {
                        runOnUiThread(() -> {

                            if(isThinking) {
                                isThinking = false;
                                thinkingHandler.removeCallbacks(thinkingRunnable);
                                chatMessages.get(aiIndexHolder[0]).text = ""; // Clear placeholder for real tokens
                                chatAdapter.notifyItemChanged(aiIndexHolder[0]);
                            }

                            // Cancel all pending UI updates
                            for (Runnable r : pendingStatusRunnables) {
                                uiHandler.removeCallbacks(r);
                            }
                            pendingStatusRunnables.clear();

                            tvLiveStatus.setText("");
                            tvLiveStatus.setVisibility(View.GONE);

                            int aiIndex = aiIndexHolder[0];
                            if (aiIndex >= 0 && aiIndex < chatMessages.size()) {
                                ChatMessage aiMsg = chatMessages.get(aiIndex);
                                aiMsg.text += token;
                                chatAdapter.notifyItemChanged(aiIndex);
                                //chatRecyclerView.scrollToPosition(chatMessages.size() - 1);
                                chatRecyclerView.post(() -> {
                                    layoutManager.scrollToPositionWithOffset(chatMessages.size() - 1, Integer.MIN_VALUE);
                                });
                            }
                        });
                    }

                    @Override
                    public void onComplete() {
                        tvLiveStatus.setVisibility(View.GONE);
                        runOnUiThread(() -> progressBar.setVisibility(View.INVISIBLE));
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            tvLiveStatus.setVisibility(View.GONE);
                            progressBar.setVisibility(View.INVISIBLE);
                            Toast.makeText(ChatActivity.this, "LLM Error: " + message, Toast.LENGTH_SHORT).show();
                        });
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvLiveStatus.setVisibility(View.GONE);
                    progressBar.setVisibility(View.INVISIBLE);
                    //tvChatAnswer.setText("Error: " + e.getMessage());
                });
            }
        }).start();
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    private byte[] getBytesFromFile(File file) throws Exception {
        InputStream in = new java.io.FileInputStream(file);
        byte[] bytes = new byte[(int) file.length()];
        in.read(bytes);
        in.close();
        return bytes;
    }

    private List<ChunkMetadata> loadMetadataFromDisk() {
        File directory = new File(getExternalFilesDir(null), "faiss_data");
        File metadataFile = new File(directory, "chunk_metadata.json");

        if (!metadataFile.exists()) {
            Log.e(TAG, "Metadata file not found at " + metadataFile.getAbsolutePath());
            return null;
        }

        try (InputStream is = new FileInputStream(metadataFile)) {
            Gson gson = new Gson();
            Reader reader = new InputStreamReader(is);
            Type listType = new TypeToken<List<ChunkMetadata>>() {}.getType();
            return gson.fromJson(reader, listType); // Deserialize JSON string into a list of ChunkMetadata
        } catch (IOException e) {
            Log.e(TAG, "Error loading metadata from disk: " + e.getMessage(), e);
            return null;
        }
    }

    private String extractTextFromPdf(Uri pdfUri, int pageNumber, int startOffset, int endOffset) {
        try {
            InputStream inputStream;

            if ("content".equalsIgnoreCase(pdfUri.getScheme())) {
                inputStream = getContentResolver().openInputStream(pdfUri);
            } else if ("file".equalsIgnoreCase(pdfUri.getScheme())) {
                File file = new File(pdfUri.getPath());
                inputStream = new FileInputStream(file);
            } else {
                Log.e(TAG, "Unsupported URI scheme: " + pdfUri.getScheme());
                return "Unsupported URI scheme.";
            }

            PDDocument document = PDDocument.load(inputStream);
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(pageNumber);
            stripper.setEndPage(pageNumber);

            String pageText = stripper.getText(document);
            document.close();

            String cleaned_pageText = cleanChunkText(pageText);

            return cleaned_pageText;

        } catch (Exception e) {
            Log.e(TAG, "Error extracting PDF: " + e.getMessage(), e);
            return "Failed to extract PDF.";
        }
    }

    private String cleanChunkText(String text) {
        if (text == null) return "";
        // Remove tabs, carriage returns and replace with a space
        text = text.replaceAll("[\\t\\n\\r]+", " ");
        // Remove non-printable ASCII characters and weird symbols
        text = text.replaceAll("[^\\x20-\\x7E]+", "");
        // Collapse multiple spaces into one
        text = text.replaceAll(" +", " ");
        // Trim leading and trailing spaces
        return text.trim();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (llamaCppWrapper != null) {
            llamaCppWrapper.freeModel();
            Log.d("ChatActivity", "LLaMA model freed.");
        }
    }
}