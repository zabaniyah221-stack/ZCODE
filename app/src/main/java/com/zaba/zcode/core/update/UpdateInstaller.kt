package com.zaba.zcode.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.zaba.zcode.core.diagnostics.Breadcrumb
import java.io.File

/**
 * UpdateInstaller — v1.0.22, luncurkan installer system (RFC D5.4/D5.5).
 *
 * Pembagian peran (jujur, RFC D1): di sini app hanya (a) memastikan izin
 * `REQUEST_INSTALL_PACKAGES` (satu panduan sekali, D5.4) dan (b) memberi
 * APK ke installer Android via FileProvider content://. Setelah intent
 * diluncurkan, **Android yang pegang**: user melihat dialog system sendiri
 * dan memilih install/batal. Identitas signer dijaga Android di sini (signer
 * beda = ditolak; versionCode turun = INSTALL_FAILED_DOWNGRADE) — app tidak
 * dan tidak bisa memverifikasi signer di HP (tidak ada toolchain).
 *
 * FileProvider (docs resmi): `getUriForFile(context, authority, file)`;
 * `<cache-path>` di res/xml/file_paths.xml; authority
 * `${applicationId}.fileprovider` — satu sumber kebenaran di [authority].
 * Intent: ACTION_VIEW + setDataAndType(MIME .apk) + FLAG_GRANT_READ_URI_
 * PERMISSION (wajib untuk content:// lintas proses) + FLAG_ACTIVITY_NEW_TASK.
 */
object UpdateInstaller {

    const val APK_MIME = "application/vnd.android.package-archive"

    fun authority(context: Context): String = "${context.packageName}.fileprovider"

    /** API 26+ — minSdk kita 26, jadi TIDAK ada jalur fallback (docs resmi). */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /**
     * Pandu user ke pengaturan per-app "Install unknown apps" (Oreo+,
     * ACTION_MANAGE_UNKNOWN_APP_SOURCES dengan Uri package: — bukan halaman
     * global). Dipanggil HANYA saat izin belum ada (sekali seumur hidup app
     * bila user mengizinkan).
     */
    fun openUnknownSourcesSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            Breadcrumb.log("UPDATE_INSTALL_LAUNCH", "panduan izin REQUEST_INSTALL_PACKAGES")
        } catch (e: Exception) {
            Breadcrumb.log("UPDATE_INSTALL_LAUNCH", "gagal buka pengaturan: ${e.message}")
        }
    }

    /**
     * Luncurkan dialog install system. Prasyarat: [canInstall] true (caller
     * memastikan — fail-closed: tanpa izin, installer tidak diluncurkan).
     * Setelah ini app menunggu restart; hasil diproses [UpdateReceipt]
     * (INSTALLED / PENDING) di start-up berikutnya.
     */
    fun launchInstall(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(context, authority(context), apkFile)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        Breadcrumb.log("UPDATE_INSTALL_LAUNCH", "${apkFile.name} → dialog system")
        context.startActivity(intent)
    }
}
