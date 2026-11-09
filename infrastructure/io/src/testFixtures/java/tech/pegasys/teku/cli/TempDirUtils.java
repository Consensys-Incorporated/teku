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

package tech.pegasys.teku.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.io.FileUtils;

public class TempDirUtils {

  public static Path createTempDir() {
    try {
      return Files.createTempDirectory("teku_unit_test_");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static boolean deleteDirLenient(
      final Path dir, final int maxDeleteAttempts, final boolean throwIfFailed) {
    IOException lastException = null;
    for (int i = 0; i < maxDeleteAttempts; i++) {
      try {
        System.out.println("Deleting");
        FileUtils.deleteDirectory(dir.toFile());
        return true;
      } catch (IOException e) {
        lastException = e;
        try {
          Thread.sleep(1000);
        } catch (InterruptedException interruptedException) {
          throw new RuntimeException(interruptedException);
        }
      }
    }
    if (throwIfFailed) {
      throw new AssertionError(
          "Directory " + dir + " couldn't be deleted after " + maxDeleteAttempts + " attempts",
          lastException);
    } else {
      return false;
    }
  }
}
