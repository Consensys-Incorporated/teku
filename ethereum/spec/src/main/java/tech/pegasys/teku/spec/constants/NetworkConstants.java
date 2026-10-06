/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.constants;

public class NetworkConstants {

  public static final int SYNC_COMMITTEE_SUBNET_COUNT = 4;
  public static final int DEFAULT_SAFE_SLOTS_TO_IMPORT_OPTIMISTICALLY = 128;

  public static final int NODE_ID_BITS = 256;

  // https://github.com/ethereum/consensus-specs/pull/3767
  public static final int MAX_CONCURRENT_REQUESTS = 2;

  public static final int MAX_REQUEST_LIGHT_CLIENT_UPDATES = 128;
}
