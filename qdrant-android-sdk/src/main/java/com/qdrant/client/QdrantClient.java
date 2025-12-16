package com.qdrant.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.ArrayList;

// Generated proto classes (guessing package structure based on proto exploration)
import qdrant.PointsGrpc;
import qdrant.Points;
import qdrant.Common;
import qdrant.JsonWithInt;

public class QdrantClient {
    private static final Logger logger = Logger.getLogger(QdrantClient.class.getName());
    private final ManagedChannel channel;
    private final PointsGrpc.PointsBlockingStub pointsStub;
    private static final String SOFT_DELETE_FIELD = "__soft_deleted";
    private static final int SOFT_DELETE_THRESHOLD = 500;

    public QdrantClient(String host, int port) {
        this(ManagedChannelBuilder.forAddress(host, port).usePlaintext().build());
    }

    public QdrantClient(ManagedChannel channel) {
        this.channel = channel;
        this.pointsStub = PointsGrpc.newBlockingStub(channel);
    }

    public void close() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    /**
     * Upsert points into the collection.
     */
    public void upsert(String collectionName, List<Points.PointStruct> points, boolean wait) {
        Points.UpsertPoints request = Points.UpsertPoints.newBuilder()
                .setCollectionName(collectionName)
                .addAllPoints(points)
                .setWait(wait)
                .build();
        pointsStub.upsert(request);
    }

    /**
     * Search for nearest neighbors.
     * Automatically filters out soft-deleted items if not specified otherwise.
     */
    public List<Points.ScoredPoint> search(String collectionName, List<Float> vector, int limit) {
        // Default filter to exclude soft-deleted items
        Common.Filter softDeleteFilter = Common.Filter.newBuilder()
                .addMustNot(Common.Condition.newBuilder()
                        .setField(Common.FieldCondition.newBuilder()
                                .setKey(SOFT_DELETE_FIELD)
                                .setMatch(Common.Match.newBuilder()
                                        .setBoolean(true).build())
                                .build())
                        .build())
                .build();

        Points.SearchPoints request = Points.SearchPoints.newBuilder()
                .setCollectionName(collectionName)
                .addAllVector(vector)
                .setLimit(limit)
                .setFilter(softDeleteFilter) // Apply soft delete filter by default
                .build();

        return pointsStub.search(request).getResultList();
    }

    /**
     * Hard Delete: Permanently remove points.
     */
    public void hardDelete(String collectionName, Points.PointsSelector pointsSelector, boolean wait) {
        Points.DeletePoints request = Points.DeletePoints.newBuilder()
                .setCollectionName(collectionName)
                .setPoints(pointsSelector)
                .setWait(wait)
                .build();
        pointsStub.delete(request);
    }

    /**
     * Soft Delete: Mark points as deleted in metadata.
     * Triggers auto-cleanup (hard delete) if threshold is exceeded.
     */
    public void softDelete(String collectionName, List<Common.PointId> ids, boolean wait) {
        // 1. Mark fields as soft deleted
        Map<String, JsonWithInt.Value> payload = new HashMap<>();
        payload.put(SOFT_DELETE_FIELD, JsonWithInt.Value.newBuilder().setBoolValue(true).build());

        Points.PointsSelector selector = Points.PointsSelector.newBuilder()
                .setPoints(Points.PointsIdsList.newBuilder().addAllIds(ids).build())
                .build();

        Points.SetPayloadPoints request = Points.SetPayloadPoints.newBuilder()
                .setCollectionName(collectionName)
                .setPointsSelector(selector)
                .putAllPayload(payload)
                .setWait(wait)
                .build();

        pointsStub.setPayload(request);

        // 2. Check and Cleanup
        checkAndCleanup(collectionName);
    }

    private void checkAndCleanup(String collectionName) {
        // Count soft-deleted items
        Common.Filter softDeletedFilter = Common.Filter.newBuilder()
                .addMust(Common.Condition.newBuilder()
                        .setField(Common.FieldCondition.newBuilder()
                                .setKey(SOFT_DELETE_FIELD)
                                .setMatch(Common.Match.newBuilder()
                                        .setBoolean(true).build())
                                .build())
                        .build())
                .build();

        Points.CountPoints countRequest = Points.CountPoints.newBuilder()
                .setCollectionName(collectionName)
                .setFilter(softDeletedFilter)
                .setExact(true)
                .build();

        Points.CountResponse response = pointsStub.count(countRequest);

        if (response.getResult().getCount() > SOFT_DELETE_THRESHOLD) {
            logger.info("Soft delete threshold exceeded (" + response.getResult().getCount()
                    + "). Triggering hard delete cleanup.");

            // Hard delete all items matching the soft-delete filter
            Points.PointsSelector filterSelector = Points.PointsSelector.newBuilder()
                    .setFilter(softDeletedFilter)
                    .build();

            hardDelete(collectionName, filterSelector, false);
        }
    }
}
