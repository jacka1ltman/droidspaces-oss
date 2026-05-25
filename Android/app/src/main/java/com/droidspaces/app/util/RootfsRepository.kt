package com.droidspaces.app.util

import android.content.Context
import com.droidspaces.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RootfsAsset(
    val name: String,
    val file: String,
    val description: String,
    val architecture: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val buildDate: String
)

sealed class RepoResult {
    data class Success(val assets: List<RootfsAsset>) : RepoResult()
    data class Error(val message: String) : RepoResult()
}

object RootfsRepository {

    private const val ROOTFS_JSON_URL =
        "https://github.com/Droidspaces/Droidspaces-rootfs-builder/raw/refs/heads/main/rootfs.json"
    private const val CONNECT_TIMEOUT = 10_000
    private const val READ_TIMEOUT    = 15_000

    suspend fun fetchAssets(context: Context): RepoResult = withContext(Dispatchers.IO) {
        runCatching {
            val rootfsJson = httpGet(ROOTFS_JSON_URL)
                ?: return@runCatching RepoResult.Error(
                    context.getString(R.string.repo_error_network)
                )

            val assets = parseRootfsJson(rootfsJson)
            if (assets.isEmpty()) {
                RepoResult.Error(context.getString(R.string.repo_error_no_assets))
            } else {
                RepoResult.Success(assets)
            }
        }.getOrElse { e ->
            RepoResult.Error(e.message ?: context.getString(R.string.repo_error_network))
        }
    }

    private fun httpGet(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT
            readTimeout    = READ_TIMEOUT
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        if (conn.responseCode != 200) return null
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return body
    }

    private fun parseRootfsJson(json: String): List<RootfsAsset> {
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val obj         = arr.getJSONObject(i)
                val downloadUrl = obj.optString("download_url", "")
                add(
                    RootfsAsset(
                        name         = obj.optString("name", ""),
                        file         = obj.optString("file", ""),
                        description  = obj.optString("description", ""),
                        architecture = obj.optString("architecture", ""),
                        downloadUrl  = downloadUrl,
                        sizeBytes    = obj.optLong("size_bytes", 0L),
                        buildDate    = obj.optString("build_date", "")
                    )
                )
            }
        }
    }
}
