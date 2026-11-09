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

package tech.pegasys.teku.api;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public abstract class AbstractSelectorFactory<T> {

  private static final String HEX_PREFIX = "0x";

  private static final String BLOCK_ID = "block ID";
  private static final String STATE_ID = "state ID";

  private static final String HEAD = "head";
  private static final String GENESIS = "genesis";
  private static final String FINALIZED = "finalized";
  private static final String JUSTIFIED = "justified";

  protected CombinedChainDataClient client;

  public AbstractSelectorFactory(final CombinedChainDataClient client) {
    this.client = client;
  }

  /** Parsing of the {state_id} parameter to determine the selector to return */
  public T createSelectorForStateId(final String stateId) {
    try {
      if (isHexString(stateId)) {
        return stateRootSelector(Bytes32.fromHexString(stateId));
      }
      return createSelectorForKeywordOrSlot(stateId);
    } catch (final IllegalArgumentException | UnsupportedOperationException __) {
      throw badRequestException(STATE_ID, stateId);
    }
  }

  /** Parsing of the {block_id} parameter to determine the selector to return */
  public T createSelectorForBlockId(final String blockId) {
    try {
      if (isHexString(blockId)) {
        return blockRootSelector(Bytes32.fromHexString(blockId));
      }
      return createSelectorForKeywordOrSlot(blockId);
    } catch (final IllegalArgumentException | UnsupportedOperationException __) {
      throw badRequestException(BLOCK_ID, blockId);
    }
  }

  public T stateRootSelector(final Bytes32 stateRoot) {
    throw new UnsupportedOperationException();
  }

  public T blockRootSelector(final Bytes32 blockRoot) {
    throw new UnsupportedOperationException();
  }

  public T headSelector() {
    throw new UnsupportedOperationException();
  }

  public T genesisSelector() {
    throw new UnsupportedOperationException();
  }

  public T finalizedSelector() {
    throw new UnsupportedOperationException();
  }

  public T justifiedSelector() {
    throw new UnsupportedOperationException();
  }

  public T slotSelector(final UInt64 slot) {
    throw new UnsupportedOperationException();
  }

  private boolean isHexString(final String identifier) {
    return identifier.startsWith(HEX_PREFIX);
  }

  private T createSelectorForKeywordOrSlot(final String identifier) {
    return switch (identifier) {
      case HEAD -> headSelector();
      case GENESIS -> genesisSelector();
      case FINALIZED -> finalizedSelector();
      case JUSTIFIED -> justifiedSelector();
      default -> slotSelector(UInt64.valueOf(identifier));
    };
  }

  private BadRequestException badRequestException(final String type, final String identifier) {
    return new BadRequestException(String.format("Invalid %s: %s", type, identifier));
  }
}
