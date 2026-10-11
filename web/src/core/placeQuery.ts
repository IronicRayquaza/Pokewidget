/**
 * Turning what someone types into something Open-Meteo's geocoder can answer — a port of
 * `data/PlaceQuery.kt`.
 *
 * The geocoder matches place *names* only: "Richmond VA" comes back as Richmond Vale and
 * "Richmond Virginia" comes back empty. So a query is read as a name to ask for plus a
 * qualifier (state, province, country) to check the answers against, several ways, before
 * the raw text is sent as a last resort.
 */

export interface GeoResult {
  name: string;
  latitude: number;
  longitude: number;
  admin1?: string | null;
  country?: string | null;
  countryCode?: string | null;
}

export interface Place {
  label: string;
  latitude: number;
  longitude: number;
}

export interface Attempt {
  name: string;
  qualifier: string | null;
}

const MAX_QUALIFIER_WORDS = 3;

/** The readings of `query`, most likely first. */
export function attempts(query: string): Attempt[] {
  const trimmed = query.trim().replace(/\s+/g, ' ');
  if (trimmed.length < 2) return [];

  const comma = trimmed.indexOf(',');
  if (comma >= 0) {
    const name = trimmed.slice(0, comma).trim();
    const qualifier = trimmed.slice(comma + 1).trim().replace(/^,+|,+$/g, '').trim();
    if (name.length < 2) return [];
    return [{ name, qualifier: qualifier || null }];
  }

  const words = trimmed.split(' ');
  const splits: Attempt[] = [];
  for (let k = 1; k <= Math.min(MAX_QUALIFIER_WORDS, words.length - 1); k++) {
    splits.push({ name: words.slice(0, -k).join(' '), qualifier: words.slice(-k).join(' ') });
  }
  return [...splits, { name: trimmed, qualifier: null }];
}

/** The answers that fit `qualifier`, or null when none do, so the next reading is tried. */
export function filter(results: GeoResult[], qualifier: string | null): GeoResult[] | null {
  const nonEmpty = (list: GeoResult[]) => (list.length ? list : null);
  if (qualifier === null) return nonEmpty(results);
  const tokens = qualifier.split(/[\s,]+/).map(fold).filter(Boolean);
  if (!tokens.length) return nonEmpty(results);
  return nonEmpty(results.filter((r) => tokens.every((t) => hits(t, r))));
}

export function toPlace(result: GeoResult): Place {
  const region = [result.admin1, result.country].filter((s): s is string => Boolean(s?.trim())).join(', ');
  return {
    label: region ? `${result.name}, ${region}` : result.name,
    latitude: result.latitude,
    longitude: result.longitude,
  };
}

function hits(token: string, result: GeoResult): boolean {
  const code = result.countryCode ? fold(result.countryCode) : null;
  if (code && (token === code || COUNTRY_ALIASES[token] === code)) return true;

  const admin1 = result.admin1 ? fold(result.admin1) : null;
  if (admin1 && SUBDIVISIONS.get(token)?.some(([cc, name]) => cc === code && fold(name) === admin1)) return true;

  if (token.length < 2) return false;
  return [admin1, result.country ? fold(result.country) : null]
    .filter((s): s is string => s !== null)
    .flatMap((s) => s.split(' '))
    .some((word) => word.startsWith(token));
}

function fold(s: string): string {
  return s
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9 ]/g, ' ')
    .trim()
    .replace(/\s+/g, ' ');
}

const COUNTRY_ALIASES: Record<string, string> = {
  usa: 'us',
  america: 'us',
  uk: 'gb',
  britain: 'gb',
  england: 'gb',
  scotland: 'gb',
  wales: 'gb',
};

const SUBDIVISIONS = (() => {
  const map = new Map<string, Array<[string, string]>>();
  const add = (cc: string, list: string) => {
    for (const pair of list.split(';')) {
      const [abbr = '', name = ''] = pair.split('=');
      map.set(abbr, [...(map.get(abbr) ?? []), [cc, name]]);
    }
  };
  add(
    'us',
    'al=Alabama;ak=Alaska;az=Arizona;ar=Arkansas;ca=California;co=Colorado;ct=Connecticut;de=Delaware;' +
      'fl=Florida;ga=Georgia;hi=Hawaii;id=Idaho;il=Illinois;in=Indiana;ia=Iowa;ks=Kansas;ky=Kentucky;' +
      'la=Louisiana;me=Maine;md=Maryland;ma=Massachusetts;mi=Michigan;mn=Minnesota;ms=Mississippi;' +
      'mo=Missouri;mt=Montana;ne=Nebraska;nv=Nevada;nh=New Hampshire;nj=New Jersey;nm=New Mexico;' +
      'ny=New York;nc=North Carolina;nd=North Dakota;oh=Ohio;ok=Oklahoma;or=Oregon;pa=Pennsylvania;' +
      'ri=Rhode Island;sc=South Carolina;sd=South Dakota;tn=Tennessee;tx=Texas;ut=Utah;vt=Vermont;' +
      'va=Virginia;wa=Washington;wv=West Virginia;wi=Wisconsin;wy=Wyoming;dc=District of Columbia',
  );
  add(
    'ca',
    'ab=Alberta;bc=British Columbia;mb=Manitoba;nb=New Brunswick;nl=Newfoundland and Labrador;' +
      'ns=Nova Scotia;nt=Northwest Territories;nu=Nunavut;on=Ontario;pe=Prince Edward Island;qc=Quebec;' +
      'sk=Saskatchewan;yt=Yukon',
  );
  add(
    'au',
    'nsw=New South Wales;vic=Victoria;qld=Queensland;wa=Western Australia;sa=South Australia;' +
      'tas=Tasmania;nt=Northern Territory;act=Australian Capital Territory',
  );
  return map;
})();
