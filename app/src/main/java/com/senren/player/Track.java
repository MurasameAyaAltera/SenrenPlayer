package com.senren.player;

import android.text.StaticLayout;

final class Track {
    String name;
    String file;
    String uri;
    long size;

    // cache tata letak teks untuk tampilan daftar
    StaticLayout layout;
    boolean small;
}
