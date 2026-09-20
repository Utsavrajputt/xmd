package com.invictus.xmd.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.invictus.xmd.ui.icons.AppIcon
import com.invictus.xmd.ui.icons.Icon
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invictus.xmd.R
import com.invictus.xmd.ui.icons.Icons

@Composable
fun OnboardingScreen(
    hasStoragePermission: Boolean,
    defaultLocationPath: String,
    hasNotificationPermission: Boolean,
    batteryOptimizationDisabled: Boolean,
    onGrantStoragePermission: () -> Unit,
    onChangeDefaultLocation: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onDisableBatteryOptimization: () -> Unit,
    onFinishOnboarding: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
        ) {
            // Top Step Indicator (3 steps: Storage, Notifications, Battery)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepDot(active = step == 0, completed = hasStoragePermission)
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(2.dp)
                        .background(
                            if (step > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
                Spacer(Modifier.width(8.dp))
                StepDot(active = step == 1, completed = hasNotificationPermission)
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(2.dp)
                        .background(
                            if (step > 1) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
                Spacer(Modifier.width(8.dp))
                StepDot(active = step == 2, completed = batteryOptimizationDisabled)
            }

            Spacer(Modifier.height(16.dp))

            // Animated Step Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                        } else {
                            (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                        }
                    },
                    label = "OnboardingStepTransition",
                ) { currentStep ->
                    when (currentStep) {
                        0 -> StorageStepContent(
                            hasPermission = hasStoragePermission,
                            defaultLocationPath = defaultLocationPath,
                            onGrantPermission = onGrantStoragePermission,
                            onChangeLocation = onChangeDefaultLocation,
                        )
                        1 -> NotificationStepContent(
                            hasPermission = hasNotificationPermission,
                            onRequestPermission = onRequestNotificationPermission,
                        )
                        2 -> BatteryStepContent(
                            batteryDisabled = batteryOptimizationDisabled,
                            onDisableBattery = onDisableBatteryOptimization,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Bottom Navigation Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step > 0) {
                    TextButton(
                        onClick = { step -= 1 },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.onboarding_back))
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }

                when (step) {
                    0 -> {
                        Column(horizontalAlignment = Alignment.End) {
                            Button(
                                onClick = { step = 1 },
                                enabled = hasStoragePermission,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                                modifier = Modifier.height(48.dp),
                            ) {
                                Text(
                                    stringResource(R.string.onboarding_next),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            if (!hasStoragePermission) {
                                Text(
                                    text = stringResource(R.string.onboarding_permission_required_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                    1 -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!hasNotificationPermission) {
                                OutlinedButton(
                                    onClick = { step = 2 },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(48.dp),
                                ) {
                                    Text(stringResource(R.string.onboarding_skip))
                                }
                            }
                            Button(
                                onClick = { step = 2 },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                                modifier = Modifier.height(48.dp),
                            ) {
                                Text(
                                    stringResource(R.string.onboarding_next),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    2 -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!batteryOptimizationDisabled) {
                                OutlinedButton(
                                    onClick = onFinishOnboarding,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(48.dp),
                                ) {
                                    Text(stringResource(R.string.onboarding_skip))
                                }
                            }
                            Button(
                                onClick = onFinishOnboarding,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                                modifier = Modifier.height(48.dp),
                            ) {
                                Text(
                                    stringResource(R.string.onboarding_get_started),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepDot(active: Boolean, completed: Boolean) {
    val backgroundColor = when {
        completed -> Color(0xFF2E7D32)
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        if (completed) {
            Icon(
                imageVector = Icons.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(10.dp),
            )
        }
    }
}

@Composable
private fun StepHeader(
    icon: AppIcon,
    title: String,
    description: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(20.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun StorageStepContent(
    hasPermission: Boolean,
    defaultLocationPath: String,
    onGrantPermission: () -> Unit,
    onChangeLocation: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StepHeader(
            icon = Icons.Folder,
            title = stringResource(R.string.onboarding_title_storage),
            description = stringResource(R.string.onboarding_desc_storage),
        )

        Spacer(Modifier.height(28.dp))

        // Permission Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (hasPermission) Color(0xFF2E7D32).copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (hasPermission) Icons.Check else Icons.Shield,
                        contentDescription = null,
                        tint = if (hasPermission) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (hasPermission) stringResource(R.string.onboarding_permission_granted)
                        else stringResource(R.string.storage_permission_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (hasPermission) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (!hasPermission) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onGrantPermission,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(stringResource(R.string.onboarding_grant_permission))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Default Path Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_default_path_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = defaultLocationPath,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onChangeLocation,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.onboarding_change_path))
                }
            }
        }
    }
}

@Composable
private fun NotificationStepContent(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StepHeader(
            icon = Icons.Notifications,
            title = stringResource(R.string.onboarding_title_notification),
            description = stringResource(R.string.onboarding_desc_notification),
        )

        Spacer(Modifier.height(28.dp))

        // Notification Permission Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (hasPermission) Color(0xFF2E7D32).copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (hasPermission) Icons.Check else Icons.Notifications,
                        contentDescription = null,
                        tint = if (hasPermission) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (hasPermission) stringResource(R.string.onboarding_notification_granted)
                        else stringResource(R.string.onboarding_grant_notification),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (hasPermission) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_notification_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!hasPermission) {
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onRequestPermission,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(stringResource(R.string.onboarding_grant_notification))
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryStepContent(
    batteryDisabled: Boolean,
    onDisableBattery: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StepHeader(
            icon = Icons.Shield,
            title = stringResource(R.string.onboarding_title_battery),
            description = stringResource(R.string.onboarding_desc_battery),
        )

        Spacer(Modifier.height(28.dp))

        // Battery Optimization Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (batteryDisabled) Color(0xFF2E7D32).copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (batteryDisabled) Icons.Check else Icons.Info,
                        contentDescription = null,
                        tint = if (batteryDisabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (batteryDisabled) stringResource(R.string.onboarding_battery_disabled)
                        else stringResource(R.string.settings_battery_optimization),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (batteryDisabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_battery_optimization_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!batteryDisabled) {
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onDisableBattery,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(stringResource(R.string.onboarding_disable_battery))
                    }
                }
            }
        }
    }
}
