package com.lossydragon.bluetoothcomms

import android.content.Context
import android.view.Gravity
import android.widget.Toast

fun Context.toast(message: String) {
    val toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
    //yOffset set to be above the FAB icon.
    toast.setGravity(Gravity.BOTTOM, 0, 325)
    toast.show()
}