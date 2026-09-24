# Appostazja

Monorepo projektu Appostazja. Aktywny kod jest rozdzielony według platformy, a wspólne pliki repozytorium pozostają w katalogu głównym.

## Struktura

```text
Appostazja/
├─ src/
│  ├─ Appostazja.DotNet/
│  │  ├─ Appostazja.sln
│  │  ├─ Appostazja.Api/
│  │  └─ Legacy/
│  │     ├─ Appostazja.Core/
│  │     ├─ Appostazja.Core.Tests/
│  │     ├─ Appostazja.Maui/
│  │     └─ Google.Maps.Utils.Android/
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
src/Appostazja.DotNet/Appostazja.sln
```

Backend API znajduje się w `src/Appostazja.DotNet/Appostazja.Api/`. Szczegóły uruchamiania i publikacji backendu są w jego lokalnym `README.md`.

Stara aplikacja .NET MAUI i powiązane projekty zostały zachowane w `src/Appostazja.DotNet/Legacy/`.

## Android

Natywny projekt Kotlin/Gradle znajduje się w:

```text
src/Appostazja.Android/
```

Przykładowy build debug na Windows:

```powershell
cd src/Appostazja.Android
.\gradlew.bat assembleDebug
```

## Czyszczenie

Z katalogu głównego na Windows:

```powershell
.\clean.cmd
```

Skrypt usuwa artefakty buildów i lokalne cache IDE z części .NET i Android/Kotlin. Celowo nie dotyka przyszłego `src/Appostazja.iOS/`.

## Git ignore

Repo używa jednego globalnego `.gitignore` w katalogu głównym. Obejmuje .NET/Visual Studio/Rider, Android/Kotlin/Gradle oraz Swift/Xcode, więc projekty nie potrzebują własnych zagnieżdżonych plików `.gitignore`.
