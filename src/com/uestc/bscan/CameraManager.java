package com.uestc.bscan;

import Decoder.BASE64Encoder;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by madmatrix on 2014/24/02.
 */
public class CameraManager {
	
    private static final String TAG = MainActivity.TAG;

    private Camera camera;

    /**
     * 前置/后置摄像头
     */
    private int facing;
    private MainActivity cameraActivity;
    private CameraConfiguration configuration;
    private CameraPreview cameraPreview;
    //private MyOrientationEventListener orientationEventListener;
    private AutoFocusManager autoFocusManager;

    /**
     * 记录手机的上一个方向
     */
    private int lastAutofocusOrientation = 0;

    /**
     * 按钮的上一次角度
     */
    private int lastBtOrientation = 0;

    private MyCameraButtonAnimation buttonAnimation;

    private SurfaceView surfaceView;

    public CameraManager(MainActivity cameraActivity, SurfaceView surfaceView, MyCameraButtonAnimation buttonAnimation) {
        this.cameraActivity = cameraActivity;
        configuration = new CameraConfiguration(cameraActivity);
        facing = Camera.CameraInfo.CAMERA_FACING_BACK;
        this.surfaceView = surfaceView;
        cameraPreview = new CameraPreview();
        //orientationEventListener = new MyOrientationEventListener(cameraActivity);
        this.buttonAnimation = buttonAnimation;
    }

    public Camera openCamera() {
        releaseCamera();
        Log.d(TAG, "open the " + getFacingDesc(facing) + " camera");

        if (facing != Camera.CameraInfo.CAMERA_FACING_BACK && facing != Camera.CameraInfo.CAMERA_FACING_FRONT) {
            Log.w(TAG, "invalid facing " + facing + ", use default CAMERA_FACING_BACK");
            facing = Camera.CameraInfo.CAMERA_FACING_BACK;
        }

        int numCameras = Camera.getNumberOfCameras();
        if (numCameras == 0) {
            return null;
        }

        int index = 0;
        while (index < numCameras) {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(index, cameraInfo);
            if (cameraInfo.facing == facing) {
                break;
            }
            ++index;
        }

        if (index < numCameras) {
            camera = Camera.open(index);
        } else {
            camera = Camera.open(0);
        }

        configuration.config(camera);
        cameraPreview.startPreview();
        //orientationEventListener.enable();

        return camera;
    }

    /**
     * 拍照
     */
    public void takePicture() {
        camera.takePicture(null, null, pictureCallback);
    }
    
    public void goBack() {
    	MainActivity.textView.setText(""); 
    }

    /**
     * 切换前置/后置摄像头
     */
    public void switchCamera() {
        if (Camera.CameraInfo.CAMERA_FACING_BACK == facing) {
            facing = Camera.CameraInfo.CAMERA_FACING_FRONT;
        } else {
            facing = Camera.CameraInfo.CAMERA_FACING_BACK;
        }

        releaseCamera();
        openCamera();
        cameraPreview.startPreview();
    }

    public void releaseCamera() {
        //orientationEventListener.disable();

        if (null != camera) {
            cameraPreview.stopPreview();
            camera.release();
            camera = null;
        }
    }

    private class PicSaveTask extends AsyncTask<byte[], Void, File> {
        @Override
        protected File doInBackground(byte[]... datas) {
            byte[] data = datas[0];
            File pictureFile = null;
            try {
                pictureFile = configuration.getPictureStorageFile();
            } catch (Exception e) {
                Log.e(TAG, "failed to create file: " + e.getMessage());
            }

            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();

                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                Uri contentUri = Uri.fromFile(pictureFile);
                mediaScanIntent.setData(contentUri);
                cameraActivity.sendBroadcast(mediaScanIntent);
            } catch (FileNotFoundException e) {
                Log.d(TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d(TAG, "Error accessing file: " + e.getMessage());
            }

            return pictureFile;
        }

        @Override
        protected void onPostExecute(File pictureFile) {
            super.onPostExecute(pictureFile);

            Toast.makeText(cameraActivity, "图片保存图库成功！"+pictureFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            Log.d(TAG, "the picture saved in " + pictureFile.getAbsolutePath());

            camera.startPreview();
        }
    }

    ;

    private Camera.PictureCallback pictureCallback = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            if (null == data || data.length == 0) {
                Toast.makeText(cameraActivity, "拍照失败，请重试！", Toast.LENGTH_SHORT).show();

                Log.e(TAG, "No media data returned");
                return;
            }
            //先旋转
    		data = rotate_byte(data);
            //保存
            new PicSaveTask().execute(data);
            new OCR().execute(data);
        }
    };
    
    private class OCR extends AsyncTask<byte[], Void, String> {
    	@Override
        protected String doInBackground(byte[]... datas) {
    		String result = baidu_ocr(datas[0]);
            return result;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            MainActivity.textView.setText(result); 
            //Toast.makeText(cameraActivity, result, Toast.LENGTH_SHORT).show();
            camera.startPreview();
        }
    }
    
    public byte[] rotate_byte(byte[] in){
    	Bitmap bm = BitmapFactory.decodeByteArray(in, 0, in.length);
    	if (Camera.CameraInfo.CAMERA_FACING_BACK == facing)
    	{
    		Matrix matrix = new Matrix();  
            matrix.reset();  
            matrix.postRotate(90);  
            bm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(),  
            		bm.getHeight(), matrix, true); 
    	}
    	else {
    		Matrix matrix = new Matrix();  
            matrix.reset();  
            matrix.postRotate(270);  
            bm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(),  
            		bm.getHeight(), matrix, true); 
    	}
    	ByteArrayOutputStream stream = new ByteArrayOutputStream();
    	bm.compress(Bitmap.CompressFormat.JPEG, 100, stream);
    	in = stream.toByteArray();
		return in;
    }
    
    public static String baidu_ocr(byte[] data){
    	
    	String httpUrl = "http://apis.baidu.com/apistore/idlocr/ocr";
		String img=null;
		BASE64Encoder encoder=new BASE64Encoder();
		img=encoder.encode(data);
		img = img.replaceAll("\\+", "%2B").replaceAll("\\r", "").replaceAll("\\n", "");
		System.out.println(img);
		String httpArg = "fromdevice=android&clientip=10.10.10.0&detecttype=LocateRecognize&languagetype=CHN_ENG&imagetype=1&image="+img;
		String jsonResult = request(httpUrl, httpArg);
		String result_jx = null;
		System.out.println(jsonResult);
		try {
			JSONObject result = new JSONObject(jsonResult);
			String errNum = result.getString("errNum");
			String errMsg = result.getString("errMsg");
			if (!errNum.equals("0"))
			{
				System.out.println(errMsg);
				return errMsg;
			}
			JSONArray array = result.getJSONArray("retData");
			System.out.println(array);
			int number = -1;
			number = array.length();
			System.out.println(number);
			StringBuffer sbf = new StringBuffer();
			for(int i=0;i<number;i++)
			{
				JSONObject line = (JSONObject) array.get(i);
				//System.out.println(line.getString("word"));
				sbf.append(line.getString("word").trim());
				sbf.append("\r\n");
			}
			result_jx = sbf.toString();
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return result_jx;
    }
    public static String request(String httpUrl, String httpArg) {
	    BufferedReader reader = null;
	    String result = null;
	    StringBuffer sbf = new StringBuffer();
	    try {
	        URL url = new URL(httpUrl);
	        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
	        connection.setRequestMethod("POST");
	        connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
	        // 填入apikey到HTTP header
	        connection.setRequestProperty("apikey",  "cbb0093323cda99c0cb4a40c3b7ab61c");
	        connection.setDoOutput(true);
	        connection.setDoInput(true);
	        connection.getOutputStream().write(httpArg.getBytes("UTF-8"));
	        connection.connect();
	        InputStream is = connection.getInputStream();
	        reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
	        String strRead = null;
	        while ((strRead = reader.readLine()) != null) {
	            sbf.append(strRead);
	            sbf.append("\r\n");
	        }
	        reader.close();
	        result = sbf.toString();
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	    return result;
	}

    private class MyOrientationEventListener extends OrientationEventListener {

        public MyOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            if (orientation == ORIENTATION_UNKNOWN) return;

            // 设置camera旋转角度以使最终照片与预览界面方向一致
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(facing, info);
            int fixedOrientation = (orientation + 45) / 90 * 90;

            int rotation = 0;
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                rotation = (info.orientation - fixedOrientation + 360) % 360;
            } else {  // back-facing camera
                rotation = (info.orientation + fixedOrientation) % 360;
            }

            Camera.Parameters cameraParameters = camera.getParameters();
            //cameraParameters.setRotation(rotation);//改为bitmap旋转了
            camera.setParameters(cameraParameters);


            // 手机旋转超过30°就重新对焦
            // 处理启动时orientation临界值情况
            if (orientation > 180 && lastAutofocusOrientation == 0) {
                lastAutofocusOrientation = 360;
            }
            if (Math.abs(orientation - lastAutofocusOrientation) > 30) {
                Log.d(TAG, "orientation=" + orientation + ", lastAutofocusOrientation=" + lastAutofocusOrientation);

                autoFocusManager.autoFocus();
                lastAutofocusOrientation = orientation;
            }


            // 使按钮随手机转动方向旋转
            // 按钮图片的旋转方向应当与手机的旋转方向相反，这样最终方向才能保持一致
            int phoneRotation = 0;
            if (orientation > 315 && orientation <= 45) {
                phoneRotation = 0;
            } else if (orientation > 45 && orientation <= 135) {
                phoneRotation = 90;
            } else if (orientation > 135 && orientation <= 225) {
                phoneRotation = 180;
            } else if (orientation > 225 && orientation <= 315) {
                phoneRotation = 270;
            }

            // 恢复自然方向时置零
            if (phoneRotation == 0 && lastBtOrientation == 360) {
                lastBtOrientation = 0;
            }

            // "就近处理"：为了让按钮旋转走"捷径"，如果起始角度与结束角度差超过180，则将为0的那个值换为360
            if ((phoneRotation == 0 || lastBtOrientation == 0) && (Math.abs(phoneRotation - lastBtOrientation) > 180)) {
                phoneRotation = phoneRotation == 0 ? 360 : phoneRotation;
                lastBtOrientation = lastBtOrientation == 0 ? 360 : lastBtOrientation;
            }

            if (phoneRotation != lastBtOrientation) {
                int fromDegress = 360 - lastBtOrientation;
                int toDegrees = 360 - phoneRotation;

                Log.i(TAG, "fromDegress=" + fromDegress + ", toDegrees=" + toDegrees);

                RotateAnimation animation = new RotateAnimation(fromDegress, toDegrees,
                        Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                animation.setDuration(1000);
                animation.setFillAfter(true);
                buttonAnimation.executeAnimation(animation);
                lastBtOrientation = phoneRotation;
            }
        }
    }

    private static String getFacingDesc(int facing) {
        if (facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return "front";
        } else {
            return "back";
        }
    }

    /**
     * 固定portrait模式下，无需调用此函数
     */
    private void setCameraDisplayOrientation() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(facing, info);
        int rotation = cameraActivity.getWindowManager().getDefaultDisplay().getRotation();

        Log.d(TAG, "[1492]rotation=" + rotation);

        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        Log.d(TAG, "[1492]info.orientation=" + info.orientation + ", degrees=" + degrees);

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }

        Log.d(TAG, "[1492]setCameraDisplayOrientation, result=" + result);

        camera.setDisplayOrientation(result);
    }


    private class CameraPreview implements SurfaceHolder.Callback {
        private SurfaceHolder mHolder;

        public CameraPreview() {
            mHolder = surfaceView.getHolder();
            mHolder.addCallback(this);
            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        public void startPreview() {
            try {
                camera.setPreviewDisplay(mHolder);
                camera.setDisplayOrientation(90);
                camera.startPreview();

                autoFocusManager = new AutoFocusManager(camera);
                autoFocusManager.startAutoFocus();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
        }

        public void stopPreview() {
            try {
                camera.stopPreview();
                autoFocusManager.stopAutoFocus();
            } catch (Exception e) {

            }
        }

        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int w, int h) {
            if (surfaceHolder.getSurface() == null) {
                return;
            }

            stopPreview();
            startPreview();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        }
    }


}
