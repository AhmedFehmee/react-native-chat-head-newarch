package com.chathead;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.net.Uri;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ChatHeadModule extends ReactContextBaseJavaModule {

    public static final String NAME = "ChatHead";
    private static final int OVERLAY_PERMISSION_REQ_CODE = 1234;
    private final ReactApplicationContext context;
    private WindowManager windowManager;
    private View chatHeadView;
    private WindowManager.LayoutParams params;
    private TextView chatHeadBadge;
    private boolean isOverlayPermissionGranted = false;
    private boolean isOpen = false;
    private Promise permissionPromise;

    public ChatHeadModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.context = reactContext;

        // Listen for onActivityResult
        reactContext.addActivityEventListener(new com.facebook.react.bridge.BaseActivityEventListener() {
            @Override
            public void onActivityResult(Activity activity, int requestCode, int resultCode, @Nullable Intent data) {
                if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
                    isOverlayPermissionGranted = Settings.canDrawOverlays(context);
                    if (permissionPromise != null) {
                        permissionPromise.resolve(isOverlayPermissionGranted);
                        permissionPromise = null;
                    }
                }
            }
        });
    }

    @NonNull
    @Override
    public String getName() {
        return NAME;
    }

    private Activity getMainActivity() {
        return getCurrentActivity();
    }

    public void startMainActivity() {
        Activity mainActivity = getMainActivity();
        if (mainActivity != null) {
            Intent intent = new Intent(mainActivity, mainActivity.getClass());
            mainActivity.startActivity(intent);
        }
    }

    public void findChatHeadBadge() {
        int badgeId = context.getResources().getIdentifier("chat_head_badge", "id", context.getPackageName());
        if (chatHeadView != null) {
            chatHeadBadge = chatHeadView.findViewById(badgeId);
        }
    }

    private void runHandler() {
        isOpen = true;
        new Handler(Looper.getMainLooper()).post(() -> {
            if (windowManager == null) {
                windowManager = (WindowManager) context.getSystemService(Service.WINDOW_SERVICE);
            }

            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                            WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );

            params.gravity = Gravity.TOP | Gravity.START;
            params.x = 0;
            params.y = 100;

            LayoutInflater inflater = LayoutInflater.from(context);
            chatHeadView = inflater.inflate(context.getResources().getIdentifier("chat_head_layout", "layout", context.getPackageName()), null);

            chatHeadView.setOnTouchListener(new View.OnTouchListener() {
                private int initialX;
                private int initialY;
                private float initialTouchX;
                private float initialTouchY;
                private int lastAction;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = params.x;
                            initialY = params.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            lastAction = event.getAction();
                            return true;
                        case MotionEvent.ACTION_UP:
                            if (lastAction == MotionEvent.ACTION_DOWN) {
                                startMainActivity();
                            }
                            lastAction = event.getAction();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            params.x = initialX + (int) (event.getRawX() - initialTouchX);
                            params.y = initialY + (int) (event.getRawY() - initialTouchY);
                            windowManager.updateViewLayout(chatHeadView, params);
                            lastAction = event.getAction();
                            return true;
                    }
                    return false;
                }
            });

            ImageView closeBtn = chatHeadView.findViewById(context.getResources().getIdentifier("close_btn", "id", context.getPackageName()));
            closeBtn.setOnClickListener(v -> hideChatHead());

            windowManager.addView(chatHeadView, params);
            findChatHeadBadge();
        });
    }

    // ================= Overlay Permissions =================
    @ReactMethod
    public void requestPermission(Promise promise) {
        permissionPromise = promise;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + context.getPackageName()));
                Activity activity = getCurrentActivity();
                if (activity != null) {
                    activity.startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
                } else {
                    promise.resolve(false);
                }
            } else {
                isOverlayPermissionGranted = true;
                promise.resolve(true);
            }
        } else {
            isOverlayPermissionGranted = true;
            promise.resolve(true);
        }
    }

    @ReactMethod
    public void checkOverlayPermission(Promise promise) {
        isOverlayPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context);
        promise.resolve(isOverlayPermissionGranted);
    }

    // ================= Chat Head Actions =================
    @ReactMethod
    public void showChatHead() {
        if (isOverlayPermissionGranted && !isOpen) {
            runHandler();
        } else {
            Log.e("ChatHead", "Overlay permission required before showing chat head");
        }
    }

    @ReactMethod
    public void hideChatHead() {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (windowManager != null && chatHeadView != null && chatHeadView.isAttachedToWindow()) {
                windowManager.removeView(chatHeadView);
                chatHeadView = null;
                isOpen = false;
            }
        });
    }

    @ReactMethod
    public void updateBadgeCount(int count) {
        if (chatHeadBadge != null) {
            chatHeadBadge.setText(String.valueOf(count));
        }
    }

    // ================= File-based Payload (16KB+) =================
    @ReactMethod
    public void updatePayloadPath(String filePath) {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... voids) {
                try {
                    byte[] bytes = Files.readAllBytes(Paths.get(filePath));
                    return new String(bytes);
                } catch (IOException e) {
                    Log.e("ChatHead", "Failed to read payload", e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(String data) {
                if (data != null) {
                    Log.d("ChatHead", "Payload length: " + data.length());
                    // TODO: handle payload, e.g., update badge or bubble content
                }
            }
        }.execute();
    }
}
