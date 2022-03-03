package com.example.cameraheartratemonitor;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.WindowManager;

//opencv
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

public class MainActivity
        extends AppCompatActivity
        implements CameraBridgeViewBase.CvCameraViewListener2 {

    private CameraBridgeViewBase mOpenCvCameraView = null;
    private static boolean initOpenCV = false;

    static {initOpenCV = OpenCVLoader.initDebug();}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        //request camera permission
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.CAMERA}, 1);

        //setup camera view
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.CameraHeartRateMonitorView);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCameraPermissionGranted();
        mOpenCvCameraView.setCvCameraViewListener(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],
                                           int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == 1){
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mOpenCvCameraView.setCameraPermissionGranted();  // <------ THIS!!!
            } else {
                // permission denied
            }
            return;
        }
        // If request is cancelled, the result arrays are empty.
    }

    @Override
    protected void onResume(){
        super.onResume();

        if(initOpenCV){ mOpenCvCameraView.enableView();}
    }

    @Override
    public void onPause(){
        super.onPause();

        //Release the camera
        if(mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
    }

    public void onCameraViewStarted(int width, int height) { }

    public void onCameraViewStopped() { }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        return inputFrame.rgba();
    }
}