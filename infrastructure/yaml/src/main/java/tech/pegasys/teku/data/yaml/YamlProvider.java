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

package tech.pegasys.teku.data.yaml;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class YamlProvider {
  private final ObjectMapper objectMapper;

  public YamlProvider(final Module... modules) {
    objectMapper = new ObjectMapper(new YAMLFactory());
    addTekuMappers();
    Stream.of(modules).forEach(objectMapper::registerModule);
  }

  private void addTekuMappers() {
    final SimpleModule module =
        new SimpleModule("TekuYaml", new Version(1, 0, 0, null, null, null));
    module.addDeserializer(UInt64.class, new UInt64Deserializer());
    module.addSerializer(UInt64.class, new UInt64Serializer());
    module.addDeserializer(Bytes32.class, new Bytes32Deserializer());
    module.addSerializer(Bytes.class, new BytesSerializer());
    objectMapper.registerModule(module).writer(new DefaultPrettyPrinter());
  }

  public <T> T read(final Bytes data, final Class<T> clazz) throws IOException {
    return objectMapper.readValue(data.toArrayUnsafe(), clazz);
  }

  public <T> void write(final OutputStream out, final T object) throws IOException {
    objectMapper.writerWithDefaultPrettyPrinter().writeValue(out, object);
  }

  public <T> Bytes write(final T object) {
    try (final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(out, object);
      return Bytes.wrap(out.toByteArray());
    } catch (JsonGenerationException | JsonMappingException e) {
      throw new IllegalStateException("Failed to serialize object", e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public <T> String writeString(final T object) {
    try (final StringWriter out = new StringWriter()) {
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(out, object);
      return out.toString();
    } catch (JsonGenerationException | JsonMappingException e) {
      throw new IllegalStateException("Failed to serialize object", e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }
}
