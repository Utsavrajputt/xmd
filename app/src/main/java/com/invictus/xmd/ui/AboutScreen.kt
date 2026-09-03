package com.invictus.xmd.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 16.dp),
        ) {
            val context = LocalContext.current
            val appIconBitmap = remember {
                context.packageManager.getApplicationIcon(context.packageName)
                    .toBitmap()
                    .asImageBitmap()
            }
            Image(
                bitmap = appIconBitmap,
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(80.dp),
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = stringResource(R.string.about_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = versionText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        // ===== GitHub link =====
        SettingsSectionCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onGithubClick)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Icon(
                    imageVector = Icons.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(R.string.about_github),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
                )
                Icon(
                    imageVector = Icons.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // ===== License =====
        Spacer(Modifier.height(8.dp))
        SettingsSectionHeader(title = stringResource(R.string.about_license_title))

        SettingsSectionCard(contentPadding = PaddingValues(16.dp)) {
            Text(
                text = stringResource(R.string.about_license_body),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ===== Developers =====
        Spacer(Modifier.height(8.dp))
        SettingsSectionHeader(title = stringResource(R.string.about_developers_title))

        SettingsSectionCard {
            developers.forEachIndexed { index, (name, role) ->
                AboutCreditRow(name, role)
                if (index != developers.lastIndex) SettingsDivider()
            }
        }

        // ===== Credits =====
        Spacer(Modifier.height(8.dp))
        SettingsSectionHeader(title = stringResource(R.string.about_credits_title))

        SettingsSectionCard {
            credits.forEachIndexed { index, (name, desc) ->
                AboutCreditRow(name, desc)
                if (index != credits.lastIndex) SettingsDivider()
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.about_made_by),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun AboutCreditRow(name: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
