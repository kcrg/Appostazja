# Instrukcje dla agentów

## Testy

Testy mają sprawdzać logikę biznesową i zachowanie, które może się realnie zepsuć: parsowanie i import danych, reguły walidacji, obliczenia (oceny, odległości, kierunki), decyzje cache/odświeżania, mapowanie błędów na stany widoczne dla użytkownika, kontrakty HTTP (kody, nagłówki, ETag).

Nie twórz testów, które:

- sprawdzają assety graficzne, ikony, kolory, istnienie zasobów lub stringów,
- sprawdzają strukturę kodu (że klasa/metoda/plik istnieje, że coś jest `internal`, liczbę parametrów, nazwy pakietów),
- powtarzają implementację (asercja na stałą, którą test sam skopiował z kodu),
- testują framework lub bibliotekę zamiast naszego kodu (np. że serializer serializuje, że `2 + 2 == 4`),
- istnieją tylko po to, żeby podbić pokrycie.

Jeśli dla zmiany nie da się napisać sensownego testu logiki, nie dopisuj testu na siłę — napisz w podsumowaniu, czego nie przetestowano i dlaczego.

Preferuj testy na prawdziwych danych wejściowych (bajty CSV/GeoJSON, odpowiedzi HTTP z podmienionego `HttpMessageHandler`, tymczasowa baza SQLite) zamiast mocków każdej warstwy.
