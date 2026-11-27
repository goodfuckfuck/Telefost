package com.telefost.messenger.ui.auth

import android.app.Activity
import android.os.Bundle

class TelefostAuthActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthScreen.attach(this)
    }
}
