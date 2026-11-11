@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.invictus.xmd.ui.settings

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.invictus.xmd.ui.components.XmdAlertDialog
import com.invictus.xmd.ui.components.WideDialogProperties
import com.invictus.xmd.ui.components.wideDialogWidth
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invictus.xmd.R
import com.invictus.xmd.preferences.Profile
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Round profile picture: the saved photo if there is one, otherwise the first
 * letter of the profile name (or a person glyph while no name is set).
 */
@Composable
fun ProfileAvatar(size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = Profile.avatarVersion
    val bitmap by produceState<Bitmap?>(initialValue = null, version) {
        value = withContext(Dispatchers.IO) { Profile.loadAvatar(context) }
    }
    val name = Profile.name().trim()
    val current = bitmap

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (name.isNotEmpty()) {
            Text(
                text = name.first().uppercaseChar().toString(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.42f).sp,
            )
        } else {
            Icon(
                imageVector = Icons.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}

/** Profile page: photo + name on a plain surface; the edit badge opens [EditProfileDialog]. */
@Composable
fun SettingsProfileScreen() {
    var editing by remember { mutableStateOf(false) }
    val name = Profile.name().trim()

    if (editing) {
        EditProfileDialog(onDismiss = { editing = false })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.size(112.dp)) {
            ProfileAvatar(
                size = 112.dp,
                modifier = Modifier
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clip(CircleShape)
                    .clickable { editing = true },
            )
            // Sits on the avatar's rim (bottom-right); the outer Box is not
            // clipped, so the badge isn't cut off by the circle.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                    .clip(CircleShape)
                    .clickable { editing = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Edit,
                    contentDescription = stringResource(R.string.settings_profile_edit_title),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (name.isEmpty()) stringResource(R.string.settings_profile_setup_title) else name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        SettingsAccountSection()
    }
}

/**
 * Edit dialog: photo preview with Choose / Crop / Remove and the display name.
 * Nothing is written until Save; Cancel (or tapping outside) discards it all.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun EditProfileDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var nameText by remember { mutableStateOf(Profile.name()) }
    // Pending photo edits: [pendingAvatar] replaces the saved photo, [removed] hides it.
    var pendingAvatar by remember { mutableStateOf<Bitmap?>(null) }
    var removed by remember { mutableStateOf(false) }
    val saved by produceState<Bitmap?>(initialValue = null, Profile.avatarVersion) {
        value = withContext(Dispatchers.IO) { Profile.loadAvatar(context) }
    }
    val shown: Bitmap? = pendingAvatar ?: if (removed) null else saved

    var cropSource by remember { mutableStateOf<Bitmap?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val decoded = withContext(Dispatchers.IO) { Profile.decodeForCrop(context, uri) }
                if (decoded != null) {
                    cropSource = decoded
                } else {
                    Toast.makeText(context, R.string.settings_profile_photo_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    cropSource?.let { source ->
        ProfileCropDialog(
            sourceBitmap = source,
            onDismiss = { cropSource = null },
            onCropped = { cropped ->
                cropSource = null
                pendingAvatar = cropped
                removed = false
            },
        )
    }

    XmdAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.wideDialogWidth(),
        properties = WideDialogProperties,
        title = { Text(stringResource(R.string.settings_profile_edit_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = shown
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        val initial = nameText.trim().firstOrNull()?.uppercaseChar()?.toString()
                        if (initial != null) {
                            Text(
                                text = initial,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 34.sp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(44.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OutlinedButton(onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text(stringResource(R.string.settings_profile_choose_photo)) }
                    if (shown != null) {
                        OutlinedButton(onClick = { cropSource = shown }) {
                            Text(stringResource(R.string.settings_profile_crop_confirm))
                        }
                    }
                }
                if (shown != null) {
                    OutlinedButton(
                        onClick = {
                            pendingAvatar = null
                            removed = true
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.settings_profile_remove_photo)) }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it.take(40) },
                    label = { Text(stringResource(R.string.settings_profile_display_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        confirmButton = {
            Button(onClick = {
                Profile.setName(nameText.trim())
                val pending = pendingAvatar
                when {
                    pending != null -> scope.launch {
                        val ok = withContext(Dispatchers.IO) { Profile.saveAvatarBitmap(context, pending) }
                        if (!ok) {
                            Toast.makeText(context, R.string.settings_profile_photo_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                    removed -> Profile.clearAvatar(context)
                }
                onDismiss()
            }) { Text(stringResource(R.string.settings_save)) }
        },
    )
}
