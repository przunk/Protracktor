# Protracktor privacy policy

Effective date: 2026-09-22

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

Protracktor opts out of Android's system backup and of device-to-device transfer, so its private
data is not copied off the device by the operating system either. Uninstalling the app removes it.

### Network requests to independent archives

Only features chosen by the user contact the network. Depending on the action, Protracktor may
connect over HTTPS to:

- `modland.com` to download its catalogue index and selected tracks;
- `asma.atari.org` to download the ASMA collection;
- `files.exotica.org.uk` to download the UnExoticA index and the game archives that hold its tunes;
- `modarchive.org` and `api.modarchive.org` to send a live search term and download a selected
  module;
- `hvsc.c64.org` to download the SID song-length database;
- `raw.githubusercontent.com` to download song metadata and song lengths published by the
  audacious-uade-tools project, Modland's favourites list, and a song database used by the Amiga
  decoder;
- `svn.code.sf.net` to download the Atari ST replay routines published by the sc68 project;
- `gitlab.com` to download the Amiga replay routines published by the UADE project.

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

### Sending to a web player

When the user scans the pairing code shown by a Protracktor web page and chooses **Send**, the app
sends the chosen queue — track titles, their archive addresses and the position in the queue — to
the address that code names. Tracks that exist only on the device are sent as files, up to 8 MB in
one transfer. Nothing is sent without that scan and that choice, and the receiving server is the one
running the page the user opened.

### The web player

Protracktor also has a web page: a second player that runs in a browser, served by whoever hosts
it — usually the user, on a machine of their own. The page keeps its playlists, play history,
downloaded catalogue indexes and settings in that browser's own storage, on that device. It sends
nothing to the developer and has no analytics, advertising or account. For what the user browses,
downloads or plays it contacts `modland.com`, `asma.atari.org`, `hvsc.c64.org` and, for song
metadata the user downloads, `raw.githubusercontent.com`, as the app does,
and those archives see the same requests described above. The server hosting the page sees the
requests for the page itself and whatever the app sends to it (above). Clearing the site's data in
the browser removes everything the page kept.

### Retention and deletion

Local information remains until it is replaced, removed by an available application action,
cleared through Android's **App info → Storage & cache → Clear storage**, or removed when the app is
uninstalled. Android may clear cache files independently. Every downloaded catalogue index, the
song metadata and the replay routines can be deleted separately under **Settings → Storage**.

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

Protracktor rezygnuje z systemowej kopii zapasowej Androida i z przenoszenia danych między
urządzeniami, więc jego prywatne dane nie są kopiowane poza urządzenie także przez system.
Odinstalowanie aplikacji je usuwa.

### Połączenia z niezależnymi archiwami

Sieć jest używana tylko przez funkcje wybrane przez użytkownika. Zależnie od działania Protracktor
może łączyć się przez HTTPS z:

- `modland.com`, aby pobrać indeks i wybrane utwory;
- `asma.atari.org`, aby pobrać kolekcję ASMA;
- `files.exotica.org.uk`, aby pobrać indeks UnExoticA i archiwa gier zawierające jej utwory;
- `modarchive.org` i `api.modarchive.org`, aby wysłać wpisane hasło i pobrać wybrany moduł;
- `hvsc.c64.org`, aby pobrać bazę długości utworów SID;
- `raw.githubusercontent.com`, aby pobrać metadane i długości utworów publikowane przez projekt
  audacious-uade-tools, listę ulubionych Modlandu oraz bazę utworów używaną przez dekoder Amigi;
- `svn.code.sf.net`, aby pobrać procedury odtwarzające Atari ST publikowane przez projekt sc68;
- `gitlab.com`, aby pobrać procedury odtwarzające Amigi publikowane przez projekt UADE.

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

### Wysyłanie do odtwarzacza w przeglądarce

Gdy użytkownik zeskanuje kod parowania wyświetlony przez stronę Protracktora i wybierze **Wyślij**,
aplikacja wysyła wybraną kolejkę — tytuły utworów, ich adresy w archiwach i miejsce w kolejce — pod
adres zapisany w tym kodzie. Utwory, które istnieją tylko na urządzeniu, są wysyłane jako pliki, do
8 MB w jednym przesłaniu. Bez tego skanu i tego wyboru nic nie jest wysyłane, a odbiorcą jest serwer
obsługujący stronę otwartą przez użytkownika.

### Odtwarzacz w przeglądarce

Protracktor ma też stronę internetową: drugi odtwarzacz działający w przeglądarce, udostępniany
przez tego, kto go hostuje — zwykle przez samego użytkownika, na jego własnym komputerze. Strona
przechowuje swoje playlisty, historię odtwarzania, pobrane indeksy katalogów i ustawienia w pamięci
tej przeglądarki, na tym urządzeniu. Niczego nie wysyła do twórcy i nie ma analityki, reklam ani
konta. Dla tego, co użytkownik przegląda, pobiera lub odtwarza, łączy się z `modland.com`,
`asma.atari.org`, `hvsc.c64.org` oraz — dla pobieranych metadanych utworów —
`raw.githubusercontent.com`, tak jak aplikacja, a te archiwa widzą te same zapytania, co
opisane wyżej. Serwer udostępniający stronę widzi zapytania o samą stronę oraz to, co aplikacja do
niego wysyła (wyżej). Wyczyszczenie danych witryny w przeglądarce usuwa wszystko, co strona
przechowywała.

### Retencja i usuwanie

Lokalne informacje pozostają do czasu zastąpienia, usunięcia przez dostępną funkcję aplikacji,
wyczyszczenia przez **App info → Storage & cache → Clear storage** albo odinstalowania aplikacji.
Android może niezależnie usuwać pliki pamięci podręcznej. Każdy pobrany indeks katalogu,
metadane utworów i procedury odtwarzające można usunąć osobno w **Settings → Storage**.

Deweloper nie przechowuje konta użytkownika ani rekordu, którego usunięcia można od niego zażądać.
W sprawie logów niezależnego archiwum należy kontaktować się z jego operatorem.

### Dzieci

Protracktor jest ogólnym narzędziem muzycznym dla osób od 13 roku życia i nie jest projektowany dla
dzieci poniżej 13 lat. Aplikacja świadomie nie zbiera danych osobowych dzieci.

### Zmiany

Polityka zostanie zaktualizowana, gdy zmieni się sposób przetwarzania danych przez aplikację. Data
obowiązywania powyżej wskazuje bieżącą wersję.
