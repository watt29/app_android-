package com.example.nongkanvelaassistant.ui

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.os.Build
import android.view.WindowManager
import android.view.ViewGroup
import android.widget.Toast
import android.provider.MediaStore
import android.media.MediaScannerConnection
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraCaptureActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private var imageCapture: ImageCapture? = null
    private var captureRequested = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCameraAndCapture()
        } else {
            Toast.makeText(this, "ต้องอนุญาตใช้กล้องก่อนนะคะ", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        previewView = PreviewView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        setContentView(previewView)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCameraAndCapture()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCameraAndCapture() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )

                previewView.postDelayed({ takePhoto() }, 1200)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "เปิดกล้องไม่สำเร็จค่ะ", Toast.LENGTH_SHORT).show()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        if (captureRequested) return
        val capture = imageCapture ?: return
        captureRequested = true

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val displayName = "nongkanvela_$timestamp.jpg"
        val legacyPhotoFile = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val photosDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
            File(photosDir, displayName)
        } else {
            null
        }

        val outputOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NongKanvelaAssistant")
            }
            ImageCapture.OutputFileOptions.Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).build()
        } else {
            ImageCapture.OutputFileOptions.Builder(legacyPhotoFile!!).build()
        }

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        MediaScannerConnection.scanFile(
                            this@CameraCaptureActivity,
                            arrayOf(legacyPhotoFile!!.absolutePath),
                            arrayOf("image/jpeg"),
                            null
                        )
                    }
                    Toast.makeText(this@CameraCaptureActivity, "ถ่ายภาพเรียบร้อยแล้วค่ะ", Toast.LENGTH_SHORT).show()
                    finish()
                }

                override fun onError(exception: ImageCaptureException) {
                    exception.printStackTrace()
                    Toast.makeText(this@CameraCaptureActivity, "ถ่ายภาพไม่สำเร็จค่ะ", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        )
    }
}
