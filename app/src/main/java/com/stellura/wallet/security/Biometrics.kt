package com.stellura.wallet.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Fingerprint / face unlock via Android's BiometricPrompt. Android decides which sensor
 * is used (apps can't pick face vs fingerprint); we only accept "Class 3 / strong"
 * biometrics, which excludes weak camera-only face unlock.
 */
object Biometrics {

    fun available(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(
        activity: FragmentActivity,
        title: String,
        onSuccess: () -> Unit,
        onFail: (String) -> Unit = {},
    ) {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(code: Int, msg: CharSequence) = onFail(msg.toString())
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info)
    }
}
