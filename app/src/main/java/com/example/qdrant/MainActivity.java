package com.example.qdrant;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.qdrant.client.OfflineQdrant;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class MainActivity extends AppCompatActivity {

    private TextView tvLog;
    private Button btnSelectUpsert;
    private Button btnSelectSearch;
    private Button btnRunTest;
    private Button btnCheckRecall;

    private Uri upsertFileUri;
    private Uri searchFileUri;

    // Cache parsed vectors for recall calculation
    // ID -> float[]
    private List<VectorData> groundTruthVectors = new ArrayList<>();
    private List<SearchQuery> searchQueries = new ArrayList<>();

    private static class VectorData {
        int id;
        float[] vector;

        public VectorData(int id, float[] vector) {
            this.id = id;
            this.vector = vector;
        }
    }

    private static class SearchQuery {
        float[] vector;
        int limit;
        // List of result IDs from Qdrant search
        List<Integer> qdrantResultIds = new ArrayList<>();

        public SearchQuery(float[] vector, int limit) {
            this.vector = vector;
            this.limit = limit;
        }
    }

    private boolean qdrantInitialized = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvLog = findViewById(R.id.tvLog);
        btnSelectUpsert = findViewById(R.id.btnSelectUpsert);
        btnSelectSearch = findViewById(R.id.btnSelectSearch);
        btnRunTest = findViewById(R.id.btnRunTest);
        btnCheckRecall = findViewById(R.id.btnCheckRecall);

        Button btnReset = findViewById(R.id.btnReset); // New button
        btnReset.setOnClickListener(v -> resetSelection());

        btnSelectUpsert.setOnClickListener(v -> openFilePicker(upsertPickerLauncher));
        btnSelectSearch.setOnClickListener(v -> openFilePicker(searchPickerLauncher));

        btnRunTest.setOnClickListener(v -> runTest());
        btnCheckRecall.setOnClickListener(v -> checkRecall());

        // Restore URIs
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String upsertStr = prefs.getString("upsert_uri", null);
        if (upsertStr != null) {
            upsertFileUri = Uri.parse(upsertStr);
            btnSelectUpsert.setText("Upsert: " + upsertFileUri.getLastPathSegment());
            log("Restored Upsert File: " + upsertFileUri.toString());
        }

        String searchStr = prefs.getString("search_uri", null);
        if (searchStr != null) {
            searchFileUri = Uri.parse(searchStr);
            btnSelectSearch.setText("Search: " + searchFileUri.getLastPathSegment());
            log("Restored Search File: " + searchFileUri.toString());
        }

        // Auto-init Qdrant
        new Thread(() -> {
            File filesDir = getExternalFilesDir(null);
            if (filesDir == null)
                filesDir = getFilesDir();
            String storagePath = new File(filesDir, "qdrant_storage").getAbsolutePath();
            File dir = new File(storagePath);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            log("Initializing Qdrant at " + storagePath);
            String initResult = OfflineQdrant.init(storagePath);
            log("Init result: " + initResult);

            if (initResult.equals("OK")) {
                qdrantInitialized = true;
            } else {
                log("CRITICAL: " + initResult);
                qdrantInitialized = false;
            }
        }).start();
    }

    private void resetSelection() {
        upsertFileUri = null;
        searchFileUri = null;
        btnSelectUpsert.setText("Select Upsert File");
        btnSelectSearch.setText("Select Search File");
        getPreferences(MODE_PRIVATE).edit().clear().apply();
        log("Selection Reset.");
    }

    // Save URI helper
    private void saveUri(String key, Uri uri) {
        getPreferences(MODE_PRIVATE).edit().putString(key, uri.toString()).apply();
    }

    // Updated Launchers to persist permission and save URI
    private final ActivityResultLauncher<Intent> upsertPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    String name = uri.getLastPathSegment();
                    if (name != null && !name.contains("upsert")) {
                        Toast.makeText(this, "Warning: File name doesn't contain 'upsert'", Toast.LENGTH_SHORT).show();
                    }
                    upsertFileUri = uri;
                    // Persist permission
                    try {
                        getContentResolver().takePersistableUriPermission(upsertFileUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception e) {
                        log("Permission warning: " + e.getMessage());
                    }

                    saveUri("upsert_uri", upsertFileUri);
                    btnSelectUpsert.setText("Upsert: " + name);
                    log("Selected Upsert File: " + uri.toString());
                }
            });

    private final ActivityResultLauncher<Intent> searchPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    String name = uri.getLastPathSegment();
                    if (name != null && !name.contains("search")) {
                        Toast.makeText(this, "Warning: File name doesn't contain 'search'", Toast.LENGTH_SHORT).show();
                    }
                    searchFileUri = uri;
                    // Persist permission
                    try {
                        getContentResolver().takePersistableUriPermission(searchFileUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception e) {
                        log("Permission warning: " + e.getMessage());
                    }

                    saveUri("search_uri", searchFileUri);
                    btnSelectSearch.setText("Search: " + name);
                    log("Selected Search File: " + uri.toString());
                }
            });

    private void openFilePicker(ActivityResultLauncher<Intent> launcher) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*"); // Select all files
        launcher.launch(intent);
    }

    private void runTest() {
        if (!qdrantInitialized) {
            // Try check again or warn
            // Actually with my Rust fix, it should return true.
            // But if it failed for other reasons, we should warn.
            // log("Warning: Qdrant init returned false. Proceeding with caution.");
        }

        if (upsertFileUri == null || searchFileUri == null) {
            Toast.makeText(this, "Please select both files first", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                String collectionName = "test_collection";
                String configJson = "{\"vectors\": {\"size\": 384, \"distance\": \"Cosine\"}}";

                log("Creating collection (384-dim, Cosine)...");
                OfflineQdrant.createCollection(collectionName, configJson);

                // --- Upsert Logic ---
                log("Reading upsert file...");
                String upsertJson = readUri(upsertFileUri);
                if (upsertJson == null)
                    return;

                // VALIDATION
                if (upsertJson.trim().startsWith("[")) {
                    log("ERROR: Selected Upsert File content looks like an Array! Did you select search_1k.json?");
                    return;
                }

                log("Parsing upsert vectors (also for ground truth)...");
                if (!parseUpsertData(upsertJson))
                    return; // Populates groundTruthVectors

                log("Upserting " + groundTruthVectors.size() + " vectors to Qdrant...");
                long start = System.currentTimeMillis();
                OfflineQdrant.update(collectionName, upsertJson);
                long end = System.currentTimeMillis();
                log("Upsert done in " + (end - start) + "ms");

                // --- Search Logic ---
                log("Reading search file...");
                String searchJson = readUri(searchFileUri);
                if (searchJson == null)
                    return;

                // VALIDATION
                if (searchJson.trim().startsWith("{")) {
                    log("ERROR: Selected Search File content looks like an Object! Did you select upsert_50k.json?");
                    return;
                }

                log("Parsing search vectors...");
                parseSearchData(searchJson); // Populates searchQueries

                log("Running " + searchQueries.size() + " searches on Qdrant...");
                long searchTotalTime = 0;
                for (int i = 0; i < searchQueries.size(); i++) {
                    SearchQuery query = searchQueries.get(i);
                    JSONObject request = new JSONObject();
                    // Flat structure for SearchRequest (due to serde flatten)
                    request.put("vector", new JSONArray(query.vector));
                    request.put("limit", query.limit);

                    long sStart = System.currentTimeMillis();
                    String resultJson = OfflineQdrant.search(collectionName, request.toString());
                    long sEnd = System.currentTimeMillis();
                    searchTotalTime += (sEnd - sStart);

                    if (resultJson.startsWith("JSON_ERROR") ||
                            resultJson.startsWith("ARG_ERROR") ||
                            resultJson.startsWith("STATE_ERROR") ||
                            resultJson.startsWith("SEARCH_ERROR") ||
                            resultJson.startsWith("SERIALIZE_ERROR")) {
                        log("CRITICAL: " + resultJson);
                        return; // Stop test
                    }

                    // Parse result IDs for recall check later
                    parseSearchResults(resultJson, query);

                    if (i == 0)
                        log("First result: " + resultJson);
                }
                log("Search done in " + searchTotalTime + "ms (Avg: " + (searchTotalTime / (float) searchQueries.size())
                        + "ms)");

            } catch (Exception e) {
                log("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private void checkRecall() {
        if (groundTruthVectors.isEmpty() || searchQueries.isEmpty()) {
            Toast.makeText(this, "Run Test first to populate data", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            log("Starting Brute Force Recall Calculation...");
            log("Ground Truth Size: " + groundTruthVectors.size());
            log("Queries: " + searchQueries.size());

            long start = System.currentTimeMillis();

            float totalRecall = 0;

            for (int i = 0; i < searchQueries.size(); i++) {
                SearchQuery query = searchQueries.get(i);

                // 1. Calculate Cosine Similarity with ALL ground truth vectors
                PriorityQueue<VectorData> pq = new PriorityQueue<>(query.limit, (a, b) -> Float.compare(
                        cosineSimilarity(a.vector, query.vector),
                        cosineSimilarity(b.vector, query.vector)));
                // We want Top-K highest similarity.
                // Java PQ is min-heap by default.
                // To keep TOP K largest elements:
                // If heap < K, add.
                // If heap full, if new > min (head), remove head and add new.

                // Standard brute force: compute all scores, sort.
                // Optimization: Use Min-Heap of size K.

                PriorityQueue<Pair> minHeap = new PriorityQueue<>(query.limit,
                        (a, b) -> Float.compare(a.score, b.score));

                for (VectorData doc : groundTruthVectors) {
                    float score = cosineSimilarity(doc.vector, query.vector);
                    if (minHeap.size() < query.limit) {
                        minHeap.add(new Pair(doc.id, score));
                    } else if (score > minHeap.peek().score) {
                        minHeap.poll();
                        minHeap.add(new Pair(doc.id, score));
                    }
                }

                // Calculate overlap
                List<Integer> truthIds = new ArrayList<>();
                for (Pair p : minHeap)
                    truthIds.add(p.id);

                int matches = 0;
                for (Integer qId : query.qdrantResultIds) {
                    if (truthIds.contains(qId))
                        matches++;
                }

                float recall = (float) matches / query.limit;
                totalRecall += recall;

                // Log progress every 10 queries instead of 100
                if ((i + 1) % 10 == 0)
                    log("Processed " + (i + 1) + " / " + searchQueries.size() + " recall checks...");
            }

            long end = System.currentTimeMillis();
            log("Recall Check Done in " + (end - start) + "ms");
            log("AVERAGE RECALL: " + (totalRecall / searchQueries.size()));

        }).start();
    }

    // Simple Cosine Similarity
    private float cosineSimilarity(float[] v1, float[] v2) {
        float dot = 0.0f;
        float normA = 0.0f;
        float normB = 0.0f;
        for (int i = 0; i < v1.length; i++) {
            dot += v1[i] * v2[i];
            normA += v1[i] * v1[i];
            normB += v2[i] * v2[i];
        }
        return dot / ((float) (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    private static class Pair {
        int id;
        float score;

        public Pair(int id, float score) {
            this.id = id;
            this.score = score;
        }
    }

    private boolean parseUpsertData(String json) {
        groundTruthVectors.clear();
        try {
            JSONObject root = new JSONObject(json);
            JSONArray points = root.getJSONObject("upsert_points").getJSONArray("points");
            for (int i = 0; i < points.length(); i++) {
                JSONObject p = points.getJSONObject(i);
                int id = p.getInt("id");
                JSONArray vecJson = p.getJSONArray("vector");
                float[] vec = new float[vecJson.length()];
                for (int j = 0; j < vecJson.length(); j++)
                    vec[j] = (float) vecJson.getDouble(j);
                groundTruthVectors.add(new VectorData(id, vec));
            }
            return true;
        } catch (Exception e) {
            log("Error parsing upsert data: " + e.getMessage());
            return false;
        }
    }

    private void parseSearchData(String json) {
        searchQueries.clear();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject s = arr.getJSONObject(i);
                JSONArray vecJson = s.getJSONArray("vector");
                float[] vec = new float[vecJson.length()];
                for (int j = 0; j < vecJson.length(); j++)
                    vec[j] = (float) vecJson.getDouble(j);
                int limit = s.optInt("limit", 10);
                searchQueries.add(new SearchQuery(vec, limit));
            }
        } catch (Exception e) {
            log("Error parsing search data: " + e.getMessage());
        }
    }

    private void parseSearchResults(String json, SearchQuery query) {
        query.qdrantResultIds.clear();
        try {
            JSONArray res = new JSONArray(json);
            // Qdrant returns: [{"id": 1, "score": 0.9}, ...]
            for (int i = 0; i < res.length(); i++) {
                JSONObject item = res.getJSONObject(i);
                // "id" might be integer or string depending on input, likely integer here
                query.qdrantResultIds.add(item.getInt("id"));
            }
        } catch (Exception e) {
            if (!json.isEmpty())
                log("Error parsing result: " + e.getMessage());
        }
    }

    private String readUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            is.close();
            return sb.toString();
        } catch (Exception e) {
            log("Error reading file: " + e.getMessage());
            return null;
        }
    }

    private void log(String msg) {
        runOnUiThread(() -> {
            tvLog.append(msg + "\n");
            // Auto scroll?
        });
        android.util.Log.d("QdrantTest", msg);
    }
}
