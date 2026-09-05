# Modizer's decoder libraries, and where each one lives

Copied out of <https://github.com/yoyofr/modizer>'s README on **2026-09-05**, deduplicated and
sorted. The reasoning about what is worth taking is in `docs/PLAN_FORMATS.md` §5; this is the raw
map, kept because working it out again means reading somebody else's Xcode project.

**Nothing may be taken from that repository.** It carries no licence file, which means all rights
reserved — it is an aggregator's convenience copy. Every row below points at the library's own home,
which is where its terms are and where a copy would have to come from.

**The list is a starting point, not a recommendation.** None of these has been built, licence-checked
or measured here. `docs/PLAN_FORMATS.md` opens with the five things each one would need.

### Already in Protracktor

Listed so the overlap is visible rather than rediscovered. Note Modizer uses a different sc68 fork and UADE 2.13 against our 3.05.

| library | upstream |
| --- | --- |
| Another Slight Atari Player(ASAP) | <https://asap.sourceforge.net> |
| Game Music Emu | <https://github.com/libgme/game-music-emu> |
| libopenmpt | <https://lib.openmpt.org/libopenmpt> |
| SC68 | <https://github.com/Zeinok/sc68> |
| Sibplayfp | <https://github.com/libsidplayfp/sidplayfp> |
| UADE | <https://zakalwe.fi/uade> |

### Decoders we do not have

39 of them. `docs/PLAN_FORMATS.md` §5 matches the largest against measured Modland counts; the rest are here for completeness.

| library | upstream |
| --- | --- |
| Adplug | <https://github.com/adplug/adplug> |
| AHX/Hively tracker | <https://github.com/pete-gordon/hivelytracker> |
| AtariAudio | <https://github.com/arnaud-carre/sndh-player> |
| Eupmini | <https://github.com/gzaffin/eupmini> |
| ffmpeg/mpg123/vorbis | <https://github.com/arthenica/ffmpeg-kit> |
| FMPmini | <https://github.com/myon98/98fmplayer> |
| Furnace | <https://github.com/tildearrow/furnace> |
| GDataXML-HTML | <https://github.com/graetzer/GDataXML-HTML> |
| HighlyExperimental | <https://gitlab.com/kode54/highly_experimental> |
| HighlyQuixotic | <https://gitlab.com/kode54/highly_quixotic> |
| HighlyTheoritical | <https://gitlab.com/kode54/highly_theoretical> |
| LazyUSF | <https://github.com/derselbst/lazyusf> |
| libarchive | <https://github.com/libarchive/libarchive> |
| Libsamplerate | <https://github.com/libsndfile/libsamplerate> |
| Libvgm | <https://github.com/ValleyBell/libvgm> |
| libXMP | <https://github.com/libxmp/libxmp> |
| mdxmini | <https://github.com/gzaffin/mdxmini> |
| MetalANGLE | <https://github.com/kakashidinho/metalangle> |
| MSColorPicker | <https://github.com/sgl0v/MSColorPicker> |
| mvtiaine UADE songlengths | <https://github.com/mvtiaine/audacious-uade> |
| NSFPlay | <https://bbbradsmith.github.io/nsfplay> |
| NVDSP | <https://github.com/bartolsthoorn/NVDSP> |
| PlayGSF | <https://github.com/yshui/playgsf> |
| Pmdmini | <https://github.com/mistydemeo/pmdmini> |
| ProjectM | <https://github.com/projectM-visualizer/projectm> |
| PT3Player | <https://github.com/Volutar/pt3player> |
| PxTone / Organya | <https://www.wothke.ch/webPixel> |
| SARUnArchiveANY | <https://github.com/saru2020/SARUnArchiveANY> |
| Snes9x/snsf | <https://github.com/loveemu/snsf9x> |
| ST-Sound | <http://leonard.oxg.free.fr/stsound_download.html> |
| Timidity++ | <https://timidity.sourceforge.net> |
| UnrarKIT | <https://github.com/abbeycode/UnrarKit> |
| V2M tinyplayer | <https://github.com/jgilje/v2m-player> |
| VGMStream | <https://github.com/vgmstream/vgmstream> |
| VIO2SF | <https://bitbucket.org/kode54/vio2sf/src/master> |
| WebNEZ | <https://bitbucket.org/wothke/webnez/src/master> |
| WebSID | <https://www.wothke.ch/websid> |
| XSF (2SF, NCSF) | <https://github.com/CyberBotX/in_xsf> |
| ZXTune | <https://bitbucket.org/zxtune/zxtune/src/develop> |

### Not decoders

iOS interface and networking pieces, irrelevant to us and listed only so nobody has to work out why they were skipped.

| library | upstream |
| --- | --- |
| ASIHTTPRequest | <https://allseeing-i.com/ASIHTTPRequest> |
| BButton | <https://github.com/mattlawer/BButton> |
| CBAutoScrollLabel | <https://github.com/cbess/AutoScrollLabel> |
| CMPopTipView | <https://github.com/chrismiles/CMPopTipView> |
| Dear ImGui | <https://github.com/ocornut/imgui> |
