/**
 * Inglês das mensagens de erro que o SERVIDOR devolve (sempre em português). As que levam nome ou número no meio
 * usam {valores}: o painel casa a mensagem real com o modelo e reaproveita os valores (veja trServer em i18n.ts).
 * As mensagens técnicas de configuração do servidor não entram aqui (só o administrador do serviço as vê).
 */
export const SERVER: Record<string, string> = {
  'E-mail ou senha inválidos': 'Invalid email or password',
  'A senha atual não confere': 'The current password is incorrect',
  'A senha nova precisa ter pelo menos {n} caracteres': 'The new password must have at least {n} characters',
  'A senha precisa ter pelo menos {n} caracteres': 'The password must have at least {n} characters',
  'Já existe um usuário com o e-mail {email}': 'A user with the email {email} already exists',
  'Você não pode desativar o seu próprio usuário': 'You cannot deactivate your own user',
  'Você não pode excluir o seu próprio usuário': 'You cannot delete your own user',
  'Este é o único administrador ativo do cliente: promova outro usuário a Administrador antes':
    'This is the only active administrator of the client: promote another user to Administrator first',
  'Este papel não pode ser dado pela equipe do cliente': "This role cannot be granted by the client's team",
  'Este usuário é da operação da plataforma e não pode ser alterado aqui': 'This user belongs to the platform operation and cannot be changed here',
  'O papel Editor só pode aplicar playlist e layout a várias TVs. As outras ações são do Administrador.':
    'The Editor role can only apply a playlist and layout to several TVs. The other actions belong to the Administrator.',
  'O papel Editor só pode escolher a playlist e o layout das TVs. Nome, local, grupos e situação são do Administrador.':
    'The Editor role can only choose the playlist and layout of TVs. Name, location, groups and status belong to the Administrator.',

  // planos, limites e cobrança
  'O plano {plan} permite até {n} TV(s) e todas já estão em uso. Exclua uma TV ou peça um plano maior.':
    'The {plan} plan allows up to {n} TV(s) and all of them are in use. Delete a TV or ask for a bigger plan.',
  'O plano {plan} permite até {n} MB de mídias e este arquivo passaria disso. Exclua mídias que não usa mais ou peça um plano maior.':
    'The {plan} plan allows up to {n} MB of media and this file would exceed it. Delete media you no longer use or ask for a bigger plan.',
  'O plano {plan} permite até {n} usuário(s) e todos já estão em uso. Desative um usuário ou peça um plano maior.':
    'The {plan} plan allows up to {n} user(s) and all of them are in use. Deactivate a user or ask for a bigger plan.',
  'A conta "{name}" foi encerrada.': 'The account "{name}" has been closed.',
  'A assinatura de "{name}" está suspensa. Regularize a cobrança para voltar a usar o painel (as TVs continuam exibindo o que já têm).':
    'The subscription of "{name}" is suspended. Settle the billing to use the panel again (the TVs keep playing what they already have).',
  'Esta fatura foi cancelada e não pode ser paga': 'This invoice was canceled and cannot be paid',
  'Esta fatura já foi paga': 'This invoice has already been paid',
  'Uma fatura paga não pode ser cancelada': 'A paid invoice cannot be canceled',
  'Fatura não encontrada: {id}': 'Invoice not found: {id}',
  'Já existe um cliente chamado "{name}"': 'A client named "{name}" already exists',
  'Já existe um plano com o código "{code}"': 'A plan with the code "{code}" already exists',
  'O código de um plano não pode ser alterado': 'A plan code cannot be changed',
  'O plano "{name}" é usado por {n} cliente(s). Troque o plano deles ou desative este plano em vez de excluir.':
    'The plan "{name}" is used by {n} client(s). Switch their plan or deactivate this plan instead of deleting it.',
  'O plano "{name}" está desativado': 'The plan "{name}" is deactivated',
  'O cliente tem {n} usuário(s) ativos e o plano {plan} permite {max}.': 'The client has {n} active user(s) and the {plan} plan allows {max}.',
  'O cliente usa {n} TV(s) e o plano {plan} permite {max}.': 'The client uses {n} TV(s) and the {plan} plan allows {max}.',
  'O cliente usa mais espaço de mídias do que o plano {plan} permite ({max} MB).':
    'The client uses more media storage than the {plan} plan allows ({max} MB).',
  'A situação SYSTEM é só da reserva de TVs': 'The SYSTEM status is only for the TV reserve',

  // não encontrado
  'Cliente não encontrado': 'Client not found',
  'Cliente não encontrado: {id}': 'Client not found: {id}',
  'Usuário não encontrado': 'User not found',
  'Usuário não encontrado: {id}': 'User not found: {id}',
  'Plano não encontrado: {id}': 'Plan not found: {id}',
  'Campanha não encontrada: {id}': 'Campaign not found: {id}',
  'Código de ativação inválido ou expirado': 'Invalid or expired activation code',
  'Dispositivo não encontrado: {id}': 'Device not found: {id}',
  'Dispositivo não encontrado entre: {ids}': 'Device not found among: {ids}',
  'Grupo não encontrado: {id}': 'Group not found: {id}',
  'Grupo não encontrado: {ids}': 'Group not found: {ids}',
  'Layout não encontrado: {id}': 'Layout not found: {id}',
  'Local não encontrado: {id}': 'Location not found: {id}',
  'Modelo de layout não encontrado: {id}': 'Layout template not found: {id}',
  'Mídia não encontrada: {id}': 'Media not found: {id}',
  'Playlist não encontrada: {id}': 'Playlist not found: {id}',
  'Playlist não encontrada entre: {ids}': 'Playlist not found among: {ids}',

  // conflitos e regras
  'Já existe um grupo chamado "{name}"': 'A group named "{name}" already exists',
  'Já existe um layout chamado "{name}"': 'A layout named "{name}" already exists',
  'Já existe um local chamado "{name}"': 'A location named "{name}" already exists',
  'A playlist "{playlist}" está em uso pela campanha "{campaign}". Troque a playlist da campanha ou exclua a campanha antes.':
    'The playlist "{playlist}" is used by the campaign "{campaign}". Change the campaign\'s playlist or delete the campaign first.',
  'A playlist "{playlist}" está em uso pelo layout "{layout}". Troque a playlist da zona ou exclua o layout antes.':
    'The playlist "{playlist}" is used by the layout "{layout}". Change the zone\'s playlist or delete the layout first.',
  'O layout "{name}" está em uso por {n} TV(s). Escolha outro layout (ou tela cheia) para elas antes de excluir.':
    'The layout "{name}" is used by {n} TV(s). Pick another layout (or full screen) for them before deleting.',
  'Muitos dispositivos aguardando ativação': 'Too many devices awaiting activation',

  // validações
  'A data de início não pode ser depois da data de fim': 'The start date cannot be after the end date',
  'A data inicial não pode ser depois da data final': 'The start date cannot be after the end date',
  'A prioridade precisa estar entre 0 e {max}': 'The priority must be between 0 and {max}',
  'O horário de início e o de fim não podem ser iguais (para o dia todo, deixe os dois vazios)':
    'The start and end times cannot be the same (for the whole day, leave both empty)',
  'Agrupamento inválido: {value} (use media, device ou day)': 'Invalid grouping: {value} (use media, device or day)',
  'Arquivo vazio': 'Empty file',
  'Cada zona precisa de um nome diferente': 'Each zone needs a different name',
  'As zonas "{a}" e "{b}" se sobrepõem': 'The zones "{a}" and "{b}" overlap',
  'O layout pode ter no máximo {n} zonas': 'A layout can have at most {n} zones',
  'O layout precisa de pelo menos uma zona': 'A layout needs at least one zone',
  'Escolha exatamente uma zona principal (a que exibe a playlist da TV)': "Choose exactly one main zone (the one that shows the TV's playlist)",
  '{label} começa fora da tela': '{label} starts off the screen',
  '{label} passa do limite da tela (100%)': '{label} goes past the screen limit (100%)',
  '{label} é a principal: ela exibe a playlist da TV, não uma playlist fixa': '{label} is the main one: it shows the TV\'s playlist, not a fixed playlist',
  '{label} é pequena demais (mínimo {min}% de largura e de altura)': '{label} is too small (minimum {min}% of width and height)',
  'Escolha para quem é a campanha (todas as TVs, TVs, locais ou grupos)': 'Choose who the campaign is for (all TVs, TVs, locations or groups)',
  '"Todas as TVs" não leva um id': '"All TVs" does not take an id',
  'Informe o id do alvo do tipo {type}': 'Provide the target id for type {type}',
  'Dia da semana inválido: {value} (use 1 = segunda ... 7 = domingo)': 'Invalid weekday: {value} (use 1 = Monday ... 7 = Sunday)',
  'Fuso horário inválido: {value}': 'Invalid time zone: {value}',
  'Informe a playlist (playlistId)': 'Provide the playlist (playlistId)',
  'Informe o grupo (groupId)': 'Provide the group (groupId)',
  'Informe o layout (layoutId)': 'Provide the layout (layoutId)',
  // fila de atendimento
  'Já existe uma fila chamada "{name}"': 'A queue named "{name}" already exists',
  'A letra "{prefix}" já é de outra fila: cada fila precisa de uma letra diferente': 'The letter "{prefix}" belongs to another queue: each queue needs a different letter',
  'Só dá para trocar a letra da fila quando não há senhas esperando nem em atendimento': 'The letter of a queue can only change when no tickets are waiting or being served',
  'A fila "{name}" ainda tem senhas esperando ou em atendimento: conclua ou cancele antes de excluir': 'The queue "{name}" still has tickets waiting or being served: finish or cancel them before deleting',
  'Já existe um guichê chamado "{name}"': 'A counter named "{name}" already exists',
  'O guichê "{name}" está com uma senha em andamento: conclua antes de desativá-lo': 'The counter "{name}" has a ticket in progress: finish it before disabling the counter',
  'O guichê "{name}" está com uma senha em andamento: conclua antes de excluí-lo': 'The counter "{name}" has a ticket in progress: finish it before deleting the counter',
  'A fila "{name}" está desativada': 'The queue "{name}" is disabled',
  'O guichê "{name}" está desativado': 'The counter "{name}" is disabled',
  'O guichê "{name}" ainda está com a senha {label}: conclua o atendimento antes de chamar a próxima': 'The counter "{name}" still has ticket {label}: finish the service before calling the next one',
  'O guichê "{name}" não atende nenhuma fila ativa': 'The counter "{name}" does not serve any active queue',
  'Não há senhas aguardando para este guichê': 'There are no tickets waiting for this counter',
  'Outro atendente chamou a mesma senha agora. Tente de novo': 'Another attendant called the same ticket just now. Try again',
  'A senha {label} está aguardando: esta ação não se aplica': 'Ticket {label} is waiting: this action does not apply',
  'A senha {label} está sendo chamada: esta ação não se aplica': 'Ticket {label} is being called: this action does not apply',
  'A senha {label} está em atendimento: esta ação não se aplica': 'Ticket {label} is being served: this action does not apply',
  'A senha {label} já foi concluída: esta ação não se aplica': 'Ticket {label} was already finished: this action does not apply',
  'A senha {label} foi marcada como não compareceu: esta ação não se aplica': 'Ticket {label} was marked as no-show: this action does not apply',
  'A senha {label} foi cancelada: esta ação não se aplica': 'Ticket {label} was canceled: this action does not apply',
  'Informe o nome da fila': 'Provide the queue name',
  'Este QR Code não está mais ativo. Procure o atendimento.': 'This QR Code is no longer active. Please ask the service desk.',
  'Este QR Code não é válido. Procure o atendimento.': 'This QR Code is not valid. Please ask the service desk.',
  'Esta senha não foi encontrada. Ela pode ter expirado.': 'This ticket was not found. It may have expired.',
  'A fila está cheia no momento. Procure o atendimento.': 'The queue is full right now. Please ask the service desk.',
  'Você já pegou senhas demais por hora. Procure o atendimento.': 'You have taken too many tickets this hour. Please ask the service desk.',
  'Informe o nome do guichê': 'Provide the counter name',
  'A letra da senha tem de 1 a 3 letras ou números (ex.: A, P, VIP)': 'The ticket letter must have 1 to 3 letters or numbers (e.g. A, P, VIP)',
  'O nome aceita no máximo 60 caracteres': 'The name accepts at most 60 characters',
  'Fila não encontrada: {id}': 'Queue not found: {id}',
  'Guichê não encontrado: {id}': 'Counter not found: {id}',
  'Senha não encontrada: {id}': 'Ticket not found: {id}',
  'Fila inválida no painel de chamadas: {item}': 'Invalid queue in the call board: {item}',
  'Escolha no máximo 20 filas': 'Choose at most 20 queues',
  'Informe a orientação (orientation)': 'Provide the orientation (orientation)',
  'Orientação inválida: use AUTO, LANDSCAPE, LANDSCAPE_FLIPPED, PORTRAIT_TOP_RIGHT ou PORTRAIT_TOP_LEFT':
    'Invalid orientation: use AUTO, LANDSCAPE, LANDSCAPE_FLIPPED, PORTRAIT_TOP_RIGHT or PORTRAIT_TOP_LEFT',
  'A orientação da tela (como a TV foi instalada) é do Administrador.': 'The screen orientation (how the TV was installed) belongs to the Administrator.',
  'Informe o local (locationId)': 'Provide the location (locationId)',
  'Escolha exatamente um alvo: deviceIds, targetGroupId ou targetLocationId': 'Choose exactly one target: deviceIds, targetGroupId or targetLocationId',
  'Situação inválida: {value} (use ONLINE, OFFLINE, NEVER, PENDING ou DISABLED)': 'Invalid status: {value} (use ONLINE, OFFLINE, NEVER, PENDING or DISABLED)',
  'O período pode ter no máximo {n} dias': 'The period can be at most {n} days',
  '{what} precisa estar entre {min} e {max}': '{what} must be between {min} and {max}',

  // mídia e páginas da web
  'Esta mídia é uma página da web: não há arquivo para baixar': 'This media is a web page: there is no file to download',
  'Tipo de mídia não informado': 'Media type not provided',
  'Tipo de mídia não suportado: {type}': 'Unsupported media type: {type}',
  'Informe o endereço da página': 'Provide the page address',
  'O endereço da página não é válido': 'The page address is not valid',
  'O endereço da página não pode conter usuário e senha': 'The page address cannot contain a username and password',
  'O endereço da página precisa começar com https://': 'The page address must start with https://',
  'O endereço é longo demais (máximo {n} caracteres)': 'The address is too long (maximum {n} characters)',

  // webhook
  'Configure o endereço do webhook antes de testar': 'Set the webhook address before testing',
  'Informe o endereço do webhook': 'Provide the webhook address',
  'O endereço do webhook não é válido': 'The webhook address is not valid',
  'O endereço do webhook não pode conter usuário e senha': 'The webhook address cannot contain a username and password',
  'O endereço do webhook precisa começar com https://': 'The webhook address must start with https://',
  'O endereço do webhook não pode apontar para a rede interna': 'The webhook address cannot point to the internal network',
  'Não foi possível encontrar o endereço do webhook ({host})': 'Could not find the webhook address ({host})',
};
