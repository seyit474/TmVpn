package com.telo.vpn.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.telo.vpn.ui.MainViewModel
import com.telo.vpn.ui.theme.TeloGreen

@Composable
fun KeyEntryScreen(vm: MainViewModel) {
    val isLoading by vm.isKeyLoading.collectAsStateWithLifecycle()
    val error by vm.keyError.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    var key by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo
        Text("telo", fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = TeloGreen)
        Text("VPN", fontSize = 22.sp, fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(48.dp))

        Icon(Icons.Default.Key, contentDescription = null,
            modifier = Modifier.size(48.dp), tint = TeloGreen)
        Spacer(Modifier.height(16.dp))

        Text("Erişim Anahtarınızı Girin", fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Yöneticinizden aldığınız abonelik anahtarını buraya yapıştırın.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("Anahtar") },
            placeholder = { Text("https://... veya token yapıştırın") },
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(
                        if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null
                    )
                }
            },
            visualTransformation = if (keyVisible) VisualTransformation.None
                                   else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            maxLines = 3,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                keyboard?.hide()
                vm.submitKey(key)
            }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TeloGreen,
                focusedLabelColor = TeloGreen
            )
        )

        Spacer(Modifier.height(8.dp))

        AnimatedVisibility(error != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    error ?: "",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                keyboard?.hide()
                vm.submitKey(key)
            },
            enabled = !isLoading && key.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TeloGreen)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Bağlan", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
