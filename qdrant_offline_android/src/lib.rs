use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jstring};
use std::sync::Arc;
use tokio::runtime::Runtime;
use once_cell::sync::OnceCell;

use storage::content_manager::toc::TableOfContent;
use storage::types::StorageConfig;
use storage::rbac::Access;
use storage::dispatcher::Dispatcher;
use storage::content_manager::collection_meta_ops::{
    CollectionMetaOperations, CreateCollectionOperation, CreateCollection
};

use collection::shards::channel_service::ChannelService;
use collection::operations::types::SearchRequest;
use shard::search::CoreSearchRequestBatch;
use collection::operations::point_ops::WriteOrdering;
use collection::operations::OperationWithClockTag;
use collection::operations::shard_selector_internal::ShardSelectorInternal;
use collection::operations::CollectionUpdateOperations; // Qdrant - Common

use common::budget::ResourceBudget;
use common::counter::hardware_accumulator::HwMeasurementAcc;

use api::rest::ScoredPoint;

static RUNTIME: OnceCell<Runtime> = OnceCell::new();
static TOC: OnceCell<Arc<TableOfContent>> = OnceCell::new();

#[no_mangle]
pub extern "system" fn Java_com_qdrant_client_OfflineQdrant_init(
    mut env: JNIEnv,
    _class: JClass,
    path: JString
) -> jstring {
    let path: String = match env.get_string(&path) {
        Ok(s) => s.into(),
        Err(e) => return env.new_string(format!("INIT_ERROR: path arg {}", e)).unwrap().into_raw(),
    };

    // If TOC is already accessible, we are initialized.
    if TOC.get().is_some() {
        return env.new_string("OK").unwrap().into_raw();
    }

    let rt = Runtime::new().unwrap();
    let runtime = RUNTIME.get_or_init(|| rt);

    // Minimal storage config for offline use
    let config_json = format!(r#"
    {{
        "storage_path": "{}",
        "snapshots_path": "{}/snapshots",
        "on_disk_payload": false,
        "optimizers": {{
            "deleted_threshold": 0.2,
            "vacuum_min_vector_number": 1000,
            "default_segment_number": 2,
            "max_segment_size_kb": 200000,
            "memmap_threshold_kb": 50000,
            "indexing_threshold_kb": 20000,
            "flush_interval_sec": 5,
            "max_optimization_threads": 1
        }},
        "wal": {{
            "wal_capacity_mb": 32,
            "wal_segments_ahead": 0
        }},
        "performance": {{
            "max_search_threads": 0,
            "max_optimization_runtime_threads": 0,
            "optimizer_cpu_budget": 0,
            "optimizer_io_budget": 0
        }},
        "hnsw_index": {{
            "m": 16,
            "ef_construct": 100,
            "full_scan_threshold_kb": 10000,
            "max_indexing_threads": 0,
            "on_disk": false
        }}
    }}
    "#, path, path);

    let config: StorageConfig = match serde_json::from_str(&config_json) {
        Ok(c) => c,
        Err(e) => {
             return env.new_string(format!("INIT_ERROR: config parse {}", e)).unwrap().into_raw();
        }
    };

    // TableOfContent::new is synchronous, not async - don't wrap in block_on
    let search_runtime = Runtime::new().unwrap();
    let update_runtime = Runtime::new().unwrap();
    let general_runtime = Runtime::new().unwrap();
    let channel_service = ChannelService::new(6333, None);
    let this_peer_id = 0;
    let consensus_proposal_sender = None;

    let toc = TableOfContent::new(
        &config,
        search_runtime,
        update_runtime,
        general_runtime,
        ResourceBudget::default(),
        channel_service,
        this_peer_id,
        consensus_proposal_sender,
    );

    match TOC.set(Arc::new(toc)) {
        Ok(_) => env.new_string("OK").unwrap().into_raw(),
        Err(_) => env.new_string("INIT_ERROR: TOC already set").unwrap().into_raw(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_qdrant_client_OfflineQdrant_createCollection(
    mut env: JNIEnv,
    _class: JClass,
    collection_name: JString,
    config_json: JString
) -> jboolean {
    let collection_name: String = match env.get_string(&collection_name) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    
    let config_json_str: String = match env.get_string(&config_json) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    let create_collection: CreateCollection = match serde_json::from_str(&config_json_str) {
        Ok(c) => c,
        Err(e) => {
             eprintln!("Failed to parse create collection config: {}", e);
             return 0;
        }
    };

    let toc = match TOC.get() {
        Some(t) => t.clone(),
        None => return 0,
    };
    let runtime = match RUNTIME.get() {
        Some(r) => r,
        None => return 0,
    };

    let result = runtime.block_on(async {
        let dispatcher = Dispatcher::new(toc);
        let create_collection_op = match CreateCollectionOperation::new(collection_name.clone(), create_collection) {
            Ok(op) => op,
            Err(e) => return Err(storage::content_manager::errors::StorageError::BadInput { description: format!("Invalid collection config: {}", e) }),
        };
        
        let op = CollectionMetaOperations::CreateCollection(create_collection_op);
        dispatcher.submit_collection_meta_op(op, Access::full("Offline"), Some(std::time::Duration::from_secs(60))).await
    });

    match result {
        Ok(true) => 1,
        Ok(false) => 0,
        Err(e) => {
            eprintln!("Create collection error: {}", e);
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_qdrant_client_OfflineQdrant_update(
    mut env: JNIEnv,
    _class: JClass,
    collection_name: JString,
    operation_json: JString
) -> jboolean {
    let collection_name: String = match env.get_string(&collection_name) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    
    let operation_json_str: String = match env.get_string(&operation_json) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    let op: CollectionUpdateOperations = match serde_json::from_str(&operation_json_str) {
        Ok(c) => c,
        Err(e) => {
             eprintln!("Failed to parse update operation: {}", e);
             return 0;
        }
    };

    let toc = match TOC.get() {
        Some(t) => t,
        None => return 0,
    };
    let runtime = match RUNTIME.get() {
        Some(r) => r,
        None => return 0,
    };

    let result = runtime.block_on(async {
        toc.update(
            &collection_name,
            OperationWithClockTag::from(op),
            true, 
            WriteOrdering::default(),
            ShardSelectorInternal::All,
            Access::full("Offline"),
            HwMeasurementAcc::disposable(),
        ).await
    });

    match result {
        Ok(_) => 1,
        Err(e) => {
            eprintln!("Update error: {}", e);
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_qdrant_client_OfflineQdrant_search(
    mut env: JNIEnv,
    _class: JClass,
    collection_name: JString,
    search_request_json: JString
) -> jstring {
    let collection_name: String = match env.get_string(&collection_name) {
        Ok(s) => s.into(),
        Err(e) => return env.new_string(format!("ARG_ERROR: name {}", e)).unwrap().into_raw(),
    };
    
    let json_str: String = match env.get_string(&search_request_json) {
        Ok(s) => s.into(),
        Err(e) => return env.new_string(format!("ARG_ERROR: json {}", e)).unwrap().into_raw(),
    };

    let req: SearchRequest = match serde_json::from_str(&json_str) {
        Ok(c) => c,
        Err(e) => {
             let msg = format!("JSON_ERROR: {}", e);
             return env.new_string(msg).unwrap().into_raw();
        }
    };

    let toc = match TOC.get() {
        Some(t) => t,
        None => return env.new_string("STATE_ERROR: TOC not initialized").unwrap().into_raw(),
    };
    let runtime = match RUNTIME.get() {
        Some(r) => r,
        None => return env.new_string("STATE_ERROR: Runtime not initialized").unwrap().into_raw(),
    };

    let result = runtime.block_on(async {
        let core_req = req.search_request;
        let batch = CoreSearchRequestBatch { searches: vec![core_req.into()] };
        
        toc.core_search_batch(
            &collection_name,
            batch,
            None, // Consistency
            ShardSelectorInternal::All,
            Access::full("Offline"),
            None, // timeout
            HwMeasurementAcc::disposable(),
        ).await
    });

    match result {
        Ok(mut batch_result) => {
            let points: Vec<ScoredPoint> = batch_result.pop().unwrap_or_default()
                .into_iter()
                .map(ScoredPoint::from)
                .collect();
            
            // Log point count via new_string hack or just assume error return covers it?
            // Easier to just return the json or error.
            match serde_json::to_string(&points) {
                Ok(json_out) => env.new_string(json_out).unwrap().into_raw(),
                Err(e) => {
                     let msg = format!("SERIALIZE_ERROR: pts={} err={}", points.len(), e);
                     env.new_string(msg).unwrap().into_raw()
                }
            }
        },
        Err(e) => {
            let msg = format!("SEARCH_ERROR: {}", e);
            env.new_string(msg).unwrap().into_raw()
        }
    }
}
