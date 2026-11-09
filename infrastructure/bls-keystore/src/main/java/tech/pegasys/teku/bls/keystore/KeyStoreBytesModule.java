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

package tech.pegasys.teku.bls.keystore;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.keystore.model.ChecksumFunction;
import tech.pegasys.teku.bls.keystore.model.CipherFunction;
import tech.pegasys.teku.bls.keystore.model.Pbkdf2PseudoRandomFunction;

class KeyStoreBytesModule extends SimpleModule {
  public KeyStoreBytesModule() {
    super("KeystoreBytes");
    addSerializer(Bytes.class, new BytesSerializer());
    addDeserializer(Bytes.class, new BytesDeserializer());
    addSerializer(Bytes32.class, new Bytes32Serializer());
    addDeserializer(Bytes32.class, new Bytes32Deserializer());

    // following deserializer allow custom exception message for invalid enum values
    addDeserializer(ChecksumFunction.class, new ChecksumFunctionDeserializer());
    addDeserializer(CipherFunction.class, new CipherFunctionDeserializer());
    addDeserializer(Pbkdf2PseudoRandomFunction.class, new Pbkdf2PseudoRandomFunctionDeserializer());
  }

  private static class BytesSerializer extends JsonSerializer<Bytes> {
    @Override
    public void serialize(
        final Bytes bytes, final JsonGenerator jGen, final SerializerProvider serializerProvider)
        throws IOException {
      // write bytes in hex without 0x
      jGen.writeString(bytes.appendHexTo(new StringBuilder()).toString());
    }
  }

  private static class BytesDeserializer extends JsonDeserializer<Bytes> {
    @Override
    public Bytes deserialize(final JsonParser p, final DeserializationContext ctxt)
        throws IOException {
      return Bytes.fromHexString(p.getValueAsString());
    }
  }

  private static class Bytes32Serializer extends JsonSerializer<Bytes32> {
    @Override
    public void serialize(
        final Bytes32 bytes, final JsonGenerator jGen, final SerializerProvider serializerProvider)
        throws IOException {
      // write bytes in hex without 0x
      jGen.writeString(bytes.appendHexTo(new StringBuilder()).toString());
    }
  }

  private static class Bytes32Deserializer extends JsonDeserializer<Bytes32> {
    @Override
    public Bytes32 deserialize(final JsonParser p, final DeserializationContext ctxt)
        throws IOException {
      return Bytes32.fromHexString(p.getValueAsString());
    }
  }

  private static class ChecksumFunctionDeserializer extends JsonDeserializer<ChecksumFunction> {
    @Override
    public ChecksumFunction deserialize(final JsonParser p, final DeserializationContext ctxt)
        throws IOException {
      final String valueAsString = p.getValueAsString();
      return EnumSet.allOf(ChecksumFunction.class).stream()
          .filter(function -> Objects.equals(function.getJsonValue(), valueAsString))
          .findFirst()
          .orElseThrow(
              () ->
                  new KeyStoreValidationException(
                      String.format("Checksum function [%s] is not supported.", valueAsString)));
    }
  }

  private static class CipherFunctionDeserializer extends JsonDeserializer<CipherFunction> {
    @Override
    public CipherFunction deserialize(final JsonParser p, final DeserializationContext ctxt)
        throws IOException {
      final String valueAsString = p.getValueAsString();
      return EnumSet.allOf(CipherFunction.class).stream()
          .filter(function -> Objects.equals(function.getJsonValue(), valueAsString))
          .findFirst()
          .orElseThrow(
              () ->
                  new KeyStoreValidationException(
                      String.format("Cipher function [%s] is not supported.", valueAsString)));
    }
  }

  private static class Pbkdf2PseudoRandomFunctionDeserializer
      extends JsonDeserializer<Pbkdf2PseudoRandomFunction> {
    @Override
    public Pbkdf2PseudoRandomFunction deserialize(
        final JsonParser p, final DeserializationContext ctxt) throws IOException {
      final String valueAsString = p.getValueAsString();
      return EnumSet.allOf(Pbkdf2PseudoRandomFunction.class).stream()
          .filter(function -> Objects.equals(function.getJsonValue(), valueAsString))
          .findFirst()
          .orElseThrow(
              () ->
                  new KeyStoreValidationException(
                      String.format(
                          "PBKDF2 pseudorandom function (prf) [%s] is not supported.",
                          valueAsString)));
    }
  }
}
