package com.example.cameraheartratemonitor;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.nfc.Tag;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;

//opencv
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity
        extends AppCompatActivity
        implements CameraBridgeViewBase.CvCameraViewListener2 {


    private static final String TAG = "App::MainActivity";
    private static final Scalar FACE_RECT_COLOR = new Scalar(0, 255, 0, 255);

    private CameraBridgeViewBase mOpenCvCameraView = null;
    private static boolean initOpenCV = false;

    private Mat mRgba;
    private Mat mGray;
    private File cascadeFile;
    private CascadeClassifier faceCascade;
    private int absoluteFaceSize;

    static {initOpenCV = OpenCVLoader.initDebug();}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        //load cascade file from app resources
        try {
            InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            cascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
            FileOutputStream os = new FileOutputStream(cascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1){
                os.write(buffer, 0, bytesRead);
            }

            is.close();
            os.close();

            faceCascade = new CascadeClassifier(cascadeFile.getAbsolutePath());
            if(faceCascade.empty()) {
                Log.e(TAG, "Failed to load cascade classifier");
            } else {
                Log.i(TAG, "Loaded cascade classifier from " + cascadeFile.getAbsolutePath());
            }
            cascadeDir.delete();

        } catch (IOException e){
            e.printStackTrace();
            Log.e(TAG,"Failed to load cascade. Exception thrown:" + e);
        }

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

    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mRgba = new Mat();
    }

    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        return detectFaceOnFrame(inputFrame);
    }

    public Mat detectFaceOnFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        MatOfRect faces = new MatOfRect();

        //equalize the frame histogram to improve the result
        //Imgproc.equalizeHist(mRgba, mRgba);

        //compute minimum face size (20% of frame height in this case)
        if (this.absoluteFaceSize == 0){
            int height = mGray.rows();
            if(Math.round(height * 0.2f) > 0){
                this.absoluteFaceSize = Math.round(height * 0.2f);
            }
        }

        //detect faces
        this.faceCascade.detectMultiScale(mGray, faces, 1.1, 2,
                Objdetect.CASCADE_SCALE_IMAGE,
                new Size(this.absoluteFaceSize, this.absoluteFaceSize),
                new Size());

        // each rectangle in faces is a face
        // draw them

        Rect[] facesArray = faces.toArray();
        for(int i = 0; i < facesArray.length; i++){
            Imgproc.rectangle(mRgba, facesArray[i].tl(),
                    facesArray[i].br(),
                    FACE_RECT_COLOR,
                    3);
        }

        return mRgba;
    }
}