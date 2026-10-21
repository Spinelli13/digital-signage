import { tr } from './i18n';
import type { ScreenOrientation } from './models';

export interface OrientationOption {
  value: ScreenOrientation;
  /** Chaves em português (traduzidas na hora de mostrar). */
  label: string;
  hint: string;
  /** Quanto a TV está girada na parede, em graus no sentido horário (só para o desenho). */
  turn: number;
}

/**
 * Como a TV foi instalada. Os nomes dizem para onde aponta o TOPO DA TV: "girada para a direita" é ambíguo, "topo para a
 * direita" não (e dá para conferir olhando a TV: o topo é o lado do botão de ligar ou da logomarca).
 */
export const ORIENTATIONS: OrientationOption[] = [
  {
    value: 'AUTO',
    label: 'Automática',
    hint: 'O Player usa a tela como o aparelho a entrega (uma TV deitada, o normal).',
    turn: 0,
  },
  { value: 'LANDSCAPE', label: 'Horizontal', hint: 'A TV está deitada, na posição normal.', turn: 0 },
  {
    value: 'PORTRAIT_TOP_RIGHT',
    label: 'Vertical, topo da TV para a direita',
    hint: 'A TV está em pé, girada no sentido horário (o topo dela aponta para a direita de quem olha).',
    turn: 90,
  },
  {
    value: 'PORTRAIT_TOP_LEFT',
    label: 'Vertical, topo da TV para a esquerda',
    hint: 'A TV está em pé, girada no sentido anti-horário (o topo dela aponta para a esquerda de quem olha).',
    turn: 270,
  },
  {
    value: 'LANDSCAPE_FLIPPED',
    label: 'Horizontal, de cabeça para baixo',
    hint: 'A TV está deitada, mas de cabeça para baixo (instalada no teto, por exemplo).',
    turn: 180,
  },
];

export const orientationOption = (value: string | null | undefined): OrientationOption =>
  ORIENTATIONS.find((option) => option.value === value) ?? ORIENTATIONS[0];

/** O texto curto do selo da TV; nulo quando é o automático (não precisa de selo). */
export function orientationBadge(value: string | null | undefined): string | null {
  switch (value) {
    case 'PORTRAIT_TOP_RIGHT':
    case 'PORTRAIT_TOP_LEFT':
      return tr('Vertical');
    case 'LANDSCAPE_FLIPPED':
      return tr('De cabeça para baixo');
    default:
      return null;
  }
}
