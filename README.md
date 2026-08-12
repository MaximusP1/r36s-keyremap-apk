# R36S Key Remap

App Android **senza root** per rimappare i tasti del pad fisico/Bluetooth della **R36S PS202** (MediaTek MT6572, Android 4.4.2) sul keycode che le app realmente si aspettano.

## Perché esiste

Su questo dispositivo il tasto fisico **A** genera correttamente `KEYCODE_BUTTON_A`, ma RetroArch e altri giochi Android su questa build rispondono solo a `KEYCODE_ENTER`. Questa app intercetta l'evento e lo traduce al volo, senza toccare `/system` e senza root.

Mapping verificato empiricamente (vedi commenti nel codice per i dettagli):

| Tasto fisico | Keycode nativo | Tradotto in |
|---|---|---|
| A | `KEYCODE_BUTTON_A` | `KEYCODE_ENTER` (66) — **confermato** |

I direzionali (Su/Giù/Sinistra/Destra) **non** sono inclusi: arrivano già come `KEYCODE_DPAD_*` standard e funzionano nativamente.

## Come funziona (sotto il cofano)

Un `AccessibilityService` con il flag `FLAG_REQUEST_FILTER_KEY_EVENTS` intercetta gli eventi tasto hardware prima che arrivino all'app in primo piano. Quando rileva un tasto mappato, consuma l'evento originale e inietta quello tradotto con `Instrumentation.sendKeyDownUpSync()`.

Questa iniezione richiede il permesso di sistema `android.permission.INJECT_EVENTS`, che le app normali non possono auto-concedersi — va quindi concesso una tantum via ADB dopo l'installazione (vedi sotto). **Non serve root**: è lo stesso meccanismo usato da tool come AutoInput/Tasker per iniettare eventi senza root.

## Build

1. Apri la cartella del progetto in **Android Studio** (genera automaticamente il Gradle wrapper mancante alla prima apertura/sync).
2. `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`.
3. Trovi l'APK generato in `app/build/outputs/apk/debug/app-debug.apk`.

In alternativa da riga di comando, se hai già Gradle e Android SDK installati sul tuo PC:

```
gradle assembleDebug
```

## Installazione e attivazione sulla console

```
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.r36s.keyremap android.permission.INJECT_EVENTS
```

Poi sulla console:

1. Apri l'app **R36S Key Remap**.
2. Tocca "Apri Impostazioni Accessibilità".
3. Trova "R36S Key Remap" nell'elenco e attivalo.

Da questo momento, premendo il tasto A (sia sul pad integrato che su un pad Bluetooth collegato, dato che entrambi mandano lo stesso keycode) l'app in primo piano riceverà `ENTER` invece di `BUTTON_A`.

## Estendere il mapping ad altri tasti

1. Con il gioco aperto, testa via ADB quale keycode serve davvero:
   ```
   adb shell input keyevent <numero>
   ```
   (es. 4 = BACK, 111 = ESCAPE, 67 = DEL — prova finché non trovi quello che funziona come "annulla")
2. Aggiungi la riga corrispondente in `KeyRemapService.java`, dentro il blocco `static { ... }`:
   ```java
   KEY_MAP.put(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK);
   ```
3. Ricompila e reinstalla (`adb install -r ...`).

## Limitazioni note

- Testato/pensato specificamente per la R36S PS202 (MT6572, Android 4.4.2, build PS202_00001). Su firmware diversi il keycode nativo dei tasti potrebbe differire.
- Se l'app perde il permesso `INJECT_EVENTS` dopo un riavvio (dipende dal firmware), va ridato lo stesso comando `adb shell pm grant`.
- Il servizio deve restare attivo in Impostazioni Accessibilità: se il sistema lo disattiva automaticamente per risparmio batteria, riattivalo manualmente.

## Licenza

MIT — usa, modifica e ridistribuisci liberamente.
