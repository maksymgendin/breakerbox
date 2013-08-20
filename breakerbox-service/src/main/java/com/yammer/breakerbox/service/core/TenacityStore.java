package com.yammer.breakerbox.service.core;

import com.google.common.base.Optional;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.microsoft.windowsazure.services.table.client.TableConstants;
import com.microsoft.windowsazure.services.table.client.TableQuery;
import com.yammer.azure.TableClient;
import com.yammer.azure.core.TableType;
import com.yammer.breakerbox.service.azure.ServiceEntity;
import com.yammer.breakerbox.service.azure.TableId;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Timer;
import com.yammer.metrics.core.TimerContext;
import com.yammer.tenacity.core.config.TenacityConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class TenacityStore {
    private final TableClient tableClient;
    private final Cache<ServiceId, ImmutableList<ServiceEntity>> listDependenciesCache = CacheBuilder
            .newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    private static final Timer LIST_SERVICES = Metrics.newTimer(TenacityStore.class, "list-services");
    private static final Timer LIST_SERVICE = Metrics.newTimer(TenacityStore.class, "list-service");
    private static final Logger LOGGER = LoggerFactory.getLogger(TenacityStore.class);

    public TenacityStore(TableClient tableClient) {
        this.tableClient = tableClient;
    }

    public boolean store(ServiceId serviceId, DependencyId dependencyId) {
        return store(ServiceEntity.build(serviceId, dependencyId));
    }

    public boolean store(ServiceId serviceId, DependencyId dependencyId, TenacityConfiguration tenacityConfiguration) {
        return store(ServiceEntity.build(serviceId, dependencyId, tenacityConfiguration));
    }

    public boolean store(ServiceEntity serviceEntity) {
        listDependenciesCache.invalidate(serviceEntity.getServiceId());
        return tableClient.insertOrReplace(serviceEntity);
    }

    public boolean remove(TableType tableType) {
        return tableClient.remove(tableType);
    }

    public Optional<ServiceEntity> retrieve(ServiceId serviceId, DependencyId dependencyId) {
        return tableClient.retrieve(ServiceEntity.build(serviceId, dependencyId));
    }

    public ImmutableList<ServiceEntity> listServices() {
        return allServiceEntities();
    }

    public ImmutableList<ServiceEntity> listDependencies(final ServiceId serviceId) {
        try {
            return listDependenciesCache.get(serviceId, new Callable<ImmutableList<ServiceEntity>>() {
                @Override
                public ImmutableList<ServiceEntity> call() throws Exception {
                    return allServiceEntities(serviceId);
                }
            });
        } catch (ExecutionException err) {
            LOGGER.warn("Could not fetch dependencies for {}", serviceId, err);
        }
        return ImmutableList.of();
    }

    private ImmutableList<ServiceEntity> allServiceEntities(ServiceId serviceId) {
        final TimerContext timerContext = LIST_SERVICE.time();
        try {
            return tableClient.search(TableQuery
                    .from(TableId.SERVICES.toString(), ServiceEntity.class)
                    .where(TableQuery
                            .generateFilterCondition(
                                    TableConstants.PARTITION_KEY,
                                    TableQuery.QueryComparisons.EQUAL,
                                    serviceId.getId())));
        } finally {
            timerContext.stop();
        }
    }

    private ImmutableList<ServiceEntity> allServiceEntities() {
        final TimerContext timerContext = LIST_SERVICES.time();
        try {
            return tableClient.search(TableQuery
                    .from(TableId.SERVICES.toString(), ServiceEntity.class));
        } finally {
            timerContext.stop();
        }
    }
}