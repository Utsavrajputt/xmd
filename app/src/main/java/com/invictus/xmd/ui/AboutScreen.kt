package com.invictus.xmd.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.invictus.xmd.R

/**
 * App identity, version, GitHub link, license notice, developer credits,
 * and the open-source libraries Xmd is built on. Rendered directly by
 * SettingsActivity's AboutRoute (NavHost route body) -- no Fragment host.
 */
@Composable
fun AboutScreen(
    versionText: String,
    onGithubClick: () -> Unit,
    developers: List<Pair<String, String>>,
    credits: List<Pair<String, String>>,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // ===== App identity =====
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp),
        ) {
            val context = LocalContext.current
            val appIconBitmap = remember {
                // painterResource() can't load an <adaptive-icon> XML (R.mipmap.xmd
                // on API 26+), only VectorDrawables and rasterized assets -- it throws
                // IllegalArgumentException at composition time. Pull the launcher icon
                // via PackageManager instead, which correctly flattens the adaptive
                // icon (background + foreground) into one drawable, then convert that
                // to an ImageBitmap for Compose.
                context.packageManager.getApplicationIcon(context.packageName)
                    .toBitmap()
                    .asImageBitmap()
            }
            Image(
                bitmap = appIconBitmap,
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(88.dp),
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                text = stringResource(R.string.about_tagline),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = versionText,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        // ===== GitHub link =====
        SettingsSectionCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onGithubClick),
            ) {
                Icon(
                    painter = painterResource(XmdIcons.Code),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = stringResource(R.string.about_github),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).padding(start = 14.dp),
                )
                Icon(
                    painter = painterResource(XmdIcons.ArrowForward),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // ===== License =====
        Text(
            text = stringResource(R.string.about_license_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 20.dp),
        )
        Column(modifier = Modifier.padding(top = 8.dp)) {
            SettingsSectionCard {
                Text(
                    text = stringResource(R.string.about_license_body),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ===== Developers =====
        Text(
            text = stringResource(R.string.about_developers_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 20.dp),
        )
        Column(modifier = Modifier.padding(top = 8.dp)) {
            SettingsSectionCard {
                developers.forEachIndexed { index, (name, role) ->
                    AboutCreditRow(name, role)
                    if (index != developers.lastIndex) SettingsDivider()
                }
            }
        }

        // ===== Credits =====
        Text(
            text = stringResource(R.string.about_credits_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = stringResource(R.string.about_credits_hint),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Column(modifier = Modifier.padding(top = 10.dp)) {
            SettingsSectionCard {
                credits.forEachIndexed { index, (name, desc) ->
                    AboutCreditRow(name, desc)
                    if (index != credits.lastIndex) SettingsDivider()
                }
            }
        }

        Text(
            text = stringResource(R.string.about_made_by),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        )
    }
}

@Composable
private fun AboutCreditRow(name: String, description: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = name,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = description,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}
