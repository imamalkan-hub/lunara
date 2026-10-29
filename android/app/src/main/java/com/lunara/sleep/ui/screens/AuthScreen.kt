package com.lunara.sleep.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunara.sleep.ui.MainViewModel
import com.lunara.sleep.ui.theme.Accent
import com.lunara.sleep.ui.theme.Canvas
import com.lunara.sleep.ui.theme.Hairline
import com.lunara.sleep.ui.theme.Surface1
import com.lunara.sleep.ui.theme.Text1
import com.lunara.sleep.ui.theme.Text2
import com.lunara.sleep.ui.theme.Text3

/**
 * Giriş / kayıt ekranı.
 *
 * Hesap zorunlu değil: uygulama cihazda da tam çalışır. Hesap yalnızca
 * senkronizasyon, geçmiş yedekleme ve doktor raporu paylaşımı içindir.
 */
@Composable
fun AuthScreen(vm: MainViewModel) {
    var isRegister by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(Canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(60.dp))
        Text("🌙", fontSize = 46.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "Lunara",
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            color = Text1,
            letterSpacing = (-1).sp,
        )
        Text(
            "Sleep tracking and doctor finder",
            fontSize = 13.sp,
            color = Text3,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        if (isRegister) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Your name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = authFieldColors(),
            )
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = authFieldColors(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(14.dp),
            colors = authFieldColors(),
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                if (isRegister) vm.register(email, password, name) else vm.login(email, password)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Canvas),
        ) {
            Text(
                if (isRegister) "Create account" else "Sign in",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
        }

        Spacer(Modifier.height(14.dp))

        androidx.compose.material3.TextButton(onClick = { isRegister = !isRegister }) {
            Text(
                if (isRegister) "Already have an account — sign in" else "No account yet — sign up",
                color = Text2,
                fontSize = 13.sp,
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            "Sleep tracking works without an account. An account is only needed for " +
                "backup and sharing reports with your doctor.",
            color = Text3,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp,
        )

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Surface1,
    unfocusedContainerColor = Surface1,
    focusedBorderColor = Accent,
    unfocusedBorderColor = Hairline,
    focusedTextColor = Text1,
    unfocusedTextColor = Text1,
    cursorColor = Accent,
    focusedLabelColor = Accent,
    unfocusedLabelColor = Text3,
)
