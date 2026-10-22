import { ACCOUNT } from './account';
import { ATTENDANCE } from './attendance';
import { ATTENDANCE_REMOTE } from './attendance-remote';
import { CONTENT } from './content';
import { CORE } from './core';
import { HOME } from './home';
import { LAYOUTS } from './layouts';
import { OPS } from './ops';
import { ORIENTATION } from './orientation';
import { PLANS } from './plans';
import { PROMOTIONS } from './promotions';
import { PRICES } from './prices';
import { PRIVACY } from './privacy';
import { PROOF } from './proof';
import { REDESIGN } from './redesign';
import { SERVER } from './server';
import { WIDGETS } from './widgets';
import { ONBOARDING } from './onboarding';
import { PAIRING } from './pairing';
import { SERVER_DATA } from './server-data';

/** Todas as seções do inglês. A chave é sempre o texto em português; veja ../i18n.ts. */
const SECTIONS: Record<string, string>[] = [PAIRING, ONBOARDING, PRIVACY, CORE, HOME, OPS, CONTENT, ACCOUNT, REDESIGN, PROOF, WIDGETS, PRICES, LAYOUTS, ORIENTATION, ATTENDANCE, ATTENDANCE_REMOTE, PLANS, PROMOTIONS, SERVER, SERVER_DATA];

/** Chaves repetidas em seções diferentes com traduções diferentes (um teste exige que esta lista fique vazia). */
export const DICTIONARY_CONFLICTS: string[] = [];

/** Só o que o SERVIDOR devolve (mensagens de erro, planos de fábrica, histórico e alertas): usado por trServer. */
export const SERVER_TEXTS: Record<string, string> = { ...SERVER, ...SERVER_DATA };

export const EN: Record<string, string> = {};
for (const section of SECTIONS) {
  for (const [key, value] of Object.entries(section)) {
    if (key in EN && EN[key] !== value) {
      DICTIONARY_CONFLICTS.push(key);
    }
    EN[key] = value;
  }
}
