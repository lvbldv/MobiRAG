package com.mobirag;

import android.Manifest;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import mobirag.R;

public class SentenceEmbeddingActivity extends AppCompatActivity {
    private static final int PICK_PDF_FILE = 1;

    private static final String TAG = "SentenceEmbedding";
//    private static final String MODEL_FILENAME = "snowflake-arctic-embed-s/model_fp16.onnx";
//    private static final String TOKENIZER_FILENAME = "snowflake-arctic-embed-s/tokenizer.json";

    private static final String MODEL_FILENAME = "all-minilm-l6-v2/model.onnx";
    private static final String TOKENIZER_FILENAME = "all-minilm-l6-v2/tokenizer.json";

    private TextView tvFileProcessingStatus;

    private EditText editTextInput, editTextSearchQuery;
    private Button btnGenerateEmbedding, btnUploadPdf, btnSearchIndex, btnUseAllPdfs;
    private Button btnCompressEmbedding;
    private TextView tvResults;
    private ProgressBar progressBar;

    private SentenceEmbeddingWrapper sentenceEmbeddingWrapper;
    private float[] currentEmbedding;

    private static final String TEMP_MANIFEST_NAME = "temp_files_manifest.json";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestPermissions();
        setContentView(R.layout.activity_sentence_embedding);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        PDFBoxResourceLoader.init(getApplicationContext());

        // Initialize UI components
        editTextInput = findViewById(R.id.editTextInput);
        editTextSearchQuery = findViewById(R.id.editTextSearchQuery);
        btnGenerateEmbedding = findViewById(R.id.btnGenerateEmbedding);
        btnCompressEmbedding = findViewById(R.id.btnCompressEmbedding);
        btnSearchIndex = findViewById(R.id.btnSearchIndex);
        tvResults = findViewById(R.id.tvResults);
        progressBar = findViewById(R.id.progressBar);
        btnUploadPdf = findViewById(R.id.btnUploadPdf);
        btnUseAllPdfs = findViewById(R.id.btnUseAllPdfs);
        tvFileProcessingStatus = findViewById(R.id.tvFileProcessingStatus);

        // Initialize sentence embedding
        initializeSentenceEmbedding();

        // Set up click listeners
        btnGenerateEmbedding.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                generateEmbedding();
            }
        });

        btnCompressEmbedding.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                compressEmbedding();
            }
        });

        // Initially disable compression button
        btnCompressEmbedding.setEnabled(false);

        btnUploadPdf.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectPdfFile();
            }
        });

        btnSearchIndex.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchIndex();
            }
        });

        btnUseAllPdfs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                Intent serviceIntent = new Intent(SentenceEmbeddingActivity.this, PdfProcessingService.class);
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                    startForegroundService(serviceIntent);
//                } else {
//                    startService(serviceIntent);
//                }
//                Toast.makeText(SentenceEmbeddingActivity.this, "PDF processing started in background", Toast.LENGTH_SHORT).show();
                useAllPdfsOnDevice();
            }
        });
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1);
        }
    }


    private void initializeSentenceEmbedding() {
        progressBar.setVisibility(View.VISIBLE);
        tvResults.setText("Initializing embedding model...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Copy model files from assets to internal storage
                    File modelFile = copyAssetToInternalStorage(MODEL_FILENAME);
                    File tokenizerFile = copyAssetToInternalStorage(TOKENIZER_FILENAME);

                    // Initialize SentenceEmbedding
                    sentenceEmbeddingWrapper = new SentenceEmbeddingWrapper();
                    sentenceEmbeddingWrapper.initSync(
                            modelFile.getAbsolutePath(),
                            getBytesFromFile(tokenizerFile),
                            true,
                            "sentence_embedding",
                            true);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvResults.setText("Model initialized successfully!\nReady to generate embeddings.");
                            progressBar.setVisibility(View.INVISIBLE);
                            btnGenerateEmbedding.setEnabled(true);
                        }
                    });
                } catch (Exception e) {
                    final String errorMessage = "Error initializing model: " + e.getMessage();
                    Log.e(TAG, errorMessage, e);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvResults.setText(errorMessage);
                            progressBar.setVisibility(View.INVISIBLE);
                            Toast.makeText(SentenceEmbeddingActivity.this,
                                    "Failed to initialize model", Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void generateEmbedding() {
        String text = editTextInput.getText().toString().trim();

        if (text.isEmpty()) {
            Toast.makeText(this, "Please enter some text", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnGenerateEmbedding.setEnabled(false);
        tvResults.setText("Generating embedding...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                //
                try {
                    // Generate embedding
                    currentEmbedding = sentenceEmbeddingWrapper.encodeSync(text);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            StringBuilder resultBuilder = new StringBuilder();
                            resultBuilder.append("Embedding generated successfully!\n\n");
                            resultBuilder.append("Text: ").append(text).append("\n");
                            resultBuilder.append("Embedding size: ").append(currentEmbedding.length).append(" dimensions\n");
                            resultBuilder.append("First 5 values: ");

                            for (int i = 0; i < Math.min(5, currentEmbedding.length); i++) {
                                resultBuilder.append(String.format("%.4f", currentEmbedding[i]));
                                if (i < 4) resultBuilder.append(", ");
                            }

                            resultBuilder.append("\n\nOriginal size: ")
                                    .append(String.format("%.2f", currentEmbedding.length * 4 / 1024.0f))
                                    .append(" KB");

                            tvResults.setText(resultBuilder.toString());
                            progressBar.setVisibility(View.INVISIBLE);
                            btnGenerateEmbedding.setEnabled(true);
                            btnCompressEmbedding.setEnabled(true);
                        }
                    });
                } catch (Exception e) {
                    final String errorMessage = "Error generating embedding: " + e.getMessage();
                    Log.e(TAG, errorMessage, e);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvResults.setText(errorMessage);
                            progressBar.setVisibility(View.INVISIBLE);
                            btnGenerateEmbedding.setEnabled(true);
                        }
                    });
                }
            }
        }).start();
    }

    private void selectPdfFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, PICK_PDF_FILE);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_PDF_FILE && resultCode == RESULT_OK && data != null) {
            progressBar.setVisibility(View.VISIBLE);
            List<Uri> uriList = new ArrayList<>();

            if (data.getClipData() != null) {
                int fileCount = data.getClipData().getItemCount();
                for (int i = 0; i < fileCount; i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    uriList.add(uri);

                    //Request persistable URI permission
                    final int takeFlags = data.getFlags()
                            & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    getContentResolver().takePersistableUriPermission(uri, takeFlags);
                }
            } else if (data.getData() != null) {
                Uri uri = data.getData();
                uriList.add(uri);

                // Request persistable URI permission
                final int takeFlags = data.getFlags()
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(uri, takeFlags);
            }

            // Now process the files as usual
            processPdf(uriList.toArray(new Uri[0]));
        }
    }

    private void processPdf(Uri[] pdfUris) {
        tvResults.setText("Processing PDF...");

        new Thread(() -> {
            try{
                List<ChunkMetadata> allMetadata = new ArrayList<>();
                List<float[]> allEmbeddings = new ArrayList<>();

                int count = 0;

                for (Uri pdfUri : pdfUris) {
                    try (InputStream inputStream = getContentResolver().openInputStream(pdfUri)) {
                        int fileNumber = ++count;
                        PDDocument document = PDDocument.load(inputStream);
                        PDFTextStripper pdfStripper = new PDFTextStripper();
                        if (fileNumber % 5 == 0 || fileNumber == pdfUris.length) {
                            runOnUiThread(() -> {
                                tvFileProcessingStatus.setText("Processing file " + fileNumber + " of " + pdfUris.length);
                            });
                        }
//                        String textContent = pdfStripper.getText(document);
                        int totalPages = document.getNumberOfPages();
                        for (int pageNumber = 1; pageNumber <= totalPages; pageNumber++) {
                            pdfStripper.setStartPage(pageNumber);
                            pdfStripper.setEndPage(pageNumber);
                            String pageText = pdfStripper.getText(document);

                            // Step 1: Split text into sentences with offsets
                            List<Map.Entry<String, Integer>> sentencesWithOffsets =
                                    splitIntoSentencesWithOffsets(pageText);

                            // Step 2: Generate sentence embeddings once
                            List<String> sentences = sentencesWithOffsets.stream()
                                    .map(Map.Entry::getKey).collect(Collectors.toList());
                            List<float[]> sentenceEmbeddings =
                                    generateSentenceEmbeddings(sentences);

                            // Step 3: Create semantic chunks with offsets using precomputed embeddings
                            double threshold = 0.70; // Adjust threshold based on experimentation
                            List<Map.Entry<List<String>, Map.Entry<Integer, Integer>>> semanticChunks =
                                    createSemanticChunksWithOffsets(sentencesWithOffsets, sentenceEmbeddings, threshold);

                            // Step 4: Process each chunk
                            for (Map.Entry<List<String>, Map.Entry<Integer, Integer>> chunkEntry : semanticChunks) {
                                Map.Entry<Integer, Integer> offsets = chunkEntry.getValue();
                                int startIndex = offsets.getKey();
                                int endIndex = offsets.getValue();

                                float[] chunkEmbedding = aggregateEmbeddings(sentenceEmbeddings.subList(startIndex, endIndex));

                                int startOffset = sentencesWithOffsets.get(startIndex).getValue();
                                int endOffset = sentencesWithOffsets.get(endIndex - 1).getValue()
                                        + sentencesWithOffsets.get(endIndex - 1).getKey().length();

                                allEmbeddings.add(chunkEmbedding);
                                allMetadata.add(new ChunkMetadata(pdfUri.toString(), pageNumber, startOffset, endOffset));
                            }
                        }

                        document.close();
                    }
                }
                // Compress embeddings using PQ and store them
                compressAndStoreEmbeddings(allEmbeddings);
                saveMetadataToDisk(allMetadata);

//                runOnUiThread(() -> {
//                    tvResults.setText("Embeddings generated for PDF content:\n" +
//                            "Total Chunks: " + chunks.size() + "\n" +
//                            "First Chunk Embedding Size: " + embeddings.get(0).length + " dimensions");
//                    progressBar.setVisibility(View.INVISIBLE);
//                    btnCompressEmbedding.setEnabled(true);
//                });
                runOnUiThread(() -> {
                    tvFileProcessingStatus.setText("Finished processing " + pdfUris.length + " file(s).");
                });
            } catch (Exception e) {
                Log.e(TAG, "Error processing PDF: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    tvResults.setText("Error processing PDF.");
                    progressBar.setVisibility(View.INVISIBLE);
                });
            }
        }).start();
    }

    private float[] aggregateEmbeddings(List<float[]> chunkEmbeddings) {
        int dimension = chunkEmbeddings.get(0).length; // Get the embedding dimension
        float[] aggregatedEmbedding = new float[dimension];

        // Sum up all embeddings in the chunk
        for (float[] embedding : chunkEmbeddings) {
            for (int i = 0; i < dimension; i++) {
                aggregatedEmbedding[i] += embedding[i];
            }
        }

        // Compute the average by dividing each element by the number of embeddings
        for (int i = 0; i < dimension; i++) {
            aggregatedEmbedding[i] /= chunkEmbeddings.size();
        }

        return aggregatedEmbedding;
    }

    private String cleanChunkText(String text) {
        if (text == null) return "";
        // Remove tabs, newlines, carriage returns and replace with a space
        text = text.replaceAll("[\\t\\n\\r]+", " ");
        // Remove non-printable ASCII characters and weird symbols
        text = text.replaceAll("[^\\x20-\\x7E]+", "");
        // Collapse multiple spaces into one
        text = text.replaceAll(" +", " ");
        // Trim leading and trailing spaces
        return text.trim();
    }


    private List<Map.Entry<String, Integer>> splitIntoSentencesWithOffsets(String text) {
        List<Map.Entry<String, Integer>> sentencesWithOffsets = new ArrayList<>();

        String clean_text = cleanChunkText(text);
        String[] sentences = clean_text.split("(?<=[.!?])(?=\\s+[A-Z])"); // Split at sentence boundaries

        int currentOffset = 0;
        for (String sentence : sentences) {
            sentencesWithOffsets.add(new HashMap.SimpleEntry<>(sentence, currentOffset));
            currentOffset += sentence.length() + 1; // Account for space or punctuation
        }

        return sentencesWithOffsets;
    }


    private List<float[]> generateSentenceEmbeddings(List<String> sentences) {
        List<float[]> embeddings = new ArrayList<>();
//        for (String sentence : sentences) {
//            embeddings.add(sentenceEmbeddingWrapper.encodeSync(sentence)); // Compute embedding once
//        }
        List<float[]> sentenceEmbeddings = sentenceEmbeddingWrapper.encodeBatchSync(sentences);
        return sentenceEmbeddings;
    }

    private List<Map.Entry<List<String>, Map.Entry<Integer, Integer>>> createSemanticChunksWithOffsets(
            List<Map.Entry<String, Integer>> sentencesWithOffsets,
            List<float[]> embeddings,
            double similarityThreshold) {

        List<Map.Entry<List<String>, Map.Entry<Integer, Integer>>> chunks = new ArrayList<>();

        final int minChunkSize = 3;
        final int maxChunkSize = 10;

        List<String> currentChunk = new ArrayList<>();
        int chunkStartIndex = 0;

        for (int i = 0; i < embeddings.size(); i++) {
            currentChunk.add(sentencesWithOffsets.get(i).getKey());

            boolean shouldSplit = false;

            if (currentChunk.size() >= maxChunkSize) {
                shouldSplit = true;
            } else if (i < embeddings.size() - 1) {
                double sim = calculateCosineSimilarity(embeddings.get(i), embeddings.get(i + 1));
                if (sim < similarityThreshold && currentChunk.size() >= minChunkSize) {
                    shouldSplit = true;
                }
            }

            if (shouldSplit || i == embeddings.size() - 1) {
                int startIdx = chunkStartIndex;
                int endIdx = i + 1;

                chunks.add(new HashMap.SimpleEntry<>(
                        new ArrayList<>(currentChunk),
                        new HashMap.SimpleEntry<>(startIdx, endIdx)
                ));

                currentChunk.clear();
                chunkStartIndex = i + 1;
            }
        }

        // Merge any remaining small final chunk
        if (!chunks.isEmpty()) {
            Map.Entry<List<String>, Map.Entry<Integer, Integer>> lastChunk = chunks.get(chunks.size() - 1);
            if (lastChunk.getKey().size() < minChunkSize && chunks.size() > 1) {
                // Merge with previous
                Map.Entry<List<String>, Map.Entry<Integer, Integer>> prevChunk = chunks.get(chunks.size() - 2);
                prevChunk.getKey().addAll(lastChunk.getKey());
                prevChunk.setValue(new HashMap.SimpleEntry<>(prevChunk.getValue().getKey(), lastChunk.getValue().getValue()));
                chunks.remove(chunks.size() - 1);
            }
        }

        return chunks;
    }

    private double calculateCosineSimilarity(float[] embedding1, float[] embedding2) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
            normA += Math.pow(embedding1[i], 2);
            normB += Math.pow(embedding2[i], 2);
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }



    public void useAllPdfsOnDevice() {
        progressBar.setVisibility(View.VISIBLE);
        tvResults.setText("Scanning device for PDFs...");

        new Thread(() -> {
            try {
                List<Uri> pdfUris = new ArrayList<>();

                // Query MediaStore.Files
                pdfUris.addAll(queryMediaStoreForPDFs(MediaStore.Files.getContentUri("external")));

//                // Also query Downloads explicitly
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                    pdfUris.addAll(queryMediaStoreForPDFs(MediaStore.Downloads.EXTERNAL_CONTENT_URI));
//                }

                if (pdfUris.isEmpty()) {
                    runOnUiThread(() -> {
                        tvResults.setText("No PDFs found on device.");
                        progressBar.setVisibility(View.INVISIBLE);
                    });
                    return;
                }

                // Deduplicate by file size + name
                Map<String, Uri> uniquePdfMap = new HashMap<>();
                for (Uri uri : pdfUris) {
                    try (Cursor cursor = getContentResolver().query(uri, new String[]{MediaStore.Files.FileColumns.SIZE,
                            MediaStore.Files.FileColumns.DISPLAY_NAME}, null, null, null)) {
                        if (cursor != null && cursor.moveToFirst()) {
                            long size = cursor.getLong(0);
                            String name = cursor.getString(1);
                            String key = size + ":" + name;
                            if (!uniquePdfMap.containsKey(key)) {
                                uniquePdfMap.put(key, uri);
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Skipping file due to error: " + e.getMessage());
                    }
                }

                List<Uri> dedupedUris = new ArrayList<>(uniquePdfMap.values());

                runOnUiThread(() -> {
                    tvResults.setText("Found " + dedupedUris.size() + " PDF(s) on device.");
                    tvFileProcessingStatus.setText("Ready to process PDFs...");
                });

                processPdfsInParallel(dedupedUris);

            } catch (Exception e) {
                Log.e(TAG, "Error scanning device for PDFs: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    tvResults.setText("Error scanning device for PDFs.");
                    progressBar.setVisibility(View.INVISIBLE);
                });
            }
        }).start();
    }

    private File writeBatchToTempFile(float[][] batch, int batchIndex) throws IOException {
        File tempFile = new File(getCacheDir(), "temp_embeddings_" + batchIndex + ".bin");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            for (float[] vec : batch) {
                for (float v : vec) {
                    int intBits = Float.floatToIntBits(v);
                    fos.write((intBits >>> 24) & 0xFF);
                    fos.write((intBits >>> 16) & 0xFF);
                    fos.write((intBits >>> 8) & 0xFF);
                    fos.write(intBits & 0xFF);
                }
            }
        }
        return tempFile;
    }

    private File writeMetadataToTempFile(List<ChunkMetadata> metadata, int batchIndex) throws IOException {
        File tempFile = new File(getCacheDir(), "temp_metadata_" + batchIndex + ".json");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            String json = new Gson().toJson(metadata);
            fos.write(json.getBytes());
        }
        return tempFile;
    }

    private List<float[]> readAllEmbeddingsFromTempFiles(List<File> tempFiles, int dim) {
        List<float[]> allEmbeddings = new ArrayList<>();

        for (File file : tempFiles) {
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[dim * 4];
                while (fis.read(buffer) == buffer.length) {
                    float[] embedding = new float[dim];
                    for (int i = 0; i < dim; i++) {
                        int intBits = ((buffer[i * 4] & 0xFF) << 24)
                                | ((buffer[i * 4 + 1] & 0xFF) << 16)
                                | ((buffer[i * 4 + 2] & 0xFF) << 8)
                                | (buffer[i * 4 + 3] & 0xFF);
                        embedding[i] = Float.intBitsToFloat(intBits);
                    }
                    allEmbeddings.add(embedding);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error reading embedding from temp file: " + file.getAbsolutePath(), e);
            }
        }
        return allEmbeddings;
    }

    private List<ChunkMetadata> readAllMetadataFromTempFiles(List<File> files) throws IOException {
        List<ChunkMetadata> allMetadata = new ArrayList<>();
        Gson gson = new Gson();
        Type listType = new TypeToken<List<ChunkMetadata>>(){}.getType();

        for (File file : files) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file))) {
                List<ChunkMetadata> metadataList = gson.fromJson(reader, listType);
                allMetadata.addAll(metadataList);
            }
        }
        return allMetadata;
    }

    private File getProcessedPdfsFile() {
        File directory = new File(getExternalFilesDir(null), "faiss_data");
        return new File(directory, "processed_pdfs.json");
    }

    private void saveProcessedUris(List<String> processedUris) {
        File file = getProcessedPdfsFile();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            Gson gson = new Gson();
            fos.write(gson.toJson(processedUris).getBytes());
        } catch (IOException e) {
            Log.e(TAG, "Error saving processed URIs", e);
        }
    }

    private Set<String> loadProcessedUris() {
        File file = getProcessedPdfsFile();
        if (!file.exists()) return new HashSet<>();

        try (FileInputStream fis = new FileInputStream(file);
             Reader reader = new InputStreamReader(fis)) {
            Gson gson = new Gson();
            Type setType = new TypeToken<Set<String>>(){}.getType();
            return gson.fromJson(reader, setType);
        } catch (IOException e) {
            Log.e(TAG, "Error loading processed URIs", e);
            return new HashSet<>();
        }
    }


    private void processPdfsInParallel(List<Uri> pdfUris) {
        final int MAX_PARALLEL_PDFS = 2;
        final int BATCH_SIZE = 16;

        ExecutorService executor = Executors.newFixedThreadPool(MAX_PARALLEL_PDFS);

        Set<String> processedUriSet = loadProcessedUris();
        List<Uri> remainingUris = pdfUris.stream()
                .filter(uri -> !processedUriSet.contains(uri.toString()))
                .collect(Collectors.toList());
        List<String> processedSoFar = new ArrayList<>(processedUriSet);

        List<String> batchedChunkTexts = new ArrayList<>();
        List<ChunkMetadata> batchedMetadata = new ArrayList<>();
//        List<File> tempEmbeddingFiles = new ArrayList<>();
//        List<File> tempMetadataFiles = new ArrayList<>();
        AtomicInteger batchIndex = new AtomicInteger(0);
        AtomicInteger fileCounter = new AtomicInteger(1);

        List<Map<String, Integer>> allDfMaps = Collections.synchronizedList(new ArrayList<>());
        List<Integer> allChunkCounts = Collections.synchronizedList(new ArrayList<>());

        List<CompletableFuture<Void>> futures = remainingUris.stream()
                .map(uri -> CompletableFuture.runAsync(() -> {
                    int index = fileCounter.getAndIncrement();
                    PdfProcessingResult result =
                            processSinglePdf(uri, index, remainingUris.size());

                    allDfMaps.add(result.dfMap);
                    allChunkCounts.add(result.chunkCount);

                    synchronized (this) {
                        batchedChunkTexts.addAll(result.chunkTexts);
                        batchedMetadata.addAll(result.metadata);

                        while (batchedChunkTexts.size() >= BATCH_SIZE) {
                            List<String> toEncode = new ArrayList<>(batchedChunkTexts.subList(0, BATCH_SIZE));
                            List<ChunkMetadata> toKeep = new ArrayList<>(batchedMetadata.subList(0, BATCH_SIZE));
                            batchedChunkTexts.subList(0, BATCH_SIZE).clear();
                            batchedMetadata.subList(0, BATCH_SIZE).clear();

                            List<float[]> encoded = sentenceEmbeddingWrapper.encodeBatchSync(toEncode);
                            float[][] batchArray = encoded.toArray(new float[0][0]);
                            int idx = batchIndex.getAndIncrement();

                            Map<String, Integer> globalDfMap = mergeDfMaps(allDfMaps);
                            int totalChunks = allChunkCounts.stream().mapToInt(Integer::intValue).sum();
                            TfIdfKeywordExtractor globalTfidf = new TfIdfKeywordExtractor(globalDfMap, totalChunks);
                            for (int i = 0; i < toEncode.size(); i++) {
                                List<String> keywords = globalTfidf.getTopKeywords(toEncode.get(i), 10);
                                toKeep.get(i).setKeywords(keywords);
                            }

                            try {
                                File embeddingFile = writeBatchToTempFile(batchArray, idx);
                                File metadataFile = writeMetadataToTempFile(toKeep, idx);
//                                tempEmbeddingFiles.add(embeddingFile);
//                                tempMetadataFiles.add(metadataFile);
                                persistTempFilePaths(embeddingFile, metadataFile);
                            } catch (IOException e) {
                                Log.e(TAG, "Failed to write batch to temp file", e);
                            }
                        }

                        processedSoFar.add(uri.toString());
                        saveProcessedUris(processedSoFar);
                    }
                }, executor)).collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
            try {
                // Final flush for leftovers
                if (!batchedChunkTexts.isEmpty()) {
                    List<float[]> lastEncoded = sentenceEmbeddingWrapper.encodeBatchSync(batchedChunkTexts);
                    float[][] lastBatch = lastEncoded.toArray(new float[0][0]);
                    int idx = batchIndex.getAndIncrement();

                    Map<String, Integer> globalDfMap = mergeDfMaps(allDfMaps);
                    int totalChunks = allChunkCounts.stream().mapToInt(Integer::intValue).sum();
                    TfIdfKeywordExtractor globalTfidf = new TfIdfKeywordExtractor(globalDfMap, totalChunks);
                    for (int i = 0; i < batchedChunkTexts.size(); i++) {
                        List<String> keywords = globalTfidf.getTopKeywords(batchedChunkTexts.get(i), 10);
                        batchedMetadata.get(i).setKeywords(keywords);
                    }

                    File embeddingFile = writeBatchToTempFile(lastBatch, idx);
                    File metadataFile = writeMetadataToTempFile(batchedMetadata, idx);
//                    tempEmbeddingFiles.add(embeddingFile);
//                    tempMetadataFiles.add(metadataFile);
                    persistTempFilePaths(embeddingFile, metadataFile);
                }

                // Merge all
                int dim = 384; // or detect dynamically
                List<File> allEmbeddingFiles = getPersistedEmbeddingFiles();
                List<File> allMetadataFiles = getPersistedMetadataFiles();
                List<float[]> allEmbeddings = readAllEmbeddingsFromTempFiles(allEmbeddingFiles, dim);
                List<ChunkMetadata> allMetadata = readAllMetadataFromTempFiles(allMetadataFiles);

                compressAndStoreEmbeddings(allEmbeddings);

                saveMetadataToDisk(allMetadata);

                // Cleanup
                for (File f : allEmbeddingFiles) f.delete();
                for (File f : allMetadataFiles) f.delete();
                clearPersistedTempFiles();

                runOnUiThread(() -> {
                    tvFileProcessingStatus.setText("Finished processing " + pdfUris.size() + " files.");
                    progressBar.setVisibility(View.INVISIBLE);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error during final flush and merge", e);
            } finally {
                executor.shutdown();
            }
        });
    }

    private Map<String, Integer> mergeDfMaps(List<Map<String, Integer>> maps) {
        Map<String, Integer> merged = new HashMap<>();
        for (Map<String, Integer> map : maps) {
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                merged.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        return merged;
    }

    private static class PdfProcessingResult {
        List<String> chunkTexts;
        List<ChunkMetadata> metadata;
        Map<String, Integer> dfMap;
        int chunkCount;

        PdfProcessingResult(List<String> texts, List<ChunkMetadata> metadata,
                            Map<String, Integer> dfMap, int chunkCount) {
            this.chunkTexts = texts;
            this.metadata = metadata;
            this.dfMap = dfMap;
            this.chunkCount = chunkCount;
        }
    }


    private void persistTempFilePaths(File embedding, File metadata) {
        try {
            JSONObject manifest = loadTempManifest();
            JSONArray embArray = manifest.optJSONArray("embedding");
            JSONArray metaArray = manifest.optJSONArray("metadata");
            if (embArray == null) embArray = new JSONArray();
            if (metaArray == null) metaArray = new JSONArray();

            embArray.put(embedding.getAbsolutePath());
            metaArray.put(metadata.getAbsolutePath());

            manifest.put("embedding", embArray);
            manifest.put("metadata", metaArray);

            File file = new File(getCacheDir(), TEMP_MANIFEST_NAME);
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(manifest.toString());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to persist temp file paths", e);
        }
    }

    private JSONObject loadTempManifest() {
        File file = new File(getCacheDir(), TEMP_MANIFEST_NAME);
        if (!file.exists()) return new JSONObject();
        try {
            String json = new String(Files.readAllBytes(file.toPath()));
            return new JSONObject(json);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load manifest", e);
            return new JSONObject();
        }
    }

    private List<File> getPersistedEmbeddingFiles() {
        JSONObject manifest = loadTempManifest();
        List<File> files = new ArrayList<>();
        JSONArray array = manifest.optJSONArray("embedding");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                files.add(new File(array.optString(i)));
            }
        }
        return files;
    }

    private List<File> getPersistedMetadataFiles() {
        JSONObject manifest = loadTempManifest();
        List<File> files = new ArrayList<>();
        JSONArray array = manifest.optJSONArray("metadata");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                files.add(new File(array.optString(i)));
            }
        }
        return files;
    }

    private void clearPersistedTempFiles() {
        File file = new File(getCacheDir(), TEMP_MANIFEST_NAME);
        if (file.exists()) file.delete();
    }





    // Returns Pair<List<float[]>, List<ChunkMetadata>>
    public PdfProcessingResult processSinglePdf(Uri pdfUri, int fileIndex, int totalFiles) {
        // List<float[]> embeddings = new ArrayList<>();
        List<String> chunkTexts = new ArrayList<>();
        List<ChunkMetadata> metadata = new ArrayList<>();
        Map<String, Integer> localDfMap = new HashMap<>();
        int localChunkCount = 0;

        try (InputStream inputStream = getContentResolver().openInputStream(pdfUri)) {

            PDDocument document;
            try {
                document = PDDocument.load(inputStream); // Attempt without password
            } catch (com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException ex) {
                Log.w(TAG, "Skipping encrypted PDF (password protected): " + pdfUri);
                return new PdfProcessingResult(chunkTexts, metadata, localDfMap, localChunkCount); // Skip it gracefully
            }
            catch (java.lang.NumberFormatException ex) {
                Log.w(TAG, "Skipping PDF - invalid page number " + pdfUri);
                return new PdfProcessingResult(chunkTexts, metadata, localDfMap, localChunkCount);// Skip it gracefully
            }
            PDFTextStripper pdfStripper = new PDFTextStripper();
            int totalPages = document.getNumberOfPages();

//            List<String> allChunkTexts = new ArrayList<>();
//            List<ChunkMetadata> allChunkMetadata = new ArrayList<>();

//            Map<String, Integer> localDfMap = new HashMap<>();
//            int localChunkCount = 0;

            for (int pageNumber = 1; pageNumber <= totalPages; pageNumber++) {
                pdfStripper.setStartPage(pageNumber);
                pdfStripper.setEndPage(pageNumber);
                String pageText = pdfStripper.getText(document);

                if (pageText.length() < 150) continue;

                List<Map.Entry<String, Integer>> sentencesWithOffsets = splitIntoSentencesWithOffsets(pageText);
                List<Map.Entry<List<String>, Map.Entry<Integer, Integer>>> slidingChunks =
                        createSlidingChunksWithOffsets(sentencesWithOffsets, 1024, 1);

                for (Map.Entry<List<String>, Map.Entry<Integer, Integer>> chunkEntry : slidingChunks) {
                    List<String> chunkSentences = chunkEntry.getKey();
                    Map.Entry<Integer, Integer> offsets = chunkEntry.getValue();

                    String chunkText = String.join(" ", chunkSentences);

                    Set<String> uniqueTokens = KeywordExtractor.tokenize(chunkText);

                    for (String token : uniqueTokens) {
                        localDfMap.merge(token, 1, Integer::sum);
                    }
                    localChunkCount++;

                    chunkTexts.add(chunkText);

                    int startOffset = sentencesWithOffsets.get(offsets.getKey()).getValue();
                    int endOffset = sentencesWithOffsets.get(offsets.getValue() - 1).getValue()
                            + sentencesWithOffsets.get(offsets.getValue() - 1).getKey().length();

                    metadata.add(new ChunkMetadata(pdfUri.toString(), pageNumber, startOffset, endOffset));
                }
            }

            document.close();


            if (chunkTexts.isEmpty()) {
                Log.w(TAG, "Skipping PDF: no valid chunks to encode for " + pdfUri);
                return new PdfProcessingResult(chunkTexts, metadata, localDfMap, localChunkCount);
                //return new AbstractMap.SimpleEntry<>(embeddings, metadata);
            }

            // Batch encode ALL chunks together
            //List<float[]> chunkEmbeddings = sentenceEmbeddingWrapper.encodeBatchSync(allChunkTexts);
            // Compute TF-IDF and update metadata with keywords
//            TfIdfKeywordExtractor tfidf = new TfIdfKeywordExtractor(allChunkTexts);
//            for (int i = 0; i < allChunkTexts.size(); i++) {
//                List<String> keywords = tfidf.getTopKeywords(allChunkTexts.get(i), 10);
//                allChunkMetadata.get(i).setKeywords(keywords);
//            }

            // 🔗 Combine embeddings and metadata
//            chunkTexts.addAll(allChunkTexts);
//            metadata.addAll(allChunkMetadata);

        } catch (Exception e) {
            Log.w(TAG, "Error processing single PDF: " + e.getMessage(), e);
            return new PdfProcessingResult(chunkTexts, metadata, localDfMap, localChunkCount);
        }

        runOnUiThread(() -> tvFileProcessingStatus.setText("Processing file " + fileIndex + " of " + totalFiles));

        return new PdfProcessingResult(chunkTexts, metadata, localDfMap, localChunkCount);
    }


    private List<Map.Entry<List<String>, Map.Entry<Integer, Integer>>> createSlidingChunksWithOffsets(
            List<Map.Entry<String, Integer>> sentencesWithOffsets,
            int charLimit,
            int overlap
    ) {
        List<Map.Entry<List<String>, Map.Entry<Integer, Integer>>> chunks = new ArrayList<>();
        int index = 0;

        while (index < sentencesWithOffsets.size()) {
            List<String> chunkSentences = new ArrayList<>();
            int chunkCharCount = 0;
            int startIdx = index;
            int endIdx = index;

            // Check if a single sentence exceeds the limit
            String currentSentence = sentencesWithOffsets.get(index).getKey();
            if (currentSentence.length() >= charLimit) {
                chunkSentences.add(currentSentence);
                endIdx = index + 1;
            } else {
                // Merge as many sentences as possible under charLimit
                while (endIdx < sentencesWithOffsets.size()) {
                    String sentence = sentencesWithOffsets.get(endIdx).getKey();
                    int lengthWithSpace = sentence.length() + (chunkSentences.isEmpty() ? 0 : 1); // account for space
                    if (chunkCharCount + lengthWithSpace > charLimit) break;

                    chunkSentences.add(sentence);
                    chunkCharCount += lengthWithSpace;
                    endIdx++;
                }
            }

            // Add the chunk
            chunks.add(new AbstractMap.SimpleEntry<>(
                    new ArrayList<>(chunkSentences),
                    new AbstractMap.SimpleEntry<>(startIdx, endIdx)
            ));

            // Determine next start index
            if (chunkSentences.size() == 1 && chunkSentences.get(0).length() >= charLimit) {
                index = endIdx; // skip long sentence, no overlap
            } else {
                index = Math.max(endIdx - overlap, startIdx + 1);
            }
        }

        return chunks;
    }



    private List<Uri> queryMediaStoreForPDFs(Uri collectionUri) {
        List<Uri> pdfUris = new ArrayList<>();
        String[] projection = {
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MIME_TYPE
        };
        String selection = MediaStore.Files.FileColumns.MIME_TYPE + "=?";
        String[] selectionArgs = new String[]{"application/pdf"};

        Cursor cursor = getContentResolver().query(
                collectionUri, projection, selection, selectionArgs, null
        );

        if (cursor != null) {
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                Uri contentUri = ContentUris.withAppendedId(collectionUri, id);
                pdfUris.add(contentUri);
            }
            cursor.close();
        }

        return pdfUris;
    }



    private void saveMetadataToDisk(List<ChunkMetadata> metadataList) {
        File directory = new File(getExternalFilesDir(null), "faiss_data");
        if (!directory.exists()) {
            boolean success = directory.mkdirs();
            Log.d(TAG, "Directory creation result: " + success);
        }

        File metadataFile = new File(directory, "chunk_metadata.json");

        try (FileOutputStream fos = new FileOutputStream(metadataFile)) {
            Gson gson = new Gson();
            String jsonString = gson.toJson(metadataList); // Convert metadata list to JSON string
            fos.write(jsonString.getBytes());
            Log.d(TAG, "Metadata saved to disk at " + metadataFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Error saving metadata to disk: " + e.getMessage(), e);
        }
    }

    private String extractTextFromPdf(Uri pdfFilePath, int pageNumber, int startOffset, int endOffset) {
        try (InputStream inputStream = getContentResolver().openInputStream(pdfFilePath)) {
            PDDocument document = PDDocument.load(inputStream);
            PDFTextStripper pdfStripper = new PDFTextStripper();
            // Set the range to extract only the specified page
            pdfStripper.setStartPage(pageNumber);
            pdfStripper.setEndPage(pageNumber);

            String pageText = pdfStripper.getText(document);
            document.close();

            return pageText.substring(startOffset, Math.min(endOffset, pageText.length()));
        } catch (IOException e) {
            Log.e(TAG, "Error extracting text from PDF: " + e.getMessage(), e);
            return "Error extracting text.";
        }
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

    private List<String> splitIntoChunks(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        int length = text.length();
        for (int i = 0; i < length; i += chunkSize) {
            chunks.add(text.substring(i, Math.min(length, i + chunkSize)));
        }
        return chunks;
    }

    private void compressAndStoreEmbeddings(List<float[]> embeddings) {
        tvResults.setText("Compressing embeddings...");

        // Convert List<float[]> to float[][] for JNI compatibility
        int numEmbeddings = embeddings.size();
        int dimension = embeddings.get(0).length;
        float[][] embeddingArray = new float[numEmbeddings][dimension];
        for (int i = 0; i < numEmbeddings; i++) {
            embeddingArray[i] = embeddings.get(i);
        }

        File directory = new File(getExternalFilesDir(null), "faiss_data");
        if (!directory.exists()) {
            boolean success = directory.mkdirs();
            Log.d(TAG, "Directory creation result: " + success);
        }
        String storagePath = directory.getAbsolutePath();

        new Thread(() -> {
            try {
                String result;
                if (embeddingArray.length <10000) {
                    result = compressEmbeddingsWithPQ(embeddingArray, 1000 /* Training vectors */, storagePath);
                }
                else{
                    result = compressEmbeddingsWithPQ(embeddingArray, 10000, storagePath);
                }


                runOnUiThread(() -> {
                    tvResults.setText(result);
                    progressBar.setVisibility(View.INVISIBLE);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error compressing embeddings: " + e.getMessage(), e);

                runOnUiThread(() -> {
                    tvResults.setText("Error compressing embeddings.");
                    progressBar.setVisibility(View.INVISIBLE);
                });
            }
        }).start();
    }

    private void searchIndex() {
        String query = editTextSearchQuery.getText().toString().trim();

        if (query.isEmpty()) {
            Toast.makeText(this, "Please enter a search query", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                float[] queryEmbedding = sentenceEmbeddingWrapper.encodeSync(query); // Generate query embedding

                File directory = new File(getExternalFilesDir(null), "faiss_data");
                String indexPath = directory.getAbsolutePath() + "/embeddings_pq.index";

                String results = searchIndexNative(queryEmbedding,15, indexPath); // Search top-5 results
                // Deserialize search results
                Gson gson = new Gson();
                Type resultType = new TypeToken<List<Integer>>() {}.getType();
                List<Integer> topKIndices = gson.fromJson(results, resultType);

                // Map results to text using metadata
                List<ChunkMetadata> metadataList = loadMetadataFromDisk();
                if (metadataList == null) throw new Exception("Failed to load metadata.");

//                StringBuilder resultBuilder = new StringBuilder("Search Results:\n");
//                for (int i = 0; i < topKIndices.size(); i++) {
//                    int index = topKIndices.get(i);
//                    if (index >= 0 && index < metadataList.size()) {
//                        ChunkMetadata metadata = metadataList.get(index);
//
//                        String extractedText = extractTextFromPdf(
//                                Uri.parse(metadata.getPdfFilePath()),
//                                metadata.getPageNumber(),
//                                metadata.getStartOffset(),
//                                metadata.getEndOffset()
//                        );
//
//                        resultBuilder.append("Rank ").append(i + 1).append(": ");
//                        resultBuilder.append("PDF Path=").append(metadata.getPdfFilePath()).append(", ");
//                        resultBuilder.append("Extracted Text: ").append(extractedText).append("\n\n");
//                    }
//                }
                Map<Integer, ChunkMetadata> metadataMap = new HashMap<>();
                for (int i = 0; i < metadataList.size(); i++) {
                    metadataMap.put(i, metadataList.get(i));
                }

                ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
                AtomicInteger rankCounter = new AtomicInteger(1);
                // Parallel text extraction
                List<CompletableFuture<String>> futures = topKIndices.stream()
                        .filter(metadataMap::containsKey) // Pre-filter valid indices
                        .map(index -> CompletableFuture.supplyAsync(() -> {
                            ChunkMetadata metadata = metadataMap.get(index);
                            Uri pdfUri = Uri.parse(metadata.getPdfFilePath());
                            String extractedText = extractTextFromPdf(
                                    pdfUri,
                                    metadata.getPageNumber(),
                                    metadata.getStartOffset(),
                                    metadata.getEndOffset()
                            );

                            String clean_extractedText = cleanChunkText(extractedText);
                            return String.format("Rank %d: PDF Path=%s, Extracted Text: %s\n\n",
                                    rankCounter.getAndIncrement(), metadata.getPdfFilePath(), clean_extractedText);
                        }, executor))
                        .collect(Collectors.toList());

                // Collect results
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join(); // Wait for all tasks to finish

                String resultText = futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.joining());


                executor.shutdown();


                runOnUiThread(() -> {
                    tvResults.setText("Search Results:\n" + resultText); // Display results
                    progressBar.setVisibility(View.INVISIBLE);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error searching index: " + e.getMessage(), e);
                runOnUiThread(() -> progressBar.setVisibility(View.INVISIBLE));
            }
        }).start();
    }


    private void compressEmbedding() {
        if (currentEmbedding == null) {
            Toast.makeText(this, "No embedding to compress", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnCompressEmbedding.setEnabled(false);
        tvResults.setText(tvResults.getText() + "\n\nCompressing embedding...");

        // Create storage path
        File directory = new File(getExternalFilesDir(null), "faiss_data");
        if (!directory.exists()) {
            boolean success = directory.mkdirs();
            Log.d(TAG, "Directory creation result: " + success);
        }
        String storagePath = directory.getAbsolutePath();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Call native method to compress the embedding
                    String result = compressEmbeddingWithPQ(currentEmbedding, storagePath);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvResults.setText(tvResults.getText() + "\n\n" + result);
                            progressBar.setVisibility(View.INVISIBLE);
                            btnCompressEmbedding.setEnabled(true);
                        }
                    });
                } catch (Exception e) {
                    final String errorMessage = "Error compressing embedding: " + e.getMessage();
                    Log.e(TAG, errorMessage, e);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvResults.setText(tvResults.getText() + "\n\n" + errorMessage);
                            progressBar.setVisibility(View.INVISIBLE);
                            btnCompressEmbedding.setEnabled(true);
                        }
                    });
                }
            }
        }).start();
    }

    // Helper method to compress embedding using the native PQ implementation
    private native String compressEmbeddingWithPQ(float[] embedding, String storagePath);
    public native String compressEmbeddingsWithPQ(float[][] embeddings, int numTrainingVectors,
                                                  String storagePath);

    public static native String searchIndexNative(float[] queryEmbedding, int topK, String indexPath);

    // Helper method to copy asset file to internal storage
    private File copyAssetToInternalStorage(String assetName) throws Exception {
        // Create the destination file reference
        File outputFile = new File(getFilesDir(), assetName);

        // Ensure parent directories exist
        if (!outputFile.getParentFile().exists()) {
            boolean dirCreated = outputFile.getParentFile().mkdirs();
            Log.d("SentenceEmbedding", "Directory creation result: " + dirCreated);
        }

        // Copy the file
        try (InputStream in = getAssets().open(assetName);
             OutputStream out = new FileOutputStream(outputFile)) {

            byte[] buffer = new byte[1024];
            int read;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

            Log.d("SentenceEmbedding", "Successfully copied " + assetName + " to " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e("SentenceEmbedding", "Failed to copy asset " + assetName, e);
            throw e;
        }

        return outputFile;
    }

    // Helper method to get bytes from file
    private byte[] getBytesFromFile(File file) throws Exception {
        InputStream in = new java.io.FileInputStream(file);
        byte[] bytes = new byte[(int) file.length()];

        try {
            in.read(bytes);
        } finally {
            in.close();
        }

        return bytes;
    }

    static {
        System.loadLibrary("faiss");
    }
}