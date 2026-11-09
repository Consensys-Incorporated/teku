#!/usr/bin/env bash
set -euo pipefail

 # Copyright (c) 2026 Consensys Incorporated. All rights reserved.
 #
 # Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 # the License. You may obtain a copy of the License at
 #
 # http://www.apache.org/licenses/LICENSE-2.0
 #
 # Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 # an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 # specific language governing permissions and limitations under the License.

for COMMAND in "jq" "curl" "echo" "cat"
do
  command -v $COMMAND 2>/dev/null || { echo >&2 "I require $COMMAND but it's not installed.  Aborting."; exit 1; }
done

TEMP=`mktemp -d`

function cleanup() {
    rm -rf "${TEMP}"
}
trap cleanup EXIT

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

SIGNER_URL=${1:-http://localhost:19000}
if [ ! -z "${2:-}" ]
then
  AUTHORIZATION_HEADER="-HAuthorization: Bearer $2"
  echo $AUTHORIZATION_HEADER
fi
echo "Signer url: $SIGNER_URL"
echo "Initialising payload.json..."
echo "{ \"pubkeys\": [" > "${TEMP}/payload.json"

echo "  - > writing keystores..."

for FILE in `ls ${DIR}/keys/*.json`
do
  if [ ! -z "${D:-}" ]
  then
    echo "," >> "${TEMP}/payload.json"
  else
    D=1
  fi
  cat $FILE|jq -c ".pubkey" >> "${TEMP}/payload.json"
done

echo "]
}" >> "${TEMP}/payload.json"

echo "Sending payload.json to ${SIGNER_URL}/eth/v1/keystores..."

curl --fail -q -X DELETE ${SIGNER_URL}/eth/v1/keystores \
     "${AUTHORIZATION_HEADER:-}" \
     -H "Content-Type: application/json" \
     -d "@${TEMP}/payload.json" \
     -o "${TEMP}/result.json"

 echo "Wrote result to ${TEMP}/result.json."
