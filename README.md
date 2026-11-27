# Glopsy Pay · Android SDK

SDK oficial de **Glopsy Pay** para pagar cobros **USDT-BEP20** de forma **nativa**
(Android Credential Manager + passkey), sin WebView.

- El **servidor del comercio** crea el cobro con su API key (`@glopsy/pay` / `POST /api/v1/charges`) y obtiene un `token`.
- La **app** recibe solo el `token` y llama al SDK. La API key **nunca** va en la app.

## Instalación (JitPack)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google(); mavenCentral(); maven { url = uri("https://jitpack.io") }
    }
}
```

```kotlin
dependencies {
    implementation("com.github.alvijosei20-cpu:glopsy-pay-android:1.0.0")
}
```

## Uso

```kotlin
import com.glopsy.pay.sdk.GlopsyPay
import com.glopsy.pay.sdk.GlopsyPayResult

lifecycleScope.launch {
    when (val r = GlopsyPay.pay(activity, chargeToken)) {
        is GlopsyPayResult.Paid -> { /* pago completado */ }
        GlopsyPayResult.Cancelled -> { /* usuario canceló */ }
        is GlopsyPayResult.Failed -> { /* mostrar r.message */ }
    }
}
```

El cobro se crea en el servidor del comercio:

```ts
const gp = new GlopsyPay({ apiKey: process.env.GLOPSY_API_KEY! });
const charge = await gp.createCharge({ amount: '25.00', concept: 'Pedido #123' });
// envía charge.token a tu app
```

## Requisitos de la app

1. Debe estar **aprobada** en Glopsy Pay (package + SHA-256 del certificado).
   Glopsy Pay publica la entrada en `https://pay.glopsy.shop/.well-known/assetlinks.json`.
2. El pagador debe tener una **passkey de Glopsy Pay** (descubrible) en el dispositivo.
3. `minSdk 24`, HTTPS, sin claves secretas en la app.

## Seguridad

- Pago con passkey (WebAuthn) y biometría; la app no maneja claves ni tarjetas.
- El SDK no incluye la API key del comercio; solo el `token` del cobro.
- El estado autoritativo del pago es el **webhook** del servidor del comercio
  (`charge.paid`) y/o `GlopsyPay.status(token)`.

## Licencia

MIT.
