package com.seyit474.tmvpn.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.seyit474.tmvpn.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.util.concurrent.TimeUnit

object UpdateManager {

    // Checks all releases including prereleases, sorted newest first
    private const val API_URL =
        "https://api.github.com/repos/seyit474/tmvpn/releases?per_page=5"

    data class UpdateInfo(val buildNum: Int, val downloadUrl: String, val releaseName: String)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun checkUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val resp = client.newCall(
                Request.Builder().url(API_URL)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
            ).execute()
            if (!resp.isSuccessful) return@withContext null
            val releases = JSONArray(resp.body!!.string())
            for (i in 0 until releases.length()) {
                val rel = releases.getJSONObject(i)
                val assets = rel.getJSONArray("assets")
                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    val name = asset.getString("name")
                    // filename format: TmVpn-{buildNum}.apk
                    val num = Regex("TmVpn-(\\d+)\\.apk").find(name)
                        ?.groupValues?.get(1)?.toIntOrNull() ?: continue
                    if (num > BuildConfig.VERSION_CODE) {
                        return@withContext UpdateInfo(
                            num,
                            asset.getString("browser_download_url"),
                            rel.getString("name"),
                        )
                    }
                }
            }
            null
        }.getOrNull()
    }

    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").also { it.mkdirs() }
            val file = File(dir, "TmVpn-update.apk")
            val resp = client.newCall(Request.Builder().url(url).build()).execute()
            val body = resp.body ?: return@withContext null
            val total = body.contentLength()
            var downloaded = 0L
            file.outputStream().use { out ->
                body.byteStream().use { inp ->
                    val buf = ByteArray(8192)
                    var n: Int
                    while (inp.read(buf).also { n = it } >= 0) {
                        out.write(buf, 0, n)
                        downloaded += n
                        if (total > 0) onProgress((downloaded * 100 / total).toInt())
                    }
                }
            }
            file
        }.getOrNull()
    }

    fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apkFile
        )
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
