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

package tech.pegasys.teku.validator.client.loader;

import com.google.common.base.Splitter;
import com.google.common.base.Suppliers;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.function.Supplier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import org.apache.commons.lang3.tuple.Pair;
import tech.pegasys.teku.infrastructure.crypto.SecureRandomProvider;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.validator.api.ValidatorConfig;

public class HttpClientExternalSignerFactory implements Supplier<HttpClient> {
  private final ValidatorConfig validatorConfig;

  public static Supplier<HttpClient> create(final ValidatorConfig validatorConfig) {
    return Suppliers.memoize(new HttpClientExternalSignerFactory(validatorConfig)::get);
  }

  private HttpClientExternalSignerFactory(final ValidatorConfig validatorConfig) {
    this.validatorConfig = validatorConfig;
  }

  @Override
  public HttpClient get() {
    final HttpClient.Builder builder = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1);
    if (isTLSEnabled()) {
      validatorConfig
          .getValidatorExternalSignerUserInfo()
          .ifPresent(userInfo -> configureBasicAuthentication(builder, userInfo));
      builder.sslContext(
          getSSLContext(
              validatorConfig.getValidatorExternalSignerKeystorePasswordFilePair(),
              validatorConfig.getValidatorExternalSignerTruststorePasswordFilePair()));
    }
    return builder.build();
  }

  private boolean isTLSEnabled() {
    return "https".equalsIgnoreCase(validatorConfig.getValidatorExternalSignerUrl().getProtocol());
  }

  private void configureBasicAuthentication(
      final HttpClient.Builder builder, final String userInfo) {
    final List<String> authCredentials = Splitter.on(':').splitToList(userInfo);
    if (authCredentials.size() != 2) {
      throw new IllegalArgumentException(
          "Invalid format for userInfo. It should be in the format 'username:password'.");
    }
    final String username = authCredentials.get(0);
    final String password = authCredentials.get(1);
    builder.authenticator(
        new Authenticator() {
          @Override
          protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(username, password.toCharArray());
          }
        });
  }

  private SSLContext getSSLContext(
      final Pair<Path, Path> keystore, final Pair<Path, Path> truststore) {
    try {
      final SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
      // if keystore or truststore is null, the defaults will be loaded by init
      sslContext.init(
          getKeyManagers(getFile(keystore.getLeft()), readPasswordFromFile(keystore.getRight())),
          getTrustManagers(
              getFile(truststore.getLeft()), readPasswordFromFile(truststore.getRight())),
          SecureRandomProvider.createSecureRandom());
      return sslContext;
    } catch (final NoSuchAlgorithmException | KeyManagementException e) {
      throw new InvalidConfigurationException(
          "Error in initializing SSLContext: " + e.getMessage(), e);
    }
  }

  private File getFile(final Path path) {
    return path == null ? null : path.toFile();
  }

  private char[] readPasswordFromFile(final Path passwordFile) {
    if (passwordFile == null) {
      return null;
    }

    try {
      return Files.readAllLines(passwordFile).stream()
          .findFirst()
          .map(String::toCharArray)
          .orElse(null);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private KeyManager[] getKeyManagers(final File keystoreFile, final char[] password) {
    if (keystoreFile == null) {
      return null;
    }

    try {
      final KeyStore keystore = KeyStore.getInstance(keystoreFile, password);

      final KeyManagerFactory keyManagerFactory =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyManagerFactory.init(keystore, password);
      return keyManagerFactory.getKeyManagers();
    } catch (final GeneralSecurityException e) {
      throw new InvalidConfigurationException("Error in loading keystore: " + e.getMessage(), e);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private TrustManager[] getTrustManagers(final File trustStoreFile, final char[] password) {
    if (trustStoreFile == null) {
      return null;
    }

    try {
      final KeyStore trustStore = KeyStore.getInstance(trustStoreFile, password);

      final TrustManagerFactory trustManagerFactory =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init(trustStore);

      return trustManagerFactory.getTrustManagers();
    } catch (final GeneralSecurityException e) {
      throw new InvalidConfigurationException("Error in loading truststore: " + e.getMessage(), e);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
