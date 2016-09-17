package com.teste.santos.opencvtest;

import android.app.Activity;
import android.os.Debug;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.teste.santos.base.ColorBlobDetector;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements View.OnTouchListener, CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String  TAG              = "OCVSample::Activity";

    private boolean              mIsColorSelected = false;
    private Mat                  mRgba;
    private Scalar               mBlobColorRgba;
    private Scalar               mBlobColorHsv;
    private ColorBlobDetector mDetector;
    private Mat                  mSpectrum;
    private Size                 SPECTRUM_SIZE;
    private Scalar               CONTOUR_COLOR_1;
    private Scalar               CONTOUR_COLOR_2;

    private Mat imgGray;
    private Mat imgBlur;
    private Mat thresh1;
    private Mat hierarchy;
    private Mat drawing;
    private MatOfInt currentHull;
    private List<MatOfPoint> contours;

    int THRESHOLD_VALUE = 70;//TODO: test with 0
    private static final int  MAX_BINARY_VALUE = 255;

    private CameraBridgeViewBase mOpenCvCameraView;

    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    mOpenCvCameraView.setOnTouchListener(MainActivity.this);
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };


    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        mOpenCvCameraView = (JavaCameraView) findViewById(R.id.java_camera_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_9, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }
    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);

        imgGray = new Mat(height, width, CvType.CV_8UC1);
        imgBlur = new Mat(height, width, CvType.CV_8UC1);
        thresh1 = new Mat(height, width, CvType.CV_8UC1);
        hierarchy = new Mat(height, width, CvType.CV_8UC1);
        drawing = new Mat(height, width, CvType.CV_8UC4);
        contours = new ArrayList<>();
        CONTOUR_COLOR_1 = new Scalar(0,255,0);
        CONTOUR_COLOR_2 = new Scalar(0,0,255);
        currentHull = new MatOfInt();
    }
    @Override
    public void onCameraViewStopped() {

        mRgba.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();

        contours.clear();
        //Take a Gray Frame
        Imgproc.cvtColor(mRgba, imgGray, Imgproc.COLOR_RGB2GRAY);
        //Take a Blur Frame
        Imgproc.GaussianBlur(imgGray, imgBlur,(new Size(5,5)), 0);
        //Take a Binary Frame
        Imgproc.threshold(imgBlur, thresh1, THRESHOLD_VALUE, MAX_BINARY_VALUE, Imgproc.THRESH_BINARY_INV+Imgproc.THRESH_OTSU);
        //Return all contour and the hierarchy (this final item its not necessary)
        Imgproc.findContours(thresh1, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
        //Extract the largest Contours
        double maxArea = 0;
        int currentPoint = 0;
        MatOfPoint currentMatPoint;
        for (int i = 0; i < contours.size(); i++){
            currentMatPoint = contours.get(i);
            double area = Imgproc.contourArea(currentMatPoint);
            if(maxArea < area){
                maxArea = area;
                currentPoint = i;
            }
        }
        //TODO: MAKE OTHER ALTERNATIVE (USE PURE BLACK BACKGROUND)
        drawing = new Mat(drawing.height(), drawing.width(), CvType.CV_8UC3);

        if(contours.isEmpty()) return drawing;

        currentMatPoint = contours.get(currentPoint);
        //Extract de convex Hull
        Imgproc.convexHull(currentMatPoint, currentHull);
        //Displaying largest contour and convex hull
        //Creating a auxiliar list to Point
        List<MatOfPoint> auxPointList =  new ArrayList<MatOfPoint>();
        auxPointList.add(currentMatPoint);
        //Creating a auxiliar list to Hull

        MatOfPoint mopOut = new MatOfPoint();
        mopOut.create((int)currentHull.size().height,1,CvType.CV_32SC2);

        for(int i = 0; i < currentHull.size().height ; i++) {
            int index = (int)currentHull.get(i, 0)[0];
            double[] point = new double[] {
                    currentMatPoint.get(index, 0)[0], currentMatPoint.get(index, 0)[1]
            };
            mopOut.put(i, 0, point);
        }
        List<MatOfPoint> auxIntList =  new ArrayList<MatOfPoint>();
        auxIntList.add(mopOut);

        Imgproc.drawContours(drawing, auxPointList, 0, CONTOUR_COLOR_1, 2);
        Imgproc.drawContours(drawing, auxIntList, 0, CONTOUR_COLOR_2, 2);

        return drawing;
    }

    public boolean onTouch(View v, MotionEvent event) {
        return false; // don't need subsequent touch events
    }


    private Scalar converScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }
}
