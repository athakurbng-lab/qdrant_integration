package com.qdrant.client;

public class OfflineQdrant {
    static {
        System.loadLibrary("qdrant_offline_android");
    }

    /**
     * Initialize the offline Qdrant instance.
     * 
     * @param storagePath Path to the directory where Qdrant data will be stored.
     * @return "OK" if initialization was successful, error message otherwise.
     */
    public static native String init(String storagePath);

    /**
     * Create a new collection.
     * 
     * @param collectionName Name of the collection.
     * @param configJson     JSON string configuration for the collection
     *                       (optional).
     * @return true if successful, false otherwise.
     */
    public static native boolean createCollection(String collectionName, String configJson);

    /**
     * Perform an update operation (upsert, delete, etc.).
     * 
     * @param collectionName Name of the collection.
     * @param operationJson  JSON string representing the update operation.
     * @return true if successful, false otherwise.
     */
    public static native boolean update(String collectionName, String operationJson);

    /**
     * Search for points in a collection.
     * 
     * @param collectionName    Name of the collection.
     * @param searchRequestJson JSON string representing the search request.
     * @return JSON string of the search results (list of ScoredPoint).
     */
    public static native String search(String collectionName, String searchRequestJson);
}
