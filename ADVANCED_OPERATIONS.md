# Advanced Operations Guide

This guide covers advanced features including deletion strategies, filtering, and complex queries.

## 🗑️ Deletion Operations

### Hard Delete (Permanent Removal)

Hard delete permanently removes points from the database.

```java
// Delete single point by ID
String deleteJson = """
{
  "delete": {
    "points": [123]
  }
}
""";

boolean success = OfflineQdrant.update("my_collection", deleteJson);
```

```java
// Delete multiple points
String deleteJson = """
{
  "delete": {
    "points": [1, 2, 3, 4, 5]
  }
}
""";

boolean success = OfflineQdrant.update("my_collection", deleteJson);
```

### Soft Delete (Mark as Deleted)

Soft delete keeps the data but marks it as deleted using a payload field.

#### 1. Mark as Deleted

```java
// Update point to mark as deleted
String softDeleteJson = """
{
  "points": [
    {
      "id": 123,
      "vector": [0.1, 0.2, ...],  // Keep same vector
      "payload": {
        "text": "Original content",
        "deleted": true,              // ← Soft delete flag
        "deleted_at": 1734364824000,  // Timestamp
        "deleted_by": "user_id_456"   // Who deleted it
      }
    }
  ]
}
""";

boolean success = OfflineQdrant.update("my_collection", softDeleteJson);
```

#### 2. Filter Out Soft-Deleted Items

```java
// Search excluding soft-deleted items
String searchJson = """
{
  "vector": [0.1, 0.2, 0.3, ...],
  "limit": 10,
  "filter": {
    "must_not": [
      {
        "key": "deleted",
        "match": {
          "value": true
        }
      }
    ]
  }
}
""";

String results = OfflineQdrant.search("my_collection", searchJson);
```

#### 3. Restore Soft-Deleted Item

```java
// Undelete by updating payload
String restoreJson = """
{
  "points": [
    {
      "id": 123,
      "vector": [0.1, 0.2, ...],  // Keep same vector
      "payload": {
        "text": "Original content",
        "deleted": false,             // ← Restore
        "restored_at": 1734365000000
      }
    }
  ]
}
""";

boolean success = OfflineQdrant.update("my_collection", restoreJson);
```

## 🔍 Filtering and Query Conditions

### Basic Filters

#### Match Exact Value

```java
String searchJson = """
{
  "vector": [0.1, 0.2, ...],
  "limit": 10,
  "filter": {
    "must": [
      {
        "key": "category",
        "match": {
          "value": "sports"
        }
      }
    ]
  }
}
""";
```

#### Multiple Conditions (AND)

```java
// Find: category = "news" AND author = "John"
String searchJson = """
{
  "vector": [0.1, 0.2, ...],
  "limit": 10,
  "filter": {
    "must": [
      {
        "key": "category",
        "match": {"value": "news"}
      },
      {
        "key": "author",
        "match": {"value": "John"}
      }
    ]
  }
}
""";
```

#### Exclude Conditions (NOT)

```java
// Exclude category = "spam"
String searchJson = """
{
  "vector": [0.1, 0.2, ...],
  "limit": 10,
  "filter": {
    "must_not": [
      {
        "key": "category",
        "match": {"value": "spam"}
      }
    ]
  }
}
""";
```

#### OR Conditions

```java
// Find: category = "sports" OR category = "news"
String searchJson = """
{
  "vector": [0.1, 0.2, ...],
  "limit": 10,
  "filter": {
    "should": [
      {
        "key": "category",
        "match": {"value": "sports"}
      },
      {
        "key": "category",
        "match": {"value": "news"}
      }
    ]
  }
}
""";
```

### Range Filters

#### Numeric Range

```java
// Find: rating >= 4.0
String searchJson = """
{
  "vector": [0.1, 0.2, ...],
  "limit": 10,
  "filter": {
    "must": [
      {
        "key": "rating",
        "range": {
          "gte": 4.0
        }
      }
    ]
  }
}
""";
```

```java
// Find: views between 1000 and 5000
String searchJson = """
{
  "vector": [0.1, 0.2, ...],
  "limit": 10,
  "filter": {
    "must": [
      {
        "key": "views",
        "range": {
          "gte": 1000,
          "lte": 5000
        }
      }
    ]
  }
}
""";
```

### Complex Queries

#### Combining Multiple Conditions

```java
// Find: 
// - category = "technology" 
// - NOT deleted
// - rating >= 4.0
// - published in last 30 days
String searchJson = """
{
  "vector": [0.1, 0.2, ...],
  "limit": 10,
  "filter": {
    "must": [
      {
        "key": "category",
        "match": {"value": "technology"}
      },
      {
        "key": "rating",
        "range": {"gte": 4.0}
      },
      {
        "key": "published_at",
        "range": {"gte": 1731686400000}
      }
    ],
    "must_not": [
      {
        "key": "deleted",
        "match": {"value": true}
      }
    ]
  }
}
""";
```

## 💡 Best Practices

### Soft Delete vs Hard Delete

| Criteria | Soft Delete | Hard Delete |
|----------|-------------|-------------|
| **Recoverability** | ✅ Can restore | ❌ Permanent |
| **Audit Trail** | ✅ Keep history | ❌ Lost |
| **Performance** | Slower (more data) | Faster (less data) |
| **Storage** | More space | Less space |
| **Use Case** | User data, legal requirements | Spam, test data |

### Recommended Approach

1. **Soft delete for user-generated content**
   ```java
   payload.put("deleted", true);
   payload.put("deleted_at", System.currentTimeMillis());
   payload.put("deleted_by", userId);
   ```

2. **Hard delete for temporary/test data**
   ```java
   {"delete": {"points": [1, 2, 3]}}
   ```

3. **Periodic cleanup job**
   ```java
   // After 30 days, hard delete soft-deleted items
   long cutoff = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
   // Query soft-deleted items older than cutoff
   // Hard delete them
   ```

## 🔄 Full Example: Soft Delete Workflow

```java
public class DocumentManager {
    private static final String COLLECTION = "documents";
    
    // 1. Create document
    public void createDocument(int id, float[] embedding, String text) {
        JSONObject point = new JSONObject();
        point.put("id", id);
        point.put("vector", new JSONArray(embedding));
        
        JSONObject payload = new JSONObject();
        payload.put("text", text);
        payload.put("deleted", false);
        payload.put("created_at", System.currentTimeMillis());
        point.put("payload", payload);
        
        JSONObject request = new JSONObject();
        request.put("points", new JSONArray().put(point));
        
        OfflineQdrant.update(COLLECTION, request.toString());
    }
    
    // 2. Soft delete document
    public void softDeleteDocument(int id, float[] embedding, String text) {
        JSONObject point = new JSONObject();
        point.put("id", id);
        point.put("vector", new JSONArray(embedding));
        
        JSONObject payload = new JSONObject();
        payload.put("text", text);
        payload.put("deleted", true);
        payload.put("deleted_at", System.currentTimeMillis());
        point.put("payload", payload);
        
        JSONObject request = new JSONObject();
        request.put("points", new JSONArray().put(point));
        
        OfflineQdrant.update(COLLECTION, request.toString());
    }
    
    // 3. Search excluding deleted
    public List<Document> searchActiveDocuments(float[] queryVector) {
        JSONObject search = new JSONObject();
        search.put("vector", new JSONArray(queryVector));
        search.put("limit", 10);
        
        // Filter out deleted items
        JSONObject filter = new JSONObject();
        JSONArray mustNot = new JSONArray();
        JSONObject deletedFilter = new JSONObject();
        deletedFilter.put("key", "deleted");
        deletedFilter.put("match", new JSONObject().put("value", true));
        mustNot.put(deletedFilter);
        filter.put("must_not", mustNot);
        search.put("filter", filter);
        
        String results = OfflineQdrant.search(COLLECTION, search.toString());
        return parseResults(results);
    }
    
    // 4. Hard delete document
    public void hardDeleteDocument(int id) {
        JSONObject delete = new JSONObject();
        delete.put("points", new JSONArray().put(id));
        
        JSONObject request = new JSONObject();
        request.put("delete", delete);
        
        OfflineQdrant.update(COLLECTION, request.toString());
    }
}
```

## 📊 Filter Operators Reference

| Operator | Usage | Example |
|----------|-------|---------|
| `must` | AND condition | All must match |
| `should` | OR condition | At least one must match |
| `must_not` | NOT condition | None should match |
| `match` | Exact value | `{"value": "text"}` |
| `range` | Numeric range | `{"gte": 0, "lte": 100}` |

### Range Operators

- `gt`: Greater than
- `gte`: Greater than or equal
- `lt`: Less than
- `lte`: Less than or equal

---

**Best Practice**: Always filter soft-deleted items in your searches unless you specifically want to show deleted content (e.g., trash/recycle bin view).
