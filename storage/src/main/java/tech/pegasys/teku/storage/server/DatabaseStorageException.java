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

package tech.pegasys.teku.storage.server;

public class DatabaseStorageException extends RuntimeException {
  private final boolean unrecoverable;

  private DatabaseStorageException(
      final String message, final boolean unrecoverable, final Throwable cause) {
    super(message, cause);
    this.unrecoverable = unrecoverable;
  }

  public static DatabaseStorageException recoverable(final String message, final Throwable cause) {
    return new DatabaseStorageException(message, false, cause);
  }

  public static DatabaseStorageException unrecoverable(
      final String message, final Throwable cause) {
    return new DatabaseStorageException(message, true, cause);
  }

  public static DatabaseStorageException unrecoverable(final String message) {
    return new DatabaseStorageException(message, true, null);
  }

  public boolean isUnrecoverable() {
    return unrecoverable;
  }
}
