# Appostazja

Monorepo projektu Appostazja. Aktywny kod jest rozdzielony według platformy, a wspólne pliki repozytorium pozostają w katalogu głównym.

## Struktura

```text
Appostazja/
├─ src/
│  ├─ Appostazja.DotNet/
│  │  ├─ Appostazja.slnx
│  │  └─ Appostazja.Api/
│  └─ Appostazja.Android/
├─ art/
├─ scripts/
│  └─ clean.ps1
├─ .gitignore
├─ clean.cmd
└─ global.json
```

Docelowo natywny klient iOS może zostać dodany jako osobny projekt w `src/Appostazja.iOS/`.

## .NET

Solution znajduje się w:

```text
src/Appostazja.DotNet/Appostazja.slnx
```

Backend API wymaga SDK .NET 11.0.100 i znajduje się w `src/Appostazja.DotNet/Appostazja.Api/`. Szczegóły uruchamiania i publikacji backendu są w jego lokalnym `README.md`.

Kod dawnej aplikacji .NET MAUI jest dostępny w historii Git. Aktywnym klientem jest Android.

## Android

Natywny projekt Kotlin/Gradle znajduje się w:

```text
src/Appostazja.Android/
```

Szczegóły konfiguracji Google Maps, adresu `Appostazja.Api`, prywatności formularza i buildu są w `src/Appostazja.Android/README.md`.

Przykładowy build debug na Windows:

```powershell
cd src/Appostazja.Android
.\gradlew.bat :app:assembleDebug
```

## Czyszczenie

Z katalogu głównego na Windows:

```powershell
.\clean.cmd
```

Skrypt usuwa artefakty buildów i lokalne cache IDE z części .NET i Android/Kotlin. Celowo nie dotyka przyszłego `src/Appostazja.iOS/`.

## Git ignore

Repo używa jednego globalnego `.gitignore` w katalogu głównym. Obejmuje .NET/Visual Studio/Rider, Android/Kotlin/Gradle oraz Swift/Xcode, więc projekty nie potrzebują własnych zagnieżdżonych plików `.gitignore`.
