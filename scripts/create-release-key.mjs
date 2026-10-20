#!/usr/bin/env node
// Cria a chave de assinatura do APK do Player (uma única vez na vida do projeto).
//
//   node scripts/create-release-key.mjs
//
// Gera ~/.signage/signage-release.jks e player/keystore.properties (ignorado pelo git). A senha é aleatória e
// NÃO é exibida. FAÇA BACKUP da pasta ~/.signage: o Android só aceita atualizar um app instalado se a nova
// versão for assinada pela MESMA chave. Sem ela, só dá para atualizar desinstalando e reinstalando cada TV.
import { execFileSync } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { existsSync, mkdirSync, writeFileSync, chmodSync, copyFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join, resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const dir = join(homedir(), '.signage');
const keystore = join(dir, 'signage-release.jks');
const propsFile = join(root, 'player', 'keystore.properties');

if (existsSync(keystore)) {
  console.error(`Já existe uma chave em ${keystore}. Não vou sobrescrever (isso invalidaria as atualizações).`);
  process.exit(1);
}

const keytool = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', 'keytool') : 'keytool';
const password = randomBytes(18).toString('base64url'); // 24 caracteres, sem símbolos problemáticos
mkdirSync(dir, { recursive: true });

execFileSync(
  keytool,
  [
    '-genkeypair', '-alias', 'signage', '-keyalg', 'RSA', '-keysize', '4096', '-validity', '36500',
    '-storetype', 'PKCS12', '-keystore', keystore, '-storepass', password, '-keypass', password,
    '-dname', 'CN=Digital Signage Player, O=Digital Signage, C=BR',
  ],
  { stdio: ['ignore', 'ignore', 'inherit'] },
);

// Barras normais: o Gradle aceita no Windows e o arquivo continua sem escapes.
writeFileSync(
  propsFile,
  `storeFile=${keystore.replace(/\\/g, '/')}\nstorePassword=${password}\nkeyAlias=signage\nkeyPassword=${password}\n`,
);
// Cópia ao lado da chave: assim o backup de ~/.signage já leva tudo que é preciso.
copyFileSync(propsFile, join(dir, 'keystore.properties'));
try {
  chmodSync(propsFile, 0o600);
  chmodSync(keystore, 0o600);
} catch {
  // Windows ignora chmod
}

console.log(`Chave criada: ${keystore}`);
console.log(`Credenciais:  ${propsFile} (fora do git)`);
console.log('FAÇA BACKUP da pasta', dir, 'em um lugar seguro (pen drive/cofre de senhas).');
