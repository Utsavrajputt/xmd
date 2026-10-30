@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.invictus.xmd.ui.settings

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/** Profile page: photo (pick / remove) and display name. Saves as you type. */
@Composable
fun SettingsProfileScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasAvatar = remember(Profile.avatarVersion) { Profile.hasAvatar(context) }
    var nameText by remember { mutableStateOf(Profile.name()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { Profile.saveAvatar(context, uri) }
                if (!ok) {
                    Toast.makeText(context, R.string.settings_profile_photo_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val pickPhoto = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.clip(CircleShape).clickable { pickPhoto() }) {
            ProfileAvatar(size = 112.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(34.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Edit,
                    contentDescription = stringResource(R.string.settings_profile_change_photo),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.padding(top = 8.dp)) {
            TextButton(onClick = { pickPhoto() }) {
                Text(stringResource(R.string.settings_profile_change_photo))
            }
            if (hasAvatar) {
                TextButton(onClick = { Profile.clearAvatar(context) }) {
                    Text(stringResource(R.string.settings_profile_remove_photo))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        SettingsSectionCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            OutlinedTextField(
                value = nameText,
                onValueChange = {
                    nameText = it.take(40)
                    Profile.setName(nameText)
                },
                label = { Text(stringResource(R.string.settings_profile_name)) },
                supportingText = { Text(stringResource(R.string.settings_profile_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
