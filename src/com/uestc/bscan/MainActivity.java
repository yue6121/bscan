package com.uestc.bscan;

import android.app.Activity;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Created by madmatrix on 2014/19/02.
 */
public class MainActivity extends Activity implements Button.OnClickListener {
    public static final String TAG = "mycamera";

    static TextView textView;
    private ImageView ivShutter;
    private ImageView ivCameraSwitcher;
    private CameraManager cameraManager;
    private SurfaceView previewLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = (TextView) findViewById(R.id.textView1);
        ivShutter = (ImageView) findViewById(R.id.mc_shutter);
        ivCameraSwitcher = (ImageView) findViewById(R.id.mc_facing_switcher);
        previewLayout = (SurfaceView) findViewById(R.id.mc_preview);

        ivShutter.setOnClickListener(this);
        ivCameraSwitcher.setOnClickListener(this);

        cameraManager = new CameraManager(this, previewLayout, btAnimation);
    }

    @Override
    protected void onResume() {
        super.onResume();

        cameraManager.openCamera();
    }

    @Override
    protected void onPause() {
        super.onPause();

        cameraManager.releaseCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.mc_shutter:
            	//Toast.makeText(getApplication(), "正在分析，请稍等。。。", Toast.LENGTH_LONG).show();
                cameraManager.takePicture();

                break;
            case R.id.mc_facing_switcher:
                cameraManager.goBack();

                break;
        }
    }
    
    
    


    private MyCameraButtonAnimation btAnimation = new MyCameraButtonAnimation() {
        @Override
        public void executeAnimation(Animation animation) {
            ivShutter.startAnimation(animation);
            ivCameraSwitcher.startAnimation(animation);
        }
    };
}
