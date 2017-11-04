package org.secuso.privacyfriendlynotes.sync;

import java.io.Serializable;

public class NoteModel implements Serializable {
    public long timestamp;
    public int type;
    public String name;
    public String content;
    public String category;
    public boolean trash;
    public boolean deleted;
}
