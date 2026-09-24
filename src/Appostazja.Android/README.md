# Appostazja Android

Natywny klient Android projektu Appostazja napisany w Kotlinie i Jetpack Compose.

## Moduły

- `app` — UI Compose/Material 3 Expressive, Google Maps, lokalny formularz i generowanie PDF.
- `api` — klient `Appostazja.Api` oparty o Retrofit i `kotlinx.serialization`.

## Konfiguracja lokalna

Skopiuj `local.properties.example` do `local.properties` i ustaw SDK oraz klucz Google Maps SDK for Android:

```properties
sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk
MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY
```

Dla emulatora debug domyślny adres backendu to `http://10.0.2.2:5000`. Inny backend ustaw przez właściwość Gradle:

```powershell
.\gradlew.bat :app:assembleDebug -PAPPOSTAZJA_API_BASE_URL=https://api.example.com
```

W buildzie release adres API trzeba podać jawnie przez `APPOSTAZJA_API_BASE_URL`; projekt celowo nie zawiera produkcyjnego endpointu ani sekretów.

## Prywatność formularza

Dane formularza nie są wysyłane do backendu. Draft jest szyfrowany AES-GCM kluczem przechowywanym w Android Keystore i zapisywany w `noBackupFilesDir`. Aplikacja ma również wyłączony Android Backup. PDF jest generowany lokalnie przez Android `PdfDocument`, a miejsce zapisu wybiera użytkownik przez systemowy Storage Access Framework.

## Build

```powershell
cd src\Appostazja.Android
.\gradlew.bat :app:assembleDebug
```
