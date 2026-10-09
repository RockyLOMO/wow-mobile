package com.winlator.wowmobile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/** Reversible mode switch; no addon or player settings are deleted or overwritten. */
final class ConsolePortIsolation {
    static boolean apply(File root, boolean nativeTouch) {
        try { root = root.getCanonicalFile(); }
        catch (IOException e) { return false; }
        File addons = new File(root, "Interface/AddOns");
        File saved = new File(root, ".olddream-consoleport");
        File from = nativeTouch ? addons : saved;
        File to = nativeTouch ? saved : addons;
        File[] entries = from.listFiles();
        if (entries == null) return !from.exists();
        ArrayList<File> pending = new ArrayList<>();
        try {
            String canonicalRoot = root.getCanonicalPath() + File.separator;
            if (!from.getCanonicalPath().startsWith(canonicalRoot) ||
                !to.getCanonicalPath().startsWith(canonicalRoot)) return false;
            for (File entry : entries) {
                if (!entry.isDirectory() || !entry.getName().startsWith("ConsolePort")) continue;
                if (!entry.getCanonicalPath().equals(entry.getAbsolutePath()) ||
                    new File(to, entry.getName()).exists()) return false;
                pending.add(entry);
            }
            if (pending.isEmpty()) return true;
            if (!to.isDirectory() && !to.mkdirs()) return false;
            int moved = 0;
            for (File entry : pending) {
                if (!entry.renameTo(new File(to, entry.getName()))) {
                    // Best effort rollback; neither side is ever overwritten.
                    for (int i = moved - 1; i >= 0; i--) {
                        File old = pending.get(i);
                        new File(to, old.getName()).renameTo(old);
                    }
                    return false;
                }
                moved++;
            }
            return true;
        }
        catch (IOException e) { return false; }
    }
}
