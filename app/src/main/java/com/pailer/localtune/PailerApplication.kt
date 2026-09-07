package com.pailer.localtune

import android.app.Application
import com.pailer.localtune.data.AppFolderRepository
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Relatorio de crash em arquivo de texto (pedido do usuario 07/09/2026, parte da pasta oficial
// do Pailer FM - ver AppFolderRepository) - so grava se o usuario ja configurou a pasta oficial;
// sem pasta configurada, o app se comporta exatamente como antes (sem custo/permissao extra).
// SEMPRE encadeia pro handler padrao anterior no final (defaultHandler?.uncaughtException(...))
// - isso so ACRESCENTA o arquivo de log, nunca substitui o comportamento normal de crash do
// Android (encerrar o processo/mostrar "app parou").
class PailerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrashReport(throwable) }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashReport(throwable: Throwable) {
        val appFolder = AppFolderRepository(this)
        if (!appFolder.hasFolder()) return
        val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale("pt", "BR")).format(Date())
        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "?"
        val content = "Pailer FM - relatorio de crash\n" +
            "Data: ${SimpleDateFormat("dd/MM/yyyy 'as' HH:mm:ss", Locale("pt", "BR")).format(Date())}\n" +
            "Versao: $versionName\n\n" +
            stackTrace
        appFolder.writeToSubfolder(
            AppFolderRepository.SUBFOLDER_LOGS,
            "crash_$timestamp.txt",
            "text/plain",
            content.toByteArray(Charsets.UTF_8),
        )
    }
}
