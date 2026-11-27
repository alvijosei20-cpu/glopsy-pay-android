package com.glopsy.pay.sdk

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Cobro de Glopsy Pay (USDT-BEP20). */
data class GlopsyCharge(
    val token: String,
    val status: String,
    val amountUsd: Double?,
    val concept: String?
)

sealed class GlopsyPayResult {
    data class Paid(val charge: GlopsyCharge?) : GlopsyPayResult()
    data object Cancelled : GlopsyPayResult()
    data class Failed(val message: String) : GlopsyPayResult()
}

/**
 * SDK oficial de Glopsy Pay para Android (pagador).
 *
 * Uso mínimo (el token del cobro lo crea el servidor del comercio):
 *   val r = GlopsyPay.pay(activity, token)
 *   when (r) { is Paid -> ...; Cancelled -> ...; is Failed -> ... }
 *
 * Requisitos:
 * - La app del comercio debe estar aprobada en Glopsy Pay (assetlinks + origen
 *   android:apk-key-hash) para poder usar el passkey de pay.glopsy.shop.
 * - La API key del comercio NUNCA se incluye en el SDK; solo el token del cobro.
 */
object GlopsyPay {
    /** Base de la API. Solo se necesita cambiar para pruebas. */
    var baseUrl: String = "https://pay.glopsy.shop/api"

    suspend fun pay(activity: Activity, token: String): GlopsyPayResult = withContext(Dispatchers.IO) {
        try {
            val optionsRes = postJson("$baseUrl/charges/${enc(token)}/passkey/options", JSONObject())
            val flowId = optionsRes.optString("flowId")
            val options = optionsRes.optJSONObject("options")
                ?: return@withContext GlopsyPayResult.Failed("El cobro no está disponible.")
            if (flowId.isBlank()) return@withContext GlopsyPayResult.Failed("Respuesta inválida del cobro.")

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(GetPublicKeyCredentialOption(options.toString()))
                .build()
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential !is PublicKeyCredential) {
                return@withContext GlopsyPayResult.Failed("No se obtuvo una passkey válida.")
            }

            val body = JSONObject()
                .put("flowId", flowId)
                .put("response", JSONObject(credential.authenticationResponseJson))
            val paid = postJson("$baseUrl/charges/${enc(token)}/passkey/pay", body)
            if (!paid.optBoolean("ok", false)) {
                return@withContext GlopsyPayResult.Failed(paid.optString("message", "No se pudo completar el pago."))
            }
            GlopsyPayResult.Paid(chargeFrom(paid.optJSONObject("charge"), token))
        } catch (_: GetCredentialCancellationException) {
            GlopsyPayResult.Cancelled
        } catch (_: NoCredentialException) {
            GlopsyPayResult.Failed("No hay una passkey de Glopsy Pay en este dispositivo.")
        } catch (e: Exception) {
            GlopsyPayResult.Failed(e.message ?: "No se pudo completar el pago.")
        }
    }

    suspend fun status(token: String): GlopsyCharge? = withContext(Dispatchers.IO) {
        runCatching {
            val res = getJson("$baseUrl/charges/${enc(token)}")
            chargeFrom(res.optJSONObject("charge"), token)
        }.getOrNull()
    }

    private fun chargeFrom(o: JSONObject?, token: String): GlopsyCharge? {
        if (o == null) return null
        val amount = o.optDouble("amount", Double.NaN)
        return GlopsyCharge(
            token = token,
            status = o.optString("status"),
            amountUsd = if (amount.isNaN()) null else amount,
            concept = o.optString("concept").takeIf { it.isNotBlank() }
        )
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun postJson(url: String, body: JSONObject): JSONObject = request(url, "POST", body)
    private fun getJson(url: String): JSONObject = request(url, "GET", null)

    private fun request(url: String, method: String, body: JSONObject?): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20000
            readTimeout = 20000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "GlopsyPaySDK/1.0 (Android)")
            if (body != null) doOutput = true
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (code !in 200..299) throw IOException(json.optString("message", "HTTP $code"))
            return json
        } finally {
            conn.disconnect()
        }
    }
}
