import { ACCOUNT } from './account';
import { CONTENT } from './content';
import { CORE } from './core';
import { HOME } from './home';
import { OPS } from './ops';
import { PROOF } from './proof';
import { REDESIGN } from './redesign';
import { SERVER } from './server';
import { SERVER_DATA } from './server-data';

/** Todas as seções do inglês. A chave é sempre o texto em português; veja ../i18n.ts. */
const SECTIONS: Record<string, string>[] = [CORE, HOME, OPS, CONTENT, ACCOUNT, REDESIGN, PROOF, SERVER, SERVER_DATA];

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
