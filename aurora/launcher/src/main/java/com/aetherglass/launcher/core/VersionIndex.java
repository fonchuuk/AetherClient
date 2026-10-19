package com.aetherglass.launcher.core;

import com.aetherglass.launcher.util.Http;
import com.aetherglass.launcher.util.Json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Список версий Minecraft из официального манифеста Mojang.
 *
 * <p>Источник — {@code piston-meta.mojang.com}. Никаких сторонних зеркал: манифест
 * предоставляется инфраструктурой Mojang и является единственным официальным способом
 * получить описание версии.</p>
 */
public final class VersionIndex {
    public static final String MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private final List<Entry> entries;

    private VersionIndex(List<Entry> entries) {
        this.entries = entries;
    }

    /** Загружает манифест версий. */
    public static VersionIndex fetch() throws IOException, InterruptedException {
        Map<String, Object> manifest = Json.asObject(Http.getJson(MANIFEST_URL));
        List<Object> versions = Json.asArray(manifest.get("versions"));
        List<Entry> entries = new ArrayList<>();
        for (Object item : versions) {
            Map<String, Object> version = Json.asObject(item);
            entries.add(new Entry(Json.requireString(version, "id"),
                    Json.string(version, "type", "release"),
                    Json.requireString(version, "url")));
        }
        if (entries.isEmpty()) {
            throw new IOException("манифест версий не содержит ни одной версии");
        }
        return new VersionIndex(entries);
    }

    /** Все версии: сначала новые. */
    public List<Entry> all() {
        return List.copyOf(this.entries);
    }

    /** Только выпущенные версии (без снимков разработки). */
    public List<Entry> releases() {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : this.entries) {
            if ("release".equals(entry.type())) {
                result.add(entry);
            }
        }
        return List.copyOf(result);
    }

    public Entry find(String id) {
        for (Entry entry : this.entries) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }
        return null;
    }

    public boolean contains(String id) {
        return find(id) != null;
    }

    /** Одна запись манифеста. */
    public record Entry(String id, String type, String url) {
        @Override
        public String toString() {
            return this.id;
        }
    }
}
