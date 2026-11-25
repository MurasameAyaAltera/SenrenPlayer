package com.senren.player;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.DocumentsContract;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQ_PICK = 1001;
    private static final int REQ_NOTIF = 1002;

    PlaybackService svc;
    private PlayerView view;
    private boolean bound = false;

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            svc = ((PlaybackService.LocalBinder) binder).get();
            svc.setListener(new Runnable() {
                @Override
                public void run() {
                    if (view != null) view.onServiceChanged();
                }
            });
            if (view != null) view.onServiceChanged();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            svc = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int bg = Color.parseColor("#ECE6E0");
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);

        TrackStore.load(this);
        view = new PlayerView(this, this);
        setContentView(view);

        bound = bindService(new Intent(this, PlaybackService.class), conn, BIND_AUTO_CREATE);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    @Override
    protected void onDestroy() {
        if (svc != null) svc.setListener(null);
        if (bound) {
            unbindService(conn);
            bound = false;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (view != null && view.dialogOpen()) {
            view.closeDialog();
        } else {
            super.onBackPressed();
        }
    }

    // ---------------------------------------------------------------- daftar lagu

    void pickFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_PICK);
        } catch (Exception e) {
            view.notice("No folder picker app was found on this device.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null) return;
        final Uri tree = data.getData();
        if (tree == null) return;

        try {
            getContentResolver().takePersistableUriPermission(
                    tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }

        // pindai folder (beserta subfolder) di thread latar agar tampilan tidak macet
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<Track> found = new ArrayList<>();
                try {
                    scanFolder(tree, DocumentsContract.getTreeDocumentId(tree), found, 0);
                } catch (Exception ignored) {
                }
                Collections.sort(found, new Comparator<Track>() {
                    @Override
                    public int compare(Track a, Track b) {
                        return a.file.compareToIgnoreCase(b.file);
                    }
                });
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onScanDone(found);
                    }
                });
            }
        }, "scan-folder").start();
    }

    private void onScanDone(ArrayList<Track> found) {
        if (isFinishing() || view == null) return;
        int added = 0;
        for (Track t : found) {
            boolean dup = false;
            for (Track x : TrackStore.tracks) {
                if (x.uri.equals(t.uri)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                TrackStore.tracks.add(t);
                added++;
            }
        }
        TrackStore.save(this);
        view.refresh();
        if (found.isEmpty()) {
            view.notice("No music files were found in that folder.");
        } else if (added == 0) {
            view.notice("All songs in that folder are already in the list.");
        } else {
            view.notice(added + (added == 1 ? " song" : " songs") + " added from the folder.");
        }
    }

    private static boolean isAudio(String mime, String name) {
        if (mime != null && (mime.startsWith("audio/") || mime.equals("application/ogg"))) return true;
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        String[] ext = {".mp3", ".m4a", ".aac", ".flac", ".ogg", ".oga", ".opus", ".wav",
                ".wma", ".amr", ".mka", ".mid", ".midi"};
        for (String e : ext) {
            if (n.endsWith(e)) return true;
        }
        return false;
    }

    private void scanFolder(Uri tree, String parentId, ArrayList<Track> out, int depth) {
        if (depth > 12) return;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        Cursor c = null;
        ArrayList<String> subDirs = new ArrayList<>();
        try {
            c = getContentResolver().query(children, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE}, null, null, null);
            if (c == null) return;
            while (c.moveToNext()) {
                String id = c.getString(0);
                String name = c.getString(1);
                String mime = c.getString(2);
                long size = c.isNull(3) ? -1 : c.getLong(3);
                if (id == null) continue;
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    subDirs.add(id);
                } else if (isAudio(mime, name)) {
                    if (name == null) name = "Song";
                    Track t = new Track();
                    t.file = name;
                    t.name = name.replaceFirst("\\.[^.]+$", "");
                    t.size = size;
                    t.uri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString();
                    out.add(t);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        for (String id : subDirs) scanFolder(tree, id, out, depth + 1);
    }

    void removeTrack(int i) {
        if (i < 0 || i >= TrackStore.tracks.size()) return;
        TrackStore.tracks.remove(i);
        TrackStore.save(this);
        if (svc != null) svc.onRemoved(i);
        view.refresh();
    }

    void clearAll() {
        TrackStore.tracks.clear();
        TrackStore.save(this);
        if (svc != null) svc.resetAll();
        view.refresh();
    }
}
