# Protracktor privacy policy

Effective date: 2026-09-03

This is the publication source for Protracktor's privacy policy. Before release it must be hosted as
an active, publicly accessible, non-geofenced HTML page. The public page and an in-app legal link
must point to the same current text.

## English

### Who is responsible

Protracktor is developed and published by Przunk, the developer identified on its Google Play store
listing. Privacy questions may be submitted through the support contact displayed on that listing
or, once the source repository is public, at
<https://github.com/przunk/protracktor/issues>.

### Summary

Protracktor has no accounts, advertising, analytics, marketing tracker or remote crash-reporting
service. The developer does not operate an application server and does not receive the user's
library, playlists or playback history.

Protracktor can contact independent music archives when the user chooses an online catalogue,
search or track. Those requests necessarily disclose normal network information to the archive,
and a live search sends the words entered by the user. Details are below.

### Information kept on the device

The application stores the following in its private application storage:

- folder access grants selected through Android's system document picker;
- file identifiers, paths and music metadata needed to build the local library;
- playlists, playback settings, last playback state and play history;
- downloaded catalogue indexes, the ASMA archive, the HVSC song-length database and cached tracks;
- temporary copies of files that the user explicitly chooses to share.

This information is used only to provide playback, browsing, history, sharing and session restore.
It is not sent to the developer.

Android may include eligible private application data in the device owner's system backup when
Android Backup is enabled. That transfer is performed by the operating system under the device
owner's Google backup settings, not by a Protracktor server.

### Network requests to independent archives

Only features chosen by the user contact the network. Depending on the action, Protracktor may
connect over HTTPS to:

- `modland.com` to download its catalogue index and selected tracks;
- `asma.atari.org` to download the ASMA collection;
- `modarchive.org` and `api.modarchive.org` to send a live search term and download a selected
  module;
- `hvsc.c64.org` to download the SID song-length database.

Like any direct web request, an archive receives the device's public IP address and technical HTTP
request data. The Mod Archive additionally receives the search words entered by the user. The
archives are independent third parties; their server logs, retention and policies are outside the
developer's control. Protracktor does not add an advertising identifier, account identifier,
precise location or analytics identifier to these requests.

Local playback and previously downloaded content remain usable without making these requests.

### Sharing

When the user chooses **Share file** or **Share link**, Protracktor sends the selected file or link
to the application chosen in Android's system share sheet. This transfer happens only after that
explicit action. The receiving application or service applies its own privacy policy. Temporary
share copies are removed after their retention window when Protracktor next performs cleanup; the
receiving application may keep its own copy.

### Retention and deletion

Local information remains until it is replaced, removed by an available application action,
cleared through Android's **App info → Storage & cache → Clear storage**, or removed when the app is
uninstalled. Android may clear cache files independently. Some downloaded catalogue data currently
has no separate in-app delete control; clearing application storage or uninstalling removes it.

The developer holds no account record to delete. Requests concerning logs held by an independent
archive must be directed to that archive.

### Children

Protracktor is a general music utility for users aged 13 and over and is not designed for children
under 13. It does not knowingly collect children's personal information.

### Changes

This policy will be updated when the application's data handling changes. The effective date above
identifies the current version.

## Polski

### Administrator

Protracktor jest rozwijany i publikowany przez Przunk — dewelopera wskazanego na stronie aplikacji
w Google Play. Pytania dotyczące prywatności można wysłać na adres pomocy widoczny na tej stronie
albo, po publicznym udostępnieniu kodu, przez
<https://github.com/przunk/protracktor/issues>.

### Podsumowanie

Protracktor nie ma kont, reklam, analityki, trackerów marketingowych ani zdalnego systemu
raportowania awarii. Deweloper nie prowadzi serwera aplikacji i nie otrzymuje biblioteki, playlist
ani historii odtwarzania użytkownika.

Gdy użytkownik wybierze katalog online, wyszukiwanie albo utwór, Protracktor może połączyć się z
niezależnym archiwum muzycznym. Takie żądanie przekazuje archiwum zwykłe informacje sieciowe, a
wyszukiwanie na żywo również wpisane słowa. Szczegóły znajdują się niżej.

### Informacje przechowywane na urządzeniu

Aplikacja zapisuje w swojej prywatnej pamięci:

- uprawnienia do folderów wybranych przez systemowy selektor dokumentów Androida;
- identyfikatory i ścieżki plików oraz metadane muzyczne potrzebne do biblioteki;
- playlisty, ustawienia odtwarzania, ostatni stan odtwarzacza i historię;
- indeksy katalogów, archiwum ASMA, bazę długości HVSC i pobrane utwory;
- tymczasowe kopie plików, które użytkownik wyraźnie wybrał do udostępnienia.

Dane te służą wyłącznie do odtwarzania, przeglądania, historii, udostępniania i przywracania sesji.
Nie są wysyłane do dewelopera.

Jeżeli właściciel urządzenia włączył Android Backup, system Android może objąć kopią zapasową
kwalifikujące się prywatne dane aplikacji. Transfer wykonuje system zgodnie z ustawieniami kopii
Google właściciela urządzenia, a nie serwer Protracktora.

### Połączenia z niezależnymi archiwami

Sieć jest używana tylko przez funkcje wybrane przez użytkownika. Zależnie od działania Protracktor
może łączyć się przez HTTPS z:

- `modland.com`, aby pobrać indeks i wybrane utwory;
- `asma.atari.org`, aby pobrać kolekcję ASMA;
- `modarchive.org` i `api.modarchive.org`, aby wysłać wpisane hasło i pobrać wybrany moduł;
- `hvsc.c64.org`, aby pobrać bazę długości utworów SID.

Jak przy każdym bezpośrednim żądaniu internetowym, archiwum otrzymuje publiczny adres IP urządzenia
i techniczne dane HTTP. The Mod Archive otrzymuje również wpisane słowa wyszukiwania. Archiwa są
niezależnymi podmiotami; ich logi, retencja i zasady pozostają poza kontrolą dewelopera. Protracktor
nie dodaje do żądań identyfikatora reklamowego, identyfikatora konta, dokładnej lokalizacji ani
identyfikatora analitycznego.

Lokalne odtwarzanie i wcześniej pobrane materiały działają bez wykonywania tych żądań.

### Udostępnianie

Po wybraniu **Udostępnij plik** lub **Udostępnij link** Protracktor przekazuje wybrany plik albo
odnośnik aplikacji wskazanej w systemowym arkuszu udostępniania Androida. Transfer następuje tylko
po takim działaniu użytkownika. Aplikacja lub usługa odbierająca stosuje własną politykę
prywatności. Tymczasowe kopie są usuwane po okresie retencji podczas kolejnego sprzątania przez
Protracktor; odbiorca może zachować własną kopię.

### Retencja i usuwanie

Lokalne informacje pozostają do czasu zastąpienia, usunięcia przez dostępną funkcję aplikacji,
wyczyszczenia przez **App info → Storage & cache → Clear storage** albo odinstalowania aplikacji.
Android może niezależnie usuwać pliki pamięci podręcznej. Część pobranych danych katalogów nie ma
jeszcze osobnej funkcji usuwania; usuwa je wyczyszczenie pamięci aplikacji lub odinstalowanie.

Deweloper nie przechowuje konta użytkownika ani rekordu, którego usunięcia można od niego zażądać.
W sprawie logów niezależnego archiwum należy kontaktować się z jego operatorem.

### Dzieci

Protracktor jest ogólnym narzędziem muzycznym dla osób od 13 roku życia i nie jest projektowany dla
dzieci poniżej 13 lat. Aplikacja świadomie nie zbiera danych osobowych dzieci.

### Zmiany

Polityka zostanie zaktualizowana, gdy zmieni się sposób przetwarzania danych przez aplikację. Data
obowiązywania powyżej wskazuje bieżącą wersję.
