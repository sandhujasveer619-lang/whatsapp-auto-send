package com.zyvo.autosend;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityService.GestureResultCallback;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class WhatsappAccessibilityService extends AccessibilityService {
    public static final String ACTION_TRIGGER = "com.zyvo.autosend.TRIGGER";
    public static final String ACTION_RESULT = "com.zyvo.autosend.SEND_RESULT";
    public static final String EXTRA_RESULT = "msg";

    private static volatile String pendingChat;
    private static volatile String pendingMessage;
    private static volatile boolean pending;
    private static volatile boolean scanPending;

    private static final String[] NAME_IDS = {
            "contact_name", "subject", "conversation_name", "chat_name",
            "group_name", "contactname", "title"
    };

    private static final String[] IGNORE = {
            "search", "search chats", "chats", "archived", "new chat", "communities",
            "status", "calls", "all", "unread", "favorites", "groups", "messages",
            "back", "menu", "more", "settings", "profile", "camera", "attach",
            "send", "emoji", "type a message", "message", "today", "yesterday",
            "online", "typing", "add", "cancel", "ok", "done", "select", "contacts",
            "new group", "new community", "invite", "notifications", "media",
            "documents", "links", "notes", "view all", "see all"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean registered;

    private final BroadcastReceiver triggerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (i != null && ChatPicker.ACTION_SCAN.equals(i.getAction())) {
                beginScan();
                return;
            }
            onTrigger();
        }
    };

    public static void requestSend(String chat, String message) {
        pendingChat = chat;
        pendingMessage = message;
        pending = true;
    }

    public static boolean isEnabled(Context c) {
        try {
            String flat = c.getPackageName() + "/" + WhatsappAccessibilityService.class.getName();
            String names = android.provider.Settings.Secure.getString(
                    c.getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return names != null && names.contains(flat);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        if (!registered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_TRIGGER);
            filter.addAction(ChatPicker.ACTION_SCAN);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(triggerReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    registerReceiver(triggerReceiver, filter);
                }
                registered = true;
            } catch (Throwable t) {
                Log.w("AutoSend", "receiver register failed", t);
            }
        }
        if (pending) handler.postDelayed(this::onTrigger, 400);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ChatPicker.ACTION_SCAN.equals(intent.getAction())) {
            beginScan();
        } else if (intent != null && ACTION_TRIGGER.equals(intent.getAction())) {
            onTrigger();
        } else if (pending) {
            handler.postDelayed(this::onTrigger, 300);
        }
        return START_STICKY;
    }

    private void beginScan() {
        if (scanPending) return;
        scanPending = true;
        handler.postDelayed(this::scanChats, 250);
    }

    private void scanChats() {
        if (!scanPending) return;
        scanPending = false;
        launchWhatsApp();
        handler.postDelayed(this::ensureListAndDump, 2600);
    }

    private void ensureListAndDump() {
        boolean onList = findByDesc("Search", "Search chats", "search") != null
                || findById("search", "action_search", "conversations_search", "search_button") != null;
        if (!onList) {
            try { performGlobalAction(GLOBAL_ACTION_BACK); } catch (Throwable ignored) {}
            handler.postDelayed(this::dumpChats, 1500);
        } else {
            dumpChats();
        }
    }

    private void dumpChats() {
        Set<String> found = new LinkedHashSet<>();
        collectById(root(), found);
        if (found.isEmpty()) collectBroad(root(), found);

        List<String> names = new ArrayList<>(found);
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        if (names.size() > 200) names = names.subList(0, 200);

        JSONArray arr = new JSONArray();
        for (String n : names) arr.put(n);
        Settings.saveChats(this, arr.toString());

        Intent ready = new Intent(ChatPicker.ACTION_CHATS_READY);
        ready.setPackage(getPackageName());
        try { sendBroadcast(ready); } catch (Throwable ignored) {}

        Intent main = new Intent(this, MainActivity.class);
        main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { startActivity(main); } catch (Throwable ignored) {}
    }

    private void collectById(AccessibilityNodeInfo node, Set<String> out) {
        if (node == null) return;
        String id = node.getViewIdResourceName();
        if (id != null) {
            for (String key : NAME_IDS) {
                if (id.contains(key)) {
                    String s = textOf(node);
                    if (valid(s)) out.add(s);
                    break;
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectById(node.getChild(i), out);
        }
    }

    private void collectBroad(AccessibilityNodeInfo node, Set<String> out) {
        if (node == null) return;
        if (!node.isEditable() && !"android.widget.Button".equals(String.valueOf(node.getClassName()))) {
            String s = textOf(node);
            if (valid(s)) out.add(s);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectBroad(node.getChild(i), out);
        }
    }

    private static String textOf(AccessibilityNodeInfo node) {
        CharSequence t = node.getText();
        if (t == null || t.toString().trim().isEmpty()) t = node.getContentDescription();
        return t == null ? "" : t.toString().trim();
    }

    private static boolean valid(String s) {
        if (s.isEmpty() || s.length() > 80 || s.indexOf('\n') >= 0) return false;
        String low = s.toLowerCase(Locale.ROOT);
        for (String bad : IGNORE) if (low.equals(bad)) return false;
        if (low.matches("[0-9:/\\s-]+")) return false;
        return low.matches(".*[a-z0-9].*");
    }

    private void report(String msg, boolean terminal) {
        Settings.saveResult(this, msg);
        try { Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); } catch (Throwable ignored) {}
        Intent b = new Intent(ACTION_RESULT);
        b.setPackage(getPackageName());
        b.putExtra(EXTRA_RESULT, msg);
        try { sendBroadcast(b); } catch (Throwable ignored) {}
        if (terminal && Trigger.consumeScreenWasOff()) {
            relockScreen();
            Trigger.releaseWakeLock();
        }
    }

    /* ---- best-effort secure-lock unlock ------------------------------- */

    private interface Done { void run(); }

    private static final String[] ENTER_WORDS = {
            "enter", "go", "done", "ok", "unlock", "return", "send"
    };

    private List<AccessibilityWindowInfo> windows() {
        try {
            List<AccessibilityWindowInfo> list = getWindows();
            return list == null ? Collections.emptyList() : list;
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    private void beginUnlock(final String chat, final String message) {
        report("Unlocking screen...", false);
        final String password = Settings.unlockPassword(this);
        if (password == null || password.isEmpty()) {
            report("No unlock password saved", true);
            return;
        }
        swipeUp(() -> handler.postDelayed(() -> enterPassword(chat, message, password), 450));
    }

    // Swipe up from the lower centre to bring up the password bouncer.
    private void swipeUp(final Done done) {
        try {
            DisplayMetrics dm = new DisplayMetrics();
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (wm != null) wm.getDefaultDisplay().getRealMetrics(dm);
            int w = dm.widthPixels > 0 ? dm.widthPixels : 1080;
            int h = dm.heightPixels > 0 ? dm.heightPixels : 1920;
            Path path = new Path();
            path.moveTo(w * 0.5f, h * 0.86f);
            path.lineTo(w * 0.5f, h * 0.28f);
            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, 320))
                    .build();
            boolean ok = dispatchGesture(gesture, new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription g) { handler.post(done::run); }
                @Override public void onCancelled(GestureDescription g) { handler.post(done::run); }
            }, null);
            if (!ok) handler.post(done::run);
        } catch (Throwable t) {
            handler.post(done::run);
        }
    }

    private void enterPassword(final String chat, final String message, final String password) {
        if (setPasswordText(password)) {
            handler.postDelayed(() -> submitAndWait(chat, message), 350);
        } else {
            // Some keyguards block ACTION_SET_TEXT — fall back to tapping the
            // on-screen keyboard keys with accessibility gestures.
            typeKeys(password, 0, () ->
                    handler.postDelayed(() -> submitAndWait(chat, message), 200));
        }
    }

    private boolean setPasswordText(String text) {
        for (AccessibilityWindowInfo window : windows()) {
            AccessibilityNodeInfo field = findEditable(window.getRoot());
            if (field == null) continue;
            try { field.performAction(AccessibilityNodeInfo.ACTION_FOCUS); } catch (Throwable ignored) {}
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            try {
                if (field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private void typeKeys(final String text, final int index, final Done done) {
        if (index >= text.length()) { done.run(); return; }
        AccessibilityNodeInfo key = findKeyNode(String.valueOf(text.charAt(index)));
        if (key == null) { done.run(); return; }
        tapNode(key, () -> handler.postDelayed(() -> typeKeys(text, index + 1, done), 90));
    }

    private AccessibilityNodeInfo findKeyNode(String key) {
        for (AccessibilityWindowInfo window : windows()) {
            AccessibilityNodeInfo found = findKeyNode(window.getRoot(), key);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findKeyNode(AccessibilityNodeInfo node, String key) {
        if (node == null) return null;
        if (key.equalsIgnoreCase(textOf(node)) && tappable(node)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findKeyNode(node.getChild(i), key);
            if (found != null) return found;
        }
        return null;
    }

    private boolean tappable(AccessibilityNodeInfo node) {
        if (node.isClickable()) return true;
        AccessibilityNodeInfo parent = node.getParent();
        return parent != null && parent.isClickable();
    }

    private void tapNode(AccessibilityNodeInfo node, final Done done) {
        try {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            if (bounds.width() <= 0 || bounds.height() <= 0) {
                handler.post(done::run);
                return;
            }
            Path path = new Path();
            path.moveTo(bounds.centerX(), bounds.centerY());
            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, 60))
                    .build();
            boolean ok = dispatchGesture(gesture, new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription g) { handler.post(done::run); }
                @Override public void onCancelled(GestureDescription g) { handler.post(done::run); }
            }, null);
            if (!ok) handler.post(done::run);
        } catch (Throwable t) {
            handler.post(done::run);
        }
    }

    private void submitAndWait(final String chat, final String message) {
        AccessibilityNodeInfo enter = null;
        for (AccessibilityWindowInfo window : windows()) {
            enter = findEnter(window.getRoot());
            if (enter != null) break;
        }
        if (enter != null) tapNode(enter, () -> pollUnlocked(chat, message, 0));
        else pollUnlocked(chat, message, 0);
    }

    private AccessibilityNodeInfo findEnter(AccessibilityNodeInfo node) {
        if (node == null) return null;
        String value = textOf(node).toLowerCase(Locale.ROOT);
        CharSequence desc = node.getContentDescription();
        String description = desc == null ? "" : desc.toString().toLowerCase(Locale.ROOT);
        if (tappable(node) && (isEnterWord(value) || isEnterWord(description))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findEnter(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    private static boolean isEnterWord(String value) {
        if (value == null) return false;
        for (String word : ENTER_WORDS) if (word.equals(value.trim())) return true;
        return false;
    }

    private void pollUnlocked(final String chat, final String message, final int attempt) {
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        boolean locked = km != null && (km.isKeyguardLocked() || km.isDeviceLocked());
        if (!locked) {
            report("Screen unlocked", false);
            handler.post(() -> runSend(chat, message));
            return;
        }
        if (attempt >= 12) {
            report("Could not unlock screen - check saved password", true);
            return;
        }
        handler.postDelayed(() -> pollUnlocked(chat, message, attempt + 1), 400);
    }

    private void relockScreen() {
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
                return;
            } catch (Throwable ignored) {}
        }
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                pm.getClass().getMethod("goToSleep", long.class)
                        .invoke(pm, SystemClock.uptimeMillis());
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onDestroy() {
        if (registered) {
            try { unregisterReceiver(triggerReceiver); } catch (Throwable ignored) {}
            registered = false;
        }
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Work is driven by the trigger broadcast; events are only used as a wake-up hint.
        if (pending && event != null) {
            CharSequence pkg = event.getPackageName();
            if (pkg != null && (pkg.toString().contains("whatsapp"))) {
                onTrigger();
            }
        }
    }

    @Override
    public void onInterrupt() {}

    private void onTrigger() {
        if (!pending) return;
        pending = false;
        final String chat = pendingChat;
        final String message = pendingMessage;
        if (chat == null || message == null) return;
        if (chat.trim().isEmpty() || message.trim().isEmpty()) return;

        if (Trigger.consumeScreenOffPrelude()) {
            try {
                Intent bridge = new Intent(this, SendBridgeActivity.class);
                bridge.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
                startActivity(bridge);
            } catch (Throwable t) {
                Log.w("AutoSend", "screen bridge failed", t);
            }
            final String password = Settings.unlockPassword(this);
            if (Settings.autoUnlock(this) && password != null && !password.isEmpty()) {
                handler.postDelayed(() -> beginUnlock(chat, message), 900);
            } else {
                handler.postDelayed(() -> runSend(chat, message), 2200);
            }
        } else {
            runSend(chat, message);
        }
    }

    private void runSend(final String chat, final String message) {
        report("Opening WhatsApp...", false);
        launchWhatsApp();
        handler.postDelayed(() -> ensureChatList(chat, message, 0), 3000);
    }

    private void ensureChatList(final String chat, final String message, final int attempt) {
        if (findSearchNode() != null) {
            openChat(chat, message, 0);
            return;
        }
        if (attempt >= 3) {
            report("Could not open WhatsApp chats list", true);
            return;
        }
        try { performGlobalAction(GLOBAL_ACTION_BACK); } catch (Throwable ignored) {}
        handler.postDelayed(() -> ensureChatList(chat, message, attempt + 1), 900);
    }

    private AccessibilityNodeInfo findSearchNode() {
        AccessibilityNodeInfo n = findByDesc("Search", "Search chats", "search", "Search conversations");
        if (n == null) n = findByDescLike("search");
        if (n == null) n = findById("search", "action_search", "conversations_search",
                "search_button", "search_icon", "conversations_list_search");
        if (n == null) n = findByText("Search chats");
        if (n == null) n = findByText("Search");
        return n;
    }

    private void launchWhatsApp() {
        PackageManager pm = getPackageManager();
        Intent in = pm.getLaunchIntentForPackage("com.whatsapp");
        if (in == null) in = pm.getLaunchIntentForPackage("com.whatsapp.w4b");
        if (in != null) {
            in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(in);
            } catch (Throwable t) {
                Log.w("AutoSend", "launch failed", t);
            }
        }
    }

    private void openChat(final String chat, final String message, final int attempt) {
        AccessibilityNodeInfo search = findSearchNode();
        if (search != null && click(search)) {
            handler.postDelayed(() -> typeSearch(chat, message, 0), 1000);
            return;
        }
        AccessibilityNodeInfo direct = findLike(chat);
        if (direct != null && click(direct)) {
            handler.postDelayed(() -> typeMessage(message, 0), 1500);
            return;
        }
        if (attempt > 6) {
            report("Chat not found: " + chat, true);
            return;
        }
        handler.postDelayed(() -> openChat(chat, message, attempt + 1), 700);
    }

    private void typeSearch(final String chat, final String message, final int attempt) {
        if (attempt > 8) {
            report("Search box not found", true);
            return;
        }
        AccessibilityNodeInfo input = findById("search_input", "search_src_text", "search_query",
                "search_edit_text", "search_field", "search");
        if (input == null) input = findEditable();

        if (input != null && setText(input, chat)) {
            handler.postDelayed(() -> pickResult(chat, message, 0), 1600);
        } else {
            handler.postDelayed(() -> typeSearch(chat, message, attempt + 1), 700);
        }
    }

    private void pickResult(final String chat, final String message, final int attempt) {
        pickResult(chat, message, attempt, chat);
    }

    private void pickResult(final String chat, final String message, final int attempt,
                            final String query) {
        if (attempt > 13) {
            report("Chat not found: " + chat, true);
            return;
        }
        AccessibilityNodeInfo node = findLike(query);
        if (node == null) node = findClickableRow(root(), query.trim().toLowerCase(Locale.ROOT));
        if (node != null && click(node)) {
            handler.postDelayed(() -> typeMessage(message, 0), 1600);
            return;
        }
        String first = query.trim().split("\\s+")[0];
        if (attempt == 7 && !first.equalsIgnoreCase(query.trim())) {
            AccessibilityNodeInfo input = findById("search_input", "search_src_text", "search_query",
                    "search_edit_text", "search_field", "search");
            if (input == null) input = findEditable();
            if (input != null && setText(input, first)) {
                handler.postDelayed(() -> pickResult(chat, message, attempt + 1, first), 1600);
                return;
            }
        }
        handler.postDelayed(() -> pickResult(chat, message, attempt + 1, query), 700);
    }

    private void typeMessage(final String message, final int attempt) {
        if (attempt > 10) {
            report("Message box not found", true);
            return;
        }
        AccessibilityNodeInfo input = findById("entry", "composer_input", "message_input",
                "chat_input", "input", "edit_text", "composer_edit_text", "composer",
                "input_text", "wacomposer_input", "text_input");
        if (input == null) input = findEditable();

        if (input != null && setText(input, message)) {
            handler.postDelayed(() -> tapSend(message, 0), 900);
        } else {
            handler.postDelayed(() -> typeMessage(message, attempt + 1), 700);
        }
    }

    private void tapSend(final String message, final int attempt) {
        if (attempt > 10) {
            report("Send button not found", true);
            return;
        }
        AccessibilityNodeInfo send = findById("send", "send_button", "composer_send", "send_message",
                "send_btn", "button_send", "send_icon", "composer_send_button");
        if (send == null) send = findSendLike(root());
        if (send == null) send = findByText("Send");

        if (send != null && click(send)) {
            report("Sent to " + pendingChat, true);
        } else {
            handler.postDelayed(() -> tapSend(message, attempt + 1), 700);
        }
    }

    private AccessibilityNodeInfo root() {
        return getRootInActiveWindow();
    }

    private AccessibilityNodeInfo findById(String... ids) {
        return findById(root(), ids);
    }

    private AccessibilityNodeInfo findById(AccessibilityNodeInfo node, String... ids) {
        if (node == null) return null;
        String id = node.getViewIdResourceName();
        if (id != null) {
            for (String candidate : ids) {
                if (id.equals(candidate) || id.endsWith("/" + candidate)) return node;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findById(node.getChild(i), ids);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findByText(String text) {
        return findByText(root(), text);
    }

    private AccessibilityNodeInfo findByText(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        CharSequence value = node.getText();
        if (value != null && value.toString().trim().equalsIgnoreCase(text.trim())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findByText(node.getChild(i), text);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findByDesc(String... descriptions) {
        return findByDesc(root(), descriptions);
    }

    private AccessibilityNodeInfo findByDesc(AccessibilityNodeInfo node, String... descriptions) {
        if (node == null) return null;
        CharSequence desc = node.getContentDescription();
        if (desc != null) {
            for (String candidate : descriptions) {
                if (desc.toString().trim().equalsIgnoreCase(candidate.trim())) return node;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findByDesc(node.getChild(i), descriptions);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findByDescLike(String needle) {
        return findByDescLike(root(), needle.toLowerCase(Locale.ROOT));
    }

    private AccessibilityNodeInfo findByDescLike(AccessibilityNodeInfo node, String needle) {
        if (node == null) return null;
        CharSequence desc = node.getContentDescription();
        if (desc != null && desc.toString().toLowerCase(Locale.ROOT).contains(needle)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findByDescLike(node.getChild(i), needle);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findLike(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        String target = name.trim().toLowerCase(Locale.ROOT);
        AccessibilityNodeInfo root = root();
        AccessibilityNodeInfo node = findByText(root, name);
        if (node != null && node.isEditable()) node = null;
        if (node == null) node = findByDesc(root, name);
        if (node == null) node = findByNameLike(root, target, true);
        if (node == null) node = findByNameLike(root, target, false);
        return node;
    }

    private AccessibilityNodeInfo findByNameLike(AccessibilityNodeInfo node, String target,
                                                  boolean nameishOnly) {
        if (node == null) return null;
        String id = node.getViewIdResourceName();
        boolean nameish = id != null && (id.contains("contact_name") || id.contains("subject")
                || id.contains("title") || id.contains("name") || id.contains("group"));
        String value = textOf(node).toLowerCase(Locale.ROOT);
        if (!value.isEmpty() && value.contains(target) && !node.isEditable()) {
            boolean ok = nameishOnly ? nameish
                    : (value.length() <= 120
                    && !"android.widget.Button".equals(String.valueOf(node.getClassName())));
            if (ok) return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findByNameLike(node.getChild(i), target, nameishOnly);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findClickableRow(AccessibilityNodeInfo node, String target) {
        if (node == null || target.isEmpty()) return null;
        if (node.isClickable() && subtreeContains(node, target, 0)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findClickableRow(node.getChild(i), target);
            if (found != null) return found;
        }
        return null;
    }

    private boolean subtreeContains(AccessibilityNodeInfo node, String target, int depth) {
        if (node == null || depth > 6) return false;
        String value = textOf(node).toLowerCase(Locale.ROOT);
        if (!value.isEmpty() && value.contains(target)) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            if (subtreeContains(node.getChild(i), target, depth + 1)) return true;
        }
        return false;
    }

    private AccessibilityNodeInfo findSendLike(AccessibilityNodeInfo node) {
        if (node == null) return null;
        CharSequence desc = node.getContentDescription();
        String id = node.getViewIdResourceName();
        boolean match = (desc != null && desc.toString().toLowerCase(Locale.ROOT).contains("send"))
                || (id != null && id.toLowerCase(Locale.ROOT).contains("send"));
        if (match && (node.isClickable()
                || (node.getParent() != null && node.getParent().isClickable()))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findSendLike(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findEditable() {
        return findEditable(root());
    }

    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        String cls = String.valueOf(node.getClassName()).toLowerCase(Locale.ROOT);
        if ((node.isEditable() || cls.contains("edittext")) &&
                (node.isFocusable() || node.isEditable())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findEditable(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    private boolean click(AccessibilityNodeInfo node) {
        if (node == null) return false;
        AccessibilityNodeInfo current = node;
        for (int i = 0; i < 6 && current != null; i++) {
            if (current.isClickable()
                    && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            current = current.getParent();
        }
        return false;
    }

    private boolean setText(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true;
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }
}
