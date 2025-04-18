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

package tech.pegasys.teku.spec.datastructures.state.beaconstate;

import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.SlotCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.TransitionCaches;

public interface BeaconStateCache {

  static TransitionCaches getTransitionCaches(final BeaconState state) {
    return state instanceof BeaconStateCache
        ? ((BeaconStateCache) state).getTransitionCaches()
        : TransitionCaches.getNoOp();
  }

  static SlotCaches getSlotCaches(final BeaconState state) {
    return state instanceof BeaconStateCache
        ? ((BeaconStateCache) state).getSlotCaches()
        : SlotCaches.getNoOp();
  }

  TransitionCaches getTransitionCaches();

  SlotCaches getSlotCaches();
}
