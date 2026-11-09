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

package tech.pegasys.teku.spec.generator.signatures;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NoOpRemoteSigner extends NoOpSigner {
  public static final NoOpRemoteSigner NO_OP_REMOTE_SIGNER = new NoOpRemoteSigner();
  private static final Logger LOG = LogManager.getLogger();

  @Override
  public Optional<URL> getSigningServiceUrl() {
    Optional<URL> result;
    try {
      result = Optional.of(new URI("http://example.com/").toURL());
    } catch (MalformedURLException | URISyntaxException e) {
      result = Optional.empty();
      LOG.error("Failed to get signing service URL", e);
    }
    return result;
  }
}
