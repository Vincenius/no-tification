package com.vincentwill.calm

import android.app.Application
import android.content.Context
import android.telecom.TelecomManager
import com.vincentwill.calm.ai.AiBackend
import com.vincentwill.calm.ai.AiClassifier
import com.vincentwill.calm.ai.AnthropicBackend
import com.vincentwill.calm.data.CalmDatabase
import com.vincentwill.calm.data.RulesRepository
import com.vincentwill.calm.data.ScheduleRepository
import com.vincentwill.calm.data.SecretStore
import com.vincentwill.calm.data.Settings
import com.vincentwill.calm.digest.DigestDelivery
import com.vincentwill.calm.digest.DigestScheduler
import com.vincentwill.calm.pipeline.NotificationPipeline
import com.vincentwill.calm.pipeline.PipelineEvents
import com.vincentwill.calm.silence.Channels
import com.vincentwill.calm.silence.DndController
import com.vincentwill.calm.silence.UrgentReposter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Hand-wired dependencies, shared by the listener service, receivers and the Flutter bridge. */
class CalmGraph private constructor(context: Context) {
    val app: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = Settings(app)
    val db = CalmDatabase.create(app)
    val events = PipelineEvents()
    val channels = Channels(app, settings)
    val dnd = DndController(app, settings)
    val reposter = UrgentReposter(app, channels)
    val rules = RulesRepository(db.rules())
    val scheduler: DigestScheduler = DigestScheduler(app, settings) { schedule }
    val schedule = ScheduleRepository(db.digestTimes(), settings) {
        scheduler.reschedule()
        events.changed()
    }
    val delivery = DigestDelivery(app, db.notifications(), channels)
    val secrets = SecretStore(app)
    val ai = AiClassifier(::aiBackend)
    val pipeline = NotificationPipeline(db.notifications(), rules, settings, events, ai, scope, ::isDialer)

    /** (api key, model) → client, rebuilt when either changes. */
    private var backend: Pair<Pair<String, String>, AnthropicBackend>? = null

    /** Null when AI is off or no key is stored: the pipeline then runs on rules only. */
    @Synchronized
    fun aiBackend(): AiBackend? {
        val key = secrets.apiKey
        if (!settings.aiEnabled || key == null) return null
        val config = key to settings.aiModel
        backend?.let { (cfg, client) -> if (cfg == config) return client else client.close() }
        return AnthropicBackend(key, config.second).also { backend = config to it }
    }

    private val telecom = app.getSystemService(TelecomManager::class.java)

    private fun isDialer(packageName: String) = telecom?.defaultDialerPackage == packageName

    /** Turns Calm on or off. Returns the resulting state (false if DND access is missing). */
    fun setEnabled(enabled: Boolean): Boolean {
        if (enabled) {
            channels.ensure()
            if (!dnd.activate()) return false
        } else {
            dnd.deactivate()
        }
        settings.enabled = enabled
        events.changed()
        return enabled
    }

    companion object {
        @Volatile
        private var instance: CalmGraph? = null

        fun get(context: Context): CalmGraph =
            instance ?: synchronized(this) { instance ?: CalmGraph(context).also { instance = it } }
    }
}

class CalmApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val graph = CalmGraph.get(this)
        graph.scope.launch {
            graph.schedule.seedDefaultsIfNeeded()
            graph.scheduler.reschedule()
        }
    }
}
