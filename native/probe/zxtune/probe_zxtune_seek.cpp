// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/* Where does ZXTune's seek dereference null?
 *
 * `SIGSEGV` in `Module::AYMRenderer::SetPosition`, reading address 0, on his phone on 2026-09-10
 * while dragging the seek bar. The stack says the renderer object was fine and something inside it
 * was not, which is a guess until it is reproduced -- so this reproduces it, on the host, where a
 * crash costs nothing and a debugger is free.
 *
 * **One case per process**, because the first segfault would otherwise hide the rest:
 *
 *   before  -- SetPosition on a renderer that has never rendered
 *   after   -- Render one chunk, then SetPosition
 *   past    -- SetPosition beyond the tune's own duration
 *   zero    -- SetPosition to the very beginning
 *
 * Build: ./scripts/build-zxtune-probe.sh  (then link this against the same objects)
 *
 * The original probe's question, kept because its loader is reused whole:
 *
 * Does ZXTune play the ZX Spectrum tracker formats this build cannot?
 *
 * `docs/PLAN_FORMATS.md` §7: ayfly plays them -- 46 of 48, 99.6% weighted -- and cannot be shipped,
 * because its player headers carry no licence and its Z80 emulator is GPL-2-**only**. ZXTune is
 * LGPL-3, covers the same formats and more, and reaches that same GPL-2-only emulator from exactly
 * one plugin we do not need. This probe is what says whether the rest of it works.
 *
 * The questions are `Backend`'s, not "is there noise":
 *
 *   1. **Does it load from a buffer?** `Binary::CreateContainer` takes bytes. The app never has a
 *      path -- SAF hands it bytes.
 *   2. **Does it render into one?** `Renderer::Render` returns a `Sound::Chunk` of 16-bit stereo
 *      samples and an empty chunk at the end, so the end of a tune is stated rather than guessed.
 *   3. **Does it know how long the tune is?** `Information::Duration`. If it does, these files
 *      arrive with a seek bar, the way AHX did.
 *   4. **Is it audible?** Peak sample. "Loads" is not "plays".
 *
 * Format detection is ZXTune's own: each decoder checks the content, and this tries them in turn
 * and takes the first that produces a module. That is what the backend will do too -- and why the
 * `.pt3` files Modland stores with the wrong extension will still open.
 *
 * Build: ./scripts/build-zxtune-probe.sh
 */
#include "binary/container_factories.h"
#include "module/holder.h"
#include "module/information.h"
#include "module/renderer.h"
#include "parameters/container.h"
#include "sound/chunk.h"

#include "formats/chiptune/aym/ascsoundmaster.h"
#include "formats/chiptune/aym/protracker2.h"
#include "formats/chiptune/aym/protracker3.h"
#include "formats/chiptune/aym/soundtracker.h"
#include "formats/chiptune/aym/soundtrackerpro.h"
#include "formats/chiptune/aym/sqtracker.h"
#include "formats/chiptune/aym/ym.h"
#include "module/players/aym/aym_base.h"
#include "module/players/aym/ascsoundmaster.h"
#include "module/players/aym/fasttracker.h"
#include "module/players/aym/globaltracker.h"
#include "module/players/aym/prosoundmaker.h"
#include "module/players/aym/protracker1.h"
#include "module/players/aym/protracker2.h"
#include "module/players/aym/protracker3.h"
#include "module/players/aym/soundtracker.h"
#include "module/players/aym/soundtrackerpro.h"
#include "module/players/aym/sqtracker.h"
#include "module/players/aym/ymvtx.h"

#include <cstdio>
#include <cstdlib>
#include <string>
#include <type_traits>
#include <vector>

namespace
{
  enum
  {
    RATE = 44100,
    SECONDS = 8
  };

  /**
   * One format tried against the bytes.
   *
   * ZXTune's AY factories produce a `Chiptune`, not a `Holder` -- `AYM::CreateHolder` is the step
   * between, and the plugin layer that normally does it (`aym_plugin.cpp`) drags in the whole
   * plugin registry. Doing it by hand here keeps the probe to the formats and off the framework.
   */
  Module::Holder::Ptr Open(const Module::AYM::Factory& factory, const Binary::Container& data)
  {
    try
    {
      auto props = Parameters::Container::Create();
      if (auto chiptune = factory.CreateChiptune(data, props))
      {
        return Module::AYM::CreateHolder(std::move(chiptune));
      }
    }
    catch (const std::exception&)
    {
      // A decoder that half-recognises a truncated file throws. Both that and an empty result mean
      // "not this format", so both fall through to the next one.
    }
    return {};
  }
}  // namespace

int main(int argc, char** argv)
{
  if (argc < 3)
  {
    std::fprintf(stderr, "usage: probe-zxtune-seek FILE before|after|past|zero\n");
    return 2;
  }
  const std::string mode = argv[2];

  std::FILE* file = std::fopen(argv[1], "rb");
  if (!file) { std::puts("unreadable"); return 2; }
  std::fseek(file, 0, SEEK_END);
  const long length = std::ftell(file);
  std::fseek(file, 0, SEEK_SET);
  std::vector<uint8_t> bytes(length > 0 ? static_cast<std::size_t>(length) : 0);
  if (bytes.empty() || std::fread(bytes.data(), 1, bytes.size(), file) != bytes.size())
  {
    std::fclose(file);
    std::puts("unreadable");
    return 2;
  }
  std::fclose(file);

  const auto data = Binary::CreateContainer(Binary::View(bytes.data(), bytes.size()));

  namespace FC = Formats::Chiptune;
  Module::Holder::Ptr holder;
  // The same factories, in the same order, as the probe this borrows its loader from. Copied
  // rather than narrowed: a seek defect that only shows on one format is exactly the thing a
  // shortened list would miss.
  // **Two shapes of factory**, as the borrowed loader's own comment warns: most AY plugins hand
  // back an `AYM::Factory`, ProTracker3 a full `Module::Factory` that makes the holder itself.
  const auto attempt = [&](auto factory) {
    if (holder) return;
    if constexpr (std::is_same_v<decltype(factory), Module::Factory::Ptr>)
    {
      try
      {
        auto props = Parameters::Container::Create();
        holder = factory->CreateModule(*props, *data, props);
      }
      catch (const std::exception&) {}
    }
    else
    {
      holder = Open(*factory, *data);
    }
  };
  attempt(Module::ProTracker3::CreateFactory(FC::ProTracker3::CreateDecoder()));
  attempt(Module::ProTracker3::CreateFactory(FC::ProTracker3::VortexTracker2::CreateDecoder()));
  attempt(Module::ProTracker2::CreateFactory());
  attempt(Module::SoundTracker::CreateFactory(FC::SoundTracker::Ver1::CreateCompiledDecoder()));
  attempt(Module::SoundTracker::CreateFactory(FC::SoundTracker::Ver3::CreateDecoder()));
  attempt(Module::ASCSoundMaster::CreateFactory(FC::ASCSoundMaster::Ver1::CreateDecoder()));
  attempt(Module::SoundTrackerPro::CreateFactory(FC::SoundTrackerPro::CreateCompiledModulesDecoder()));
  attempt(Module::SQTracker::CreateFactory());
  attempt(Module::ProTracker1::CreateFactory());
  attempt(Module::ProSoundMaker::CreateFactory());
  attempt(Module::FastTracker::CreateFactory());
  attempt(Module::GlobalTracker::CreateFactory());
  attempt(Module::YMVTX::CreateFactory(FC::YM::CreatePackedYMDecoder()));
  attempt(Module::YMVTX::CreateFactory(FC::YM::CreateYMDecoder()));
  attempt(Module::YMVTX::CreateFactory(FC::YM::CreateVTXDecoder()));
  if (!holder) { std::puts("not recognised"); return 2; }

  const double duration =
      holder->GetModuleInformation().Duration.CastTo<Time::Second>().Get();
  std::printf("duration %.1fs\n", duration);
  std::fflush(stdout);

  // Exactly what `ZxTuneBackend::start()` does.
  auto renderer = holder->CreateRenderer(RATE, holder->GetModuleProperties());
  if (!renderer) { std::puts("RESULT no-renderer"); return 1; }

  if (mode == "after")
  {
    const auto chunk = renderer->Render();
    std::printf("rendered %zu samples first\n", chunk.size());
    std::fflush(stdout);
  }

  double target = duration * 0.9;
  if (mode == "past") target = duration + 30.0;
  if (mode == "zero") target = 0.0;
  // What the backend's clamp actually asks for, so the margin is measured rather than assumed.
  if (mode == "edge") target = duration > 0.5 ? duration - 0.5 : 0.0;
  if (mode == "hair") target = duration > 0.05 ? duration - 0.05 : 0.0;
  if (mode == "exact") target = duration;
  std::printf("seeking to %.1fs ...\n", target);
  std::fflush(stdout);

  renderer->SetPosition(
      Time::AtMillisecond() + Time::Milliseconds(static_cast<uint_t>(target * 1000.0)));

  std::puts("survived SetPosition");
  const auto chunk = renderer->Render();
  std::printf("RESULT ok, %zu samples after the seek\n", chunk.size());
  return 0;
}
