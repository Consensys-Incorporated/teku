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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeyResult;

public class ActiveLocalValidatorSource {
  private final Path keystorePath;
  private final Path passwordPath;

  public ActiveLocalValidatorSource(final Path keystorePath, final Path passwordPath) {
    this.keystorePath = keystorePath;
    this.passwordPath = passwordPath;
  }

  public DeleteKeyResult delete() {
    try {
      Files.delete(keystorePath);
      Files.delete(passwordPath);
      return DeleteKeyResult.success();
    } catch (IOException e) {
      return DeleteKeyResult.error("Failed to delete file " + e.getMessage());
    }
  }

  public Path getKeystorePath() {
    return keystorePath;
  }

  public Path getPasswordPath() {
    return passwordPath;
  }
}
