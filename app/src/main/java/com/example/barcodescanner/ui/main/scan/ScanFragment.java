package com.example.barcodescanner.ui.main.scan;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.example.barcodescanner.databinding.FragmentScanBinding;
import com.example.barcodescanner.ui.base.BaseFragment;
import com.example.barcodescanner.util.CommonUtil;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

/**
 * Created by Trung on 8/7/2020
 */
public class ScanFragment extends BaseFragment implements ScanPresenter.View, ImageAnalysis.Analyzer {

    private static final int REQUEST_CODE_CAMERA_PERMISSION = 1;

    private FragmentScanBinding mBinding;
    private BarcodeDetector mBarCodeDetector;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        bindView();
        return mBinding.getRoot();
    }

    private void bindView() {
        mBinding = FragmentScanBinding.inflate(getLayoutInflater());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        processSubscribingCameraPreviewAndAnalysis();
        init();
    }

    private void init() {
        mBarCodeDetector = new BarcodeDetector
                .Builder(requireContext())
                .setBarcodeFormats(
                        com.google.android.gms.vision.barcode.Barcode.DATA_MATRIX
                                | Barcode.QR_CODE
                )
                .build();

        Paint paint = new Paint();
        paint.setColor(Color.RED);
        mBinding.barCodeDetectionView.setPaint(paint);
    }

    private void processSubscribingCameraPreviewAndAnalysis() {
        if (isCameraPermissionGranted()) {
            subscribeCameraPreviewAndAnalysis();
        } else requestCameraPermission();
    }

    private boolean isCameraPermissionGranted() {
        return getBaseActivity().checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        String[] permissions = new String[]{Manifest.permission.CAMERA};
        requestPermissions(permissions, REQUEST_CODE_CAMERA_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            processSubscribingCameraPreviewAndAnalysis();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void subscribeCameraPreviewAndAnalysis() {
        ListenableFuture<ProcessCameraProvider> cameraSubscriber =
                ProcessCameraProvider.getInstance(requireContext());

        cameraSubscriber.addListener(() -> {
                    try {
                        setupCameraForPreviewAndAnalysis(cameraSubscriber);
                    } catch (ExecutionException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }, ContextCompat.getMainExecutor(requireContext())
        );
    }

    private void setupCameraForPreviewAndAnalysis(
            ListenableFuture<ProcessCameraProvider> cameraSubscriber)
            throws ExecutionException, InterruptedException {
        // used to the activity lifecycle to the lifecycle of camera
        ProcessCameraProvider cameraProvider = cameraSubscriber.get();

        // attach the cameraPreview to the preview
        Preview preview = new Preview
                .Builder()
                .build();
        preview.setSurfaceProvider(
                mBinding.cameraPreview.createSurfaceProvider()
        );

        ImageAnalysis analyzer = new ImageAnalysis.Builder()
                .build();
        analyzer.setAnalyzer(
                ContextCompat.getMainExecutor(requireContext()),
                this);

        CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;
        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this,
                selector, preview, analyzer);
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        @SuppressLint("UnsafeExperimentalUsageError")
        Bitmap bitmap = CommonUtil.toBitmap(image.getImage());
        Frame frame = new Frame.Builder()
                .setBitmap(bitmap)
                .build();

        SparseArray<com.google.android.gms.vision.barcode.Barcode> detectedBarCodes
                = mBarCodeDetector.detect(frame);

        showBarCodeDetection(detectedBarCodes, bitmap);

        /* Close after completely processing to get another frame*/
        image.close();
    }

    private void showBarCodeDetection(
            SparseArray<Barcode> detectedBarCodes, Bitmap image) {

        if (detectedBarCodes.size() == 0) {
            mBinding.barCodeDetectionView.removeAllAndInvalidate();
            return;
        }

        setDetectionViewProperties(image);

        for (int i = 0; i < detectedBarCodes.size(); i++) {
            int key = detectedBarCodes.keyAt(0);
            BarCodeDetectionGraphic barCodeDetection = new BarCodeDetectionGraphic(
                    mBinding.barCodeDetectionView, detectedBarCodes.get(key));
            mBinding.barCodeDetectionView.add(barCodeDetection);
        }
        mBinding.barCodeDetectionView.postInvalidate();
    }

    private void setDetectionViewProperties(Bitmap image) {
        mBinding.barCodeDetectionView.removeAllWithoutInvalidate();
        mBinding.barCodeDetectionView.setCameraInfo(
                image.getWidth(),
                image.getHeight(),
                CameraSource.CAMERA_FACING_BACK
        );
    }
}
