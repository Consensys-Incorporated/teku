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

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.KeyStoreFilesLocator;

public class LocalValidatorVerifier {
  private final KeyStoreFilesLocator keyStoreFilesLocator;
  private final LocalValidatorSource validatorSource;

  public LocalValidatorVerifier(
      final Spec spec, final List<String> keystoreFiles, final AsyncRunner asyncRunner) {
    keyStoreFilesLocator = new KeyStoreFilesLocator(keystoreFiles, File.pathSeparator);

    validatorSource =
        new LocalValidatorSource(
            spec,
            true,
            new KeystoreLocker(),
            keyStoreFilesLocator,
            asyncRunner,
            true,
            Optional.empty());
  }

  public List<Pair<Path, Path>> parse() {
    return keyStoreFilesLocator.parse();
  }

  public ValidatorSource.ValidatorProvider createValidatorProvider(
      final Pair<Path, Path> keystorePasswordPathPair) {
    return validatorSource.createValidatorProvider(keystorePasswordPathPair);
  }
}
