package com.winlator.xserver;

import android.os.Environment;
import android.os.SystemClock;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class InputEventLogger {
    private BufferedWriter writer;
    private final long startTime = SystemClock.elapsedRealtime();

    public InputEventLogger() {
        File parent = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Winlator/input");
        if (!parent.isDirectory()) parent.mkdirs();

        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ENGLISH).format(new Date());
        for (int index = 0; index < 100 && writer == null; index++) {
            String suffix = index == 0 ? "" : "-"+index;
            File candidate = new File(parent, "input-events-"+timestamp+suffix+".log");
            try {
                if (!candidate.createNewFile()) continue;
                writer = new BufferedWriter(new FileWriter(candidate, false));
                log("session_start");
            }
            catch (IOException error) {
                close();
                candidate.delete();
            }
        }
    }

    public synchronized void log(String message) {
        if (writer == null) return;
        try {
            writer.write((SystemClock.elapsedRealtime()-startTime)+" "+message+"\n");
            writer.flush();
        }
        catch (IOException error) {
            close();
        }
    }

    public synchronized void close() {
        if (writer == null) return;
        try {
            writer.write((SystemClock.elapsedRealtime()-startTime)+" session_end\n");
            writer.flush();
            writer.close();
        }
        catch (IOException ignored) {}
        finally {
            writer = null;
        }
    }

    public static String describeWindow(Window window) {
        if (window == null) return "null";
        Window parent = window.getParent();
        return "id="+window.id+
               ",parent="+(parent != null ? parent.id : 0)+
               ",name="+sanitize(window.getName())+
               ",class="+sanitize(window.getClassName())+
               ",mapped="+window.attributes.isMapped()+
               ",viewable="+window.attributes.isViewable()+
               ",enabled="+window.attributes.isEnabled()+
               ",renderable="+window.isRenderable()+
               ",surface="+window.isSurface()+
               ",application="+window.isApplicationWindow()+
               ",keyPress="+window.hasEventListenerFor(com.winlator.xserver.events.Event.KEY_PRESS)+
               ",keyRelease="+window.hasEventListenerFor(com.winlator.xserver.events.Event.KEY_RELEASE)+
               ",focusChange="+window.hasEventListenerFor(com.winlator.xserver.events.Event.FOCUS_CHANGE);
    }

    private static String sanitize(String value) {
        return value.replace('\n', ' ').replace('\r', ' ').replace(',', ';');
    }
}
