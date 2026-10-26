package com.vincentwill.calm.data

import android.content.Context
import androidx.core.content.edit
import com.vincentwill.calm.digest.ScheduleMode

/** Non-secret app state. Secrets (the API key) get their own encrypted store in milestone 3. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("calm_settings", Context.MODE_PRIVATE)

    /** Master switch: when off, Calm doesn't touch notifications at all. */
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit { putBoolean("enabled", value) }

    var scheduleMode: ScheduleMode
        get() = prefs.getString("schedule_mode", null)
            ?.let { runCatching { ScheduleMode.valueOf(it) }.getOrNull() } ?: ScheduleMode.EVERY_DAY
        set(value) = prefs.edit { putString("schedule_mode", value.name) }

    /** Epoch millis of the scheduled alarm, or null if no digest is scheduled. */
    var nextDigestAt: Long?
        get() = prefs.getLong("next_digest_at", -1).takeIf { it >= 0 }
        set(value) = prefs.edit { putLong("next_digest_at", value ?: -1) }

    var nextDigestExact: Boolean
        get() = prefs.getBoolean("next_digest_exact", true)
        set(value) = prefs.edit { putBoolean("next_digest_exact", value) }

    /** AI classification for the gray zone; only active when an API key is stored too. */
    var aiEnabled: Boolean
        get() = prefs.getBoolean("ai_enabled", true)
        set(value) = prefs.edit { putBoolean("ai_enabled", value) }

    var aiModel: String
        get() = prefs.getString("ai_model", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_AI_MODEL
        set(value) = prefs.edit { putString("ai_model", value.trim().ifEmpty { DEFAULT_AI_MODEL }) }

    var defaultsSeeded: Boolean
        get() = prefs.getBoolean("defaults_seeded", false)
        set(value) = prefs.edit { putBoolean("defaults_seeded", value) }

    /** Id of our Do Not Disturb mode (Android 15+). */
    var zenRuleId: String?
        get() = prefs.getString("zen_rule_id", null)
        set(value) = prefs.edit { putString("zen_rule_id", value) }

    /** The user's DND state before we turned it on (Android 14 and older), as "filter|cat|calls|msgs|effects". */
    var savedDndState: String?
        get() = prefs.getString("saved_dnd_state", null)
        set(value) = prefs.edit { putString("saved_dnd_state", value) }

    /**
     * Channels can't gain DND bypass after creation, so we create them anew (with a new id) the
     * first time DND access is available.
     */
    var channelGeneration: Int
        get() = prefs.getInt("channel_generation", 1)
        set(value) = prefs.edit { putInt("channel_generation", value) }

    var channelsCreatedWithDndAccess: Boolean
        get() = prefs.getBoolean("channels_with_dnd", false)
        set(value) = prefs.edit { putBoolean("channels_with_dnd", value) }
}

const val DEFAULT_AI_MODEL = "claude-haiku-5-5"
