package com.senren.player;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

final class TrackStore {
    static final ArrayList<Track> tracks = new ArrayList<>();
    private static boolean loaded = false;

    private TrackStore() {}

    static void load(Context c) {
        if (loaded) return;
        loaded = true;
        SharedPreferences sp = c.getSharedPreferences("music_player", Context.MODE_PRIVATE);
        try {
            JSONArray a = new JSONArray(sp.getString("tracks", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Track t = new Track();
                t.name = o.getString("name");
                t.file = o.optString("file", t.name);
                t.uri = o.getString("uri");
                t.size = o.optLong("size", -1);
                tracks.add(t);
            }
        } catch (Exception ignored) {
        }
    }

    static void save(Context c) {
        JSONArray a = new JSONArray();
        try {
            for (Track t : tracks) {
                JSONObject o = new JSONObject();
                o.put("name", t.name);
                o.put("file", t.file);
                o.put("uri", t.uri);
                o.put("size", t.size);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        c.getSharedPreferences("music_player", Context.MODE_PRIVATE)
                .edit().putString("tracks", a.toString()).apply();
    }
}
