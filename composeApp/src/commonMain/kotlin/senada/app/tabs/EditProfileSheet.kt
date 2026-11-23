package senada.app.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import senada.app.UserProfileController
import senada.app.deleteProfilePhoto
import senada.app.minTouchTarget
import senada.app.rememberProfilePhotoPicker
import senada.app.saveProfilePhoto
import senada.app.shell.ProfileAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditProfileSheet(controller: UserProfileController, currentPhoto: Any?, onDismiss: () -> Unit) {
    val profile = remember(controller) { controller.state.value }
    val context = LocalPlatformContext.current
    val picker = rememberProfilePhotoPicker()
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(profile.name) }
    var newPhoto by remember { mutableStateOf<ByteArray?>(null) }
    var removePhoto by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val preview = newPhoto ?: currentPhoto.takeUnless { removePhoto }
    val sheet = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !busy },
    )

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Ubah profil", Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
            )
            ProfileAvatar(
                name, size = 84.dp, borderWidth = 0.dp, photoModel = preview,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Nama profil" },
                label = { Text("Nama") },
                singleLine = true,
                enabled = !busy,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                picker.pick()?.let { newPhoto = it }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                error = "Foto tidak dapat dibuka."
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.minTouchTarget(),
                ) { Text("Pilih foto") }
                if (preview != null) {
                    TextButton(
                        onClick = {
                            newPhoto = null
                            removePhoto = true
                            error = null
                        },
                        enabled = !busy,
                        modifier = Modifier.minTouchTarget(),
                    ) { Text("Hapus foto") }
                }
            }
            error?.let {
                Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val jpeg = newPhoto
                            val keepCurrent = !removePhoto
                            val updatedName = name
                            withContext(Dispatchers.IO + NonCancellable) {
                                val oldPath = profile.photoPath
                                val newPath = if (jpeg != null) saveProfilePhoto(context, jpeg)
                                    else oldPath.takeIf { keepCurrent }
                                try {
                                    controller.update(updatedName, newPath)
                                } catch (failure: Exception) {
                                    if (jpeg != null && newPath != null) runCatching { deleteProfilePhoto(context, newPath) }
                                    throw failure
                                }
                                if (oldPath != null && oldPath != newPath) runCatching { deleteProfilePhoto(context, oldPath) }
                            }
                            onDismiss()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            error = "Profil tidak dapat disimpan."
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().minTouchTarget(),
            ) { Text("Simpan") }
        }
    }
}
