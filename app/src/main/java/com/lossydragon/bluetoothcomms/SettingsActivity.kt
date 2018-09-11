package com.lossydragon.bluetoothcomms

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import butterknife.BindView
import butterknife.ButterKnife

class SettingsActivity : AppCompatActivity() {

    @BindView(R.id.pref_switch_autoConnect) lateinit var autoSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        ButterKnife.bind(this)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        Log.d(TAG, "Opened...")

        val prefs = getSharedPreferences(PREFERENCES, 0)
        val auto = prefs.getInt(AUTO_CONNECT, 1)

        autoSwitch.isChecked = auto != 0

        autoSwitch.setOnCheckedChangeListener { _, isChecked ->
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
