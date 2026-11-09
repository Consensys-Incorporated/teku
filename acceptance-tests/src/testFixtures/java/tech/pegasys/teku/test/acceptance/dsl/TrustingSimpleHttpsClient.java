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

package tech.pegasys.teku.test.acceptance.dsl;

import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.crypto.MessageDigestFactory;
import tech.pegasys.teku.infrastructure.crypto.SecureRandomProvider;

public class TrustingSimpleHttpsClient extends SimpleHttpClient {
  private static final Logger LOG = LogManager.getLogger();
  private static final TrustManager[] TRUST_VALIDATOR_CERT =
      new TrustManager[] {
        new X509TrustManager() {
          @Override
          public void checkClientTrusted(final X509Certificate[] chain, final String authType) {}

          @Override
          public void checkServerTrusted(final X509Certificate[] chain, final String authType)
              throws CertificateException {

            for (X509Certificate certificate : chain) {
              MessageDigest digest = MessageDigestFactory.createSha256();
              byte[] hash = digest.digest(certificate.getSignature());
              // accept the validatorApi.pfx signature
              if (Bytes.of(hash)
                  .toBase64String()
                  .equals("tOK/4GIE05ityxxcli3KkGC/luqwL/Ef+e+TbBNLtvM=")) {
                LOG.debug("Verified server signature");
                return;
              }
            }

            throw new CertificateException();
          }

          @Override
          public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[] {};
          }
        }
      };
  private static final X509TrustManager TRUST_MANAGER =
      new X509TrustManager() {
        @Override
        public void checkClientTrusted(final X509Certificate[] chain, final String authType) {}

        @Override
        public void checkServerTrusted(final X509Certificate[] chain, final String authType) {}

        @Override
        public X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[] {};
        }
      };

  TrustingSimpleHttpsClient() {
    super();
    this.httpClient = getUnsafeOkHttpClient();
  }

  private static OkHttpClient getUnsafeOkHttpClient() {
    try {
      final SSLContext sslContext = SSLContext.getInstance("SSL");
      sslContext.init(null, TRUST_VALIDATOR_CERT, SecureRandomProvider.createSecureRandom());
      OkHttpClient.Builder builder = new OkHttpClient.Builder();
      builder.hostnameVerifier((hostname, session) -> true);

      builder.sslSocketFactory(sslContext.getSocketFactory(), TRUST_MANAGER);
      return builder.build();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
