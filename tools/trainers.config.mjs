/**
 * Human metadata for the trainer picker. The generator lists every sprite Showdown hosts;
 * this file says who each one is, which region they belong to and what role they play,
 * because none of that can be read off a filename.
 *
 * Anything not named here is still shipped — as a generic trainer class, filed under the
 * region its `-genN` suffix points at — and printed by the generator, so the table can
 * grow deliberately.
 */

export const REGIONS = [
  ['kanto', 'Kanto'],
  ['johto', 'Johto'],
  ['hoenn', 'Hoenn'],
  ['sinnoh', 'Sinnoh'],
  ['unova', 'Unova'],
  ['kalos', 'Kalos'],
  ['alola', 'Alola'],
  ['galar', 'Galar'],
  ['hisui', 'Hisui'],
  ['paldea', 'Paldea'],
  ['other', 'Elsewhere'],
];

export const ROLES = [
  ['player', 'Player'],
  ['rival', 'Rival'],
  ['leader', 'Gym Leader'],
  ['elite4', 'Elite Four'],
  ['champion', 'Champion'],
  ['frontier', 'Frontier Brain'],
  ['villain', 'Villain'],
  ['professor', 'Professor'],
  ['notable', 'Notable'],
  ['generic', 'Trainer class'],
];

/** Which region a `-genN` suffix belongs to, for trainers not named below. */
export const REGION_BY_GEN = {
  1: 'kanto', 2: 'johto', 3: 'hoenn', 4: 'sinnoh', 5: 'unova',
  6: 'kalos', 7: 'alola', 8: 'galar', 9: 'paldea',
};

const table = (region, role, names) =>
  Object.fromEntries(Object.entries(names).map(([id, name]) => [id, { name, region, role }]));

/** base filename → who they are. */
export const CHARACTERS = {
  // ---- Kanto -------------------------------------------------------------------
  ...table('kanto', 'player', { red: 'Red', leaf: 'Leaf', green: 'Green', yellow: 'Yellow' }),
  ...table('kanto', 'rival', { blue: 'Blue' }),
  ...table('kanto', 'leader', {
    brock: 'Brock', misty: 'Misty', ltsurge: 'Lt. Surge', erika: 'Erika', koga: 'Koga',
    sabrina: 'Sabrina', blaine: 'Blaine', janine: 'Janine',
  }),
  ...table('kanto', 'elite4', { lorelei: 'Lorelei', bruno: 'Bruno', agatha: 'Agatha' }),
  ...table('kanto', 'villain', {
    giovanni: 'Giovanni', rocketgrunt: 'Team Rocket Grunt', rocketgruntf: 'Team Rocket Grunt ♀',
    teamrocket: 'Team Rocket', jessiejames: 'Jessie & James', rocketexecutive: 'Rocket Executive',
    rocketexecutivef: 'Rocket Executive ♀', rocket: 'Team Rocket',
    teamrocketgruntm: 'Team Rocket Grunt', teamrocketgruntf: 'Team Rocket Grunt ♀',
    rainbowrocketgrunt: 'Rainbow Rocket Grunt', rainbowrocketgruntf: 'Rainbow Rocket Grunt ♀',
  }),
  ...table('kanto', 'professor', { oak: 'Professor Oak', samsonoak: 'Samson Oak' }),
  ...table('kanto', 'notable', { bill: 'Bill', mrfuji: 'Mr. Fuji', daisy: 'Daisy' }),

  // ---- Johto -------------------------------------------------------------------
  ...table('johto', 'player', { ethan: 'Ethan', lyra: 'Lyra', kris: 'Kris' }),
  ...table('johto', 'rival', { silver: 'Silver' }),
  ...table('johto', 'leader', {
    falkner: 'Falkner', bugsy: 'Bugsy', whitney: 'Whitney', morty: 'Morty', chuck: 'Chuck',
    jasmine: 'Jasmine', pryce: 'Pryce', clair: 'Clair',
  }),
  ...table('johto', 'elite4', { will: 'Will', karen: 'Karen' }),
  ...table('johto', 'champion', { lance: 'Lance' }),
  ...table('johto', 'villain', { archer: 'Archer', ariana: 'Ariana', proton: 'Proton', petrel: 'Petrel' }),
  ...table('johto', 'professor', { elm: 'Professor Elm' }),
  ...table('johto', 'notable', { eusine: 'Eusine', kurt: 'Kurt' }),

  // ---- Hoenn -------------------------------------------------------------------
  ...table('hoenn', 'player', { brendan: 'Brendan', may: 'May' }),
  ...table('hoenn', 'rival', { wally: 'Wally' }),
  ...table('hoenn', 'leader', {
    roxanne: 'Roxanne', brawly: 'Brawly', wattson: 'Wattson', flannery: 'Flannery',
    norman: 'Norman', winona: 'Winona', tate: 'Tate', liza: 'Liza', tateandliza: 'Tate & Liza',
    juan: 'Juan',
  }),
  ...table('hoenn', 'elite4', { sidney: 'Sidney', phoebe: 'Phoebe', glacia: 'Glacia', drake: 'Drake' }),
  ...table('hoenn', 'champion', { steven: 'Steven', wallace: 'Wallace' }),
  ...table('hoenn', 'frontier', {
    anabel: 'Anabel', brandon: 'Brandon', greta: 'Greta', lucy: 'Lucy', noland: 'Noland',
    spenser: 'Spenser', tucker: 'Tucker', scott: 'Scott',
  }),
  ...table('hoenn', 'villain', {
    maxie: 'Maxie', archie: 'Archie', tabitha: 'Tabitha', shelly: 'Shelly', courtney: 'Courtney',
    matt: 'Matt', aquagrunt: 'Team Aqua Grunt', aquagruntf: 'Team Aqua Grunt ♀',
    magmagrunt: 'Team Magma Grunt', magmagruntf: 'Team Magma Grunt ♀',
    teamaquagruntm: 'Team Aqua Grunt', teamaquagruntf: 'Team Aqua Grunt ♀',
    teammagmagruntm: 'Team Magma Grunt', teammagmagruntf: 'Team Magma Grunt ♀',
    aquasuit: 'Team Aqua (Diving Suit)', magmasuit: 'Team Magma (Suit)', teamaquabeta: 'Team Aqua (Beta)',
  }),
  ...table('hoenn', 'professor', { birch: 'Professor Birch' }),
  ...table('hoenn', 'notable', { mrstone: 'Mr. Stone', mrbriney: 'Mr. Briney', lisia: 'Lisia', zinnia: 'Zinnia' }),

  // ---- Sinnoh ------------------------------------------------------------------
  ...table('sinnoh', 'player', { lucas: 'Lucas', dawn: 'Dawn' }),
  ...table('sinnoh', 'rival', { barry: 'Barry' }),
  ...table('sinnoh', 'leader', {
    roark: 'Roark', gardenia: 'Gardenia', maylene: 'Maylene', crasherwake: 'Crasher Wake',
    fantina: 'Fantina', byron: 'Byron', candice: 'Candice', volkner: 'Volkner',
  }),
  ...table('sinnoh', 'elite4', { aaron: 'Aaron', bertha: 'Bertha', flint: 'Flint', lucian: 'Lucian' }),
  ...table('sinnoh', 'champion', { cynthia: 'Cynthia' }),
  ...table('sinnoh', 'frontier', {
    palmer: 'Palmer', thorton: 'Thorton', dahlia: 'Dahlia', darach: 'Darach', argenta: 'Argenta',
  }),
  ...table('sinnoh', 'villain', {
    cyrus: 'Cyrus', mars: 'Mars', jupiter: 'Jupiter', saturn: 'Saturn', charon: 'Charon',
    galacticgrunt: 'Team Galactic Grunt', galacticgruntf: 'Team Galactic Grunt ♀',
  }),
  ...table('sinnoh', 'professor', { rowan: 'Professor Rowan' }),
  ...table('sinnoh', 'notable', { riley: 'Riley', cheryl: 'Cheryl', buck: 'Buck', marley: 'Marley', mira: 'Mira' }),

  // ---- Unova -------------------------------------------------------------------
  ...table('unova', 'player', { hilbert: 'Hilbert', hilda: 'Hilda', nate: 'Nate', rosa: 'Rosa' }),
  ...table('unova', 'rival', { cheren: 'Cheren', bianca: 'Bianca', n: 'N', hugh: 'Hugh' }),
  ...table('unova', 'leader', {
    cilan: 'Cilan', chili: 'Chili', cress: 'Cress', lenora: 'Lenora', burgh: 'Burgh',
    elesa: 'Elesa', clay: 'Clay', skyla: 'Skyla', brycen: 'Brycen', drayden: 'Drayden',
    iris: 'Iris', roxie: 'Roxie', marlon: 'Marlon',
  }),
  ...table('unova', 'elite4', { shauntal: 'Shauntal', grimsley: 'Grimsley', caitlin: 'Caitlin', marshal: 'Marshal' }),
  ...table('unova', 'champion', { alder: 'Alder' }),
  ...table('unova', 'frontier', { ingo: 'Ingo', emmet: 'Emmet' }),
  ...table('unova', 'villain', {
    ghetsis: 'Ghetsis', colress: 'Colress', zinzolin: 'Zinzolin', shadowtriad: 'Shadow Triad',
    plasmagrunt: 'Team Plasma Grunt', plasmagruntf: 'Team Plasma Grunt ♀',
  }),
  ...table('unova', 'professor', { juniper: 'Professor Juniper', cedricjuniper: 'Cedric Juniper' }),
  ...table('unova', 'notable', { benga: 'Benga', brycenman: 'Brycen-Man', anthea: 'Anthea', concordia: 'Concordia' }),

  // ---- Kalos -------------------------------------------------------------------
  ...table('kalos', 'player', { calem: 'Calem', serena: 'Serena' }),
  ...table('kalos', 'rival', { shauna: 'Shauna', tierno: 'Tierno', trevor: 'Trevor' }),
  ...table('kalos', 'leader', {
    viola: 'Viola', grant: 'Grant', korrina: 'Korrina', ramos: 'Ramos', clemont: 'Clemont',
    valerie: 'Valerie', olympia: 'Olympia', wulfric: 'Wulfric',
  }),
  ...table('kalos', 'elite4', { malva: 'Malva', siebold: 'Siebold', wikstrom: 'Wikstrom', drasna: 'Drasna' }),
  ...table('kalos', 'champion', { diantha: 'Diantha' }),
  ...table('kalos', 'villain', {
    lysandre: 'Lysandre', xerosic: 'Xerosic', aliana: 'Aliana', bryony: 'Bryony', celosia: 'Celosia',
    mable: 'Mable', flaregrunt: 'Team Flare Grunt', flaregruntf: 'Team Flare Grunt ♀',
  }),
  ...table('kalos', 'professor', { sycamore: 'Professor Sycamore' }),
  ...table('kalos', 'notable', { az: 'AZ', dexio: 'Dexio', alain: 'Alain', emma: 'Emma' }),

  // ---- Alola -------------------------------------------------------------------
  ...table('alola', 'player', { elio: 'Elio', selene: 'Selene' }),
  ...table('alola', 'rival', { hau: 'Hau', gladion: 'Gladion' }),
  ...table('alola', 'leader', {
    ilima: 'Ilima', lana: 'Lana', kiawe: 'Kiawe', mallow: 'Mallow', sophocles: 'Sophocles',
    acerola: 'Acerola', mina: 'Mina', hala: 'Hala', olivia: 'Olivia', nanu: 'Nanu', hapu: 'Hapu',
  }),
  ...table('alola', 'elite4', { molayne: 'Molayne', kahili: 'Kahili' }),
  ...table('alola', 'villain', {
    guzma: 'Guzma', plumeria: 'Plumeria', lusamine: 'Lusamine', faba: 'Faba',
    skullgrunt: 'Team Skull Grunt', skullgruntf: 'Team Skull Grunt ♀',
    aetherfoundation: 'Aether Foundation', aetherfoundation2: 'Aether Foundation', aetherfoundationf: 'Aether Foundation ♀',
  }),
  ...table('alola', 'professor', { kukui: 'Professor Kukui', burnet: 'Professor Burnet' }),
  ...table('alola', 'notable', { lillie: 'Lillie', wicke: 'Wicke', ryuki: 'Ryuki' }),

  // ---- Galar -------------------------------------------------------------------
  ...table('galar', 'player', { victor: 'Victor', gloria: 'Gloria' }),
  ...table('galar', 'rival', { hop: 'Hop', bede: 'Bede', marnie: 'Marnie' }),
  ...table('galar', 'leader', {
    milo: 'Milo', nessa: 'Nessa', kabu: 'Kabu', bea: 'Bea', allister: 'Allister', opal: 'Opal',
    gordie: 'Gordie', melony: 'Melony', piers: 'Piers', raihan: 'Raihan', klara: 'Klara', avery: 'Avery',
  }),
  ...table('galar', 'champion', { leon: 'Leon' }),
  ...table('galar', 'villain', {
    rose: 'Chairman Rose', oleana: 'Oleana', sordward: 'Sordward', shielbert: 'Shielbert',
    yellgrunt: 'Team Yell Grunt', yellgruntf: 'Team Yell Grunt ♀',
  }),
  ...table('galar', 'professor', { magnolia: 'Professor Magnolia', sonia: 'Sonia' }),
  ...table('galar', 'notable', { mustard: 'Mustard', honey: 'Honey', peony: 'Peony' }),

  // ---- Hisui -------------------------------------------------------------------
  ...table('hisui', 'player', { rei: 'Rei', akari: 'Akari' }),
  ...table('hisui', 'notable', {
    adaman: 'Adaman', irida: 'Irida', cyllene: 'Cyllene', kamado: 'Kamado', volo: 'Volo',
    laventon: 'Professor Laventon', laventon2: 'Professor Laventon', ingo: 'Ingo',
    diamondclanmember: 'Diamond Clan member', pearlclanmember: 'Pearl Clan member',
    arezu: 'Arezu', calaba: 'Calaba', lian: 'Lian', melli: 'Melli', palina: 'Palina', sabi: 'Sabi',
    gaeric: 'Gaeric', zisu: 'Zisu', mai: 'Mai', beni: 'Beni', grisham: 'Grisham', ginter: 'Ginter',
  }),

  // ---- Paldea ------------------------------------------------------------------
  ...table('paldea', 'player', { florian: 'Florian', juliana: 'Juliana' }),
  ...table('paldea', 'rival', { nemona: 'Nemona', arven: 'Arven', penny: 'Penny', kieran: 'Kieran', carmine: 'Carmine' }),
  ...table('paldea', 'leader', {
    katy: 'Katy', brassius: 'Brassius', iono: 'Iono', kofu: 'Kofu', larry: 'Larry',
    ryme: 'Ryme', tulip: 'Tulip', grusha: 'Grusha',
  }),
  ...table('paldea', 'elite4', {
    rika: 'Rika', poppy: 'Poppy', hassel: 'Hassel', crispin: 'Crispin', amarys: 'Amarys', lacey: 'Lacey', drayton: 'Drayton',
  }),
  ...table('paldea', 'champion', { geeta: 'Geeta' }),
  ...table('paldea', 'villain', {
    giacomo: 'Giacomo', mela: 'Mela', atticus: 'Atticus', ortega: 'Ortega', eri: 'Eri',
    stargrunt: 'Team Star Grunt', stargruntf: 'Team Star Grunt ♀',
  }),
  ...table('paldea', 'professor', { sada: 'Professor Sada', turo: 'Professor Turo' }),
  ...table('paldea', 'notable', { briar: 'Briar', jacq: 'Jacq', dendra: 'Dendra', clavell: 'Director Clavell', salvatore: 'Salvatore', tyme: 'Tyme' }),

  // ---- Elsewhere ---------------------------------------------------------------
  ...table('other', 'notable', { ash: 'Ash' }),
};

/** Display names for trainer classes whose filename does not read as English. */
export const CLASS_NAMES = {
  acetrainer: 'Ace Trainer', acetrainerf: 'Ace Trainer ♀', acetrainercouple: 'Ace Duo',
  acetrainersnow: 'Ace Trainer (Snow)', acetrainersnowf: 'Ace Trainer ♀ (Snow)',
  aetheremployee: 'Aether Employee', aetheremployeef: 'Aether Employee ♀',
  aromalady: 'Aroma Lady', artistf: 'Artist ♀', backpacker: 'Backpacker', backpackerf: 'Backpacker ♀',
  backersf: 'Backers ♀', ballguy: 'Ball Guy', battlegirl: 'Battle Girl', birdkeeper: 'Bird Keeper',
  blackbelt: 'Black Belt', bodybuilderf: 'Bodybuilder ♀', bugcatcher: 'Bug Catcher',
  bugmaniac: 'Bug Maniac', cafemaster: 'Café Master', clerkf: 'Clerk ♀', cueball: 'Cue Ball',
  cyclistf: 'Cyclist ♀', delinquentf: 'Delinquent ♀', delinquentf2: 'Delinquent ♀', depotagent: 'Depot Agent',
  doctorf: 'Doctor ♀', doubleteam: 'Double Team', dragontamer: 'Dragon Tamer', expertf: 'Expert ♀',
  fairytalegirl: 'Fairy Tale Girl', firebreather: 'Fire Breather', freediver: 'Free Diver',
  furisodegirl: 'Furisode Girl', hexmaniac: 'Hex Maniac', jrtrainer: 'Jr. Trainer', jrtrainerf: 'Jr. Trainer ♀',
  kimonogirl: 'Kimono Girl', leaguestaff: 'League Staff', leaguestafff: 'League Staff ♀',
  ninjaboy: 'Ninja Boy', nurseryaide: 'Nursery Aide', officeworker: 'Office Worker',
  officeworkerf: 'Office Worker ♀', oldcouple: 'Old Couple', parasollady: 'Parasol Lady',
  pokefan: 'Poké Fan', pokefanf: 'Poké Fan ♀', pokekid: 'Poké Kid', pokekidf: 'Poké Kid ♀',
  pokemaniac: 'Poké Maniac', pokemonbreeder: 'Pokémon Breeder', pokemonbreederf: 'Pokémon Breeder ♀',
  pokemoncenterlady: 'Pokémon Center Lady', pokemonranger: 'Pokémon Ranger', pokemonrangerf: 'Pokémon Ranger ♀',
  preschoolerf: 'Preschooler ♀', psychicf: 'Psychic ♀', psychicfjp: 'Psychic ♀ (JP)', punkgirl: 'Punk Girl',
  punkguy: 'Punk Guy', railstaff: 'Rail Staff', richboy: 'Rich Boy', risingstar: 'Rising Star',
  risingstarf: 'Rising Star ♀', rollerskater: 'Roller Skater', rollerskaterf: 'Roller Skater ♀',
  ruinmaniac: 'Ruin Maniac', sbcmember: 'SBC Member', schoolboy: 'School Boy', schoolgirl: 'School Girl',
  schoolkid: 'School Kid', schoolkidf: 'School Kid ♀', scientistf: 'Scientist ♀', scubadiver: 'Scuba Diver',
  securitycorps: 'Security Corps', securitycorpsf: 'Security Corps ♀', sightseerf: 'Sightseer ♀',
  sisandbro: 'Sis and Bro', skierf: 'Skier ♀', skytrainer: 'Sky Trainer', skytrainerf: 'Sky Trainer ♀',
  srandjr: 'Sr. and Jr.', streetthug: 'Street Thug', supernerd: 'Super Nerd', swimmerf: 'Swimmer ♀',
  swimmerf2: 'Swimmer ♀', swimmerfjp: 'Swimmer ♀ (JP)', swimmerm: 'Swimmer', touristf: 'Tourist ♀',
  touristf2: 'Tourist ♀', trialguide: 'Trial Guide', trialguidef: 'Trial Guide ♀',
  triathletebiker: 'Triathlete (Biker)', triathletebikerf: 'Triathlete ♀ (Biker)', triathletebikerm: 'Triathlete (Biker)',
  triathleterunner: 'Triathlete (Runner)', triathleterunnerf: 'Triathlete ♀ (Runner)', triathleterunnerm: 'Triathlete (Runner)',
  triathleteswimmer: 'Triathlete (Swimmer)', triathleteswimmerf: 'Triathlete ♀ (Swimmer)', triathleteswimmerm: 'Triathlete (Swimmer)',
  tuberf: 'Tuber ♀', veteranf: 'Veteran ♀', workerf: 'Worker ♀', workerice: 'Worker (Ice)',
  youngathlete: 'Young Athlete', youngathletef: 'Young Athlete ♀', youngcouple: 'Young Couple', youngn: 'Young N',
  playerf: 'Player ♀', unknownf: 'Unknown ♀', hero2: 'Hero', heroine2: 'Heroine',
};

/** Human labels for the filename suffix that says which game a sprite comes from. */
export const VARIANT_LABELS = {
  gen1: 'Red & Green', gen1rb: 'Red & Blue', gen1main: 'Red & Blue (title)', gen1title: 'Red & Blue (title)',
  gen1champion: 'Red & Green, Champion', gen1rbchampion: 'Red & Blue, Champion', gen1two: 'Red & Green, rematch',
  gen1rbtwo: 'Red & Blue, rematch', gen2: 'Gold, Silver & Crystal', gen2c: 'Crystal', gen2jp: 'Gold & Silver (JP)',
  gen3: 'FireRed, LeafGreen & Emerald', gen3rs: 'Ruby & Sapphire', rs: 'Ruby & Sapphire', rse: 'Ruby, Sapphire & Emerald',
  e: 'Emerald', gen3jp: 'Gen 3 (JP)', gen3champion: 'FireRed & LeafGreen, Champion', gen3two: 'FireRed & LeafGreen, rematch',
  gen4: 'Platinum & HeartGold/SoulSilver', gen4dp: 'Diamond & Pearl', gen4pt: 'Platinum',
  gen5bw: 'Black & White', gen5bw2: 'Black 2 & White 2', gen6: 'Omega Ruby & Alpha Sapphire',
  gen6xy: 'X & Y', gen6oras: 'Omega Ruby & Alpha Sapphire', gen7: 'Sun & Moon', usum: 'Ultra Sun & Ultra Moon',
  gen8: 'Sword & Shield', gen9: 'Scarlet & Violet', lgpe: "Let's Go", lza: 'Legends: Z-A',
  masters: 'Pokémon Masters', masters2: 'Pokémon Masters (alt)', masters3: 'Pokémon Masters (alt 2)',
  masters4: 'Pokémon Masters (alt 3)', masters5: 'Pokémon Masters (alt 4)', conquest: 'Conquest',
  anime: 'Anime', contest: 'Contest', pokeathlon: 'Pokéathlon', league: 'League', champion: 'Champion',
};

const gen = (n) => REGION_BY_GEN[n];

/**
 * Trainers whose *back* sprite exists in a game's source, from pret's decompilations —
 * the view of the player from behind at the start of a battle. Showdown only has fronts.
 *
 * `frameH` slices a multi-frame throw animation strip and keeps the first (standing) pose.
 * Game Boy sprites are stored as 2-bit grayscale with the colours applied elsewhere in the
 * ROM, so `palette` recolours them (lightest → darkest), taken from the same repository.
 */
export const PRET = {
  pokeemerald: '5eff78649e7170a877b961ef0b3da13b81a16038',
  pokefirered: 'c75f352304d529f6ba92d4f74b9cf8b5c3810788',
  pokecrystal: '7a7881d0d62e0ddbd82dcf10e7116807487ac651',
  pokered: 'a1a22aaf84d1675bcdbaeb194592379d586d838e',
};

const emerald = (file) => ({ repo: 'pokeemerald', path: `graphics/trainers/back_pics/${file}.png`, frameH: 64 });
const firered = (file) => ({ repo: 'pokefirered', path: `graphics/trainers/back_pics/${file}_back_pic.png`, frameH: 64 });

export const BACK_SPRITES = {
  'brendan-gen3': emerald('brendan'),
  'may-gen3': emerald('may'),
  'steven-gen3': emerald('steven'),
  'wally-gen3': emerald('wally'),
  'brendan-gen3rs': firered('ruby_sapphire_brendan'),
  'brendan-rs': firered('ruby_sapphire_brendan'),
  'may-gen3rs': firered('ruby_sapphire_may'),
  'may-rs': firered('ruby_sapphire_may'),
  'red-gen3': firered('red'),
  'leaf-gen3': firered('leaf'),
  // Crystal: Chris (Ethan's original name) shares Cal's palette, Kris shares Falkner's.
  'ethan-gen2c': { repo: 'pokecrystal', path: 'gfx/player/chris_back.png', palette: ['#ffffff', '#ce9463', '#b54a29', '#000000'] },
  'ethan-gen2': { repo: 'pokecrystal', path: 'gfx/player/chris_back.png', palette: ['#ffffff', '#ce9463', '#b54a29', '#000000'] },
  'kris-gen2': { repo: 'pokecrystal', path: 'gfx/player/kris_back.png', palette: ['#ffffff', '#de8c73', '#3929ff', '#000000'] },
  // Red and Blue ran on a monochrome Game Boy; grey is the genuine article.
  'red-gen1': { repo: 'pokered', path: 'gfx/player/redb.png', palette: ['#ffffff', '#aaaaaa', '#555555', '#000000'] },
  'red-gen1rb': { repo: 'pokered', path: 'gfx/player/redb.png', palette: ['#ffffff', '#aaaaaa', '#555555', '#000000'] },
  'red-gen1main': { repo: 'pokered', path: 'gfx/player/redb.png', palette: ['#ffffff', '#aaaaaa', '#555555', '#000000'] },
};

/**
 * Battle backgrounds from the Showdown client, pinned to one commit and served via
 * jsDelivr. Tournament-branded ones (spl, scl, wcop, npa) are left out on purpose.
 */
export const SHOWDOWN_CLIENT_SHA = 'f2faebd6892b314d785a0038f77309afed36970c';

/**
 * Showdown draws its Gen 3 and Gen 4 backgrounds as a whole battle screen: coloured side
 * panels and a text box around the field. This is the field, measured from the images
 * (all share one layout) and inset a few pixels past the frame.
 */
const FRAMED = [156, 96, 428, 284];

// Gen 1 and Gen 2 are left out: their "backgrounds" are an empty white battle box.
export const BACKGROUNDS = [
  ['gen3', 'Ruby & Sapphire', 3, FRAMED],
  ['gen3-arena', 'Battle Arena', 3, FRAMED],
  ['gen3-cave', 'Cave', 3, FRAMED],
  ['gen3-forest', 'Forest', 3, FRAMED],
  ['gen3-ocean', 'Ocean', 3, FRAMED],
  ['gen3-sand', 'Desert', 3, FRAMED],
  ['gen4', 'Diamond & Pearl', 4, FRAMED],
  ['gen4-cave', 'Cave', 4, FRAMED],
  ['gen4-indoors', 'Indoors', 4, FRAMED],
  ['gen4-snow', 'Snow', 4, FRAMED],
  ['gen4-water', 'Water', 4, FRAMED],
  ['route', 'Route', 5],
  ['meadow', 'Meadow', 5],
  ['forest', 'Forest', 5],
  ['city', 'City', 5],
  ['river', 'River', 5],
  ['beach', 'Beach', 5],
  ['beachshore', 'Shoreline', 5],
  ['desert', 'Desert', 5],
  ['mountain', 'Mountain', 5],
  ['thunderplains', 'Thunder Plains', 5],
  ['deepsea', 'Deep Sea', 5],
  ['dampcave', 'Damp Cave', 5],
  ['earthycave', 'Earthy Cave', 5],
  ['icecave', 'Ice Cave', 5],
  ['volcanocave', 'Volcano Cave', 5],
];

export { gen };
