package com.yammer.breakerbox.service.store.tests;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.netflix.turbine.discovery.Instance;
import com.yammer.breakerbox.service.store.TenacityPropertyKeysStore;
import com.yammer.breakerbox.service.tenacity.TenacityPoller;
import org.junit.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TenacityPropertyKeysStoreTest {
    @Test
    public void tenacityPropertyKeysForUris() {
        final TenacityPoller.Factory mockFactory = mock(TenacityPoller.Factory.class);
        final TenacityPoller mockPoller = mock(TenacityPoller.class);
        final TenacityPoller mockPoller2 = mock(TenacityPoller.class);
        final Instance firstInstance = new Instance("http://localhost:1234", "ignored", true);
        final Instance secondInstance = new Instance("http://another:8080", "ignored", true);

        when(mockFactory.create(firstInstance)).thenReturn(mockPoller);
        when(mockFactory.create(secondInstance)).thenReturn(mockPoller2);
        when(mockPoller.execute()).thenReturn(Optional.of(ImmutableList.of("things", "stuff")));
        when(mockPoller2.execute()).thenReturn(Optional.of(ImmutableList.of("morethings", "stuff")));

        final TenacityPropertyKeysStore tenacityPropertyKeysStore = new TenacityPropertyKeysStore(mockFactory);

        assertThat(tenacityPropertyKeysStore.tenacityPropertyKeysFor(ImmutableList.of(firstInstance, secondInstance)))
                .isEqualTo(ImmutableSet.of("things", "stuff", "morethings"));
    }
}
