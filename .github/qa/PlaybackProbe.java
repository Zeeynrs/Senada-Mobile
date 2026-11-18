package io.github.zeeynrs.senada.qa;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlaybackProbe extends Instrumentation {
    private UiAutomation automation;
    private static final Pattern PROGRESS = Pattern.compile("0:(\\d{2}) / 0:12");

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            automation = getUiAutomation();
            verifyPlayback();
            result.putString("stream", "\nPASS: search, queue, pause, seek, resume, next, previous, automatic advance and HTTPS\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", "\nFAIL: " + android.util.Log.getStackTraceString(failure) + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void verifyPlayback() throws Exception {
        shell("am start -n io.github.zeeynrs.senada/senada.platform.MainActivity");
        waitFor("two demo tracks", () -> labels("Tambah").size() == 2);
        tap("Cari judul atau artis");
        shell("input text Pagi");
        waitFor("one result for Pagi", () -> labels("Tambah").size() == 1 && !labels("Sebelum Pagi").isEmpty());
        tap("Tambah");
        tap("Pagi");
        shell("input keyevent 123 67 67 67 67");
        shell("input keyevent 4");
        waitFor("both results again", () -> labels("Tambah").size() == 2);
        tap("Tambah");
        tap("Putar");
        waitForLabel("Sebelum Pagi · Memutar file lokal");
        tap("Jeda");
        waitForLabel("Sebelum Pagi · Dijeda");
        tap("Posisi pemutaran", 0.55);
        waitFor("seek while paused", () -> !labels("Sebelum Pagi · Dijeda").isEmpty() && playedSeconds() >= 5);
        int pausedAt = playedSeconds();
        SystemClock.sleep(1200);
        if (playedSeconds() != pausedAt) throw new AssertionError("Paused position kept moving");

        tap("Putar");
        waitForLabel("Sebelum Pagi · Memutar file lokal");
        waitFor("resume advances the audio position", () -> playedSeconds() > pausedAt);
        tap("Berikutnya");
        waitForLabel("Selepas Hujan · Memutar file lokal");
        tap("Sebelumnya");
        waitForLabel("Sebelum Pagi · Memutar file lokal");
        waitForLabel("Selepas Hujan · Memutar file lokal");
        tap("Putar HTTPS");
        waitForLabel("Selepas Hujan · Memutar dari HTTPS");
    }

    private void waitForLabel(String text) throws Exception {
        waitFor(text, () -> !labels(text).isEmpty());
    }

    private void waitFor(String description, java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 22000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition.getAsBoolean()) return;
            SystemClock.sleep(220);
        }
        List<String> visible = new ArrayList<>();
        visit(automation.getRootInActiveWindow(), node -> {
            if (node.getText() != null) visible.add(node.getText().toString());
            return false;
        });
        throw new AssertionError("Timed out waiting for " + description + ". Visible text: " + visible);
    }

    private List<AccessibilityNodeInfo> labels(String text) {
        List<AccessibilityNodeInfo> matches = new ArrayList<>();
        visit(automation.getRootInActiveWindow(), node -> {
            if (text.contentEquals(node.getText() == null ? "" : node.getText()) ||
                text.contentEquals(node.getContentDescription() == null ? "" : node.getContentDescription())) {
                matches.add(node);
            }
            return false;
        });
        return matches;
    }

    private int playedSeconds() {
        int[] result = {-1};
        visit(automation.getRootInActiveWindow(), node -> {
            if (node.getText() == null) return false;
            Matcher match = PROGRESS.matcher(node.getText());
            if (!match.matches()) return false;
            result[0] = Integer.parseInt(match.group(1));
            return true;
        });
        return result[0];
    }

    private boolean visit(AccessibilityNodeInfo node, Predicate<AccessibilityNodeInfo> stop) {
        if (node == null) return false;
        if (stop.test(node)) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            if (visit(node.getChild(i), stop)) return true;
        }
        return false;
    }

    private void tap(String text) throws Exception {
        waitFor("enabled " + text, () -> labels(text).stream().anyMatch(AccessibilityNodeInfo::isEnabled));
        for (AccessibilityNodeInfo node : labels(text)) {
            if (node.isEnabled() && node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
        }
        tap(text, 0.5);
    }

    private void tap(String text, double fromLeft) throws Exception {
        waitForLabel(text);
        Rect bounds = new Rect();
        labels(text).stream().filter(AccessibilityNodeInfo::isEnabled).findFirst().orElseThrow(AssertionError::new).getBoundsInScreen(bounds);
        shell("input tap " + (bounds.left + Math.round(bounds.width() * fromLeft)) + " " + bounds.centerY());
    }

    private void shell(String command) throws Exception {
        try (InputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))) {
            byte[] buffer = new byte[1024];
            while (stream.read(buffer) != -1) { }
        }
    }
}
