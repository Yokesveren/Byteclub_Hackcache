package app.sevasetu.patient.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sevasetu.patient.ui.theme.ColorEmergency
import app.sevasetu.patient.ui.theme.LocalExtraColors
import app.sevasetu.patient.ui.theme.SevaTheme

@Composable
fun SevaScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    onListen: (() -> Unit)? = null,
    onEmergencyClick: () -> Unit,
    isOffline: Boolean = false,
    lastUpdatedTime: String? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val extraColors = LocalExtraColors.current

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                border = if (extraColors.isHighContrast) BorderStroke(2.dp, Color.Black) else null,
                modifier = Modifier.padding(top = 28.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Go back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )

                        if (onListen != null) {
                            ListenButton(onClick = onListen)
                        }
                    }

                    if (isOffline) {
                        OfflineBanner(lastUpdatedTime = lastUpdatedTime)
                    }
                }
            }
        },
        bottomBar = {
            EmergencyBar(onClick = onEmergencyClick)
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            content(innerPadding)
        }
    }
}

@Composable
fun EmergencyBar(onClick: () -> Unit) {
    val extraColors = LocalExtraColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable { onClick() },
        color = extraColors.emergencyBar,
        border = if (extraColors.isHighContrast) BorderStroke(2.dp, Color.Black) else null
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "EMERGENCY / आपातकालीन",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }
    }
}

enum class ButtonType { PRIMARY, SECONDARY, DANGER }

@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    type: ButtonType = ButtonType.PRIMARY,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val extraColors = LocalExtraColors.current
    val borderStroke = if (extraColors.isHighContrast) BorderStroke(2.dp, Color.Black) else null

    when (type) {
        ButtonType.PRIMARY -> {
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                border = borderStroke
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text(text = text, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
        ButtonType.SECONDARY -> {
            OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                modifier = modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp),
                border = borderStroke ?: BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text(text = text, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
        ButtonType.DANGER -> {
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ColorEmergency,
                    contentColor = Color.White
                ),
                border = borderStroke
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text(text = text, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
fun PictureCard(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extraColors = LocalExtraColors.current
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val borderWidth = if (isSelected || extraColors.isHighContrast) 2.dp else 1.dp

    Card(
        modifier = modifier
            .padding(6.dp)
            .clickable { onSelect() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(borderWidth, if (extraColors.isHighContrast) Color.Black else borderColor)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            if (isSelected) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Selected",
                        style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

@Composable
fun StepHeader(
    currentStep: Int,
    totalSteps: Int,
    question: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Step $currentStep of $totalSteps",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        )
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { currentStep.toFloat() / totalSteps },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = question,
            style = MaterialTheme.typography.headlineLarge
        )
    }
}

data class StatusStyle(
    val containerColor: Color,
    val contentColor: Color,
    val icon: ImageVector
)

@Composable
fun StatusPill(
    label: String,
    type: String, // routine, priority, emergency, waiting, called, done
    modifier: Modifier = Modifier
) {
    val extraColors = LocalExtraColors.current
    val style = when (type.lowercase()) {
        "routine", "green" -> StatusStyle(extraColors.routineContainer, extraColors.routineContent, Icons.Default.CheckCircle)
        "priority", "yellow" -> StatusStyle(extraColors.priorityContainer, extraColors.priorityContent, Icons.Default.Notifications)
        "emergency", "red" -> StatusStyle(extraColors.emergencyContainer, extraColors.emergencyContent, Icons.Default.Warning)
        "called" -> StatusStyle(extraColors.routineContainer, extraColors.routineContent, Icons.Default.Notifications)
        else -> StatusStyle(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, Icons.Default.Info)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = style.containerColor,
        border = BorderStroke(if (extraColors.isHighContrast) 2.dp else 1.dp, if (extraColors.isHighContrast) Color.Black else style.contentColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = style.icon, contentDescription = null, tint = style.contentColor, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = style.contentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            )
        }
    }
}

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val extraColors = LocalExtraColors.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            if (extraColors.isHighContrast) 2.dp else 1.dp,
            if (extraColors.isHighContrast) Color.Black else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

@Composable
fun ListenButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.semantics { contentDescription = "Read screen aloud" }
    ) {
        Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = "Listen", fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LastUpdatedText(time: String) {
    Text(
        text = "Last updated at $time",
        style = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
    )
}

@Composable
fun OfflineBanner(lastUpdatedTime: String? = null) {
    val extraColors = LocalExtraColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = extraColors.offlineContainer,
        border = if (extraColors.isHighContrast) BorderStroke(1.dp, Color.Black) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = extraColors.offlineContent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "No internet. Showing saved info${if (lastUpdatedTime != null) " (Updated $lastUpdatedTime)" else ""}.",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = extraColors.offlineContent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun LoadingState(message: String = "Loading...") {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(24.dp))
        BigButton(
            text = "Try again",
            onClick = onRetry,
            type = ButtonType.PRIMARY,
            icon = Icons.Default.Refresh
        )
    }
}

// Compose Previews (F50)
@Preview
@Composable
fun ComponentPreviews() {
    SevaTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            EmergencyBar(onClick = {})
            Spacer(modifier = Modifier.height(10.dp))
            BigButton(text = "Register Now", onClick = {})
            Spacer(modifier = Modifier.height(10.dp))
            StatusPill(label = "Priority", type = "priority")
            Spacer(modifier = Modifier.height(10.dp))
            OfflineBanner(lastUpdatedTime = "10:42")
        }
    }
}
