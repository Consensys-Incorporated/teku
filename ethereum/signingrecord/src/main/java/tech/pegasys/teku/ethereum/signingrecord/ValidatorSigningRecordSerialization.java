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

package tech.pegasys.teku.ethereum.signingrecord;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.data.yaml.YamlProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ValidatorSigningRecordSerialization {

  private static final String GENESIS_VALIDATORS_ROOT_FIELD_NAME = "genesisValidatorsRoot";
  private static final String BLOCK_SLOT_FIELD_NAME = "lastSignedBlockSlot";
  private static final String SOURCE_EPOCH_FIELD_NAME = "lastSignedAttestationSourceEpoch";
  private static final String TARGET_EPOCH_FIELD_NAME = "lastSignedAttestationTargetEpoch";

  public static final SimpleModule SIGNING_RECORD_MODULE =
      new SimpleModule()
          .addSerializer(ValidatorSigningRecord.class, new ValidatorSigningRecordSerializer())
          .addDeserializer(ValidatorSigningRecord.class, new ValidatorSigningRecordDeserializer());

  private static final YamlProvider YAML_PROVIDER = new YamlProvider(SIGNING_RECORD_MODULE);

  static ValidatorSigningRecord readRecord(final Bytes data) {
    try {
      return YAML_PROVIDER.read(data, ValidatorSigningRecord.class);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static Bytes writeRecord(final ValidatorSigningRecord record) {
    return YAML_PROVIDER.write(record);
  }

  private static class ValidatorSigningRecordSerializer
      extends JsonSerializer<ValidatorSigningRecord> {

    @Override
    public void serialize(
        final ValidatorSigningRecord value,
        final JsonGenerator gen,
        final SerializerProvider serializers)
        throws IOException {
      gen.writeStartObject();
      if (value.genesisValidatorsRoot().isPresent()) {
        gen.writeStringField(
            GENESIS_VALIDATORS_ROOT_FIELD_NAME, value.genesisValidatorsRoot().get().toHexString());
      }
      writeUInt64(gen, BLOCK_SLOT_FIELD_NAME, value.blockSlot());
      writeUInt64(gen, SOURCE_EPOCH_FIELD_NAME, value.attestationSourceEpoch());
      writeUInt64(gen, TARGET_EPOCH_FIELD_NAME, value.attestationTargetEpoch());
      gen.writeEndObject();
    }

    private void writeUInt64(final JsonGenerator gen, final String fieldName, final UInt64 value)
        throws IOException {
      if (ValidatorSigningRecord.isNeverSigned(value)) {
        gen.writeNullField(fieldName);
      } else {
        gen.writeNumberField(fieldName, value.bigIntegerValue());
      }
    }
  }

  private static class ValidatorSigningRecordDeserializer
      extends JsonDeserializer<ValidatorSigningRecord> {
    @Override
    public ValidatorSigningRecord deserialize(final JsonParser p, final DeserializationContext ctxt)
        throws IOException {
      final TreeNode node = p.getCodec().readTree(p);
      final Bytes32 genesisValidatorsRoot = getBytes32(node, GENESIS_VALIDATORS_ROOT_FIELD_NAME);
      final UInt64 blockSlot = getUInt64(node, BLOCK_SLOT_FIELD_NAME);
      final UInt64 attestationSourceEpoch = getUInt64(node, SOURCE_EPOCH_FIELD_NAME);
      final UInt64 attestationTargetEpoch = getUInt64(node, TARGET_EPOCH_FIELD_NAME);
      return new ValidatorSigningRecord(
          Optional.ofNullable(genesisValidatorsRoot),
          blockSlot,
          attestationSourceEpoch,
          attestationTargetEpoch);
    }

    private Bytes32 getBytes32(final TreeNode node, final String fieldName) {
      final TreeNode valueNode = node.get(fieldName);
      if (valueNode == null) {
        return null;
      }
      return valueNode instanceof NullNode
          ? null
          : Bytes32.fromHexString(((TextNode) valueNode).textValue());
    }

    private UInt64 getUInt64(final TreeNode node, final String fieldName) {
      final TreeNode valueNode = node.get(fieldName);
      if (valueNode instanceof NullNode) {
        return ValidatorSigningRecord.NEVER_SIGNED;
      }
      final UInt64 value = UInt64.valueOf(((NumericNode) valueNode).bigIntegerValue());
      return value.equals(UInt64.MAX_VALUE) ? ValidatorSigningRecord.NEVER_SIGNED : value;
    }
  }
}
