package com.lossydragon.bluetoothcomms

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_settings.*

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        Log.d(TAG, "Opened...")

        val prefs = getSharedPreferences(PREFERENCES, 0)
        val auto = prefs.getInt(AUTO_CONNECT, 1)

        pref_switch_autoConnect.isChecked = auto != 0

        pref_switch_autoConnect.setOnCheckedChangeListener { _, isChecked ->
            val prefs2 = getSharedPreferences(PREFERENCES, 0).edit()

            if (isChecked)
                prefs2.putInt(AUTO_CONNECT, 1)
            else
                prefs2.putInt(AUTO_CONNECT, 0)

            prefs2.apply()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val TAG = "PreferenceActivity"
        const val PREFERENCES = "com.lossydragon.bluetoothcomms.preferences"
        const val AUTO_CONNECT = "auto_connect"
    }
}
