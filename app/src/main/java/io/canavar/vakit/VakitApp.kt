package io.canavar.vakit

import android.app.Application

class VakitApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
