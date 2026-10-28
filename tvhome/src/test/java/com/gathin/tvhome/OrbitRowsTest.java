package com.gathin.tvhome;

import android.content.pm.ApplicationInfo;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class OrbitRowsTest {
    @Test public void favoritesKeepTheExistingOrderAndAlsoAppearInTheirCategories() {
        List<OrbitRows.Row> rows = OrbitRows.build(Arrays.asList("org.smarttube.beta", "org.jellyfin.androidtv", "other"),
                new int[]{-1, -1, -1}, new long[]{0, 0, 0}, 2);
        assertEquals(OrbitRows.Section.FAVORITES, rows.get(0).section);
        assertEquals(Arrays.asList(0, 1), rows.get(0).positions);
        assertEquals(OrbitRows.Section.STREAMING, rows.get(1).section);
        assertEquals(Collections.singletonList(0), rows.get(1).positions);
        assertEquals(OrbitRows.Section.MEDIA, rows.get(2).section);
        assertEquals(Collections.singletonList(1), rows.get(2).positions);
        assertEquals(OrbitRows.Section.APPS, rows.get(3).section);
    }

    @Test public void recentContainsOnlyOpenedAppsNewestFirstAndStopsAtSix() {
        List<OrbitRows.Row> rows = OrbitRows.build(Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h"),
                new int[8], new long[]{0, 2, 9, 5, 7, 6, 3, 4}, 0);
        assertEquals(OrbitRows.Section.RECENT, rows.get(0).section);
        assertEquals(Arrays.asList(2, 4, 5, 3, 7, 6), rows.get(0).positions);
    }

    @Test public void equalRecentTimesPreserveTheSavedAppOrder() {
        List<OrbitRows.Row> rows = OrbitRows.build(Arrays.asList("a", "b"), new int[]{-1, -1}, new long[]{7, 7}, 0);
        assertEquals(Arrays.asList(0, 1), rows.get(0).positions);
    }

    @Test public void emptySlotsAndEmptyGroupsAreOmitted() {
        List<OrbitRows.Row> rows = OrbitRows.build(Arrays.asList(null, "org.videolan.vlc"),
                new int[]{-1, -1}, new long[]{100, 0}, 1);
        assertEquals(1, rows.size());
        assertEquals(OrbitRows.Section.MEDIA, rows.get(0).section);
        assertEquals(Collections.singletonList(1), rows.get(0).positions);
        assertTrue(OrbitRows.build(Collections.emptyList(), new int[0], new long[0], 6).isEmpty());
    }

    @Test public void appMetadataSuppliesCategoriesForUnknownPackages() {
        assertEquals(OrbitRows.Section.MUSIC, OrbitRows.category("player", ApplicationInfo.CATEGORY_AUDIO));
        assertEquals(OrbitRows.Section.GAMES, OrbitRows.category("game", ApplicationInfo.CATEGORY_GAME));
        assertEquals(OrbitRows.Section.MEDIA, OrbitRows.category("video", ApplicationInfo.CATEGORY_VIDEO));
        assertEquals(OrbitRows.Section.SOCIAL, OrbitRows.category("chat", ApplicationInfo.CATEGORY_SOCIAL));
        assertEquals(OrbitRows.Section.NEWS, OrbitRows.category("news", ApplicationInfo.CATEGORY_NEWS));
        assertEquals(OrbitRows.Section.TOOLS, OrbitRows.category("work", ApplicationInfo.CATEGORY_PRODUCTIVITY));
        assertEquals(OrbitRows.Section.APPS, OrbitRows.category("unknown", ApplicationInfo.CATEGORY_UNDEFINED));
    }

    @Test public void knownAppsUseOrbitsAssignmentsAheadOfGenericMetadata() {
        assertEquals(OrbitRows.Section.STREAMING, OrbitRows.category("com.stremio.one", ApplicationInfo.CATEGORY_VIDEO));
        assertEquals(OrbitRows.Section.TOOLS, OrbitRows.category("io.github.viren070.aiostreams", ApplicationInfo.CATEGORY_VIDEO));
    }
}
