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

package tech.pegasys.teku.services.chainstorage;

import static tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory.DEFAULT_MAX_QUEUE_SIZE;
import static tech.pegasys.teku.spec.config.Constants.STORAGE_QUERY_CHANNEL_PARALLELISM;

import com.google.common.annotations.VisibleForTesting;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.AsyncRunnerEventThread;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.storage.api.CombinedStorageChannel;
import tech.pegasys.teku.storage.api.Eth1DepositStorageChannel;
import tech.pegasys.teku.storage.api.VoteUpdateChannel;
import tech.pegasys.teku.storage.archive.DataArchive;
import tech.pegasys.teku.storage.archive.fsarchive.FileSystemArchive;
import tech.pegasys.teku.storage.archive.nooparchive.NoopDataArchive;
import tech.pegasys.teku.storage.server.BatchingVoteUpdateChannel;
import tech.pegasys.teku.storage.server.ChainStorage;
import tech.pegasys.teku.storage.server.CombinedStorageChannelSplitter;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.DepositStorage;
import tech.pegasys.teku.storage.server.RetryingStorageUpdateChannel;
import tech.pegasys.teku.storage.server.StorageConfiguration;
import tech.pegasys.teku.storage.server.VersionedDatabaseFactory;
import tech.pegasys.teku.storage.server.network.EphemeryException;
import tech.pegasys.teku.storage.server.pruner.BlobSidecarPruner;
import tech.pegasys.teku.storage.server.pruner.BlockPruner;
import tech.pegasys.teku.storage.server.pruner.StatePruner;

public class StorageService extends Service implements StorageServiceFacade {
  public static final Duration STATE_PRUNING_INTERVAL = Duration.ofMinutes(1);
  private final StorageConfiguration config;
  private volatile ChainStorage chainStorage;
  private final ServiceConfig serviceConfig;
  private volatile Database database;
  private volatile BatchingVoteUpdateChannel batchingVoteUpdateChannel;
  private volatile Optional<BlockPruner> blockPruner = Optional.empty();
  private volatile Optional<BlobSidecarPruner> blobsPruner = Optional.empty();
  private volatile Optional<StatePruner> statePruner = Optional.empty();
  private final boolean depositSnapshotStorageEnabled;
  private final boolean blobSidecarsStorageCountersEnabled;
  private static final Logger LOG = LogManager.getLogger();
  private final Optional<Eth2Network> maybeNetwork;

  public StorageService(
      final ServiceConfig serviceConfig,
      final StorageConfiguration storageConfiguration,
      final boolean depositSnapshotStorageEnabled,
      final boolean blobSidecarsStorageCountersEnabled,
      final Optional<Eth2Network> eth2Network) {
    this.serviceConfig = serviceConfig;
    this.config = storageConfiguration;
    this.depositSnapshotStorageEnabled = depositSnapshotStorageEnabled;
    this.blobSidecarsStorageCountersEnabled = blobSidecarsStorageCountersEnabled;
    this.maybeNetwork = eth2Network;
  }

  @Override
  protected SafeFuture<?> doStart() {
    return SafeFuture.fromRunnable(
            () -> {
              final AsyncRunner storagePrunerAsyncRunner =
                  serviceConfig.createAsyncRunner(
                      "storagePrunerAsyncRunner",
                      1,
                      DEFAULT_MAX_QUEUE_SIZE,
                      Thread.NORM_PRIORITY - 1);
              final VersionedDatabaseFactory dbFactory =
                  new VersionedDatabaseFactory(
                      serviceConfig.getMetricsSystem(),
                      serviceConfig.getDataDirLayout().getBeaconDataDirectory(),
                      config,
                      maybeNetwork);
              try {
                database = dbFactory.createDatabase();
              } catch (EphemeryException e) {
                final EphemeryDatabaseReset ephemeryDatabaseReset = new EphemeryDatabaseReset();
                LOG.warn(
                    "Ephemery network deposit contract id has updated, resetting the stored database and slashing protection data.");
                database = ephemeryDatabaseReset.resetDatabaseAndCreate(serviceConfig, dbFactory);
              }

              final SettableLabelledGauge pruningTimingsLabelledGauge =
                  SettableLabelledGauge.create(
                      serviceConfig.getMetricsSystem(),
                      TekuMetricCategory.STORAGE,
                      "pruning_time",
                      "Tracks last pruning duration in milliseconds",
                      "type");

              final SettableLabelledGauge pruningActiveLabelledGauge =
                  SettableLabelledGauge.create(
                      serviceConfig.getMetricsSystem(),
                      TekuMetricCategory.STORAGE,
                      "pruning_active",
                      "Tracks when pruner is active",
                      "type");

              if (!config.getDataStorageMode().storesAllBlocks()) {
                blockPruner =
                    Optional.of(
                        new BlockPruner(
                            config.getSpec(),
                            database,
                            storagePrunerAsyncRunner,
                            config.getBlockPruningInterval(),
                            config.getBlockPruningLimit(),
                            "block",
                            pruningTimingsLabelledGauge,
                            pruningActiveLabelledGauge));
              }
              if (config.getDataStorageMode().storesFinalizedStates()
                  && config.getRetainedSlots() > 0) {
                configureStatePruner(
                    config.getRetainedSlots(),
                    storagePrunerAsyncRunner,
                    config.getStatePruningInterval(),
                    pruningTimingsLabelledGauge,
                    pruningActiveLabelledGauge);
              } else if (!config.getDataStorageMode().storesFinalizedStates()) {
                final Duration statePruningInterval =
                    config
                            .getStatePruningInterval()
                            .equals(StorageConfiguration.DEFAULT_STATE_PRUNING_INTERVAL)
                        ? STATE_PRUNING_INTERVAL
                        : config.getStatePruningInterval();
                configureStatePruner(
                    StorageConfiguration.DEFAULT_STORAGE_RETAINED_SLOTS,
                    storagePrunerAsyncRunner,
                    statePruningInterval,
                    pruningTimingsLabelledGauge,
                    pruningActiveLabelledGauge);
              }

              final DataArchive dataArchive =
                  config
                      .getBlobsArchivePath()
                      .<DataArchive>map(path -> new FileSystemArchive(Path.of(path)))
                      .orElse(new NoopDataArchive());

              if (config.getSpec().isMilestoneSupported(SpecMilestone.DENEB)) {
                blobsPruner =
                    Optional.of(
                        new BlobSidecarPruner(
                            config.getSpec(),
                            database,
                            dataArchive,
                            serviceConfig.getMetricsSystem(),
                            storagePrunerAsyncRunner,
                            serviceConfig.getTimeProvider(),
                            config.getBlobsPruningInterval(),
                            config.getBlobsPruningLimit(),
                            blobSidecarsStorageCountersEnabled,
                            "blob_sidecar",
                            pruningTimingsLabelledGauge,
                            pruningActiveLabelledGauge,
                            config.isStoreNonCanonicalBlocksEnabled()));
              }
              final EventChannels eventChannels = serviceConfig.getEventChannels();
              chainStorage =
                  ChainStorage.create(
                      database,
                      config.getSpec(),
                      config.getDataStorageMode(),
                      config.getStateRebuildTimeoutSeconds());
              final DepositStorage depositStorage =
                  DepositStorage.create(
                      eventChannels.getPublisher(Eth1EventsChannel.class),
                      database,
                      depositSnapshotStorageEnabled);

              batchingVoteUpdateChannel =
                  new BatchingVoteUpdateChannel(
                      chainStorage,
                      new AsyncRunnerEventThread(
                          "batch-vote-updater", serviceConfig.getAsyncRunnerFactory()));

              eventChannels.subscribe(
                  CombinedStorageChannel.class,
                  new CombinedStorageChannelSplitter(
                      serviceConfig.createAsyncRunner(
                          "storage_query", STORAGE_QUERY_CHANNEL_PARALLELISM),
                      new RetryingStorageUpdateChannel(
                          chainStorage, serviceConfig.getTimeProvider()),
                      chainStorage));

              eventChannels
                  .subscribe(Eth1DepositStorageChannel.class, depositStorage)
                  .subscribe(Eth1EventsChannel.class, depositStorage)
                  .subscribe(VoteUpdateChannel.class, batchingVoteUpdateChannel);
            })
        .thenCompose(
            __ ->
                blockPruner
                    .map(BlockPruner::start)
                    .orElseGet(() -> SafeFuture.completedFuture(null)))
        .thenCompose(
            __ ->
                blobsPruner
                    .map(BlobSidecarPruner::start)
                    .orElseGet(() -> SafeFuture.completedFuture(null)))
        .thenCompose(
            __ ->
                statePruner
                    .map(StatePruner::start)
                    .orElseGet(() -> SafeFuture.completedFuture(null)));
  }

  void configureStatePruner(
      final long slotsToRetain,
      final AsyncRunner storagePrunerAsyncRunner,
      final Duration pruningInterval,
      final SettableLabelledGauge pruningTimingsLabelledGauge,
      final SettableLabelledGauge pruningActiveLabelledGauge) {
    if (config.getDataStorageCreateDbVersion() == DatabaseVersion.LEVELDB_TREE) {
      throw new InvalidConfigurationException(
          "State pruning is not supported with leveldb_tree database.");
    }

    LOG.info(
        "State pruner will run every: {} minute(s), retaining states for the last {} finalized slots. Limited to {} state prune per execution.",
        config.getStatePruningInterval().toMinutes(),
        slotsToRetain,
        config.getStatePruningLimit());

    statePruner =
        Optional.of(
            new StatePruner(
                config.getSpec(),
                database,
                storagePrunerAsyncRunner,
                pruningInterval,
                slotsToRetain,
                config.getStatePruningLimit(),
                "state",
                pruningTimingsLabelledGauge,
                pruningActiveLabelledGauge));
  }

  @VisibleForTesting
  public Optional<StatePruner> getStatePruner() {
    return statePruner;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.allOf(
            blockPruner.map(BlockPruner::stop).map(SafeFuture::toVoid).orElse(SafeFuture.COMPLETE),
            blobsPruner
                .map(BlobSidecarPruner::stop)
                .map(SafeFuture::toVoid)
                .orElse(SafeFuture.COMPLETE),
            statePruner.map(StatePruner::stop).map(SafeFuture::toVoid).orElse(SafeFuture.COMPLETE))
        .thenCompose(__ -> SafeFuture.fromRunnable(database::close));
  }

  @Override
  public ChainStorage getChainStorage() {
    return chainStorage;
  }
}
