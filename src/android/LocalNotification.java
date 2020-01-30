/*
 * Apache 2.0 License
 *
 * Copyright (c) Sebastian Katzer 2017
 *
 * This file contains Original Code and/or Modifications of Original Code
 * as defined in and that are subject to the Apache License
 * Version 2.0 (the 'License'). You may not use this file except in
 * compliance with the License. Please obtain a copy of the License at
 * http://opensource.org/licenses/Apache-2.0/ and read it before using this
 * file.
 *
 * The Original Code and all software distributed under the License are
 * distributed on an 'AS IS' basis, WITHOUT WARRANTY OF ANY KIND, EITHER
 * EXPRESS OR IMPLIED, AND APPLE HEREBY DISCLAIMS ALL SUCH WARRANTIES,
 * INCLUDING WITHOUT LIMITATION, ANY WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE, QUIET ENJOYMENT OR NON-INFRINGEMENT.
 * Please see the License for the specific language governing rights and
 * limitations under the License.
 */

// codebeat:disable[TOO_MANY_FUNCTIONS]

package de.appplant.cordova.plugin.localnotification;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.media.AudioAttributes;
import android.content.ContentResolver;
import android.app.KeyguardManager;
import android.content.Context;
import android.util.Pair;
import android.view.View;
import android.net.Uri;
import android.app.NotificationManager;
import android.app.NotificationChannel;
import android.media.RingtoneManager;
import android.support.v4.app.NotificationCompat;
import android.os.Build;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import de.appplant.cordova.plugin.notification.Manager;
import de.appplant.cordova.plugin.notification.Notification;
import de.appplant.cordova.plugin.notification.Options;
import de.appplant.cordova.plugin.notification.Request;
import de.appplant.cordova.plugin.notification.action.ActionGroup;

import static de.appplant.cordova.plugin.notification.Notification.Type.SCHEDULED;
import static de.appplant.cordova.plugin.notification.Notification.Type.TRIGGERED;

/**
 * This plugin utilizes the Android AlarmManager in combination with local
 * notifications. When a local notification is scheduled the alarm manager takes
 * care of firing the event. When the event is processed, a notification is put
 * in the Android notification center and status bar.
 */
@SuppressWarnings({"Convert2Diamond", "Convert2Lambda"})
public class LocalNotification extends CordovaPlugin {

    // Reference to the web view for static access
    private static WeakReference<CordovaWebView> webView = null;

    // Indicates if the device is ready (to receive events)
    private static Boolean deviceready = false;

    private static Activity cordovaActivity;

    // Queues all events before deviceready
    private static ArrayList<String> eventQueue = new ArrayList<String>();

    // Launch details
    private static Pair<Integer, String> launchDetails;

    private static NotificationChannel defaultNotificationChannel = null;
    public static String defaultChannelId = null;
    public static String defaultChannelName = null;

    @Override
    protected void pluginInitialize() {
        cordovaActivity = this.cordova.getActivity();
        this.cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    defaultChannelId = "lm_default_channel";
                    defaultChannelName = "Default";
                    createDefaultChannel();
                }catch (Exception e){
                    Log.e("LocalNotification", e.getMessage());
                }
            }
        });
    }

    /**
     * Called after plugin construction and fields have been initialized.
     * Prefer to use pluginInitialize instead since there is no value in
     * having parameters on the initialize() function.
     */
    @Override
    public void initialize (CordovaInterface cordova, CordovaWebView webView) {
        LocalNotification.webView = new WeakReference<CordovaWebView>(webView);
    }

    /**
     * Called when the activity will start interacting with the user.
     *
     * @param multitasking Flag indicating if multitasking is turned on for app.
     */
    @Override
    public void onResume (boolean multitasking) {
        super.onResume(multitasking);
        deviceready();
    }

    /**
     * The final call you receive before your activity is destroyed.
     */
    @Override
    public void onDestroy() {
        deviceready = false;
    }

    /**
     * Executes the request.
     *
     * This method is called from the WebView thread. To do a non-trivial
     * amount of work, use:
     *      cordova.getThreadPool().execute(runnable);
     *
     * To run on the UI thread, use:
     *     cordova.getActivity().runOnUiThread(runnable);
     *
     * @param action  The action to execute.
     * @param args    The exec() arguments in JSON form.
     * @param command The callback context used when calling back into
     *                JavaScript.
     *
     * @return Whether the action was valid.
     */
    @Override
    public boolean execute (final String action, final JSONArray args,
                            final CallbackContext command) throws JSONException {

        if (action.equals("launch")) {
            launch(command);
            return true;
        }

        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                if (action.equals("ready")) {
                    deviceready();
                } else
                if (action.equals("check")) {
                    check(command);
                } else
                if (action.equals("request")) {
                    request(command);
                } else
                if (action.equals("actions")) {
                    actions(args, command);
                } else
                if (action.equals("schedule")) {
                    schedule(args, command);
                } else
                if (action.equals("update")) {
                    update(args, command);
                } else
                if (action.equals("cancel")) {
                    cancel(args, command);
                } else
                if (action.equals("cancelAll")) {
                    cancelAll(command);
                } else
                if (action.equals("clear")) {
                    clear(args, command);
                } else
                if (action.equals("clearAll")) {
                    clearAll(command);
                } else
                if (action.equals("type")) {
                    type(args, command);
                } else
                if (action.equals("ids")) {
                    ids(args, command);
                } else
                if (action.equals("notification")) {
                    notification(args, command);
                } else
                if (action.equals("notifications")) {
                    notifications(args, command);
                } else
                if (action.equals("createChannel")) {
                    try
                    {
                        createChannel(command, args.getJSONObject(0));
                    }
                    catch(JSONException e)
                    {
                        Log.e("LocalNotification", e.getMessage());
                    }
                } else 
                if (action.equals("deleteChannel")) {
                    try
                    {
                        deleteChannel(command, args.getString(0));
                    }
                    catch(JSONException e)
                    {
                        Log.e("LocalNotification", e.getMessage());
                    }
                } else 
                if (action.equals("listChannels")) {
                    listChannels(command);
                } else 
                if (action.equals("setDefaultChannel")) {
                    try
                    {
                        setDefaultChannel(command, args.getJSONObject(0));
                    }
                    catch(JSONException e)
                    {
                        Log.e("LocalNotification", e.getMessage());
                    }
                }
            }
        });

        return true;
    }

    /**
     * Set launchDetails object.
     *
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    @SuppressLint("DefaultLocale")
    private void launch(CallbackContext command) {
        if (launchDetails == null)
            return;

        JSONObject details = new JSONObject();

        try {
            details.put("id", launchDetails.first);
            details.put("action", launchDetails.second);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        command.success(details);

        launchDetails = null;
    }

    /**
     * Ask if user has enabled permission for local notifications.
     *
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    private void check (CallbackContext command) {
        boolean allowed = getNotMgr().hasPermission();
        success(command, allowed);
    }

    /**
     * Request permission for local notifications.
     *
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    private void request (CallbackContext command) {
        check(command);
    }

    /**
     * Register action group.
     *
     * @param args    The exec() arguments in JSON form.
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    private void actions (JSONArray args, CallbackContext command) {
        int task        = args.optInt(0);
        String id       = args.optString(1);
        JSONArray list  = args.optJSONArray(2);
        Context context = cordova.getActivity();

        switch (task) {
            case 0:
                ActionGroup group = ActionGroup.parse(context, id, list);
                ActionGroup.register(group);
                command.success();
                break;
            case 1:
                ActionGroup.unregister(id);
                command.success();
                break;
            case 2:
                boolean found = ActionGroup.isRegistered(id);
                success(command, found);
                break;
        }
    }

    /**
     * Schedule multiple local notifications.
     *
     * @param toasts  The notifications to schedule.
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    private void schedule (JSONArray toasts, CallbackContext command) {
        Manager mgr = getNotMgr();

        for (int i = 0; i < toasts.length(); i++) {
            JSONObject dict    = toasts.optJSONObject(i);
            Options options    = new Options(dict);
            Request request    = new Request(options);
            Notification toast = mgr.schedule(request, TriggerReceiver.class);

            if (toast != null) {
                fireEvent("add", toast);
            }
        }

        check(command);
    }

    /**
     * Update multiple local notifications.
     *
     * @param updates Notification properties including their IDs.
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    private void update (JSONArray updates, CallbackContext command) {
        Manager mgr = getNotMgr();

        for (int i = 0; i < updates.length(); i++) {
            JSONObject update  = updates.optJSONObject(i);
            int id             = update.optInt("id", 0);
            Notification toast = mgr.update(id, update, TriggerReceiver.class);

            if (toast == null)
                continue;

            fireEvent("update", toast);
        }

        check(command);
    }

    /**
     * Cancel multiple local notifications.
     *
     * @param ids     Set of local notification IDs.
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    private void cancel (JSONArray ids, CallbackContext command) {
        Manager mgr = getNotMgr();

        for (int i = 0; i < ids.length(); i++) {
            int id             = ids.optInt(i, 0);
            Notification toast = mgr.cancel(id);

            if (toast == null)
                continue;

            fireEvent("cancel", toast);
        }

        command.success();
    }

    /**
     * Cancel all scheduled notifications.
     *
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    private void cancelAll(CallbackContext command) {
        getNotMgr().cancelAll();
        fireEvent("cancelall");
        command.success();
    }

    /**
     * Clear multiple local notifications without canceling them.
     *
     * @param ids     Set of local notification IDs.
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    private void clear(JSONArray ids, CallbackContext command) {
        Manager mgr = getNotMgr();

        for (int i = 0; i < ids.length(); i++) {
            int id             = ids.optInt(i, 0);
            Notification toast = mgr.clear(id);

            if (toast == null)
                continue;

            fireEvent("clear", toast);
        }

        command.success();
    }

    /**
     * Clear all triggered notifications without canceling them.
     *
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    private void clearAll(CallbackContext command) {
        getNotMgr().clearAll();
        fireEvent("clearall");
        command.success();
    }

    /**
     * Get the type of the notification (unknown, scheduled, triggered).
     *
     * @param args    The exec() arguments in JSON form.
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    private void type (JSONArray args, CallbackContext command) {
        int id             = args.optInt(0);
        Notification toast = getNotMgr().get(id);

        if (toast == null) {
            command.success("unknown");
            return;
        }

        switch (toast.getType()) {
            case SCHEDULED:
                command.success("scheduled");
                break;
            case TRIGGERED:
                command.success("triggered");
                break;
            default:
                command.success("unknown");
                break;
        }
    }

    /**
     * Set of IDs from all existent notifications.
     *
     * @param args    The exec() arguments in JSON form.
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    private void ids (JSONArray args, CallbackContext command) {
        int type    = args.optInt(0);
        Manager mgr = getNotMgr();
        List<Integer> ids;

        switch (type) {
            case 0:
                ids = mgr.getIds();
                break;
            case 1:
                ids = mgr.getIdsByType(SCHEDULED);
                break;
            case 2:
                ids = mgr.getIdsByType(TRIGGERED);
                break;
            default:
                ids = new ArrayList<Integer>(0);
                break;
        }

        command.success(new JSONArray(ids));
    }

    /**
     * Options from local notification.
     *
     * @param args    The exec() arguments in JSON form.
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    private void notification (JSONArray args, CallbackContext command) {
        int id       = args.optInt(0);
        Options opts = getNotMgr().getOptions(id);

        if (opts != null) {
            command.success(opts.getDict());
        } else {
            command.success();
        }
    }

    /**
     * Set of options from local notification.
     *
     * @param args    The exec() arguments in JSON form.
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    private void notifications (JSONArray args, CallbackContext command) {
        int type      = args.optInt(0);
        JSONArray ids = args.optJSONArray(1);
        Manager mgr   = getNotMgr();
        List<JSONObject> options;

        switch (type) {
            case 0:
                options = mgr.getOptions();
                break;
            case 1:
                options = mgr.getOptionsByType(SCHEDULED);
                break;
            case 2:
                options = mgr.getOptionsByType(TRIGGERED);
                break;
            case 3:
                options = mgr.getOptionsById(toList(ids));
                break;
            default:
                options = new ArrayList<JSONObject>(0);
                break;
        }

        command.success(new JSONArray(options));
    }

    /**
     * Call all pending callbacks after the deviceready event has been fired.
     */
    private static synchronized void deviceready() {
        deviceready = true;

        for (String js : eventQueue) {
            sendJavascript(js);
        }

        eventQueue.clear();
    }

    /**
     * Invoke success callback with a single boolean argument.
     *
     * @param command The callback context used when calling back into
     *                JavaScript.
     * @param arg     The single argument to pass through.
     */
    private void success(CallbackContext command, boolean arg) {
        PluginResult result = new PluginResult(PluginResult.Status.OK, arg);
        command.sendPluginResult(result);
    }

    /**
     * Fire given event on JS side. Does inform all event listeners.
     *
     * @param event The event name.
     */
    private void fireEvent (String event) {
        fireEvent(event, null, new JSONObject());
    }

    /**
     * Fire given event on JS side. Does inform all event listeners.
     *
     * @param event        The event name.
     * @param notification Optional notification to pass with.
     */
    static void fireEvent (String event, Notification notification) {
        fireEvent(event, notification, new JSONObject());
    }

    /**
     * Fire given event on JS side. Does inform all event listeners.
     *
     * @param event The event name.
     * @param toast Optional notification to pass with.
     * @param data  Event object with additional data.
     */
    static void fireEvent (String event, Notification toast, JSONObject data) {
        String params, js;

        try {
            data.put("event", event);
            data.put("foreground", isInForeground());
            data.put("queued", !deviceready);

            if (toast != null) {
                data.put("notification", toast.getId());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (toast != null) {
            params = toast.toString() + "," + data.toString();
        } else {
            params = data.toString();
        }

        js = "cordova.plugins.notification.local.fireEvent(" +
                "\"" + event + "\"," + params + ")";

        if (launchDetails == null && !deviceready && toast != null) {
            launchDetails = new Pair<Integer, String>(toast.getId(), event);
        }

        sendJavascript(js);
    }

    /**
     * Use this instead of deprecated sendJavascript
     *
     * @param js JS code snippet as string.
     */
    private static synchronized void sendJavascript(final String js) {

        if (!deviceready || webView == null) {
            eventQueue.add(js);
            return;
        }

        final CordovaWebView view = webView.get();

        ((Activity)(view.getContext())).runOnUiThread(new Runnable() {
            public void run() {
                view.loadUrl("javascript:" + js);
            }
        });
    }

    /**
     * If the app is running in foreground.
     */
    private static boolean isInForeground() {

        if (!deviceready || webView == null)
            return false;

        CordovaWebView view = webView.get();

        KeyguardManager km = (KeyguardManager) view.getContext()
                .getSystemService(Context.KEYGUARD_SERVICE);

        //noinspection SimplifiableIfStatement
        if (km != null && km.isKeyguardLocked())
            return false;

        return view.getView().getWindowVisibility() == View.VISIBLE;
    }

    /**
     * If the app is running.
     */
    static boolean isAppRunning() {
        return webView != null;
    }

    /**
     * Convert JSON array of integers to List.
     *
     * @param ary Array of integers.
     */
    private List<Integer> toList (JSONArray ary) {
        List<Integer> list = new ArrayList<Integer>();

        for (int i = 0; i < ary.length(); i++) {
            list.add(ary.optInt(i));
        }

        return list;
    }

    /**
     * Notification manager instance.
     */
    private Manager getNotMgr() {
        return Manager.getInstance(cordova.getActivity());
    }

    public void createChannel(final CallbackContext callbackContext, final JSONObject options) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    createChannel(options);
                    callbackContext.success();
                } catch (Exception e) {
                    Log.e("LocalNotification", e.getMessage());
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    protected static NotificationChannel createChannel(final JSONObject options) throws JSONException {
        NotificationChannel channel = null;
        // only call on Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String id = options.getString("id");
            Log.i("LocalNotification", "Creating channel id="+id);

            if(channelExists(id)){
                deleteChannel(id);
            }

            NotificationManager nm = (NotificationManager) cordovaActivity.getSystemService(Context.NOTIFICATION_SERVICE);
            String packageName = cordovaActivity.getPackageName();

            String name = options.optString("name", "");
            Log.d("LocalNotification", "Channel "+id+" - name="+name);

            int importance = options.optInt("importance", NotificationManager.IMPORTANCE_HIGH);
            Log.d("LocalNotification", "Channel "+id+" - importance="+importance);

            channel = new NotificationChannel(id,
                    name,
                    importance);

            // Light
            boolean light = options.optBoolean("light", true);
            Log.d("LocalNotification", "Channel "+id+" - light="+light);
            channel.enableLights(light);

            int lightColor = options.optInt("lightColor", -1);
            if (lightColor != -1) {
                Log.d("LocalNotification", "Channel "+id+" - lightColor="+lightColor);
                channel.setLightColor(lightColor);
            }

            // Visibility
            int visibility = options.optInt("visibility", NotificationCompat.VISIBILITY_PUBLIC);
            Log.d("LocalNotification", "Channel "+id+" - visibility="+visibility);
            channel.setLockscreenVisibility(visibility);

            // Badge
            boolean badge = options.optBoolean("badge", true);
            Log.d("LocalNotification", "Channel "+id+" - badge="+badge);
            channel.setShowBadge(badge);

            // Sound
            String sound = options.optString("sound", "default");
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build();
            if ("ringtone".equals(sound)) {
                channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), audioAttributes);
                Log.d("LocalNotification", "Channel "+id+" - sound=ringtone");
            } else if (sound != null && !sound.contentEquals("default")) {
                Uri soundUri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/raw/" + sound);
                channel.setSound(soundUri, audioAttributes);
                Log.d("LocalNotification", "Channel "+id+" - sound="+sound);
            } else if (sound != "false"){
                channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttributes);
                Log.d("LocalNotification", "Channel "+id+" - sound=default");
            }else{
                Log.d("LocalNotification", "Channel "+id+" - sound=none");
            }

            // Vibration: if vibration setting is an array set vibration pattern, else set enable vibration.
            JSONArray pattern = options.optJSONArray("vibration");
            if (pattern != null) {
                int patternLength = pattern.length();
                long[] patternArray = new long[patternLength];
                for (int i = 0; i < patternLength; i++) {
                    patternArray[i] = pattern.optLong(i);
                }
                channel.enableVibration(true);
                channel.setVibrationPattern(patternArray);
                Log.d("LocalNotification", "Channel "+id+" - vibrate="+pattern);
            } else {
                boolean vibrate = options.optBoolean("vibration", true);
                channel.enableVibration(vibrate);
                Log.d("LocalNotification", "Channel "+id+" - vibrate="+vibrate);
            }

            // Create channel
            nm.createNotificationChannel(channel);
        }
        return channel;
    }

    protected static void createDefaultChannel() throws JSONException {
        JSONObject options = new JSONObject();
        options.put("id", defaultChannelId);
        options.put("name", defaultChannelName);
        createDefaultChannel(options);
    }

    protected static void createDefaultChannel(final JSONObject options) throws JSONException {
        defaultNotificationChannel = createChannel(options);
    }

    public void setDefaultChannel(final CallbackContext callbackContext, final JSONObject options) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    deleteChannel(defaultChannelId);

                    String id = options.optString("id", null);
                    if(id != null){
                        defaultChannelId = id;
                    }

                    String name = options.optString("name", null);
                    if(name != null){
                        defaultChannelName = name;
                    }
                    createDefaultChannel(options);
                    callbackContext.success();
                } catch (Exception e) {
                    Log.e("LocalNotification", e.getMessage());
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    public void deleteChannel(final CallbackContext callbackContext, final String channelID) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    deleteChannel(channelID);
                    callbackContext.success();
                } catch (Exception e) {
                    Log.e("LocalNotification", e.getMessage());
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    protected static void deleteChannel(final String channelID){
        // only call on Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) cordovaActivity.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.deleteNotificationChannel(channelID);
        }
    }

    public void listChannels(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    List<NotificationChannel> notificationChannels = listChannels();
                    JSONArray channels = new JSONArray();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        for (NotificationChannel notificationChannel : notificationChannels) {
                            JSONObject channel = new JSONObject();
                            channel.put("id", notificationChannel.getId());
                            channel.put("name", notificationChannel.getName());
                            channels.put(channel);
                        }
                    }
                    callbackContext.success(channels);
                } catch (Exception e) {
                    Log.e("LocalNotification", e.getMessage());
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    public static List<NotificationChannel> listChannels(){
        List<NotificationChannel> notificationChannels = null;
        // only call on Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) cordovaActivity.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationChannels = nm.getNotificationChannels();
        }
        return notificationChannels;
    }

    public static boolean channelExists(String channelId){
        boolean exists = false;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            List<NotificationChannel> notificationChannels = listChannels();
            if(notificationChannels != null){
                for (NotificationChannel notificationChannel : notificationChannels) {
                    if(notificationChannel.getId() == channelId){
                        exists = true;
                    }
                }
            }
        }
        return exists;
    }
}

// codebeat:enable[TOO_MANY_FUNCTIONS]
