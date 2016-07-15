package com.uestc.bscan;

import android.content.Context;
import android.graphics.Point;
import android.hardware.Camera;
import android.os.Environment;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * Created by madmatrix on 2014/24/02.
 */
public class CameraConfiguration {

    private static final String TAG = MainActivity.TAG;

    private Camera.Parameters cameraParameters;

    private Camera camera;

    private Point screenResolution;

    private Context context;

    /**
     * 最小预览界面的分辨率
     */
    private static final int MIN_PREVIEW_PIXELS = 480 * 320;

    /**
     * 最大宽高比差
     */
    private static final double MAX_ASPECT_DISTORTION = 0.15;


    public CameraConfiguration(Context context) {
        this.context = context;
    }

    public void config(Camera camera) {
        this.camera = camera;
        this.cameraParameters = camera.getParameters();

        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = manager.getDefaultDisplay();
        screenResolution = new Point(display.getWidth(), display.getHeight());
        Log.d(TAG, "screen resolution " + screenResolution.x + "*" + screenResolution.y);
        
        initCameraResolution();
        initFocusMode();
        initPictureResolution();
    }

    /**
     * 初始化camera分辨率
     */
    public void initCameraResolution() {
        Point cameraResolution = findBestPreviewResolution();

        Log.d(TAG, "set the preview size=" + cameraResolution);
        cameraParameters.setPreviewSize(cameraResolution.x, cameraResolution.y);
        camera.setParameters(cameraParameters);

        Camera.Parameters afterParameters = camera.getParameters();
        Camera.Size afterResolutions = afterParameters.getPreviewSize();
        if (afterResolutions != null && (cameraResolution.x != afterResolutions.width || cameraResolution.y != afterResolutions.height)) {
            Log.w(TAG, "Camera said it supported preview resolution " + cameraResolution.x + 'x' + cameraResolution.y
                    + ", but after setting it, preview resolution is " + afterResolutions.width + 'x' + afterResolutions.height);
        }
    }

    /**
     * 初始化聚焦模式
     * 即使是同一个手机，前置和后置摄像头的聚焦模式可能不同，如果强行设置可能会出现以下异常：
     * Failed substring capabilities check, unsupported parameter: 'auto', original: fixed
     */
    public void initFocusMode() {
        List<String> supportedFocusModes = cameraParameters.getSupportedFocusModes();
        String focusMode = findSettableValue(supportedFocusModes, Camera.Parameters.FOCUS_MODE_AUTO);

        if (null == focusMode) {
            focusMode = findSettableValue(cameraParameters.getSupportedFocusModes(),
                    Camera.Parameters.FOCUS_MODE_MACRO,
                    Camera.Parameters.FOCUS_MODE_EDOF);
        }

        if (null != focusMode) {
            cameraParameters.setFocusMode(focusMode);
        } else {
            cameraParameters.setFocusMode(supportedFocusModes.get(0));
        }
        camera.setParameters(cameraParameters);
    }

    /**
     * 初始化最终拍得的照片像素值
     */
    public void initPictureResolution() {
        Point bestPictureResolution = findBestPictureResolution();
        int aa = 768;//80万像素
        int bb = (int) (aa*bestPictureResolution.y*1.0/bestPictureResolution.x);
        cameraParameters.setPictureSize(aa,bb);
        //cameraParameters.setPictureSize(bestPictureResolution.x, bestPictureResolution.y);
        Log.d(TAG, "set the picture resolution " + bestPictureResolution.x + "x" + bestPictureResolution.y);
        camera.setParameters(cameraParameters);

        Camera.Size afterPicResolution = camera.getParameters().getPictureSize();
        if (null != afterPicResolution &&
                (afterPicResolution.width != bestPictureResolution.x &&
                 afterPicResolution.height != bestPictureResolution.y)) {
            Log.w(TAG, "camera support the picture resolution " + bestPictureResolution.x
             + "x" + bestPictureResolution.y + ", but after set, the picture resolution is " +
            bestPictureResolution.x + "x" + bestPictureResolution.y);
        }
    }

    /**
     * 获取存储图片的文件
     *
     * @return
     * @throws Exception
     */
    public File getPictureStorageFile() throws Exception {
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            throw new Exception("no sdcard found or can't write in the sdcard!");
        }

        File pictureStoreDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM), "Camera_OCR");

        if (!pictureStoreDir.exists()) {
            if (!pictureStoreDir.mkdirs()) {
                throw new Exception("failed to create directory: " + pictureStoreDir.getAbsolutePath());
            }
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File picture = new File(pictureStoreDir.getPath() + File.separator +
                "IMG_" + timeStamp + ".jpg");

        return picture;
    }


    /**
     * 找出最适合的预览界面分辨率
     *
     * @return
     */
    private Point findBestPreviewResolution() {
        Camera.Size defaultPreviewResolution = cameraParameters.getPreviewSize();
        Log.d(TAG, "camera default resolution " + defaultPreviewResolution.width + "x" + defaultPreviewResolution.height);

        List<Camera.Size> rawSupportedSizes = cameraParameters.getSupportedPreviewSizes();
        if (rawSupportedSizes == null) {
            Log.w(TAG, "Device returned no supported preview sizes; using default");
            return new Point(defaultPreviewResolution.width, defaultPreviewResolution.height);
        }

        // 按照分辨率从大到小排序
        List<Camera.Size> supportedPreviewResolutions = new ArrayList<Camera.Size>(rawSupportedSizes);
        Collections.sort(supportedPreviewResolutions, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size a, Camera.Size b) {
                int aPixels = a.height * a.width;
                int bPixels = b.height * b.width;
                if (bPixels < aPixels) {
                    return -1;
                }
                if (bPixels > aPixels) {
                    return 1;
                }
                return 0;
            }
        });

        StringBuilder previewResolutionSb = new StringBuilder();
        for (Camera.Size supportedPreviewResolution : supportedPreviewResolutions) {
            previewResolutionSb.append(supportedPreviewResolution.width).append('x').append(supportedPreviewResolution.height)
                    .append(' ');
        }
        Log.v(TAG, "Supported preview resolutions: " + previewResolutionSb);


        // 移除不符合条件的分辨率
        double screenAspectRatio = (double) screenResolution.x / (double) screenResolution.y;
        Iterator<Camera.Size> it = supportedPreviewResolutions.iterator();
        while (it.hasNext()) {
            Camera.Size supportedPreviewResolution = it.next();
            int width = supportedPreviewResolution.width;
            int height = supportedPreviewResolution.height;

            // 移除低于下限的分辨率，尽可能取高分辨率
            if (width * height < MIN_PREVIEW_PIXELS) {
                it.remove();
                continue;
            }

            // 在camera分辨率与屏幕分辨率宽高比不相等的情况下，找出差距最小的一组分辨率
            // 由于camera的分辨率是width>height，我们设置的portrait模式中，width<height
            // 因此这里要先交换然preview宽高比后在比较
            boolean isCandidatePortrait = width > height;
            int maybeFlippedWidth = isCandidatePortrait ? height : width;
            int maybeFlippedHeight = isCandidatePortrait ? width : height;
            double aspectRatio = (double) maybeFlippedWidth / (double) maybeFlippedHeight;
            double distortion = Math.abs(aspectRatio - screenAspectRatio);
            if (distortion > MAX_ASPECT_DISTORTION) {
                it.remove();
                continue;
            }

            // 找到与屏幕分辨率完全匹配的预览界面分辨率直接返回
            if (maybeFlippedWidth == screenResolution.x && maybeFlippedHeight == screenResolution.y) {
                Point exactPoint = new Point(width, height);
                Log.d(TAG, "found preview resolution exactly matching screen resolutions: " + exactPoint);

                return exactPoint;
            }
        }

        // 如果没有找到合适的，并且还有候选的像素，则设置其中最大比例的，对于配置比较低的机器不太合适
        if (!supportedPreviewResolutions.isEmpty()) {
            Camera.Size largestPreview = supportedPreviewResolutions.get(0);
            Point largestSize = new Point(largestPreview.width, largestPreview.height);
            Log.d(TAG, "using largest suitable preview resolution: " + largestSize);
            return largestSize;
        }

        // 没有找到合适的，就返回默认的
        Point defaultResolution = new Point(defaultPreviewResolution.width, defaultPreviewResolution.height);
        Log.d(TAG, "No suitable preview resolutions, using default: " + defaultResolution);

        return defaultResolution;
    }

    /**
     * 找出最佳照片分辨率
     * 该方法的逻辑与findBestPreviewResolution思路很相似，但还是有部分逻辑不通，分开写的好
     *
     * @return
     */
    private Point findBestPictureResolution() {
        List<Camera.Size> supportedPicResolutions = cameraParameters.getSupportedPictureSizes(); // 至少会返回一个值

        StringBuilder picResolutionSb = new StringBuilder();
        for (Camera.Size supportedPicResolution : supportedPicResolutions) {
            picResolutionSb.append(supportedPicResolution.width).append('x').append(supportedPicResolution.height).append(" ");
        }
        Log.d(TAG, "Supported picture resolutions: " + picResolutionSb);

        Camera.Size defaultPictureResolution = cameraParameters.getPictureSize();
        Log.d(TAG, "default picture resolution " + defaultPictureResolution.width + "x" + defaultPictureResolution.height);

        // 排序
        List<Camera.Size> sortedSupportedPicResolutions = new ArrayList<Camera.Size>(supportedPicResolutions);
        Collections.sort(sortedSupportedPicResolutions, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size a, Camera.Size b) {
                int aPixels = a.height * a.width;
                int bPixels = b.height * b.width;
                if (bPixels < aPixels) {
                    return -1;
                }
                if (bPixels > aPixels) {
                    return 1;
                }
                return 0;
            }
        });

        // 移除不符合条件的分辨率
        double screenAspectRatio = (double) screenResolution.x / (double) screenResolution.y;
        Iterator<Camera.Size> it = sortedSupportedPicResolutions.iterator();
        while (it.hasNext()) {
            Camera.Size supportedPreviewResolution = it.next();
            int width = supportedPreviewResolution.width;
            int height = supportedPreviewResolution.height;

            // 在camera分辨率与屏幕分辨率宽高比不相等的情况下，找出差距最小的一组分辨率
            // 由于camera的分辨率是width>height，我们设置的portrait模式中，width<height
            // 因此这里要先交换然后在比较宽高比
            boolean isCandidatePortrait = width > height;
            int maybeFlippedWidth = isCandidatePortrait ? height : width;
            int maybeFlippedHeight = isCandidatePortrait ? width : height;
            double aspectRatio = (double) maybeFlippedWidth / (double) maybeFlippedHeight;
            double distortion = Math.abs(aspectRatio - screenAspectRatio);
            if (distortion > MAX_ASPECT_DISTORTION) {
                it.remove();
                continue;
            }
        }

        // 如果没有找到合适的，并且还有候选的像素，对于照片，则取其中最大比例的，而不是选择与屏幕分辨率相同的
        if (!sortedSupportedPicResolutions.isEmpty()) {
            Camera.Size largestPreview = sortedSupportedPicResolutions.get(0);
            Point largestSize = new Point(largestPreview.width, largestPreview.height);
            Log.d(TAG, "using largest suitable picture resolution: " + largestSize);
            return largestSize;
        }

        // 没有找到合适的，就返回默认的
        Point defaultResolution = new Point(defaultPictureResolution.width, defaultPictureResolution.height);
        Log.d(TAG, "No suitable picture resolutions, using default: " + defaultResolution);

        return defaultResolution;
    }

    private static String findSettableValue(Collection<String> supportedValues, String... desiredValues) {
        String result = null;
        if (supportedValues != null) {
            for (String desiredValue : desiredValues) {
                if (supportedValues.contains(desiredValue)) {
                    result = desiredValue;
                    break;
                }
            }
        }
        return result;
    }
}
