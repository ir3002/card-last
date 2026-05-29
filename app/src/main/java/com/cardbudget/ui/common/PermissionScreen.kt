package com.cardbudget.ui.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@Composable
fun PermissionSetupScreen(onComplete: () -> Unit) {
    val context = LocalContext.current

    fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var smsGranted by remember { mutableStateOf(isGranted(Manifest.permission.READ_SMS)) }
    var receiveSmsGranted by remember { mutableStateOf(isGranted(Manifest.permission.RECEIVE_SMS)) }
    var notifGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                isGranted(Manifest.permission.POST_NOTIFICATIONS)
            else true
        )
    }

    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { smsGranted = it }

    val receiveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { receiveSmsGranted = it }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { notifGranted = it }

    val allGranted = smsGranted && receiveSmsGranted && notifGranted

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CreditCard,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text("카드 가계부", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "앱을 시작하기 전에 필요한 권한을 허용해주세요",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(32.dp))

        PermissionItem(
            icon = Icons.Default.Sms,
            title = "SMS 읽기 권한",
            desc = "카드사 결제 문자를 자동으로 읽어 거래를 등록합니다",
            granted = smsGranted,
            onRequest = { smsLauncher.launch(Manifest.permission.READ_SMS) }
        )
        Spacer(Modifier.height(12.dp))
        PermissionItem(
            icon = Icons.Default.MarkEmailRead,
            title = "SMS 수신 권한",
            desc = "새 결제 문자를 실시간으로 감지합니다",
            granted = receiveSmsGranted,
            onRequest = { receiveLauncher.launch(Manifest.permission.RECEIVE_SMS) }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Spacer(Modifier.height(12.dp))
            PermissionItem(
                icon = Icons.Default.Notifications,
                title = "알림 권한",
                desc = "예산 초과 및 결제일 알림을 전송합니다",
                granted = notifGranted,
                onRequest = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            )
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                when {
                    !smsGranted -> smsLauncher.launch(Manifest.permission.READ_SMS)
                    !receiveSmsGranted -> receiveLauncher.launch(Manifest.permission.RECEIVE_SMS)
                    !notifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else -> onComplete()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (allGranted) "시작하기" else "권한 허용", fontSize = 16.sp)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onComplete) {
            Text(
                "나중에 설정하기 (일부 기능 제한)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    desc: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (granted) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(
                    desc,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
            if (granted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                TextButton(onClick = onRequest) { Text("허용", fontSize = 12.sp) }
            }
        }
    }
}
