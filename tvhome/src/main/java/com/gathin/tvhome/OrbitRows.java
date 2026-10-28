package com.gathin.tvhome;

import android.content.pm.ApplicationInfo;

import java.util.ArrayList;
import java.util.List;

/** Row positions refer to the existing app model, so launching and ordering share one source of truth. */
final class OrbitRows {
    enum Section { FAVORITES, RECENT, STREAMING, MEDIA, MUSIC, GAMES, SOCIAL, NEWS, TOOLS, APPS }

    static final class Row {
        final Section section;
        final List<Integer> positions = new ArrayList<>();

        Row(Section section) { this.section = section; }
    }

    private OrbitRows() {}

    static List<Row> build(List<String> packages, int[] categories, long[] opened, int favorites) {
        List<Row> rows = new ArrayList<>();
        for (Section section : Section.values()) {
            Row row = new Row(section);
            for (int i = 0; i < packages.size(); i++) {
                String pkg = packages.get(i);
                if (pkg == null) continue;
                if (section == Section.FAVORITES ? i < favorites
                        : section == Section.RECENT ? opened[i] > 0
                        : category(pkg, categories[i]) == section) row.positions.add(i);
            }
            if (section == Section.RECENT) {
                row.positions.sort((a, b) -> Long.compare(opened[b], opened[a]));
                if (row.positions.size() > 6) row.positions.subList(6, row.positions.size()).clear();
            }
            if (!row.positions.isEmpty()) rows.add(row);
        }
        return rows;
    }

    static Section category(String pkg, int category) {
        switch (pkg) {
            case "org.smarttube.beta": case "com.netflix.ninja": case "com.stremio.one":
            case "com.rblive.app": case "app.cricfy.tv": case "com.sportzx.live":
            case "com.github.razenfr.xtratv": return Section.STREAMING;
            case "org.jellyfin.androidtv": case "org.videolan.vlc": return Section.MEDIA;
            case "com.esaba.downloader": case "org.localsend.localsend_app":
            case "com.gaditek.purevpnics": case "com.phlox.tvwebbrowser":
            case "org.fossify.gallery": case "com.alphainventor.filemanager":
            case "io.github.viren070.aiostreams": case "com.lionlev.tv.capacity": return Section.TOOLS;
            default:
                switch (category) {
                    case ApplicationInfo.CATEGORY_GAME: return Section.GAMES;
                    case ApplicationInfo.CATEGORY_AUDIO: return Section.MUSIC;
                    case ApplicationInfo.CATEGORY_VIDEO: case ApplicationInfo.CATEGORY_IMAGE: return Section.MEDIA;
                    case ApplicationInfo.CATEGORY_SOCIAL: return Section.SOCIAL;
                    case ApplicationInfo.CATEGORY_NEWS: return Section.NEWS;
                    case ApplicationInfo.CATEGORY_MAPS: case ApplicationInfo.CATEGORY_PRODUCTIVITY: return Section.TOOLS;
                    default: return Section.APPS;
                }
        }
    }
}
