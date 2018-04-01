package org.uncopyrightedapps.android.utils.screenrecorder

import android.content.pm.PackageManager
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity

fun AppCompatActivity.isPermissionGranted(permission: String) =
        ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun AppCompatActivity.shouldShowPermissionRationale(permission: String) =
        ActivityCompat.shouldShowRequestPermissionRationale(this, permission)

fun AppCompatActivity.batchRequestPermissions(permissions: Array<String>, requestId: Int) =
        ActivityCompat.requestPermissions(this, permissions, requestId)