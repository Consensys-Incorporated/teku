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

package tech.pegasys.teku.networking.eth2.gossip.config;

import java.time.Duration;

/**
 * Options for enforcing some rate of message delivery on a topic. If a peer's delivery falls below
 * this rate, it will be penalized.
 */
class MessageDeliveriesOptions {
  private static final double DEFAULT_MESSAGE_RATE_FACTOR = 1.0 / 50.0;
  private static final Duration DEFAULT_DELIVERY_WINDOW = Duration.ofSeconds(2);

  // A multiplier (between 0 and 1) which is applied to the expected message rate
  // If a peer's message rate drops below the scaled message rate, it will be penalized
  private final double messageRateFactor;
  // The peer must be in the mesh for longer than the activation window before penalties start to
  // be applied
  private final Duration activationWindow;
  // A message must be delivered first or within {@code deliveriesWindow} of the first delivered
  // message to count towards the deliveries gossip counter
  private final Duration deliveriesWindow;
  private final double capFactor;
  private final int decaySlots;

  private MessageDeliveriesOptions(
      final double messageRateFactor,
      final Duration activationWindow,
      final Duration deliveriesWindow,
      final double capFactor,
      final int decaySlots) {
    this.messageRateFactor = messageRateFactor;
    this.activationWindow = activationWindow;
    this.deliveriesWindow = deliveriesWindow;
    this.capFactor = capFactor;
    this.decaySlots = decaySlots;
  }

  public static MessageDeliveriesOptions create(
      final Duration activationWindow, final double capFactor, final int decaySlots) {
    return new MessageDeliveriesOptions(
        DEFAULT_MESSAGE_RATE_FACTOR,
        activationWindow,
        DEFAULT_DELIVERY_WINDOW,
        capFactor,
        decaySlots);
  }

  public double getMessageRateFactor() {
    return messageRateFactor;
  }

  public Duration getActivationWindow() {
    return activationWindow;
  }

  public Duration getDeliveriesWindow() {
    return deliveriesWindow;
  }

  public double getCapFactor() {
    return capFactor;
  }

  public int getDecaySlots() {
    return decaySlots;
  }
}
