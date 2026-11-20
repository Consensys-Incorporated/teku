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

package tech.pegasys.teku.spec.signatures.xmss;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.signatures.SlashableConditionException;
import tech.pegasys.teku.xmss.XmssPublicKey;

/**
 * Thrown when signing is refused because the key already signed a different message at the leaf, or
 * the leaf is below its region's watermark. Signing would expose the leaf and let anyone forge
 * signatures at it.
 */
public class XmssLeafReuseException extends SlashableConditionException {

  public XmssLeafReuseException(
      final String messageType, final XmssPublicKey publicKey, final UInt64 leafIndex) {
    super(
        String.format(
            "Refused to sign %s at XMSS leaf %s for %s: the leaf is already used",
            messageType, leafIndex, publicKey));
  }

  public XmssLeafReuseException(final XmssPublicKey publicKey) {
    super(String.format("Refused to sign for %s: every reserved XMSS leaf is used", publicKey));
  }
}
