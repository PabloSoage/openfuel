package com.varuna.openfuel.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.varuna.openfuel.R
import com.varuna.openfuel.core.update.Releases
import com.varuna.openfuel.util.Intents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

private sealed interface Download {
    data object Idle : Download
    data class Running(val progress: Float) : Download
    /** Downloaded; [installers] are the apps that can open it, listed when there is more than one. */
    data class Ready(val file: File, val installers: List<AppUpdate.Installer>) : Download
    data object Failed : Download
}

/** The release notes, then download and install the APK for this device (or open the release page). */
@Composable
fun UpdateDialog(update: Releases.Update, updates: AppUpdate, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Download>(Download.Idle) }
    val running = state is Download.Running

    // One installer: straight to it. Several: the user picks, because under Advanced Protection
    // only a Shizuku-based one (Install With Options) can install at all.
    fun install(ready: Download.Ready) {
        when (ready.installers.size) {
            0 -> AppUpdate.installWithChooser(context, ready.file)
            1 -> AppUpdate.installWith(context, ready.file, ready.installers.single())
            else -> Unit
        }
    }

    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text(stringResource(R.string.update_available_title, update.title)) },
        text = {
            Column {
                Text(
                    plainNotes(update.body).ifBlank { update.tag },
                    style = MaterialTheme.typography.bodySmall,
                    // Shorter while the installers are listed, so they fit under it.
                    modifier = Modifier
                        .heightIn(max = if ((state as? Download.Ready)?.installers.orEmpty().size > 1) 180.dp else 320.dp)
                        .verticalScroll(rememberScrollState()),
                )
                when (val s = state) {
                    is Download.Running -> if (s.progress >= 0f) {
                        LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                    }
                    is Download.Ready -> if (s.installers.size > 1) {
                        Text(
                            stringResource(R.string.update_choose_installer),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                        s.installers.forEach { installer ->
                            OutlinedButton(
                                onClick = { AppUpdate.installWith(context, s.file, installer) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(installer.label) }
                        }
                        TextButton(onClick = { AppUpdate.installWithChooser(context, s.file) }) {
                            Text(stringResource(R.string.update_other_installer))
                        }
                    }
                    Download.Failed -> Text(
                        stringResource(R.string.update_download_failed),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    else -> Unit
                }
            }
        },
        confirmButton = {
            val apk = update.apk
            val choosing = (state as? Download.Ready)?.installers.orEmpty().size > 1
            if (choosing) {
                // The list above is the action.
            } else if (apk == null) {
                Button(onClick = { Intents.openUrl(context, update.htmlUrl) }) { Text(stringResource(R.string.update_open_github)) }
            } else {
                Button(
                    enabled = !running,
                    onClick = {
                        (state as? Download.Ready)?.let { install(it); return@Button }
                        state = Download.Running(-1f)
                        scope.launch {
                            state = try {
                                val file = updates.download(context, apk) { state = Download.Running(it) }
                                Download.Ready(file, AppUpdate.installers(context, file))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                Download.Failed
                            }
                            (state as? Download.Ready)?.let { install(it) }
                        }
                    },
                ) {
                    Text(
                        stringResource(
                            when (state) {
                                is Download.Ready -> R.string.update_install
                                Download.Failed -> R.string.action_retry
                                else -> R.string.update_download
                            },
                        ),
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !running) { Text(stringResource(R.string.update_later)) }
        },
    )
}

/** GitHub markdown as plain text: headings, bold, code and table rules without their marks. */
internal fun plainNotes(md: String): String =
    md.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        .lineSequence()
        .filterNot { it.trim().matches(Regex("""\|?\s*:?-{3,}.*""")) }
        .joinToString("\n") { line ->
            line.trimEnd()
                .replace(Regex("^#{1,6}\\s*"), "")
                .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
                .replace(Regex("`([^`]+)`"), "$1")
                .replace(Regex("""\[([^\]]+)]\([^)]+\)"""), "$1")
        }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
