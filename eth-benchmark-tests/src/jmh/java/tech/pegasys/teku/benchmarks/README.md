# JMH Micro benchmarks

These benchmarks can be  easily run in Intellij by installing the JMH plugin.
JMH benchmarks can be run from the eth-benchmark-tests by executing JMH tests under eth-benchmark-tests

```bash
./gradlew jmh
```

JMH is a very comprehensive benchmarking library. https://github.com/openjdk/jmh

## Snappy implementation benchmark

`SnappyCompressionBenchmark` compares four Snappy block-compression implementations across realistic
gossip payloads: snappy-java (JNI native, current gossip path), Netty (pure-Java, current RPC path),
aircompressor-v3 native (FFM via `java.lang.foreign`), and aircompressor-v3 pure-Java. It also
asserts, on startup, that every implementation produces a Snappy block wire format mutually decodable
with snappy-java — which additionally proves the aircompressor FFM native library loaded on this
platform.

Run all payload/implementation combinations with allocation profiling:

```bash
./gradlew :eth-benchmark-tests:jmh --args="SnappyCompressionBenchmark -prof gc"
```

Run a single payload (e.g. the large data-column-sidecar case):

```bash
./gradlew :eth-benchmark-tests:jmh --args="SnappyCompressionBenchmark -prof gc -p payload=DATA_COLUMN_SIDECAR"
```

Run a single implementation (e.g. just the aircompressor FFM-native path):

```bash
./gradlew :eth-benchmark-tests:jmh --args="SnappyCompressionBenchmark -prof gc -p impl=AIRCOMPRESSOR_NATIVE"
```

Throughput/latency comes from the standard JMH score columns; allocation per op comes from the
`gc.alloc.rate.norm` row reported by `-prof gc`.