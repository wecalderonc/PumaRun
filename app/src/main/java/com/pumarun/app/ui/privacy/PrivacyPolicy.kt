package com.pumarun.app.ui.privacy

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

private const val PRIVACY_POLICY_URL = "https://wecalderonc.github.io/PumaRun/"

/** Opens the published privacy policy. Spanish devices land on the Spanish section. */
fun Context.openPrivacyPolicy() {
    val spanish = resources.configuration.locales[0].language == "es"
    val url = if (spanish) "$PRIVACY_POLICY_URL#es" else "$PRIVACY_POLICY_URL#en"
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
    }
}
