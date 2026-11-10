package app.dosis.android

import android.app.Application

class DosisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
