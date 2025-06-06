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

package tech.pegasys.teku.networking.eth2;

import static org.assertj.core.util.Preconditions.checkState;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.provider.Arguments;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BeaconBlockBodyBellatrix;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodyCapella;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BeaconBlockBodyElectra;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.phase0.BeaconBlockBodyPhase0;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public abstract class AbstractRpcMethodIntegrationTest {
  protected StorageSystem peerStorage;

  private Spec baseSpec;
  private Optional<Spec> nextSpec;

  protected UInt64 nextSpecSlot;

  protected final UInt64 nextSpecEpoch = UInt64.valueOf(2);
  private final Eth2P2PNetworkFactory networkFactory = new Eth2P2PNetworkFactory();

  protected void setUp(
      final SpecMilestone baseMilestone, final Optional<SpecMilestone> nextMilestone) {
    setUpBaseSpec(baseMilestone);
    nextMilestone.ifPresent(this::setUpNextSpec);
  }

  private void setUpBaseSpec(final SpecMilestone specMilestone) {
    baseSpec = TestSpecFactory.createMinimal(specMilestone);
  }

  private void setUpNextSpec(final SpecMilestone nextSpecMilestone) {
    switch (baseSpec.getGenesisSpec().getMilestone()) {
      case PHASE0 -> {
        checkState(nextSpecMilestone.equals(SpecMilestone.ALTAIR), "next spec should be altair");
        nextSpec = Optional.of(TestSpecFactory.createMinimalWithAltairForkEpoch(nextSpecEpoch));
      }
      case ALTAIR -> {
        checkState(
            nextSpecMilestone.equals(SpecMilestone.BELLATRIX), "next spec should be bellatrix");
        nextSpec = Optional.of(TestSpecFactory.createMinimalWithBellatrixForkEpoch(nextSpecEpoch));
      }
      case BELLATRIX -> {
        checkState(nextSpecMilestone.equals(SpecMilestone.CAPELLA), "next spec should be capella");
        nextSpec = Optional.of(TestSpecFactory.createMinimalWithCapellaForkEpoch(nextSpecEpoch));
      }
      case CAPELLA -> {
        checkState(nextSpecMilestone.equals(SpecMilestone.DENEB), "next spec should be deneb");
        nextSpec = Optional.of(TestSpecFactory.createMinimalWithDenebForkEpoch(nextSpecEpoch));
      }
      case DENEB -> {
        checkState(nextSpecMilestone.equals(SpecMilestone.ELECTRA), "next spec should be electra");
        nextSpec = Optional.of(TestSpecFactory.createMinimalWithElectraForkEpoch(nextSpecEpoch));
      }
      case ELECTRA -> throw new RuntimeException("Base spec is already latest supported milestone");
    }
    nextSpecSlot = nextSpec.orElseThrow().computeStartSlotAtEpoch(nextSpecEpoch);
  }

  @AfterEach
  public void tearDown() throws Exception {
    networkFactory.stopAll();
  }

  protected Eth2Peer createPeer() {
    return createRemotePeerAndNetwork().peer();
  }

  private Spec getSpec(final boolean nextSpecEnabled) {
    return nextSpecEnabled ? nextSpec.orElseThrow() : baseSpec;
  }

  protected void setupPeerStorage(final boolean enableNextSpec) {
    final Spec remoteSpec = getSpec(enableNextSpec);
    peerStorage = InMemoryStorageSystemBuilder.create().specProvider(remoteSpec).build();
    peerStorage.chainUpdater().initializeGenesis();
  }

  /**
   * Create and connect 2 networks, return an Eth2Peer representing the remote network to which we
   * can send requests.
   *
   * @param enableNextSpecLocally Whether the "local" node supports next scheduled spec
   * @param enableNextSpecRemotely Whether the remote peer receiving requests supports next
   *     scheduled spec
   * @return An Eth2Peer to which we can send requests
   */
  protected Eth2Peer createPeer(
      final boolean enableNextSpecLocally, final boolean enableNextSpecRemotely) {

    return createRemotePeerAndNetwork(
            getSpec(enableNextSpecLocally), getSpec(enableNextSpecRemotely))
        .peer();
  }

  /**
   * Create and connect 2 networks, return an Eth2Peer representing the remote network to which we
   * can send requests.
   *
   * @param spec The spec which the "local" and remote peer will use
   * @return An Eth2Peer to which we can send requests
   */
  protected Eth2Peer createPeer(final Spec spec) {
    return createRemotePeerAndNetwork(spec, spec).peer();
  }

  protected PeerAndNetwork createRemotePeerAndNetwork() {
    return createRemotePeerAndNetwork(getSpec(false), getSpec(false));
  }

  /**
   * Create and connect 2 networks, return an Eth2Peer representing the remote network to which we
   * can send requests along with the corresponding remote Eth2P2PNetwork.
   *
   * @param enableNextSpecLocally Whether the "local" node supports next scheduled spec
   * @param enableNextSpecRemotely Whether the remote peer receiving requests supports next
   *     scheduled spec
   * @return An Eth2Peer to which we can send requests along with its corresponding Eth2P2PNetwork
   */
  protected PeerAndNetwork createRemotePeerAndNetwork(
      final boolean enableNextSpecLocally, final boolean enableNextSpecRemotely) throws Exception {
    return createRemotePeerAndNetwork(
        getSpec(enableNextSpecLocally), getSpec(enableNextSpecRemotely));
  }

  /**
   * Create and connect 2 networks, return an Eth2Peer representing the remote network to which we
   * can send requests along with the corresponding remote Eth2P2PNetwork.
   *
   * @param localSpec The spec which the "local" node will use
   * @param remoteSpec The spec which the remote peer will use
   * @return An Eth2Peer to which we can send requests along with its corresponding Eth2P2PNetwork
   */
  protected PeerAndNetwork createRemotePeerAndNetwork(final Spec localSpec, final Spec remoteSpec) {
    // Set up remote peer storage
    if (peerStorage == null) {
      peerStorage = InMemoryStorageSystemBuilder.create().specProvider(remoteSpec).build();
      peerStorage.chainUpdater().initializeGenesis();
    }

    // Set up local storage
    try (final StorageSystem localStorage =
        InMemoryStorageSystemBuilder.create().specProvider(localSpec).build()) {
      localStorage.chainUpdater().initializeGenesis();

      final Eth2P2PNetwork remotePeerNetwork =
          networkFactory
              .builder()
              .rpcEncoding(
                  RpcEncoding.createSszSnappyEncoding(
                      remoteSpec.getNetworkingConfig().getMaxPayloadSize()))
              .recentChainData(peerStorage.recentChainData())
              .historicalChainData(peerStorage.chainStorage())
              .spec(remoteSpec)
              .startNetwork();

      final Eth2P2PNetwork localNetwork =
          networkFactory
              .builder()
              .rpcEncoding(
                  RpcEncoding.createSszSnappyEncoding(
                      localSpec.getNetworkingConfig().getMaxPayloadSize()))
              .peer(remotePeerNetwork)
              .recentChainData(localStorage.recentChainData())
              .historicalChainData(localStorage.chainStorage())
              .spec(localSpec)
              .startNetwork();

      final Eth2Peer peer = localNetwork.getPeer(remotePeerNetwork.getNodeId()).orElseThrow();
      return new PeerAndNetwork(peer, remotePeerNetwork);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public record PeerAndNetwork(Eth2Peer peer, Eth2P2PNetwork network) {}

  protected static Stream<Arguments> generateSpecTransitionWithCombinationParams() {
    return SpecMilestone.getAllMilestonesFrom(SpecMilestone.ALTAIR).stream()
        .flatMap(
            milestone -> {
              final SpecMilestone prevMilestone = milestone.getPreviousMilestone();
              return Stream.of(
                  Arguments.of(prevMilestone, milestone, true, true),
                  Arguments.of(prevMilestone, milestone, false, true),
                  Arguments.of(prevMilestone, milestone, true, false),
                  Arguments.of(prevMilestone, milestone, false, false));
            });
  }

  protected static Stream<Arguments> generateSpecTransition() {
    return SpecMilestone.getAllMilestonesFrom(SpecMilestone.ALTAIR).stream()
        .map(milestone -> Arguments.of(milestone.getPreviousMilestone(), milestone));
  }

  protected static Stream<Arguments> generateSpec() {
    return Arrays.stream(SpecMilestone.values()).map(Arguments::of);
  }

  protected List<BlobSidecar> retrieveCanonicalBlobSidecarsFromPeerStorage(
      final Stream<UInt64> slots) {

    return slots
        .map(
            slot ->
                peerStorage
                    .recentChainData()
                    .getBlockRootInEffectBySlot(slot)
                    .map(root -> new SlotAndBlockRoot(slot, root)))
        .flatMap(Optional::stream)
        .map(this::safeRetrieveBlobSidecars)
        .flatMap(Collection::stream)
        .toList();
  }

  private List<BlobSidecar> safeRetrieveBlobSidecars(final SlotAndBlockRoot slotAndBlockRoot) {
    try {
      return Waiter.waitFor(
          peerStorage.chainStorage().getBlobSidecarsBySlotAndBlockRoot(slotAndBlockRoot));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected static Class<?> milestoneToBeaconBlockBodyClass(final SpecMilestone milestone) {
    return switch (milestone) {
      case PHASE0 -> BeaconBlockBodyPhase0.class;
      case ALTAIR -> BeaconBlockBodyAltair.class;
      case BELLATRIX -> BeaconBlockBodyBellatrix.class;
      case CAPELLA -> BeaconBlockBodyCapella.class;
      case DENEB -> BeaconBlockBodyDeneb.class;
      case ELECTRA -> BeaconBlockBodyElectra.class;
    };
  }
}
