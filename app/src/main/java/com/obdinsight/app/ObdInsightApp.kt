package com.obdinsight.app

import android.app.Application
import com.obdinsight.app.ai.SecurePrefs
import com.obdinsight.app.core.DiagnosticsController
import com.obdinsight.app.data.AppDatabase
import com.obdinsight.app.data.Repository

/** Simple manual DI root - no framework needed for an app this size. */
class ObdInsightApp : Application() {

    lateinit var repository: Repository
        private set
    lateinit var securePrefs: SecurePrefs
        private set
    lateinit var controller: DiagnosticsController
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(AppDatabase.get(this))
        securePrefs = SecurePrefs(this)
        controller = DiagnosticsController(this, repository, securePrefs)
    }
}
