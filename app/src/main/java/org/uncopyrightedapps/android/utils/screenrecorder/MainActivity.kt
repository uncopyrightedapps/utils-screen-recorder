package org.uncopyrightedapps.android.utils.screenrecorder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.widget.ToggleButton
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import kotlinx.android.synthetic.main.activity_main.*
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.util.DisplayMetrics
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private var recordingOn: Boolean = false

    private lateinit var mProjectionManager: MediaProjectionManager
    private lateinit var mMediaRecorder: MediaRecorder
    private lateinit var toggleButton: ToggleButton

    private var mScreenDensity: Int = 0
    private var mMediaProjection: MediaProjection? = null
    private var mMediaProjectionCallback: MediaProjectionCallback? = null
    private var mVirtualDisplay: VirtualDisplay? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toggleButton = findViewById(R.id.recording_toggle)
        toggleButton.setOnClickListener { showStartStopRecording() }

    }

    private fun showStartStopRecording() {
        if (recordingOn) {
            stopRecording()
        } else {
            checkPermissionsAndStartRecording()
        }
    }


    /*********************************************************************************************
     * Start Recording
     ********************************************************************************************/

    private fun checkPermissionsAndStartRecording() {
        Log.i(TAG, "Start recording pressed. Checking permissions.")
        if (permissionsAreMissing()) {
            toggleButton.isChecked = false
            requestRequiredPermissions()
        } else {
            Log.i(TAG,"Permissions have already been granted. Starting the recording.")
            startAuthorizedRecording()
        }
    }

    private fun permissionsAreMissing() =
            !isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                    !isPermissionGranted(Manifest.permission.RECORD_AUDIO)

    private fun startAuthorizedRecording() {
        recordingOn = true

        startRecordingUIUpdate()

        startRecording()
    }

    private fun startRecordingUIUpdate() {
        // Not possible to turn audio on/off while recording
        findViewById<ToggleButton>(R.id.audio_toggle).isEnabled = false

        toggleButton.isChecked = true
    }

    private fun startRecording() {
        initAllRecordingResources()
        if (mMediaProjection == null) {
            requestPermissionToRecordTheScreen()
        } else {
            startRecordingNow()
        }
    }

    private fun startRecordingNow() {
        mVirtualDisplay = createVirtualDisplay()
        mMediaRecorder.start()
        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
    }

    private fun requestPermissionToRecordTheScreen() {
        startActivityForResult(mProjectionManager.createScreenCaptureIntent(), PERMISSION_CODE)
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode != PERMISSION_CODE) {
            Log.e(TAG, "Unknown request code: $requestCode")
            return
        }

        if (resultCode != Activity.RESULT_OK) {
            Toast.makeText(this,
                    "Screen recording permission denied", Toast.LENGTH_SHORT).show()
            toggleButton.isChecked = false
            return
        }

        mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data)
        mMediaProjection!!.registerCallback(mMediaProjectionCallback, null)
        startRecordingNow()
    }

    private fun createVirtualDisplay(): VirtualDisplay {
        return mMediaProjection!!.createVirtualDisplay("MainActivity",
                getRecordingWidth(), getRecordingHeight(), mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mMediaRecorder.surface, null /*Handler*/, null)/*Callbacks*/
    }

    /*********************************************************************************************
     * Stop Recording
     ********************************************************************************************/

    private fun stopRecording() {
        recordingOn = false

        findViewById<ToggleButton>(R.id.audio_toggle).isEnabled = true

        mMediaRecorder.stop()
        mMediaRecorder.reset()

        Log.v(TAG, "Recording Stopped")
        stopScreenSharing()

        Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show()
    }

    private fun stopScreenSharing() {
        if (mVirtualDisplay != null) {
            mVirtualDisplay!!.release()
        }
        //mMediaRecorder.release();
    }

    private inner class MediaProjectionCallback : MediaProjection.Callback() {
        override fun onStop() {
            if (toggleButton.isChecked) {
                toggleButton.isChecked = false
                mMediaRecorder.stop()
                mMediaRecorder.reset()
                Log.v(TAG, "Recording Stopped")
            }
            mMediaProjection = null
            stopScreenSharing()
            Log.i(TAG, "MediaProjection Stopped")
        }
    }


    /*********************************************************************************************
     * Initialization
     ********************************************************************************************/

    private fun initAllRecordingResources() {
        setScreenDensity()
        initRecorder()
        prepareRecorder()

        mProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mMediaProjectionCallback = MediaProjectionCallback()
    }

    private fun setScreenDensity() {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        mScreenDensity = metrics.densityDpi
    }

    private fun prepareRecorder() {
        try {
            mMediaRecorder.prepare()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            finish()
        } catch (e: IOException) {
            e.printStackTrace()
            finish()
        }
    }

    private fun initRecorder() {
        mMediaRecorder = MediaRecorder()

        if (findViewById<ToggleButton>(R.id.audio_toggle).isChecked) {
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        }

        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

        if (findViewById<ToggleButton>(R.id.audio_toggle).isChecked) {
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        }

        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mMediaRecorder.setVideoEncodingBitRate(ENCODING_BIT_RATE * 1000)
        mMediaRecorder.setVideoFrameRate(30)

        mMediaRecorder.setVideoSize(getRecordingWidth(), getRecordingHeight())
        mMediaRecorder.setOutputFile(getFilePath())
    }

    private fun getRecordingHeight(): Int {
        //val densityDpi = resources.displayMetrics.densityDpi
        //val h = resources.displayMetrics.heightPixels * (densityDpi / 160)
        return resources.displayMetrics.heightPixels
    }

    private fun getRecordingWidth(): Int {
        //val densityDpi = resources.displayMetrics.densityDpi
        //val w = resources.displayMetrics.widthPixels * (densityDpi / 160)
        return resources.displayMetrics.widthPixels
    }


    /*********************************************************************************************
     * Permission Management
     ********************************************************************************************/

    private fun requestRequiredPermissions() {
        Log.i(TAG, "REQUIRED permission have NOT been granted. Requesting permissions.")
        if (shouldShowPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                shouldShowPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            showPermissionRationale()
        } else {
            batchRequestPermissions(
                    PERMISSIONS,
                    REQUEST_PERMISSIONS)
        }
    }

    private fun showPermissionRationale() {
        Log.i(TAG, "Displaying permission rationale to provide additional context.")
        Snackbar.make(mainLayout, R.string.permission_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, {
                    ActivityCompat.requestPermissions(this,
                            PERMISSIONS,
                            REQUEST_PERMISSIONS)
                })
                .show()
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray) {
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.size == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAuthorizedRecording()
            } else {
                toggleButton.isChecked = false
            }
        }
    }


    /*********************************************************************************************
     * Storage
     ********************************************************************************************/

    private fun getFilePath(): String? {
        val directory = Environment.getExternalStorageDirectory().toString() + File.separator + "Recordings"
        if (Environment.MEDIA_MOUNTED != Environment.getExternalStorageState()) {
            Toast.makeText(this, "Failed to get External Storage", Toast.LENGTH_SHORT).show()
            return null
        }
        val folder = File(directory)
        var success = true
        if (!folder.exists()) {
            success = folder.mkdir()
        }
        val filePath: String
        if (success) {
            val videoName = "capture_" + getCurSysDate() + ".mp4"
            filePath = directory + File.separator + videoName
        } else {
            Toast.makeText(this, "Failed to create Recordings directory", Toast.LENGTH_SHORT).show()
            return null
        }
        return filePath
    }

    @SuppressLint("SimpleDateFormat")
    private fun getCurSysDate(): String {
        return SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())
    }


    /*********************************************************************************************
     * Inner Classes
     ********************************************************************************************/

    companion object {

        const val TAG = "MainActivity"

        const val REQUEST_PERMISSIONS = 1

        const val PERMISSION_CODE = 1

        const val ENCODING_BIT_RATE = 2014

        val PERMISSIONS = arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO)
    }
}
