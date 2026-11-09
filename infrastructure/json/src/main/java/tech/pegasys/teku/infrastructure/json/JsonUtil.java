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

package tech.pegasys.teku.infrastructure.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.json.types.DeserializableOneOfTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;

public class JsonUtil {

  public static final JsonFactory FACTORY = new JsonFactory();

  public static <T> String serialize(final T value, final SerializableTypeDefinition<T> type)
      throws JsonProcessingException {
    return serialize(gen -> type.serialize(value, gen));
  }

  public static <T> String prettySerialize(final T value, final SerializableTypeDefinition<T> type)
      throws JsonProcessingException {
    return prettySerialize(gen -> type.serialize(value, gen));
  }

  public static String serialize(final JsonWriter serializer) throws JsonProcessingException {
    return serialize(FACTORY, serializer);
  }

  public static String prettySerialize(final JsonWriter serializer) throws JsonProcessingException {
    return prettySerialize(FACTORY, serializer);
  }

  public static String serialize(final JsonFactory factory, final JsonWriter serializer)
      throws JsonProcessingException {
    final StringWriter writer = new StringWriter();
    try (final JsonGenerator gen = factory.createGenerator(writer)) {
      serializer.accept(gen);
    } catch (final JsonProcessingException e) {
      throw e;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return writer.toString();
  }

  public static String prettySerialize(final JsonFactory factory, final JsonWriter serializer)
      throws JsonProcessingException {
    final StringWriter writer = new StringWriter();
    try (final JsonGenerator gen = factory.createGenerator(writer)) {
      gen.setPrettyPrinter(new DefaultPrettyPrinter());
      serializer.accept(gen);
    } catch (final JsonProcessingException e) {
      throw e;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return writer.toString();
  }

  public static <T> void serializeToBytes(
      final T value, final SerializableTypeDefinition<T> type, final OutputStream out)
      throws JsonProcessingException {
    serializeToBytes(FACTORY, gen -> type.serialize(value, gen), out);
  }

  public static void serializeToBytes(
      final JsonFactory factory, final JsonWriter serializer, final OutputStream out)
      throws JsonProcessingException {
    try {
      serializeToBytesChecked(factory, serializer, out);
    } catch (final JsonProcessingException e) {
      throw e;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static <T> void serializeToBytesChecked(
      final T value, final SerializableTypeDefinition<T> type, final OutputStream out)
      throws IOException {
    serializeToBytesChecked(FACTORY, gen -> type.serialize(value, gen), out);
  }

  public static void serializeToBytesChecked(
      final JsonFactory factory, final JsonWriter serializer, final OutputStream out)
      throws IOException {
    try (final JsonGenerator gen = factory.createGenerator(out)) {
      serializer.accept(gen);
    }
  }

  public static <T> T parse(final String json, final DeserializableTypeDefinition<T> type)
      throws JsonProcessingException {
    return parse(() -> FACTORY.createParser(json), type);
  }

  public static <T> T parse(final String json, final DeserializableOneOfTypeDefinition<T> oneOfType)
      throws JsonProcessingException {
    final DeserializableTypeDefinition<? extends T> typeDefinition =
        oneOfType.getMatchingType(json);
    return parse(json, typeDefinition);
  }

  public static <T> T parse(
      final JsonFactory factory, final InputStream json, final DeserializableTypeDefinition<T> type)
      throws JsonProcessingException {
    return parse(() -> factory.createParser(json), type);
  }

  public static <T> T parse(final InputStream json, final DeserializableTypeDefinition<T> type)
      throws JsonProcessingException {
    return parse(() -> FACTORY.createParser(json), type);
  }

  private static <T> T parse(
      final ParserSupplier parserSupplier, final DeserializableTypeDefinition<T> type)
      throws JsonProcessingException {
    try (final JsonParser parser = parserSupplier.get()) {
      parser.nextToken();
      return type.deserialize(parser);
    } catch (final JsonProcessingException e) {
      throw e;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static <T> T parseBasedOnHeader(
      final String header, final String json, final DeserializableOneOfTypeDefinition<T> oneOfType)
      throws JsonProcessingException {
    final DeserializableTypeDefinition<? extends T> typeDefinition =
        oneOfType.getMatchingType(header);
    return parse(json, typeDefinition);
  }

  public static <T> Optional<T> getAttribute(
      final String json, final DeserializableTypeDefinition<T> type, final String... path)
      throws JsonProcessingException {
    try (final JsonParser parser = FACTORY.createParser(json)) {
      return getAttributeFromParser(parser, type, 0, path);
    } catch (final JsonProcessingException e) {
      throw e;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static <T> Optional<T> getAttributeFromParser(
      final JsonParser parser,
      final DeserializableTypeDefinition<T> type,
      final int i,
      final String... path)
      throws IOException {
    if (!JsonToken.START_OBJECT.equals(parser.nextToken())) {
      throw new IllegalStateException("getAttribute was not passed an object");
    }
    final String fieldName = path[i];
    while (!parser.isClosed()) {
      final JsonToken jsonToken = parser.nextToken();
      if (JsonToken.FIELD_NAME.equals(jsonToken)) {
        final String currentFieldName = parser.currentName();
        if (currentFieldName.equals(fieldName)) {
          if (path.length == i + 1) {
            parser.nextToken();
            return Optional.of(type.deserialize(parser));
          } else {
            return getAttributeFromParser(parser, type, i + 1, path);
          }
        }
      } else if (JsonToken.START_ARRAY.equals(jsonToken)
          || JsonToken.START_OBJECT.equals(jsonToken)) {
        parser.skipChildren();
      }
    }
    return Optional.empty();
  }

  public interface JsonWriter {
    void accept(JsonGenerator gen) throws IOException;
  }

  private interface ParserSupplier {
    JsonParser get() throws IOException;
  }
}
