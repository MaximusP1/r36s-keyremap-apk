package com.r36s.keyremap;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servizio di accessibilita' che intercetta i tasti hardware del pad R36S PS202
 * e li traduce nel keycode Android che le app/giochi si aspettano davvero.
 *
 * Mapping verificato empiricamente su questo dispositivo (MT6572, Android 4.4.2):
 * il tasto fisico A arriva come KEYCODE_BUTTON_A, ma RetroArch e altri giochi
 * su questa console rispondono correttamente solo a KEYCODE_ENTER.
 *
 * Aggiungi altre voci a KEY_MAP dopo averle verificate con:
 *   adb shell input keyevent <codice>
 * mentre il gioco e' aperto, per confermare quale keycode l'app si aspetta davvero.
 */
public class KeyRemapService extends AccessibilityService {

    private static final String TAG = "R36SKeyRemap";

    private static final Map<Integer, Integer> KEY_MAP = new HashMap<>();
    static {
        // BTN_MODE (tasto "FN", sopra Select/Start) -> arriva come KEYCODE_BUTTON_MODE
        // -> tradotto in KEYCODE_ENTER. Da riconfermare con:
        //    adb shell getevent -l   (premi FN, verifica che compaia BTN_MODE)
        KEY_MAP.put(KeyEvent.KEYCODE_BUTTON_MODE, KeyEvent.KEYCODE_ENTER);

        // Esempio per aggiungere altri tasti dopo averli verificati:
        // KEY_MAP.put(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER);
        // KEY_MAP.put(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK);
    }

    private Instrumentation instrumentation;
    private ExecutorService injectorExecutor;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            info = new AccessibilityServiceInfo();
        }
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        setServiceInfo(info);

        instrumentation = new Instrumentation();
        injectorExecutor = Executors.newSingleThreadExecutor();

        Log.i(TAG, "R36S Key Remap attivo. Mapping caricato: " + KEY_MAP.size() + " tasti.");
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        Integer targetKeyCode = KEY_MAP.get(event.getKeyCode());
        if (targetKeyCode == null) {
            // Nessuna regola per questo tasto: lascialo passare inalterato
            return false;
        }

        // Inietta solo alla pressione (DOWN), ignora il rilascio per non duplicare l'invio
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            injectKeyEvent(targetKeyCode);
        }

        // true = evento originale intercettato, non inoltrato all'app in foreground
        return true;
    }

    private void injectKeyEvent(final int keyCode) {
        if (injectorExecutor == null || instrumentation == null) {
            return;
        }
        injectorExecutor.execute(() -> {
            try {
                instrumentation.sendKeyDownUpSync(keyCode);
            } catch (SecurityException e) {
                Log.e(TAG, "Permesso INJECT_EVENTS mancante. Esegui via ADB: "
                        + "adb shell pm grant " + getPackageName()
                        + " android.permission.INJECT_EVENTS", e);
            } catch (Exception e) {
                Log.e(TAG, "Errore durante l'iniezione del keycode " + keyCode, e);
            }
        });
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Non serve gestire eventi di accessibilita' standard per questo caso d'uso
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Servizio interrotto dal sistema");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (injectorExecutor != null) {
            injectorExecutor.shutdownNow();
        }
    }
}
