package com.invictus.xmd.ui.settings

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.invictus.xmd.R

/** A developer credit -- [name] shown as the row title, [githubId] shown as
 * subtext and used to open `github.com/<githubId>` when the row is tapped. */
data class AboutDeveloper(val name: String, val githubId: String)

/**
 * App identity, version, GitHub link, license notice, developer credits,
 * and the open-source libraries Xmd is built on. Rendered directly by
 * SettingsActivity's AboutRoute (NavHost route body) -- no Fragment host.
 *
 * Redesigned with mpvRx's About screen as the visual reference: an animated
 * gradient hero card for identity, pill-badge version tag, a pair of
 * action buttons, and avatar-style rows for developer credits -- adapted
 * to Xmd's own content rather than copied wholesale (no update/donation
 * sections, since Xmd doesn't have those flows).
 */
@Composable
fun AboutScreen(
    versionText: String,
    onGithubClick: () -> Unit,
    onShareClick: () -> Unit,
    developers: List<AboutDeveloper>,
    credits: List<Pair<String, String>>,
    onDeveloperClick: (AboutDeveloper) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // ===== Hero identity card =====
        val cs = MaterialTheme.colorScheme
        val transition = rememberInfiniteTransition()
        val fraction by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 5000),
                repeatMode = RepeatMode.Reverse,
            ),
        )
        val heroCornerRadius = 28.dp

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawWithCache {
                    val cx = size.width - size.width * fraction
                    val cy = size.height * fraction
                    val gradient = Brush.radialGradient(
                        colors = listOf(cs.primaryContainer, cs.tertiaryContainer),
                        center = Offset(cx, cy),
                        radius = 800f,
                    )
                    onDrawBehind {
                        drawRoundRect(
                            brush = gradient,
                            cornerRadius = CornerRadius(heroCornerRadius.toPx(), heroCornerRadius.toPx()),
                        )
                    }
                }
                .padding(20.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val context = LocalContext.current
                    val appIconBitmap = remember {
                        context.packageManager.getApplicationIcon(context.packageName)
                            .toBitmap()
                            .asImageBitmap()
                    }
                    Image(
                        bitmap = appIconBitmap,
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.size(64.dp),
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = cs.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.about_tagline),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onPrimaryContainer.copy(alpha = 0.85f),
                        )
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = cs.primary.copy(alpha = 0.16f),
                        ) {
                            Text(
                                text = versionText,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.onPrimaryContainer,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val btnContainer = cs.primary
                    val btnContent = cs.onPrimary
                    Button(
                        onClick = onGithubClick,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = btnContainer,
                            contentColor = btnContent,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Code,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.about_github),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }

                    Button(
                        onClick = onShareClick,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = btnContainer,
                            contentColor = btnContent,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Share,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.about_share),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        // ===== License =====
        Spacer(Modifier.height(8.dp))
        SettingsSectionHeader(title = stringResource(R.string.about_license_title))

        SettingsSectionCard(contentPadding = PaddingValues(16.dp)) {
            Row {
                Icon(
                    imageVector = Icons.Article,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp).padding(top = 2.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "AGPL-3.0",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.about_license_body),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ===== Developers =====
        Spacer(Modifier.height(8.dp))
        SettingsSectionHeader(title = stringResource(R.string.about_developers_title))

        SettingsSectionCard {
            developers.forEachIndexed { index, developer ->
                DeveloperRow(
                    developer = developer,
                    onClick = { onDeveloperClick(developer) },
                )
                if (index != developers.lastIndex) SettingsDivider()
            }
        }

        // ===== Credits =====
        Spacer(Modifier.height(8.dp))
        SettingsSectionHeader(title = stringResource(R.string.about_credits_title))
        Text(
            text = stringResource(R.string.about_credits_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            credits.forEach { (name, desc) ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.about_made_by),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun DeveloperRow(developer: AboutDeveloper, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = developer.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "@${developer.githubId}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            imageVector = Icons.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp),
        )
    }
}
