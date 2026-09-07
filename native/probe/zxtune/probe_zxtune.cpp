// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/* Does ZXTune play the ZX Spectrum tracker formats this build cannot?
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
  if (argc < 2)
  {
    std::fprintf(stderr, "usage: probe-zxtune FILE\n");
    return 2;
  }

  std::FILE* file = std::fopen(argv[1], "rb");
  if (!file)
  {
    std::puts("VERDICT unreadable");
    return 2;
  }
  std::fseek(file, 0, SEEK_END);
  const long length = std::ftell(file);
  std::fseek(file, 0, SEEK_SET);
  std::vector<uint8_t> bytes(length > 0 ? static_cast<std::size_t>(length) : 0);
  if (bytes.empty() || std::fread(bytes.data(), 1, bytes.size(), file) != bytes.size())
  {
    std::fclose(file);
    std::puts("VERDICT unreadable");
    return 2;
  }
  std::fclose(file);

  const auto data = Binary::CreateContainer(Binary::View(bytes.data(), bytes.size()));

  Module::Holder::Ptr holder;
  std::string opened;
  // **Two shapes of factory, and the difference is not decoration.** Most AY plugins hand back an
  // `AYM::Factory`, which makes a chiptune that `AYM::CreateHolder` turns into a module. ProTracker3
  // hands back a full `Module::Factory` that does both steps itself. The backend will meet the same
  // split, so the probe meets it here rather than discovering it later.
  const auto attempt = [&](const char* name, auto factory) {
    if (holder)
    {
      return;
    }
    Module::Holder::Ptr found;
    if constexpr (std::is_same_v<decltype(factory), Module::Factory::Ptr>)
    {
      try
      {
        auto props = Parameters::Container::Create();
        found = factory->CreateModule(*props, *data, props);
      }
      catch (const std::exception&)
      {
      }
    }
    else
    {
      found = Open(*factory, *data);
    }
    if (found)
    {
      holder = std::move(found);
      opened = name;
    }
  };

  // The same decoder/factory pairs `src/core/plugins/players/ay/*_supp.cpp` registers, in the order
  // that puts Modland's biggest formats first.
  namespace FC = Formats::Chiptune;
  attempt("PT3", Module::ProTracker3::CreateFactory(FC::ProTracker3::CreateDecoder()));
  attempt("PT3v", Module::ProTracker3::CreateFactory(FC::ProTracker3::VortexTracker2::CreateDecoder()));
  attempt("PT2", Module::ProTracker2::CreateFactory());
  attempt("STC", Module::SoundTracker::CreateFactory(FC::SoundTracker::Ver1::CreateCompiledDecoder()));
  attempt("ST1", Module::SoundTracker::CreateFactory(FC::SoundTracker::Ver1::CreateUncompiledDecoder()));
  attempt("ST3", Module::SoundTracker::CreateFactory(FC::SoundTracker::Ver3::CreateDecoder()));
  attempt("AS0", Module::ASCSoundMaster::CreateFactory(FC::ASCSoundMaster::Ver0::CreateDecoder()));
  attempt("ASC", Module::ASCSoundMaster::CreateFactory(FC::ASCSoundMaster::Ver1::CreateDecoder()));
  attempt("STP", Module::SoundTrackerPro::CreateFactory(FC::SoundTrackerPro::CreateCompiledModulesDecoder()));
  attempt("SQT", Module::SQTracker::CreateFactory());
  // Added after the app claimed these three names and the backend did not implement them. The probe
  // measures what `SupportedFormats` promises, or it is measuring something else.
  attempt("PT1", Module::ProTracker1::CreateFactory());
  attempt("PSM", Module::ProSoundMaker::CreateFactory());
  attempt("FTC", Module::FastTracker::CreateFactory());
  attempt("GTR", Module::GlobalTracker::CreateFactory());

  if (!holder)
  {
    std::puts("VERDICT reject:load");
    return 1;
  }

  // Everything from here can throw, and the first run found out the hard way: an exception out of
  // `GetModuleInformation` aborted the process, and `exit-6` on all 48 files says nothing about
  // which stage failed. Naming the stage is the difference between a measurement and a mystery.
  const char* stage = "information";
  try
  {
  const auto info = holder->GetModuleInformation();
  const auto stated = info.Duration.CastTo<Time::Second>().Get();

  stage = "renderer";
  // **The module's own properties, not a fresh container.** The AY renderer reads its frequency
  // table from parameters -- `aym_parameters.cpp` says "frequency table is mandatory!!!" and
  // throws when it is absent -- and the table is something the *file* chose, put there by the
  // chiptune during loading. Handing the renderer an empty container threw on every tune, at the
  // first `Render` rather than at construction, which is why it looked like a decoding failure.
  auto renderer = holder->CreateRenderer(RATE, holder->GetModuleProperties());
  stage = "render";
  long peak = 0;
  long frames = 0;
  bool ended = false;
  while (frames < static_cast<long>(RATE) * SECONDS)
  {
    const auto chunk = renderer->Render();
    if (chunk.empty())
    {
      ended = true;
      break;
    }
    for (const auto& sample : chunk)
    {
      for (const auto value : {sample.Left(), sample.Right()})
      {
        const long v = value < 0 ? -static_cast<long>(value) : value;
        if (v > peak)
        {
          peak = v;
        }
      }
    }
    frames += static_cast<long>(chunk.size());
  }

  std::printf("VERDICT %s as=%s len=%us ends=%s peak=%ld\n", peak > 0 ? "full" : "silent",
              opened.c_str(), static_cast<unsigned>(stated), ended ? "yes" : "no", peak);
  }
  catch (const std::exception& e)
  {
    std::printf("VERDICT reject:%s as=%s what=%s\n", stage, opened.c_str(), e.what());
    return 1;
  }
  catch (...)
  {
    // ZXTune's own `Error` is not a `std::exception`, so this arm is not belt and braces.
    std::printf("VERDICT reject:%s as=%s what=unknown\n", stage, opened.c_str());
    return 1;
  }
  return 0;
}
