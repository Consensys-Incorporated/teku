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

package tech.pegasys.teku.statetransition.forkchoice.fastconfirmation;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.events.VoidReturningChannelInterface;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * Fired once per slot after the fast confirmation algorithm ({@code on_fast_confirmation}) has run,
 * regardless of whether the confirmed block changed. Backs the {@code fast_confirmation} Beacon API
 * event stream topic.
 */
public interface FastConfirmationEventChannel extends VoidReturningChannelInterface {

  FastConfirmationEventChannel NOOP = (confirmedRoot, confirmedSlot, currentSlot) -> {};

  /**
   * @param confirmedRoot root of the most recent confirmed block
   * @param confirmedSlot slot of the most recent confirmed block
   * @param currentSlot wall-clock slot at which the algorithm was executed
   */
  void onFastConfirmation(Bytes32 confirmedRoot, UInt64 confirmedSlot, UInt64 currentSlot);
}
