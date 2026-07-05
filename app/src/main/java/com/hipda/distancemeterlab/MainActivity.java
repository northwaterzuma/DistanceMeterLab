package com.hipda.distancemeterlab;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_CAMERA = 17;

    private TextureView cameraPreview;
    private WebView webView;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private String cameraId;
    private Size previewSize;

    private final TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCameraIfReady();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
            showToast("相机打开失败：" + error);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.BLACK);
            getWindow().setNavigationBarColor(Color.BLACK);
        }
        setupViews();
        if (hasCameraPermission()) {
            openCameraIfReady();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCameraThread();
        if (cameraPreview != null) {
            if (cameraPreview.isAvailable()) {
                openCameraIfReady();
            } else {
                cameraPreview.setSurfaceTextureListener(textureListener);
            }
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopCameraThread();
        super.onPause();
    }

    private void setupViews() {
        FrameLayout root = new FrameLayout(this);
        cameraPreview = new TextureView(this);
        cameraPreview.setSurfaceTextureListener(textureListener);
        root.addView(cameraPreview, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.TRANSPARENT);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                return super.onConsoleMessage(consoleMessage);
            }
        });
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private boolean hasCameraPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void startCameraThread() {
        if (cameraThread != null) {
            return;
        }
        cameraThread = new HandlerThread("Camera2Preview");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private void stopCameraThread() {
        if (cameraThread == null) {
            return;
        }
        cameraThread.quitSafely();
        try {
            cameraThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        cameraThread = null;
        cameraHandler = null;
    }

    private void openCameraIfReady() {
        if (!hasCameraPermission() || cameraPreview == null || !cameraPreview.isAvailable() || cameraDevice != null) {
            return;
        }
        startCameraThread();
        try {
            CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
            chooseCamera(manager);
            if (cameraId == null) {
                showToast("没有找到可用后置相机");
                return;
            }
            manager.openCamera(cameraId, stateCallback, cameraHandler);
        } catch (CameraAccessException e) {
            showToast("无法访问相机：" + e.getMessage());
        } catch (SecurityException e) {
            showToast("请允许相机权限");
        }
    }

    private void chooseCamera(CameraManager manager) throws CameraAccessException {
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                continue;
            }
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                continue;
            }
            cameraId = id;
            previewSize = choosePreviewSize(map.getOutputSizes(SurfaceTexture.class));
            return;
        }
    }

    private Size choosePreviewSize(Size[] sizes) {
        if (sizes == null || sizes.length == 0) {
            return new Size(1280, 720);
        }
        Size best = sizes[0];
        long bestArea = 0;
        for (Size size : sizes) {
            if (size.getWidth() > 1920 || size.getHeight() > 1080) {
                continue;
            }
            long area = (long) size.getWidth() * size.getHeight();
            if (area > bestArea) {
                best = size;
                bestArea = area;
            }
        }
        return best;
    }

    private void startPreview() {
        if (cameraDevice == null || !cameraPreview.isAvailable()) {
            return;
        }
        try {
            SurfaceTexture texture = cameraPreview.getSurfaceTexture();
            if (texture == null) {
                return;
            }
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface surface = new Surface(texture);
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    captureSession = session;
                    try {
                        captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, cameraHandler);
                        runOnUiThread(() -> webView.evaluateJavascript(
                                "window.nativeCameraReady&&window.nativeCameraReady()", null));
                    } catch (CameraAccessException e) {
                        showToast("相机预览失败：" + e.getMessage());
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    showToast("相机预览配置失败");
                }
            }, cameraHandler);
        } catch (CameraAccessException e) {
            showToast("相机预览启动失败：" + e.getMessage());
        }
    }

    private void closeCamera() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    private void showToast(final String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCameraIfReady();
            if (webView != null) {
                webView.evaluateJavascript("window.nativeCameraReady&&window.nativeCameraReady()", null);
            }
        } else if (requestCode == REQ_CAMERA) {
            showToast("请允许相机权限");
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    public class AndroidBridge {
        @JavascriptInterface
        public boolean isNativeCamera() {
            return true;
        }

        @JavascriptInterface
        public String getCameraFrameDataUrl(int maxWidth) {
            try {
                Bitmap bitmap = cameraPreview == null ? null : cameraPreview.getBitmap();
                if (bitmap == null) {
                    return "";
                }
                Bitmap output = bitmap;
                if (maxWidth > 0 && bitmap.getWidth() > maxWidth) {
                    int width = maxWidth;
                    int height = Math.round(bitmap.getHeight() * (maxWidth / (float) bitmap.getWidth()));
                    output = Bitmap.createScaledBitmap(bitmap, width, height, true);
                }
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                output.compress(Bitmap.CompressFormat.JPEG, 86, stream);
                if (output != bitmap) {
                    output.recycle();
                }
                return "data:image/jpeg;base64," + Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP);
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void toast(final String msg) {
            showToast(msg);
        }

        @JavascriptInterface
        public void sharePng(final String dataUrl) {
            runOnUiThread(() -> {
                try {
                    String base64 = dataUrl;
                    int comma = base64.indexOf(',');
                    if (comma >= 0) {
                        base64 = base64.substring(comma + 1);
                    }
                    byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                    String name = "measure_"
                            + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                            + ".png";
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DistanceMeterLab");
                        values.put(MediaStore.Images.Media.IS_PENDING, 1);
                    }
                    ContentResolver resolver = getContentResolver();
                    Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) {
                        throw new IllegalStateException("Unable to create image file");
                    }
                    OutputStream outputStream = resolver.openOutputStream(uri);
                    if (outputStream == null) {
                        throw new IllegalStateException("Unable to write image file");
                    }
                    outputStream.write(bytes);
                    outputStream.flush();
                    outputStream.close();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.clear();
                        values.put(MediaStore.Images.Media.IS_PENDING, 0);
                        resolver.update(uri, values, null, null);
                    }
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("image/png");
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(send, "Share measurement result"));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Share failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}
