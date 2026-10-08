/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.validator.client.proposerconfig.loader;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.http.UrlSanitizer;
import tech.pegasys.teku.validator.client.ProposerConfig;

public class ProposerConfigLoader {
  private final ObjectMapper objectMapper;

  public ProposerConfigLoader() {
    objectMapper = new ObjectMapper();
    addTekuMappers();
  }

  private void addTekuMappers() {
    final SimpleModule module =
        new SimpleModule("ProposerConfigLoader", new Version(1, 0, 0, null, null, null));
    module.addDeserializer(BLSPublicKey.class, new BLSPublicKeyDeserializer());
    module.addSerializer(BLSPublicKey.class, new BLSPublicKeySerializer());
    module.addKeyDeserializer(Bytes48.class, new Bytes48KeyDeserializer());
    module.addDeserializer(Bytes.class, new BytesDeserializer());

    objectMapper.registerModule(module);
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  public ProposerConfig getProposerConfig(final URL source) {
    try {
      return objectMapper.readValue(source, ProposerConfig.class);

    } catch (ValueInstantiationException ex) {
      final String error =
          getErrorMessage(
              source, ExceptionUtil.getRootCauseMessage(ex), Optional.of(ex.getLocation()));
      throw new InvalidConfigurationException(error, ex);
    } catch (JsonMappingException ex) {
      final String error =
          getErrorMessage(source, ex.getOriginalMessage(), Optional.ofNullable(ex.getLocation()));
      throw new InvalidConfigurationException(error, ex);
    } catch (IOException ex) {
      final String error =
          getErrorMessage(source, ExceptionUtil.getMessageOrSimpleName(ex), Optional.empty());
      throw new InvalidConfigurationException(error, ex);
    }
  }

  private static String getErrorMessage(
      final URL source, final String exceptionMessage, final Optional<JsonLocation> maybeLocation) {
    return String.format(
        "Failed to load proposer config from '%s' - %s%s",
        UrlSanitizer.sanitizePotentialUrl(source.toString()),
        exceptionMessage,
        maybeLocation.map(jsonLocation -> " at " + jsonLocation.offsetDescription()).orElse(""));
  }
}
