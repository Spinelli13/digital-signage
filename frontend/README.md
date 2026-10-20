# Frontend

Painel Web administrativo — Angular 21 + TypeScript (componentes standalone, signals, sem zone.js).

Protótipo de UX de referência: [prototipo_painel_digital_signage.html](../prototipo_painel_digital_signage%20(1).html).

## Telas

- **Login** (JWT guardado no `localStorage`; 401 da API volta ao login).
- **Dashboard** — contagem de dispositivos online / aguardando ativação, mídias, playlists e lista de dispositivos que precisam de atenção (atualiza a cada 10 s).
- **Dispositivos** — ativar pelo código exibido na TV, editar nome/local, escolher a playlist, desativar/reativar, excluir.
- **Biblioteca** — upload de JPG/PNG/WEBP/MP4 com barra de progresso (a duração do vídeo é lida no navegador), miniaturas, exclusão.
- **Playlists** — editor com ordem, duração por item e "Publicar alterações".

Fora do MVP (existem no protótipo, não implementadas): Campanhas, Pontos & Locais, Relatórios, Configurações.

## Rodando

```bash
cd frontend
npm install
npm start          # http://localhost:4200
```

A URL da API fica em [`public/config.json`](./public/config.json) e é lida **em tempo de execução** — para apontar para outro backend basta editar esse arquivo, sem recompilar (padrão: `http://localhost:8080`).

## Testes e build

```bash
npm test -- --watch=false   # Vitest
npm run build               # saída em dist/painel/browser
```

Os testes cobrem a lógica que mais quebra: editor de playlist (ordem, duração mínima, versões), status dos dispositivos, interceptor de autenticação (JWT só para a API, nunca para outros hosts; 401 desloga) e guard de rota.

Deploy: ver [docs/DEPLOY.md](../docs/DEPLOY.md).
