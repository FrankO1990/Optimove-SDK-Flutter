package com.optimove.flutter;

import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.Uri;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.optimove.android.Optimove;
import com.optimove.android.OptimoveConfig;
import com.optimove.android.optimobile.DeferredDeepLinkHandlerInterface;
import com.optimove.android.optimobile.OptimoveInApp;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static com.optimove.flutter.OptimoveFlutterPlugin.eventSink;
import static com.optimove.flutter.OptimoveFlutterPlugin.eventSinkDelayed;

public class OptimoveInitProvider extends ContentProvider {
    private static final String TAG = OptimoveInitProvider.class.getName();

    private static final String OPTIMOVE_CREDENTIALS_KEY = "optimoveCredentials";
    private static final String OPTIMOBILE_CREDENTIALS_KEY = "optimobileCredentials";
    private static final String PUSH_ANDROID_ICON_RESOURCE = "pushAndroidIconResource";
    private static final String IN_APP_CONSENT_STRATEGY_KEY = "inAppConsentStrategy";
    private static final String IN_APP_CONSENT_AUTO_ENROLL = "auto-enroll";
    private static final String IN_APP_CONSENT_EXPLICIT_BY_USER = "explicit-by-user";
    private static final String IN_APP_DISPLAY_MODE_KEY = "inAppDisplayMode";
    private static final String IN_APP_DISPLAY_MODE_AUTOMATIC = "automatic";
    private static final String IN_APP_DISPLAY_MODE_PAUSED = "paused";
    private static final String ENABLE_DDL_KEY = "enableDeferredDeepLinking";

    private static final String SDK_VERSION = "3.3.0";
    private static final int SDK_TYPE = 105;
    private static final int RUNTIME_TYPE = 9;
    private static final String RUNTIME_VERSION = "Unknown";

    @Override
    public boolean onCreate() {
        OptimoveConfig.Builder config;
        try {
            config = readConfig();
        } catch (IOException e) {
            e.printStackTrace();
            return true;
        }

        if (config == null) {
            Log.i(TAG, "Skipping init, no config file found...");
            return true;
        }
        OptimoveConfig optimoveConfig = config.build();

        Optimove.initialize((Application) getContext().getApplicationContext(), optimoveConfig);
        setAdditionalListeners(optimoveConfig);

        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] strings, @Nullable String s, @Nullable String[] strings1,
                        @Nullable String s1) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues contentValues) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String s, @Nullable String[] strings) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues contentValues, @Nullable String s,
                      @Nullable String[] strings) {
        return 0;
    }

    private JsonReader getConfigReader() {
        String path = "flutter_assets" + File.separator + "optimove.json";
        AssetManager assetManager = getContext().getAssets();
        InputStream is;
        try {
            is = assetManager.open(path);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        JsonReader reader;
        reader = new JsonReader(new InputStreamReader(is, StandardCharsets.UTF_8));

        return reader;
    }

    private OptimoveConfig.Builder readConfig() throws IOException {
        JsonReader reader = getConfigReader();
        if (null == reader) {
            return null;
        }

        String optimoveCredentials = null;
        String optimobileCredentilas = null;
        String inAppConsentStrategy = null;
        String pushAndroidIconResourceName = null;
        boolean enableDeepLinking = false;
        String deepLinkingCname = null;
        String inAppDisplayMode = null;

        try {
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                switch (key) {
                    case OPTIMOVE_CREDENTIALS_KEY:
                        optimoveCredentials = reader.nextString();
                        break;
                    case OPTIMOBILE_CREDENTIALS_KEY:
                        optimobileCredentilas = reader.nextString();
                        break;
                    case PUSH_ANDROID_ICON_RESOURCE:
                        pushAndroidIconResourceName = reader.nextString();
                        break;
                    case IN_APP_CONSENT_STRATEGY_KEY:
                        inAppConsentStrategy = reader.nextString();
                        break;
                    case IN_APP_DISPLAY_MODE_KEY:
                        inAppDisplayMode = reader.nextString();
                        break;
                    case ENABLE_DDL_KEY:
                        JsonToken tok = reader.peek();
                        if (JsonToken.BOOLEAN == tok) {
                            enableDeepLinking = reader.nextBoolean();
                        } else if (JsonToken.STRING == tok) {
                            enableDeepLinking = true;
                            deepLinkingCname = reader.nextString();
                        } else {
                            reader.skipValue();
                        }
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        OptimoveConfig.Builder configBuilder = new OptimoveConfig.Builder(optimoveCredentials, optimobileCredentilas);

        configureInAppMessaging(configBuilder, inAppConsentStrategy, inAppDisplayMode);

        if (enableDeepLinking) {
            configureDeepLinking(configBuilder, deepLinkingCname);
        }
        if (pushAndroidIconResourceName != null && getContext() != null) {
            configBuilder.setPushSmallIconId(getContext().getResources().getIdentifier(pushAndroidIconResourceName, "drawable", getContext().getPackageName()));
        }

        overrideInstallInfo(configBuilder);

        return configBuilder;
    }

    private void configureInAppMessaging(@NonNull OptimoveConfig.Builder config, @Nullable String consentStrategyString, @Nullable String displayModeString) {
        OptimoveConfig.InAppConsentStrategy consentStrategy = null;
        OptimoveConfig.InAppDisplayMode displayMode;

        if (consentStrategyString != null && consentStrategyString.equals(IN_APP_CONSENT_AUTO_ENROLL)) {
            consentStrategy = OptimoveConfig.InAppConsentStrategy.AUTO_ENROLL;
        } else if (consentStrategyString != null && consentStrategyString.equals(IN_APP_CONSENT_EXPLICIT_BY_USER)) {
            consentStrategy = OptimoveConfig.InAppConsentStrategy.EXPLICIT_BY_USER;
        } else {
            return;
        }

        if (displayModeString == null) {
            config.enableInAppMessaging(consentStrategy);
            return;
        }

        if (displayModeString.equals(IN_APP_DISPLAY_MODE_AUTOMATIC)) {
            displayMode = OptimoveConfig.InAppDisplayMode.AUTOMATIC;
        } else if (displayModeString.equals(IN_APP_DISPLAY_MODE_PAUSED)) {
            displayMode = OptimoveConfig.InAppDisplayMode.PAUSED;
        } else {
            return;
        }

        config.enableInAppMessaging(consentStrategy, displayMode);
    }

    private void setAdditionalListeners(OptimoveConfig optimoveConfig) {
        Optimove.getInstance()
                .setPushActionHandler(PushReceiver::handlePushOpen);

        if (!optimoveConfig.isOptimobileConfigured()) {
            return;
        }

        OptimoveInApp.getInstance()
                .setOnInboxUpdated(() -> {
                    Map<String, String> event = new HashMap<>(1);
                    event.put("type", "inbox.updated");
                    eventSink.send(event);
                });

        OptimoveInApp.getInstance()
                .setDeepLinkHandler((context, data) -> {
                    Map<String, Object> event = new HashMap<>(2);
                    Map<String, Object> eventData = new HashMap<>(3);
                    event.put("type", "in-app.deepLinkPressed");
                    eventData.put("messageId", data.getMessageId());
                    try {
                        eventData.put("deepLinkData", JsonUtils.toMap(data.getDeepLinkData()));
                        eventData.put("messageData",
                                data.getMessageData() != null ? JsonUtils.toMap(data.getMessageData()) : null);
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                    event.put("data", eventData);
                    eventSink.send(event);
                });
    }

    private void configureDeepLinking(@NonNull OptimoveConfig.Builder config, @Nullable String deepLinkingCname) {
        DeferredDeepLinkHandlerInterface deferredDeepLinkHandlerInterface = getDDLHandlerInterface();

        if (deepLinkingCname != null) {
            config.enableDeepLinking(deepLinkingCname, deferredDeepLinkHandlerInterface);

            return;
        }
        config.enableDeepLinking(deferredDeepLinkHandlerInterface);
    }

    private void overrideInstallInfo(@NonNull OptimoveConfig.Builder configBuilder) {
        JSONObject sdkInfo = new JSONObject();
        JSONObject runtimeInfo = new JSONObject();

        try {
            sdkInfo.put("id", SDK_TYPE);
            sdkInfo.put("version", SDK_VERSION);
            runtimeInfo.put("id", RUNTIME_TYPE);
            runtimeInfo.put("version", RUNTIME_VERSION);

            configBuilder.setSdkInfo(sdkInfo);
            configBuilder.setRuntimeInfo(runtimeInfo);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private DeferredDeepLinkHandlerInterface getDDLHandlerInterface() {
        return (context, resolution, link, data) -> {
            Map<String, Object> linkMap = null;
            if (null != data) {
                linkMap = new HashMap<>(2);

                Map<String, Object> contentMap = new HashMap<>(2);
                contentMap.put("title", data.content.title);
                contentMap.put("description", data.content.description);

                linkMap.put("content", contentMap);

                try {
                    linkMap.put("data", data.data != null ? JsonUtils.toMap(data.data) : null);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            Map<String, Object> eventData = new HashMap<>(3);
            eventData.put("url", link);
            eventData.put("resolution", resolution.ordinal());
            eventData.put("link", linkMap);

            Map<String, Object> event = new HashMap<>(2);
            event.put("type", "deep-linking.linkResolved");
            event.put("data", eventData);

            eventSinkDelayed.send(event);
        };
    }

}

