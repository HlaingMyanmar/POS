package com.sspd.servicemgmt.core.util

import android.content.Context
import org.json.JSONObject

/** Local drafts keyed by staff + server + job so unsaved notes survive process death. */
class JobFormDraftStore(context: Context) {
    private val prefs = context.getSharedPreferences("technician_job_drafts", Context.MODE_PRIVATE)

    fun load(staffId: Int, serverUrl: String, jobId: Int, form: String): Map<String, String> {
        val raw = prefs.getString(key(staffId, serverUrl, jobId, form), null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { k -> put(k, json.optString(k, "")) }
            }
        }.getOrDefault(emptyMap())
    }

    fun save(staffId: Int, serverUrl: String, jobId: Int, form: String, fields: Map<String, String>) {
        val meaningful = fields.filterValues { it.isNotBlank() }
        if (meaningful.isEmpty()) {
            clear(staffId, serverUrl, jobId, form)
            return
        }
        val json = JSONObject()
        meaningful.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString(key(staffId, serverUrl, jobId, form), json.toString()).apply()
    }

    fun clear(staffId: Int, serverUrl: String, jobId: Int, form: String) {
        prefs.edit().remove(key(staffId, serverUrl, jobId, form)).apply()
    }

    fun hasDraft(staffId: Int, serverUrl: String, jobId: Int, form: String): Boolean =
        load(staffId, serverUrl, jobId, form).isNotEmpty()

    private fun key(staffId: Int, serverUrl: String, jobId: Int, form: String): String =
        listOf(staffId.toString(), serverUrl.trim().lowercase(), jobId.toString(), form).joinToString("|")
}
