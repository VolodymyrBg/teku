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

package tech.pegasys.teku.api.schema.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import tech.pegasys.teku.api.schema.altair.BeaconStateAltair;
import tech.pegasys.teku.api.schema.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.api.schema.capella.BeaconStateCapella;
import tech.pegasys.teku.api.schema.deneb.BeaconStateDeneb;
import tech.pegasys.teku.api.schema.electra.BeaconStateElectra;
import tech.pegasys.teku.api.schema.phase0.BeaconStatePhase0;

@Schema(
    oneOf = {
      BeaconStatePhase0.class,
      BeaconStateAltair.class,
      BeaconStateBellatrix.class,
      BeaconStateCapella.class,
      BeaconStateDeneb.class,
      BeaconStateElectra.class
    })
public interface State {}
