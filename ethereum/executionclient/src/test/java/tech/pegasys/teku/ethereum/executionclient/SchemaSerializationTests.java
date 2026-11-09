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

package tech.pegasys.teku.ethereum.executionclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV4;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes20Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes20Serializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes32Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes8Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesSerializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt256AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt256AsHexSerializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt64AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt64AsHexSerializer;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(milestone = SpecMilestone.BELLATRIX)
public class SchemaSerializationTests {
  Writer jsonWriter;
  JsonGenerator jsonGenerator;
  ObjectMapper objectMapper;
  SerializerProvider serializerProvider;
  DataStructureUtil dataStructureUtil;

  @BeforeEach
  void setUp(final SpecContext specContext) throws IOException {
    jsonWriter = new StringWriter();
    jsonGenerator = new JsonFactory().createGenerator(jsonWriter);
    objectMapper = new ObjectMapper();
    serializerProvider = objectMapper.getSerializerProvider();
    dataStructureUtil = specContext.getDataStructureUtil();
  }

  @TestTemplate
  void shouldDeserializeBytes8() throws IOException {
    Bytes8 originalBytes8 = dataStructureUtil.randomBytes8();

    JsonParser parser = prepareDeserializationContext("\"" + originalBytes8.toHexString() + "\"");
    Bytes8Deserializer deserializer = new Bytes8Deserializer();
    Bytes8 result = deserializer.deserialize(parser, objectMapper.getDeserializationContext());

    assertThat(originalBytes8).isEqualTo(result);
  }

  @TestTemplate
  void shouldSerializeDeserializeBytes20() throws IOException {
    Bytes20 originalBytes20 = dataStructureUtil.randomBytes20();

    new Bytes20Serializer().serialize(originalBytes20, jsonGenerator, serializerProvider);
    jsonGenerator.flush();

    JsonParser parser = prepareDeserializationContext(jsonWriter.toString());
    Bytes20Deserializer deserializer = new Bytes20Deserializer();
    Bytes20 result = deserializer.deserialize(parser, objectMapper.getDeserializationContext());

    assertThat(originalBytes20).isEqualTo(result);
  }

  @TestTemplate
  void shouldSerializeDeserializeBytes32() throws IOException {
    Bytes32 originalBytes32 = dataStructureUtil.randomBytes32();

    new BytesSerializer().serialize(originalBytes32, jsonGenerator, serializerProvider);
    jsonGenerator.flush();

    JsonParser parser = prepareDeserializationContext(jsonWriter.toString());
    Bytes32Deserializer deserializer = new Bytes32Deserializer();
    Bytes32 result = deserializer.deserialize(parser, objectMapper.getDeserializationContext());

    assertThat(originalBytes32).isEqualTo(result);
  }

  @TestTemplate
  void shouldSerializeDeserializeUInt64() throws IOException {
    UInt64 originalUInt64 = dataStructureUtil.randomUInt64();

    new UInt64AsHexSerializer().serialize(originalUInt64, jsonGenerator, serializerProvider);
    jsonGenerator.flush();

    JsonParser parser = prepareDeserializationContext(jsonWriter.toString());
    UInt64AsHexDeserializer deserializer = new UInt64AsHexDeserializer();
    UInt64 result = deserializer.deserialize(parser, objectMapper.getDeserializationContext());

    assertThat(originalUInt64).isEqualTo(result);
  }

  @TestTemplate
  void shouldSerializeDeserializeUInt256() throws IOException {
    UInt256 originalUInt256 = dataStructureUtil.randomUInt256();

    new UInt256AsHexSerializer().serialize(originalUInt256, jsonGenerator, serializerProvider);
    jsonGenerator.flush();

    JsonParser parser = prepareDeserializationContext(jsonWriter.toString());
    UInt256AsHexDeserializer deserializer = new UInt256AsHexDeserializer();
    UInt256 result = deserializer.deserialize(parser, objectMapper.getDeserializationContext());

    assertThat(originalUInt256).isEqualTo(result);
  }

  @TestTemplate
  void shouldThrowDeserializingUIntQuantityNot0xPrefixed() {

    assertThrows(
        IllegalArgumentException.class,
        () -> {
          JsonParser parser = prepareDeserializationContext("123");
          UInt256AsHexDeserializer deserializer = new UInt256AsHexDeserializer();
          deserializer.deserialize(parser, objectMapper.getDeserializationContext());
        });

    assertThrows(
        IllegalArgumentException.class,
        () -> {
          JsonParser parser = prepareDeserializationContext("123");
          UInt64AsHexDeserializer deserializer = new UInt64AsHexDeserializer();
          deserializer.deserialize(parser, objectMapper.getDeserializationContext());
        });
  }

  @TestTemplate
  void shouldThrowDeserializingUIntQuantityWithLeadingZeros() {

    assertThrows(
        IllegalArgumentException.class,
        () -> {
          JsonParser parser = prepareDeserializationContext("\"0x00123\"");
          UInt256AsHexDeserializer deserializer = new UInt256AsHexDeserializer();
          deserializer.deserialize(parser, objectMapper.getDeserializationContext());
        });

    assertThrows(
        IllegalArgumentException.class,
        () -> {
          JsonParser parser = prepareDeserializationContext("\"0x00123\"");
          UInt64AsHexDeserializer deserializer = new UInt64AsHexDeserializer();
          deserializer.deserialize(parser, objectMapper.getDeserializationContext());
        });
  }

  @TestTemplate
  void shouldThrowDeserializingUInt0xQuantity() {

    assertThrows(
        IllegalArgumentException.class,
        () -> {
          JsonParser parser = prepareDeserializationContext("\"0x\"");
          UInt256AsHexDeserializer deserializer = new UInt256AsHexDeserializer();
          deserializer.deserialize(parser, objectMapper.getDeserializationContext());
        });

    assertThrows(
        IllegalArgumentException.class,
        () -> {
          JsonParser parser = prepareDeserializationContext("\"0x\"");
          UInt64AsHexDeserializer deserializer = new UInt64AsHexDeserializer();
          deserializer.deserialize(parser, objectMapper.getDeserializationContext());
        });
  }

  @TestTemplate
  void shouldDeserializingUInt256Quantity0x0() throws IOException {
    JsonParser parser = prepareDeserializationContext("\"0x0\"");
    UInt256AsHexDeserializer deserializer = new UInt256AsHexDeserializer();
    deserializer.deserialize(parser, objectMapper.getDeserializationContext());
  }

  @TestTemplate
  void shouldDeserializingUInt64Quantity0x0() throws IOException {
    JsonParser parser = prepareDeserializationContext("\"0x0\"");
    UInt64AsHexDeserializer deserializer = new UInt64AsHexDeserializer();
    deserializer.deserialize(parser, objectMapper.getDeserializationContext());
  }

  private JsonParser prepareDeserializationContext(final String serializedValue)
      throws IOException {
    String json = String.format("{\"value\":%s}", serializedValue);
    JsonParser parser = objectMapper.getFactory().createParser(json);
    parser.nextToken();
    parser.nextToken();
    parser.nextToken();
    return parser;
  }

  @TestTemplate
  void shouldSerializeDeserializeForkChoiceStateV1() throws IOException {
    ForkChoiceStateV1 forkChoiceStateV1Orig =
        new ForkChoiceStateV1(
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32());

    String forkChoiceStateV1OrigSerialized = objectMapper.writeValueAsString(forkChoiceStateV1Orig);
    ForkChoiceStateV1 forkChoiceStateV1New =
        objectMapper.readValue(forkChoiceStateV1OrigSerialized, ForkChoiceStateV1.class);

    assertThat(forkChoiceStateV1Orig).isEqualTo(forkChoiceStateV1New);
  }

  @TestTemplate
  void shouldSerializeDeserializePayloadAttributesV1() throws IOException {
    PayloadAttributesV1 payloadAttributesV1Orig =
        new PayloadAttributesV1(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes20());

    String payloadAttributesV1OrigSerialized =
        objectMapper.writeValueAsString(payloadAttributesV1Orig);
    PayloadAttributesV1 payloadAttributesV1New =
        objectMapper.readValue(payloadAttributesV1OrigSerialized, PayloadAttributesV1.class);

    assertThat(payloadAttributesV1Orig).isEqualTo(payloadAttributesV1New);
  }

  @TestTemplate
  void shouldSerializeDeserializePayloadAttributesV4() throws IOException {
    PayloadAttributesV4 payloadAttributesV4Orig =
        new PayloadAttributesV4(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes20(),
            List.of(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64());

    String payloadAttributesV4OrigSerialized =
        objectMapper.writeValueAsString(payloadAttributesV4Orig);
    PayloadAttributesV4 payloadAttributesV4New =
        objectMapper.readValue(payloadAttributesV4OrigSerialized, PayloadAttributesV4.class);

    assertThat(payloadAttributesV4Orig).isEqualTo(payloadAttributesV4New);
  }

  @TestTemplate
  void shouldSerializeDeserializeExecutionPayloadV1() throws IOException {
    ExecutionPayload internalExecutionPayload = dataStructureUtil.randomExecutionPayload();
    ExecutionPayloadV1 executionPayloadV1Orig =
        ExecutionPayloadV1.fromInternalExecutionPayload(internalExecutionPayload);

    String executionPayloadV1OrigSerialized =
        objectMapper.writeValueAsString(executionPayloadV1Orig);
    ExecutionPayloadV1 executionPayloadV1New =
        objectMapper.readValue(executionPayloadV1OrigSerialized, ExecutionPayloadV1.class);

    assertThat(executionPayloadV1Orig).isEqualTo(executionPayloadV1New);
    assertThat(
            executionPayloadV1Orig.asInternalExecutionPayload(internalExecutionPayload.getSchema()))
        .isEqualTo(internalExecutionPayload);
  }

  @TestTemplate
  void shouldDeserializePayloadStatusWithNulls() throws IOException {
    PayloadStatusV1 payloadStatusV1Expected =
        new PayloadStatusV1(ExecutionPayloadStatus.VALID, null, null);
    PayloadStatus internalPayloadStatusExpected = PayloadStatus.VALID;
    String json = "{\"status\": \"VALID\", \"latestValidHash\": null, \"validationError\": null }";
    PayloadStatusV1 payloadStatusV1Deserialized =
        objectMapper.readValue(json, PayloadStatusV1.class);

    PayloadStatus internalPayloadStatus = payloadStatusV1Deserialized.asInternalExecutionPayload();

    assertThat(payloadStatusV1Deserialized).isEqualTo(payloadStatusV1Expected);
    assertThat(internalPayloadStatus).isEqualTo(internalPayloadStatusExpected);
  }

  @TestTemplate
  void shouldDeserializePayloadStatus() throws IOException {
    Bytes32 lastValidHash = dataStructureUtil.randomBytes32();
    PayloadStatusV1 payloadStatusV1Expected =
        new PayloadStatusV1(ExecutionPayloadStatus.INVALID, lastValidHash, "test");
    PayloadStatus internalPayloadStatusExpected =
        PayloadStatus.invalid(Optional.of(lastValidHash), Optional.of("test"));

    String json =
        "{\"status\": \"INVALID\", \"latestValidHash\": \""
            + lastValidHash.toHexString()
            + "\", \"validationError\": \"test\" }";
    PayloadStatusV1 payloadStatusV1Deserialized =
        objectMapper.readValue(json, PayloadStatusV1.class);

    PayloadStatus internalPayloadStatus = payloadStatusV1Deserialized.asInternalExecutionPayload();

    assertThat(payloadStatusV1Deserialized).isEqualTo(payloadStatusV1Expected);
    assertThat(internalPayloadStatus).isEqualTo(internalPayloadStatusExpected);
  }

  @TestTemplate
  void shouldThrowDeserializingInvalidPayloadStatusV1() {
    assertThrows(
        InvalidFormatException.class,
        () -> {
          String json =
              "{\"status\": \"wrong\", \"latestValidHash\": null, \"validationError\": null }";
          objectMapper.readValue(json, PayloadStatusV1.class);
        });

    assertThrows(
        ValueInstantiationException.class,
        () -> {
          String json = "{\"status\": null, \"latestValidHash\": null, \"validationError\": null }";
          objectMapper.readValue(json, PayloadStatusV1.class);
        });

    assertThrows(
        JsonMappingException.class,
        () -> {
          String json =
              "{\"status\": null, \"latestValidHash\": \"wrong\", \"validationError\": null }";
          objectMapper.readValue(json, PayloadStatusV1.class);
        });
  }

  @TestTemplate
  void shouldDeserializeForkChoiceUpdatedResultWithNulls() throws IOException {
    ForkChoiceUpdatedResult forkChoiceUpdatedResultExpected =
        createExternalForkChoiceUpdatedResult(ExecutionPayloadStatus.SYNCING, null, null);
    tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult
        internalForkChoiceUpdatedResultExpected =
            createInternalForkChoiceUpdatedResult(
                ExecutionPayloadStatus.SYNCING, Optional.empty(), Optional.empty());
    String json = "{\"payloadStatus\": {\"status\": \"SYNCING\"}, \"payloadId\": null }";
    ForkChoiceUpdatedResult executeForkChoiceUpdatedResultDeserialized =
        objectMapper.readValue(json, ForkChoiceUpdatedResult.class);

    tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult internalForkChoiceUpdatedResult =
        executeForkChoiceUpdatedResultDeserialized.asInternalExecutionPayload();

    assertThat(executeForkChoiceUpdatedResultDeserialized)
        .isEqualTo(forkChoiceUpdatedResultExpected);
    assertThat(internalForkChoiceUpdatedResult).isEqualTo(internalForkChoiceUpdatedResultExpected);
  }

  @TestTemplate
  void shouldDeserializeForkChoiceUpdatedResult() throws IOException {
    Bytes8 payloadId = dataStructureUtil.randomBytes8();
    Bytes32 latestValidHash = dataStructureUtil.randomBytes32();
    ForkChoiceUpdatedResult forkChoiceUpdatedResultExpected =
        createExternalForkChoiceUpdatedResult(
            ExecutionPayloadStatus.VALID, latestValidHash, payloadId);
    tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult
        internalForkChoiceUpdatedResultExpected =
            createInternalForkChoiceUpdatedResult(
                ExecutionPayloadStatus.VALID, Optional.of(latestValidHash), Optional.of(payloadId));
    String json =
        "{\"payloadStatus\": {\"status\": \"VALID\", \"latestValidHash\": \""
            + latestValidHash.toHexString()
            + "\"}, \"payloadId\": \""
            + payloadId.toHexString()
            + "\" }";
    ForkChoiceUpdatedResult executeForkChoiceUpdatedResultDeserialized =
        objectMapper.readValue(json, ForkChoiceUpdatedResult.class);

    tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult internalForkChoiceUpdatedResult =
        executeForkChoiceUpdatedResultDeserialized.asInternalExecutionPayload();

    assertThat(executeForkChoiceUpdatedResultDeserialized)
        .isEqualTo(forkChoiceUpdatedResultExpected);
    assertThat(internalForkChoiceUpdatedResult).isEqualTo(internalForkChoiceUpdatedResultExpected);
  }

  @TestTemplate
  void shouldThrowDeserializingInvalidForkChoiceUpdatedResult() {
    assertThrows(
        ValueInstantiationException.class,
        () -> {
          String json = "{\"wrong\": \"wrong\", \"payloadId\": null }";
          objectMapper.readValue(json, ForkChoiceUpdatedResult.class);
        });

    assertThrows(
        MismatchedInputException.class,
        () -> {
          String json = "{\"payloadStatus\": \"wrong\", \"payloadId\": null }";
          objectMapper.readValue(json, ForkChoiceUpdatedResult.class);
        });

    assertThrows(
        InvalidFormatException.class,
        () -> {
          String json = "{\"payloadStatus\": {\"status\": \"wrong\"}, \"payloadId\": null }";
          objectMapper.readValue(json, ForkChoiceUpdatedResult.class);
        });

    assertThrows(
        ValueInstantiationException.class,
        () -> {
          String json = "{\"payloadStatus\": null, \"payloadId\": null }";
          objectMapper.readValue(json, ForkChoiceUpdatedResult.class);
        });

    assertThrows(
        JsonMappingException.class,
        () -> {
          String json = "{\"payloadStatus\": null, \"payloadId\": \"wrong\" }";
          objectMapper.readValue(json, ForkChoiceUpdatedResult.class);
        });
  }

  @TestTemplate
  void shouldSerializeForkChoiceState() throws IOException {
    final ForkChoiceStateV1 forkChoiceState =
        new ForkChoiceStateV1(
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32());

    String serialized = objectMapper.writeValueAsString(forkChoiceState);

    assertThat(serialized).isNotEmpty();
  }

  @TestTemplate
  void shouldSerializePayloadAttributes() throws IOException {
    final PayloadAttributesV1 payloadAttributes =
        new PayloadAttributesV1(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes20());

    String serialized = objectMapper.writeValueAsString(payloadAttributes);

    assertThat(serialized).isNotEmpty();
  }

  private ForkChoiceUpdatedResult createExternalForkChoiceUpdatedResult(
      final ExecutionPayloadStatus status, final Bytes32 latestValidHash, final Bytes8 payloadId) {
    return new ForkChoiceUpdatedResult(
        new PayloadStatusV1(status, latestValidHash, null), payloadId);
  }

  private tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult
      createInternalForkChoiceUpdatedResult(
          final ExecutionPayloadStatus status,
          final Optional<Bytes32> latestValidHash,
          final Optional<Bytes8> payloadId) {
    return new tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult(
        PayloadStatus.create(status, latestValidHash, Optional.empty()), payloadId);
  }
}
