/*
 * Copyright (c) 2026 Consensys Incorporated. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.benchmarks;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import tech.pegasys.teku.ethereum.executionclient.schema.BlobAndProofV2;
import tech.pegasys.teku.ethereum.executionclient.schema.BlobsBundleV2;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV3;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV5Response;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

/**
 * Benchmarks Jackson deserialization for Engine API engine_getBlobsV2 and engine_getPayloadV5. Uses
 * the same ObjectMapper configuration as AbstractExecutionEngineClient
 *
 * <p>Run with: ./gradlew :eth-benchmark-tests:jmh --tests "*.EngineApiDeserializationBenchmark"
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class EngineApiDeserializationBenchmark {

  private static final int BLOBS_PER_BLOCK = 71;
  private static final int CELLS_PER_BLOB = 128;
  private static final int BYTES_PER_BLOB = 131072;
  private static final int BYTES_PER_PROOF = 48;

  // Same ObjectMapper configuration as AbstractExecutionEngineClient
  private static final ObjectMapper OBJECT_MAPPER =
      JsonMapper.builder()
          .addModule(new BlackbirdModule())
          .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .build();

  private static final TypeReference<List<BlobAndProofV2>> BLOBS_V2_TYPE = new TypeReference<>() {};
  private static final TypeReference<GetPayloadV5Response> GET_PAYLOAD_V5_RESPONSE_TYPE =
      new TypeReference<>() {};

  private String blobsAndProofsV2Json;
  private String getPayloadV5ResponseJson;

  @Setup(Level.Trial)
  public void setup() throws Exception {
    final List<BlobAndProofV2> blobsAndProofsV2 =
        IntStream.range(0, BLOBS_PER_BLOCK)
            .mapToObj(
                i ->
                    new BlobAndProofV2(
                        Bytes.random(BYTES_PER_BLOB),
                        IntStream.range(0, CELLS_PER_BLOB)
                            .mapToObj(j -> Bytes48.wrap(Bytes.random(BYTES_PER_PROOF)))
                            .toList()))
            .toList();

    blobsAndProofsV2Json = OBJECT_MAPPER.writeValueAsString(blobsAndProofsV2);

    DataStructureUtil dataStructureUtil =
        new DataStructureUtil(TestSpecFactory.createMainnetFulu());

    GetPayloadV5Response getPayloadV5Response =
        new GetPayloadV5Response(
            ExecutionPayloadV3.fromInternalExecutionPayload(
                dataStructureUtil.randomExecutionPayload()),
            dataStructureUtil.randomUInt256(),
            BlobsBundleV2.fromInternalBlobsBundle(
                dataStructureUtil.randomBlobsBundle(BLOBS_PER_BLOCK)),
            false,
            dataStructureUtil.randomEncodedExecutionRequests());

    getPayloadV5ResponseJson = OBJECT_MAPPER.writeValueAsString(getPayloadV5Response);
  }

  @Benchmark
  public List<BlobAndProofV2> deserializeGetBlobsV2Response() throws Exception {
    return OBJECT_MAPPER.readValue(blobsAndProofsV2Json, BLOBS_V2_TYPE);
  }

  @Benchmark
  public GetPayloadV5Response deserializeGetPayloadV5Response() throws Exception {
    return OBJECT_MAPPER.readValue(getPayloadV5ResponseJson, GET_PAYLOAD_V5_RESPONSE_TYPE);
  }
}
