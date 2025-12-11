package com.fredhappyface.fhcode

import android.content.Intent
import android.os.Bundle
import android.widget.Button

class ActivityStart : ActivityThemable() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        findViewById<Button>(R.id.btn_new_file).setOnClickListener {
            startActivity(Intent(this, ActivityMain::class.java))
        }

        findViewById<Button>(R.id.btn_open_file).setOnClickListener {
            val intent = Intent(this, ActivityMain::class.java)
            intent.putExtra("action", "open")
            startActivity(intent)
        }

        findViewById<Button>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, ActivitySettings::class.java))
        }

        findViewById<Button>(R.id.btn_about).setOnClickListener {
            startActivity(Intent(this, ActivityAbout::class.java))
        }
    }
}
