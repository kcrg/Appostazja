# Appostazja
Aplikacja .NET MAUI wyświetlająca dane parafii z Mapy Apostazji oraz generująca
deklarację apostazji w PDF.

## Stos technologiczny

- .NET 11 Preview 7 / .NET MAUI 11 Preview 7
- Android, iOS i Windows
- NativeAOT: iOS oraz eksperymentalnie Android
- CommunityToolkit.Maui i CommunityToolkit.Mvvm
- System.Net.Http i source-generated System.Text.Json
- własny generator PDF w czystym .NET z osadzoną czcionką TrueType

Aplikacja nie używa QuestPDF, RestSharp, Sentry, Plainer, mavvm ani runtime
SkiaSharp. SkiaSharp występuje wyłącznie wewnątrz narzędzia build-time
Microsoft.Maui.Resizetizer, które przetwarza SVG/ikony; nie trafia do AAB.

## Wymagania

- SDK 11.0.100-preview.7.26381.103
- workload set 11.0.100-preview.7.26410.2
- Android SDK API 37 oraz workload android
- macOS + Xcode dla publikacji iOS

Wersje są przypięte w global.json. Preview 7 workloadu Android wskazuje
nieopublikowany build paczek NativeAOT 26378; Directory.Build.targets
przypina zgodny i opublikowany build SDK 26381.

## Konfiguracja Google Maps na Androidzie

Klucz nie jest przechowywany w repozytorium. Przekaż go przy buildzie:

~~~powershell
dotnet build Appostazja.Maui/Appostazja.Maui.csproj -f net11.0-android -c Release -r android-arm64 -p:GoogleMapsApiKey=TWÓJ_KLUCZ
~~~

Ogranicz klucz w Google Cloud Console do aplikacji Android
app.apostazja.siostra i właściwego odcisku SHA-1 certyfikatu podpisującego.

## Build i testy

~~~powershell
dotnet test Appostazja.Core.Tests/Appostazja.Core.Tests.csproj -c Release
dotnet build Appostazja.Maui/Appostazja.Maui.csproj -f net11.0-windows10.0.19041.0 -c Debug
dotnet build Appostazja.Maui/Appostazja.Maui.csproj -f net11.0-android -c Release -r android-arm64 -p:GoogleMapsApiKey=TWÓJ_KLUCZ
~~~

Publikację iOS NativeAOT wykonuj na Macu:

~~~bash
dotnet publish Appostazja.Maui/Appostazja.Maui.csproj -f net11.0-ios -c Release -r ios-arm64
~~~

NativeAOT na Androidzie w .NET 11 Preview 7 pozostaje funkcją eksperymentalną.
Nie należy traktować tego targetu jako gotowego do produkcji bez testów na
fizycznych urządzeniach.

## Dane i PDF

Mapa pobiera GeoJSON z https://mapaapostazji.pl/api/map-data.php.

Generator PDF uwzględnia wymagania Dekretu Ogólnego KEP obowiązującego od
19 lutego 2016 r.: dane osobowe, datę i parafię chrztu, motywację,
dobrowolność aktu oraz miejsce na własnoręczny podpis. Dokument składa się
osobiście proboszczowi parafii miejsca zamieszkania. Aplikacja nie świadczy
porady prawnej ani kanonicznej; przed użyciem warto zweryfikować aktualną
procedurę.
