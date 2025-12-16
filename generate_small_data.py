import json
import random
import math
import time

def normalize(vec):
    norm = math.sqrt(sum(x*x for x in vec))
    if norm == 0: return vec
    return [x/norm for x in vec]

def generate_vector(dim):
    # Using simple uniform random.
    vec = [random.uniform(-1.0, 1.0) for _ in range(dim)]
    return normalize(vec)

DIM = 384
NUM_UPSERT = 5000
NUM_SEARCH = 100

print(f"Generating {NUM_UPSERT} vectors of dimension {DIM}...")
start_time = time.time()

points = []
for i in range(NUM_UPSERT):
    vec = generate_vector(DIM)
    points.append({
        "id": i + 1,
        "vector": vec,
        "payload": {"i": i}
    })
    if (i + 1) % 1000 == 0:
        print(f"  Generated {i + 1} points...")

upsert_data = {
    "upsert_points": {
        "points": points
    }
}

print("Writing upsert_5k.json...")
with open("upsert_5k.json", "w") as f:
    json.dump(upsert_data, f)

print(f"Generating {NUM_SEARCH} search vectors...")
searches = []
for i in range(NUM_SEARCH):
    vec = generate_vector(DIM)
    searches.append({
        "vector": vec,
        "limit": 10
    })

print("Writing search_100.json...")
with open("search_100.json", "w") as f:
    json.dump(searches, f) # List of search objects

print(f"Done in {time.time() - start_time:.2f}s")
