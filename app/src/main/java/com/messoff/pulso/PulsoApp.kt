package com.messoff.pulso

import android.app.Application
import com.messoff.pulso.data.Api
import com.messoff.pulso.data.LocalStore
import com.messoff.pulso.data.Session
import com.messoff.pulso.focus.FocusAlarm
import com.messoff.pulso.haptics.Haptics
import com.messoff.pulso.reminders.Reminders

class PulsoApp : Application() {
    lateinit var session: Session
        private set
    lateinit var api: Api
        private set
    lateinit var haptics: Haptics
        private set
    lateinit var store: LocalStore
        private set

    override fun onCreate() {
        super.onCreate()
        session = Session(this)
        api = Api(session)
        haptics = Haptics(this)
        store = LocalStore(this)
        FocusAlarm.createChannel(this)
        Reminders.createChannel(this)
    }
}
