package com.example.cameraheartratemonitor;

import static org.opencv.core.CvType.CV_32F;
import static java.lang.Math.abs;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.TextView;

//opencv
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity
        extends AppCompatActivity
        implements CameraBridgeViewBase.CvCameraViewListener2 {


    private static final String TAG = "App::MainActivity";
    private static final Scalar FACE_RECT_COLOR = new Scalar(0, 255, 0, 255);
    private static final Scalar FOREHEAD_RECT_COLOR = new Scalar(255, 0, 0, 255);

    private CameraBridgeViewBase mOpenCvCameraView = null;
    private int mCameraIndex = CameraBridgeViewBase.CAMERA_ID_FRONT;
    private static boolean initOpenCV = false;

    private Mat mRgba;
    private Mat mGray;
    private File mFaceCascadeFile;
    private File mEyeCascadeFile;
    private CascadeClassifier mFaceCascade;
    private CascadeClassifier mEyeCascade;
    private int mAbsoluteFaceSize;
    private int mAbsoluteEyeSize;

    private Mat mROIMatrix;
    private double mFrameRawSignalRED = 0.0;
    private double mFrameRawSignalGREEN = 0.0;
    private double mFrameRawSignalBLUE = 0.0;


    GraphView graph;
    private LineGraphSeries<DataPoint> redChannelSeries;
    private LineGraphSeries<DataPoint> greenChannelSeries;
    private LineGraphSeries<DataPoint> blueChannelSeries;
    int graphCounter = 0;

    static {initOpenCV = OpenCVLoader.initDebug();}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        /*
        redTV = (TextView) findViewById(R.id.textViewRed);
        blueTV = (TextView) findViewById(R.id.textViewBlue);
        greenTV = (TextView) findViewById(R.id.textViewGreen);
        redTV.setTextColor(Color.RED);
        greenTV.setTextColor(Color.GREEN);
        blueTV.setTextColor(Color.BLUE);
        */

        //load cascade file from app resources
        try {
            InputStream is = getResources().openRawResource(R.raw.haarcascade_frontalface_default);
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            mFaceCascadeFile = new File(cascadeDir, "haarcascade_frontalface_default.xml");
            FileOutputStream os = new FileOutputStream(mFaceCascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1){
                os.write(buffer, 0, bytesRead);
            }

            is.close();
            os.close();

            mFaceCascade = new CascadeClassifier(mFaceCascadeFile.getAbsolutePath());
            if(mFaceCascade.empty()) {
                Log.e(TAG, "Failed to load cascade classifier");
            } else {
                Log.i(TAG, "Loaded cascade classifier from " + mFaceCascadeFile.getAbsolutePath());
            }
            cascadeDir.delete();

        } catch (IOException e){
            e.printStackTrace();
            Log.e(TAG,"Failed to load cascade. Exception thrown:" + e);
        }

        /* EYE DETECTION
        try {
            InputStream is = getResources().openRawResource(R.raw.haarcascade_eye);
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            mEyeCascadeFile = new File(cascadeDir, "haarcascade_eye.xml");
            FileOutputStream os = new FileOutputStream(mEyeCascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1){
                os.write(buffer, 0, bytesRead);
            }

            is.close();
            os.close();

            mEyeCascade = new CascadeClassifier(mEyeCascadeFile.getAbsolutePath());
            if(mEyeCascade.empty()) {
                Log.e(TAG, "Failed to load cascade classifier");
            } else {
                Log.i(TAG, "Loaded cascade classifier from " + mEyeCascadeFile.getAbsolutePath());
            }
            cascadeDir.delete();

        } catch (IOException e){
            e.printStackTrace();
            Log.e(TAG,"Failed to load cascade. Exception thrown:" + e);
        }

        */

        //request camera permission
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.CAMERA}, 1);

        //setup camera view
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.CameraHeartRateMonitorView);

        mOpenCvCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK);
        mCameraIndex = CameraBridgeViewBase.CAMERA_ID_BACK;

        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCameraPermissionGranted();
        mOpenCvCameraView.setCvCameraViewListener(this);

        mROIMatrix = new Mat();

        //initialize real-time graph
        graph = (GraphView) findViewById(R.id.graph);

        redChannelSeries = new LineGraphSeries<>(new DataPoint[]{
                new DataPoint(graphCounter, (int)mFrameRawSignalRED)});
        redChannelSeries.setColor(Color.RED);

        blueChannelSeries = new LineGraphSeries<>(new DataPoint[]{
                new DataPoint(graphCounter, (int)mFrameRawSignalBLUE)});
        blueChannelSeries.setColor(Color.BLUE);

        greenChannelSeries = new LineGraphSeries<>(new DataPoint[]{
                new DataPoint(graphCounter, (int)mFrameRawSignalGREEN)});
        greenChannelSeries.setColor(Color.GREEN);


        graph.addSeries(redChannelSeries);
        graph.addSeries(greenChannelSeries);
        graph.addSeries(blueChannelSeries);
    }

    private void updateGraph(){
        graphCounter++;
        redChannelSeries.appendData(new DataPoint( graphCounter, (int)mFrameRawSignalRED), false, 256);
        greenChannelSeries.appendData(new DataPoint( graphCounter, (int)mFrameRawSignalGREEN), false, 256);
        blueChannelSeries.appendData(new DataPoint( graphCounter, (int)mFrameRawSignalBLUE), false, 256);
    }

    private void loadCascadeFile(String filePath, int resourceId, CascadeClassifier classifier)
    {
        try {
            InputStream is = getResources().openRawResource(resourceId);
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            mFaceCascadeFile = new File(cascadeDir, filePath);
            FileOutputStream os = new FileOutputStream(mFaceCascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1){
                os.write(buffer, 0, bytesRead);
            }

            is.close();
            os.close();

            mFaceCascade = new CascadeClassifier(mFaceCascadeFile.getAbsolutePath());
            if(mFaceCascade.empty()) {
                Log.e(TAG, "Failed to load cascade classifier");
            } else {
                Log.i(TAG, "Loaded cascade classifier from " + mFaceCascadeFile.getAbsolutePath());
            }
            cascadeDir.delete();

        } catch (IOException e){
            e.printStackTrace();
            Log.e(TAG,"Failed to load cascade. Exception thrown:" + e);
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],
                                           int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == 1){
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mOpenCvCameraView.setCameraPermissionGranted();
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

    public void computeRawSignalFromROI(Mat ROIMatrix){

        double matArea = ROIMatrix.size().area();

        double[] rgbTotalValue = {0.0, 0.0, 0.0};

        for(int i = 0; i < ROIMatrix.rows(); i++){
            for(int j = 0; j < ROIMatrix.cols(); j++){
                double[] temp = ROIMatrix.get(i, j);
                rgbTotalValue[0] += temp[0]; //red
                rgbTotalValue[1] += temp[1]; //green
                rgbTotalValue[2] += temp[2]; //blue
            }
        }

        mFrameRawSignalRED = rgbTotalValue[0]/matArea;
        mFrameRawSignalGREEN = rgbTotalValue[1]/matArea;
        mFrameRawSignalBLUE = rgbTotalValue[2]/matArea;


        assert(mFrameRawSignalRED < 255.0);
        assert(mFrameRawSignalGREEN < 255.0);
        assert(mFrameRawSignalBLUE < 255.0);
    }

    public Mat detectFaceOnFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        //rotate frames counter clockwise so that face detection works
        //when the smartphone is in portrait mode. If we don't do this, it only works
        //when the phone is being held in landscape mode.
        //TODO: detect orientation and decide or not to flip the frame based on that
        if(mCameraIndex == CameraBridgeViewBase.CAMERA_ID_BACK){
            Core.flip(inputFrame.gray().t(), mGray, 1);
            Core.flip(inputFrame.rgba().t(), mRgba, 1);
        } else if(mCameraIndex == CameraBridgeViewBase.CAMERA_ID_FRONT){
            Core.flip(inputFrame.gray().t(), mGray, 0);
            Core.flip(inputFrame.rgba().t(), mRgba, 0);
        }

        //mGray = inputFrame.gray();
        //mRgba = inputFrame.rgba();

        MatOfRect faces = new MatOfRect();
        //MatOfRect eyes = new MatOfRect();

        //equalize the frame histogram to improve the result
        //Imgproc.equalizeHist(mGray, mGray);

        //compute minimum face size
        //set it to 20% of frame height in this case
        if (this.mAbsoluteFaceSize == 0){
            int height = mGray.rows();
            if(Math.round(height * 0.2f) > 0){
                this.mAbsoluteFaceSize = Math.round(height * 0.2f);
            }
        }

        //compute minimum eye size
        //set it 10% of face size
        if (this.mAbsoluteFaceSize == 0){
                this.mAbsoluteEyeSize = Math.round(mAbsoluteFaceSize * 0.1f);
        }

        //detect faces
        this.mFaceCascade.detectMultiScale(
                mGray, faces, 1.1, 2,
                Objdetect.CASCADE_SCALE_IMAGE,
                new Size(mAbsoluteFaceSize, mAbsoluteFaceSize),
                new Size()
        );

        /* EYE DETECTION
        this.mEyeCascade.detectMultiScale(
                mGray, eyes, 1.1, 2,
                Objdetect.CASCADE_SCALE_IMAGE,
                new Size(mAbsoluteEyeSize, mAbsoluteEyeSize),
                new Size()
        );
        */

        // draw each rectangle in faces
        Rect[] facesArray = faces.toArray();
        for(int i = 0; i < facesArray.length; i++){
            Imgproc.rectangle(mRgba, facesArray[i].tl(),
                    facesArray[i].br(),
                    FACE_RECT_COLOR,
                    3);

            double yLength = abs(facesArray[i].tl().y - facesArray[i].br().y);
            double xLength = abs(facesArray[i].tl().x - facesArray[i].br().x);

            double xMidPoint = facesArray[i].tl().x + abs(xLength * 0.5);
            double yMidPoint = facesArray[i].tl().y + abs(yLength * 0.5);

            Point foreheadTL = new Point(
                    //xMidPoint - 150.0,
                    //facesArray[i].tl().y + (0.1 * yLength)
                    facesArray[i].tl().x + (0.3 * xLength),
                    facesArray[i].tl().y + (0.1 * yLength)
            );

            Point foreheadBR = new Point(
            //        xMidPoint + 150,
             //       foreheadTL.y + 300.0
                    facesArray[i].br().x - (0.3 * xLength),
                    facesArray[i].br().y - (0.7 * yLength)
            );

            Rect ROIrect = new Rect(
                    (int)Math.floor(foreheadTL.x),
                    (int)Math.floor(foreheadTL.y),
                    (int)Math.floor(xLength),
                    (int)Math.floor(yLength)
            );
            mROIMatrix = new Mat(mRgba, ROIrect);
            computeRawSignalFromROI(mROIMatrix);

            /*
            if(redTV != null || greenTV != null || blueTV != null) {
                redTV.setText(Double.toString(mFrameRawSignalRED));
                greenTV.setText(Double.toString(mFrameRawSignalGREEN));
                blueTV.setText(Double.toString(mFrameRawSignalBLUE));
            }
            */

            updateGraph();


            Imgproc.rectangle(mRgba, foreheadTL, foreheadBR, FOREHEAD_RECT_COLOR, 2);
        }

        /* EYE DETECTION
        // draw each rectangle in eyes
        Rect[] eyesArray = eyes.toArray();
        for(int i = 0; i < eyesArray.length; i++){
            Imgproc.rectangle(mRgba, eyesArray[i].tl(),
                    eyesArray[i].br(),
                    FACE_RECT_COLOR,
                    3);
        }
        */

        //Rotate frames back:
        if(mCameraIndex == CameraBridgeViewBase.CAMERA_ID_BACK){
            Core.flip(mRgba.t(), mRgba, 0);
        } else if(mCameraIndex == CameraBridgeViewBase.CAMERA_ID_FRONT){
            Core.flip(mRgba.t(), mRgba, 1);
        }

        return mRgba;
    }
}