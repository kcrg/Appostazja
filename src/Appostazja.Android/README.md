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

### Lokalny backend przez USB

Build debug domyślnie używa `http://127.0.0.1:5000`. Android 17 pozwala aplikacji korzystać z HTTP dla adresu loopback, a ADB przekierowuje port telefonu na komputer.

Uruchom API:

```powershell
cd ..\Appostazja.DotNet\Appostazja.Api
dotnet run --launch-profile Appostazja.Api
```

Sprawdź na hoście:

```powershell
curl http://127.0.0.1:5000/health
curl http://127.0.0.1:5000/api/v1/dataset
```

Podłącz telefon z włączonym debugowaniem USB. W katalogu `Appostazja.Android` wykonaj:

```powershell
adb reverse tcp:5000 tcp:5000
.\gradlew.bat :app:installDebug
```

Przekierowanie ustaw ponownie po rozłączeniu telefonu. Na Androidzie 16 i starszym użyj backendu HTTPS z certyfikatem zaufanym przez urządzenie. Inny adres ustawisz przez właściwość Gradle:

```powershell
.\gradlew.bat :app:assembleDebug -PAPPOSTAZJA_API_BASE_URL=https://api.example.com
```

W buildzie release adres API trzeba podać jawnie przez `APPOSTAZJA_API_BASE_URL`; projekt celowo nie zawiera produkcyjnego endpointu ani sekretów.

## Prywatność formularza

Dane formularza nie są wysyłane do backendu. Draft jest szyfrowany AES-GCM kluczem przechowywanym w Android Keystore i zapisywany w `noBackupFilesDir`. Aplikacja ma również wyłączony Android Backup. PDF jest generowany lokalnie przez Android `PdfDocument`, a miejsce zapisu wybiera użytkownik przez systemowy Storage Access Framework.

## Build

Projekt używa Gradle 9.9.1, AGP 9.5.1, Kotlin 2.4.21, JDK 27 do uruchamiania Gradle oraz JDK 17 dla modułu JVM. W Android SDK zainstaluj platformę `android-37.2` i Build Tools 37.0.0.

```powershell
cd src\Appostazja.Android
.\gradlew.bat :app:assembleDebug
```
