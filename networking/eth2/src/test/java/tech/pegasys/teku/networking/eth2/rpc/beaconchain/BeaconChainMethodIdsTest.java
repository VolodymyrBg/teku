/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

public class BeaconChainMethodIdsTest {
  private final Spec spec = TestSpecFactory.createDefault();
  protected final RpcEncoding rpcEncoding =
      RpcEncoding.createSszSnappyEncoding(spec.getNetworkingConfig().getMaxPayloadSize());

  @Test
  public void getProtocolId() {

    final String protocolId =
        BeaconChainMethodIds.getMethodId(
            BeaconChainMethodIds.BEACON_BLOCKS_BY_RANGE, 2, rpcEncoding);
    final String expected = "/eth2/beacon_chain/req/beacon_blocks_by_range/2/ssz_snappy";
    assertThat(protocolId).isEqualTo(expected);
  }

  @Test
  public void extractVersion() {
    String protocolId =
        BeaconChainMethodIds.getMethodId(BeaconChainMethodIds.STATUS, 1, rpcEncoding);
    assertThat(BeaconChainMethodIds.extractVersion(protocolId, BeaconChainMethodIds.STATUS))
        .isEqualTo(1);

    protocolId = BeaconChainMethodIds.getMethodId(BeaconChainMethodIds.STATUS, 2, rpcEncoding);
    assertThat(BeaconChainMethodIds.extractVersion(protocolId, BeaconChainMethodIds.STATUS))
        .isEqualTo(2);

    protocolId = BeaconChainMethodIds.getMethodId(BeaconChainMethodIds.STATUS, 10, rpcEncoding);
    assertThat(BeaconChainMethodIds.extractVersion(protocolId, BeaconChainMethodIds.STATUS))
        .isEqualTo(10);

    protocolId = BeaconChainMethodIds.getMethodId(BeaconChainMethodIds.STATUS, 11, rpcEncoding);
    assertThat(BeaconChainMethodIds.extractVersion(protocolId, BeaconChainMethodIds.STATUS))
        .isEqualTo(11);
  }
}
