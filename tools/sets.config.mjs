/**
 * Human metadata for each sprite set, keyed by its path under `sprites/pokemon/`.
 *
 * The generator discovers which directories actually exist and which Pokémon each
 * one covers; this file only supplies the things a machine can't infer — the game's
 * real name, the hardware it ran on, and the order we want them listed in.
 *
 * `animated: true` means the files are multi-frame GIFs. Everything else is a
 * single PNG and gets a generated idle animation in the widget.
 *
 * Sets served by veekun rather than PokeAPI live in VEEKUN_SETS below.
 *
 * Any directory found on disk but missing from this table is reported by the
 * generator and skipped, so adding a new upstream set is a visible, deliberate act.
 */
export const SET_META = {
  // ---- Animated sets: the reason this app exists -------------------------------
  'other/showdown': {
    label: 'Showdown',
    game: 'Pokémon Showdown',
    hardware: 'Fan-made',
    gen: 5,
    animated: true,
    ext: 'gif',
    order: 0,
    note: 'Gen 5 battle style, hand-drawn by the Smogon sprite project. The only animated set that covers every Pokémon through Gen 9.',
  },
  'versions/generation-v/black-white/animated': {
    label: 'Black / White',
    game: 'Pokémon Black & White',
    hardware: 'Nintendo DS',
    gen: 5,
    animated: true,
    ext: 'gif',
    order: 1,
    note: 'The original in-game animated battle sprites. Gen 1–5 only.',
  },
  'versions/generation-ii/crystal/animated': {
    label: 'Crystal',
    game: 'Pokémon Crystal',
    hardware: 'Game Boy Color',
    gen: 2,
    animated: true,
    ext: 'gif',
    order: 2,
    note: 'The first animated sprites in the series. Gen 1–2 only.',
  },

  // ---- Game Boy / Game Boy Color ----------------------------------------------
  'versions/generation-i/red-blue': {
    label: 'Red / Blue',
    game: 'Pokémon Red & Blue',
    hardware: 'Game Boy',
    gen: 1,
    animated: false,
    ext: 'png',
    order: 10,
  },
  'versions/generation-i/yellow': {
    label: 'Yellow',
    game: 'Pokémon Yellow',
    hardware: 'Game Boy',
    gen: 1,
    animated: false,
    ext: 'png',
    order: 11,
  },
  'versions/generation-ii/gold': {
    label: 'Gold',
    game: 'Pokémon Gold',
    hardware: 'Game Boy Color',
    gen: 2,
    animated: false,
    ext: 'png',
    order: 12,
  },
  'versions/generation-ii/silver': {
    label: 'Silver',
    game: 'Pokémon Silver',
    hardware: 'Game Boy Color',
    gen: 2,
    animated: false,
    ext: 'png',
    order: 13,
  },
  'versions/generation-ii/crystal': {
    label: 'Crystal (still)',
    game: 'Pokémon Crystal',
    hardware: 'Game Boy Color',
    gen: 2,
    animated: false,
    ext: 'png',
    order: 14,
  },

  // ---- Game Boy Advance --------------------------------------------------------
  'versions/generation-iii/ruby-sapphire': {
    label: 'Ruby / Sapphire',
    game: 'Pokémon Ruby & Sapphire',
    hardware: 'Game Boy Advance',
    gen: 3,
    animated: false,
    ext: 'png',
    order: 20,
  },
  'versions/generation-iii/emerald': {
    label: 'Emerald (still)',
    game: 'Pokémon Emerald',
    hardware: 'Game Boy Advance',
    gen: 3,
    animated: false,
    ext: 'png',
    order: 21,
  },
  'versions/generation-iii/firered-leafgreen': {
    label: 'FireRed / LeafGreen',
    game: 'Pokémon FireRed & LeafGreen',
    hardware: 'Game Boy Advance',
    gen: 3,
    animated: false,
    ext: 'png',
    order: 22,
  },

  // ---- Nintendo DS -------------------------------------------------------------
  'versions/generation-iv/diamond-pearl': {
    label: 'Diamond / Pearl (still)',
    game: 'Pokémon Diamond & Pearl',
    hardware: 'Nintendo DS',
    gen: 4,
    animated: false,
    ext: 'png',
    order: 30,
  },
  'versions/generation-iv/platinum': {
    label: 'Platinum (still)',
    game: 'Pokémon Platinum',
    hardware: 'Nintendo DS',
    gen: 4,
    animated: false,
    ext: 'png',
    order: 31,
  },
  'versions/generation-iv/heartgold-soulsilver': {
    label: 'HeartGold / SoulSilver (still)',
    game: 'Pokémon HeartGold & SoulSilver',
    hardware: 'Nintendo DS',
    gen: 4,
    animated: false,
    ext: 'png',
    order: 32,
  },
  'versions/generation-v/black-white': {
    label: 'Black / White (still)',
    game: 'Pokémon Black & White',
    hardware: 'Nintendo DS',
    gen: 5,
    animated: false,
    ext: 'png',
    order: 33,
  },

  // ---- Nintendo 3DS ------------------------------------------------------------
  'versions/generation-vi/x-y': {
    label: 'X / Y',
    game: 'Pokémon X & Y',
    hardware: 'Nintendo 3DS',
    gen: 6,
    animated: false,
    ext: 'png',
    order: 40,
  },
  'versions/generation-vi/omegaruby-alphasapphire': {
    label: 'Omega Ruby / Alpha Sapphire',
    game: 'Pokémon Omega Ruby & Alpha Sapphire',
    hardware: 'Nintendo 3DS',
    gen: 6,
    animated: false,
    ext: 'png',
    order: 41,
  },
  'versions/generation-vii/ultra-sun-ultra-moon': {
    label: 'Ultra Sun / Ultra Moon',
    game: 'Pokémon Ultra Sun & Ultra Moon',
    hardware: 'Nintendo 3DS',
    gen: 7,
    animated: false,
    ext: 'png',
    order: 42,
  },

  // ---- Nintendo Switch ---------------------------------------------------------
  'versions/generation-viii/brilliant-diamond-shining-pearl': {
    label: 'Brilliant Diamond / Shining Pearl',
    game: 'Pokémon Brilliant Diamond & Shining Pearl',
    hardware: 'Nintendo Switch',
    gen: 8,
    animated: false,
    ext: 'png',
    order: 50,
  },
  'versions/generation-ix/scarlet-violet': {
    label: 'Scarlet / Violet',
    game: 'Pokémon Scarlet & Violet',
    hardware: 'Nintendo Switch',
    gen: 9,
    animated: false,
    ext: 'png',
    order: 51,
  },

  'versions/generation-ix/champions': {
    label: 'Champions',
    game: 'Pokémon Champions',
    hardware: 'Nintendo Switch',
    gen: 9,
    animated: false,
    ext: 'png',
    order: 52,
  },

  // ---- Box / menu icons --------------------------------------------------------
  //
  // Generation 5's are the only animated ones: Black and White's PC boxes bob their icons,
  // and the dump preserves that as two-frame APNG. Sun/Moon and Sword/Shield icons are
  // genuinely still.
  'versions/generation-v/icons/animated': {
    label: 'Box icons (Gen 5, animated)',
    game: 'Pokémon Black & White',
    hardware: 'Nintendo DS',
    gen: 5,
    animated: true,
    // APNG, so the file extension really is png. The decoder sniffs the acTL chunk rather
    // than trusting this — see ApngFrames.isApng.
    ext: 'png',
    order: 7,
    note: 'The animated PC-box icons, exactly as Black and White drew them.',
  },
  'versions/generation-v/icons': {
    label: 'Box icons (Gen 5)',
    game: 'Pokémon Black & White',
    hardware: 'Nintendo DS',
    gen: 5,
    animated: false,
    ext: 'png',
    order: 59,
  },
  'versions/generation-vii/icons': {
    label: 'Box icons (Gen 7)',
    game: 'Pokémon Sun & Moon',
    hardware: 'Nintendo 3DS',
    gen: 7,
    animated: false,
    ext: 'png',
    order: 60,
  },
  'versions/generation-viii/icons': {
    label: 'Box icons (Gen 8)',
    game: 'Pokémon Sword & Shield',
    hardware: 'Nintendo Switch',
    gen: 8,
    animated: false,
    ext: 'png',
    order: 61,
  },

  // ---- High-resolution art -----------------------------------------------------
  '': {
    label: 'Default',
    game: 'PokéAPI default',
    hardware: 'Artwork',
    gen: 8,
    animated: false,
    ext: 'png',
    order: 69,
  },
  'other/official-artwork': {
    label: 'Official artwork',
    game: 'Promotional art',
    hardware: 'Artwork',
    gen: 0,
    animated: false,
    ext: 'png',
    order: 70,
  },
  'other/home': {
    label: 'HOME',
    game: 'Pokémon HOME',
    hardware: 'Render',
    gen: 8,
    animated: false,
    ext: 'png',
    order: 71,
  },
};

/**
 * Directory segments that describe a *variant of the same set* rather than a new set.
 * Anything else in a path (`animated`, a game name) is part of the set identity.
 */
export const VARIANT_SEGMENTS = new Set([
  'back',
  'shiny',
  'female',
  'transparent',
  'gray',
  'gbc',
]);

/**
 * Sets served by veekun.com rather than PokeAPI/sprites, keyed by their path under
 * `pokemon/main-sprites/`.
 *
 * These exist because PokeAPI simply does not carry them, and their absence is what made
 * "Emerald has no animated sprites" true. veekun's dump is the only public host of:
 *
 *  - `emerald/animated` — the real Generation 3 battle animations, 16–23 frames per
 *    Pokémon, normal and shiny, covering ids 1–386. Not a community re-creation: this is
 *    the animation Emerald itself plays.
 *  - `<gen 4 game>/frame2` — the second half of Generation 4's two-frame battle loop.
 *    Diamond/Pearl, Platinum and HeartGold/SoulSilver all animate in-game, but every
 *    mirror ships only the resting frame, so they look static everywhere else.
 *
 * `variants` is listed explicitly rather than discovered, because veekun is browsed over
 * HTML directory indexes and there is no cheap way to enumerate subdirectories reliably.
 * `frameDirs` names the directories whose files make up one animation, in playback order;
 * `frameDelaysMs` is how long each is held. The games rest longer than they move, hence
 * the uneven pairs.
 */
export const VEEKUN_SETS = {
  'emerald/animated': {
    label: 'Emerald',
    game: 'Pokémon Emerald',
    hardware: 'Game Boy Advance',
    gen: 3,
    animated: true,
    ext: 'gif',
    order: 3,
    variants: ['', 'shiny'],
    note: "The real Generation 3 battle animation, not a re-creation. Unown is stored per-form upstream rather than by id, so #201 falls back to another set.",
  },
  'diamond-pearl': {
    label: 'Diamond / Pearl',
    game: 'Pokémon Diamond & Pearl',
    hardware: 'Nintendo DS',
    gen: 4,
    animated: true,
    ext: 'png',
    order: 4,
    variants: [''],
    frameDirs: ['', 'frame2'],
    frameDelaysMs: [380, 220],
    note: 'The two-frame in-game idle, reassembled from the separately stored frames.',
  },
  'platinum': {
    label: 'Platinum',
    game: 'Pokémon Platinum',
    hardware: 'Nintendo DS',
    gen: 4,
    animated: true,
    ext: 'png',
    order: 5,
    variants: [''],
    frameDirs: ['', 'frame2'],
    frameDelaysMs: [380, 220],
    note: 'The two-frame in-game idle, reassembled from the separately stored frames.',
  },
  'heartgold-soulsilver': {
    label: 'HeartGold / SoulSilver',
    game: 'Pokémon HeartGold & SoulSilver',
    hardware: 'Nintendo DS',
    gen: 4,
    animated: true,
    ext: 'png',
    order: 6,
    variants: [''],
    frameDirs: ['', 'frame2'],
    frameDelaysMs: [380, 220],
    note: 'The two-frame in-game idle, reassembled from the separately stored frames.',
  },
};

/** Sets we deliberately skip, with the reason, so the generator's report stays clean. */
export const SKIPPED_SETS = {
  'other/dream-world': 'SVG only; no raster pipeline in the app.',
};
