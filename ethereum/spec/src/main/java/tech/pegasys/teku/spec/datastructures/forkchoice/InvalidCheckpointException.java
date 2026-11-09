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

package tech.pegasys.teku.spec.datastructures.forkchoice;

/**
 * Thrown when a checkpoint state cannot be generated because the checkpoint is invalid. Most
 * commonly because the blockRoot is for a block after the epoch start slot.
 */
public class InvalidCheckpointException extends RuntimeException {
  public InvalidCheckpointException(final String message) {
    super(message);
  }

  public InvalidCheckpointException(final Throwable cause) {
    super(cause);
  }
}
