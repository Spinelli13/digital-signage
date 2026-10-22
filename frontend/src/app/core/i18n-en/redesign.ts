/** Inglês: textos do visual novo (estados vazios que ensinam, painel inicial, login, menu e avisos). */
export const REDESIGN: Record<string, string> = {
  // comuns
  'Mais ações': 'More actions',
  Atualizar: 'Refresh',
  'Baixar CSV': 'Download CSV',
  'Adicionar zona': 'Add zone',
  'Menu da conta': 'Account menu',
  'Ocultar senha': 'Hide password',
  'Mostrar senha': 'Show password',
  Operação: 'Operations',
  Monitoramento: 'Monitoring',
  Administração: 'Administration',

  // login
  'Todas as suas telas, em um só lugar.': 'All your screens, in one place.',
  'Veja quais TVs estão no ar, em tempo real.': 'See which TVs are on air, in real time.',
  'Monte playlists e campanhas com agenda.': 'Build playlists and scheduled campaigns.',
  'Receba alertas e acompanhe relatórios.': 'Get alerts and follow reports.',

  // painel inicial
  'Olá, {name}. Veja como está a sua rede agora.': 'Hello, {name}. Here is how your network looks right now.',
  'Ative uma TV para começar a acompanhar a rede.': 'Activate a TV to start monitoring your network.',
  'Tudo funcionando: todas as TVs ativas estão no ar.': 'All good: every active TV is on air.',
  '{n} TV(s) precisam de atenção.': '{n} TV(s) need attention.',
  'Vamos colocar a sua primeira tela no ar': "Let's get your first screen live",
  'Ative a TV': 'Activate the TV',
  'Envie as mídias': 'Upload your media',
  'Imagens, vídeos, páginas da web ou QR Codes.': 'Images, videos, web pages or QR Codes.',
  'Monte uma playlist': 'Build a playlist',
  'Escolha a ordem e o tempo de cada mídia.': 'Choose the order and duration of each item.',
  'Escolha o que a TV exibe': 'Choose what the TV shows',
  'Selecione a playlist na lista de dispositivos.': 'Pick the playlist in the devices list.',
  online: 'online',
  'Saúde da rede': 'Network health',
  '{online} de {active} TVs online': '{online} of {active} TVs online',
  '{percent}% das TVs ativas estão online': '{percent}% of active TVs are online',
  'Precisa de atenção': 'Needs attention',
  'Nenhuma TV precisa de você agora.': 'No TV needs you right now.',
  Ativar: 'Activate',
  'Crie o primeiro e escolha-o ao ativar cada TV.': 'Create the first one and pick it when you activate each TV.',
  'Nenhuma TV ativa ainda': 'No active TV yet',

  // dispositivos
  'Atualizar todas as TVs': 'Update all TVs',
  'Tente outra busca ou limpe os filtros.': 'Try another search or clear the filters.',
  'Nenhum dispositivo ainda': 'No devices yet',
  'Abra o app Player na TV: ele mostra um código de 6 caracteres. Depois clique em Adicionar dispositivo e digite esse código.':
    'Open the Player app on the TV: it shows a 6-character code. Then click Add device and type that code.',
  'v{version} disponível': 'v{version} available',
  'Agora: campanha {name}': 'Now: campaign {name}',
  'Na tela: {name}': 'On screen: {name}',
  'Player v{version}': 'Player v{version}',
  'Conexões, quedas, trocas de conteúdo e atualizações vão aparecendo aqui.': 'Connections, drops, content changes and updates will show up here.',

  // locais, grupos, layouts
  'Nenhum local ainda': 'No locations yet',
  'Crie o primeiro (por exemplo, Loja Centro) e escolha-o ao ativar cada TV. Assim você filtra e age em várias TVs de uma vez.':
    'Create the first one (for example, Downtown Store) and pick it when you activate each TV. That way you can filter and act on many TVs at once.',
  'Ver as TVs deste local': "See this location's TVs",
  'Nenhum grupo ainda': 'No groups yet',
  'Crie um (por exemplo, Recepções) e escolha quais TVs fazem parte. Depois é só trocar a playlist de todas de uma vez.':
    'Create one (for example, Lobbies) and choose which TVs belong to it. Then you can change the playlist of all of them at once.',
  'Ver as TVs deste grupo': "See this group's TVs",
  'Nenhum layout ainda': 'No layouts yet',
  'Escolha um modelo pronto (por exemplo, principal + faixa lateral) e ajuste as zonas. Sem layout, a TV exibe um conteúdo só, em tela cheia.':
    'Pick a ready-made template (for example, main + side strip) and adjust the zones. Without a layout, the TV shows a single item, full screen.',
  '{n} zona(s)': '{n} zone(s)',

  // conteúdo
  'Nenhuma mídia ainda': 'No media yet',
  'Nenhuma playlist ainda': 'No playlists yet',
  'Crie uma e adicione mídias da biblioteca: a ordem e a duração de cada item você define na hora.':
    'Create one and add media from the library: you set the order and duration of each item as you go.',
  'Esta playlist está vazia': 'This playlist is empty',
  'Biblioteca vazia': 'Library is empty',
  'Nenhuma campanha ainda': 'No campaigns yet',
  'Sem campanhas, cada TV exibe a sua playlist. Crie uma para, por exemplo, trocar o conteúdo nos fins de semana ou durante uma promoção.':
    'Without campaigns, each TV shows its own playlist. Create one to, for example, switch the content on weekends or during a promotion.',

  // alertas, relatórios, cobrança, plataforma
  'Nenhum alerta aberto': 'No open alerts',
  'Quando uma TV ficar sem contato, nunca se conectar ou o Player errar demais, o aviso aparece aqui.':
    'When a TV loses contact, never connects, or the Player fails too often, the alert shows up here.',
  'Nenhuma exibição registrada neste período.': 'No plays recorded in this period.',
  'As contagens vêm do Player (versão 0.5 ou mais nova) a cada contato com o servidor.':
    'Counts come from the Player (version 0.5 or newer) on every contact with the server.',
  'Quando você ativar as TVs, o inventário completo aparece aqui.': 'Once you activate TVs, the full inventory shows up here.',
  'campanha {name}': 'campaign {name}',
  'Nenhuma fatura ainda.': 'No invoices yet.',
  'A primeira é gerada perto do fim do período de teste.': 'The first one is generated near the end of the trial period.',
  'Nenhum cliente ainda': 'No clients yet',
  'Cadastre o primeiro.': 'Add the first one.',

  // servidor acordando
  'O servidor está acordando': 'The server is waking up',
  'Na primeira vez do dia isso pode levar até 1 minuto. As suas TVs não são afetadas: elas continuam exibindo o que já baixaram.':
    'The first time each day this can take up to 1 minute. Your TVs are not affected: they keep showing what they already downloaded.',
  'Servidor pronto': 'Server ready',
  'Já pode continuar.': 'You can continue now.',
  'O servidor não respondeu': 'The server did not respond',
  'Confira a sua internet. Vamos continuar tentando.': 'Check your internet connection. We will keep trying.',
  'Tentar de novo': 'Try again',
};
