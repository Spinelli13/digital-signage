import { HttpEventType } from '@angular/common/http';
import { Component, ElementRef, OnInit, effect, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ErrorService } from '../core/error.service';
import { formatBytes, mediaTypeLabel } from '../core/format';
import { Media, MediaType } from '../core/models';
import { widgetInfo } from '../core/widgets';
import { TPipe, tr } from '../core/i18n';
import { Notice } from '../core/notice';
import { IconComponent } from '../shared/icon';
import { IconName } from '../shared/icons';
import { EmptyStateComponent, MenuComponent, PageHeaderComponent, PillComponent } from '../shared/ui';
import { MediaThumb } from './media-thumb';
import { WidgetEditorComponent } from './widget-editor';
import { MAX_UPLOAD_BYTES, VIDEO_CONVERTER, planConversion } from './video-convert';
import { VideoInfo, exceedsFullHd, looksLikeVideo, probeVideo } from './video-probe';
import { QR_THEMES, QR_IMAGE_SIZE, QrTheme, drawQr, qrFileName } from './qr';

// MP4 e MOV (o que o iPhone grava, em H.264 ou HEVC); a extensão vale quando o celular não informa o tipo.
const ACCEPTED = 'image/jpeg,image/png,image/webp,video/mp4,video/quicktime,video/x-m4v,.mov,.m4v';
export const DEFAULT_WEB_SECONDS = 30;
export const DEFAULT_QR_CAPTION = 'Aponte a câmera do celular';

@Component({
  selector: 'app-media',
  imports: [MediaThumb, FormsModule, TPipe, IconComponent, PillComponent, PageHeaderComponent, EmptyStateComponent, MenuComponent, WidgetEditorComponent],
  template: `
    <app-page-header [title]="'Biblioteca de Mídia' | t" [subtitle]="'Imagens (JPG, PNG, WEBP), vídeos (MP4 ou MOV do iPhone: a conversão para MP4 Full HD é feita ao enviar), páginas da web e QR Codes disponíveis para as playlists.' | t" icon="image">
      @if (auth.can('content')) {
        <div class="head-actions">
          <button class="secondary" (click)="openWidget()"><app-icon name="puzzle" />{{ 'Widget' | t }}</button>
          <button class="secondary" (click)="openWeb()"><app-icon name="globe" />{{ 'Página web' | t }}</button>
          <button class="secondary" (click)="openQr()"><app-icon name="qr-code" />{{ 'QR Code' | t }}</button>
          <label class="primary upload">
            <app-icon name="upload" />{{ 'Enviar mídia' | t }}
            <input type="file" multiple [accept]="accepted" (change)="onFiles($event)" [disabled]="uploading()" hidden />
          </label>
        </div>
      }
    </app-page-header>

    @if (notice.text(); as text) {
      <div class="notice" role="status"><app-icon name="circle-check" [size]="18" /><span>{{ text }}</span></div>
    }

    @if (warnings().length > 0) {
      <div class="warnbox" role="status">
        <app-icon name="triangle-alert" [size]="18" />
        <div class="warn-list">
          @for (text of warnings(); track text) {
            <p>{{ text }}</p>
          }
        </div>
        <button type="button" class="secondary small" (click)="warnings.set([])">{{ 'Entendi' | t }}</button>
      </div>
    }

    @if (uploading()) {
      <div class="card progress">
        <div class="prog-row">
          <app-icon name="cloud-upload" [size]="20" />
          <span class="prog-text">
            @if (phase() === 'converting') {
              {{ 'Convertendo para MP4 Full HD' | t }}
            } @else {
              {{ 'Enviando' | t }}
            }
            <b>{{ currentFile() }}</b>…
          </span>
          <span class="num prog-pct">{{ progress() }}%</span>
        </div>
        <div class="bar"><div class="fill" [style.width.%]="progress()"></div></div>
        @if (phase() === 'converting') {
          <p class="label prog-note">{{ 'A conversão acontece neste aparelho e pode levar alguns minutos, conforme o tamanho do vídeo. Mantenha esta página aberta.' | t }}</p>
        }
      </div>
    }

    @if (items().length === 0 && !uploading()) {
      <div class="card">
        <app-empty-state icon="image-plus" [title]="'Nenhuma mídia ainda' | t"
          [text]="'Envie sua primeira imagem ou vídeo. Também dá para usar um widget (relógio, clima, notícias…), uma página da web ou gerar um QR Code.' | t">
          @if (auth.can('content')) {
            <label class="primary upload"><app-icon name="upload" />{{ 'Enviar mídia' | t }}<input type="file" multiple [accept]="accepted" (change)="onFiles($event)" [disabled]="uploading()" hidden /></label>
          }
        </app-empty-state>
      </div>
    } @else {
      <div class="grid">
        @for (media of items(); track media.id) {
          <div class="card hoverable item">
            <div class="thumb">
              <app-media-thumb [mediaId]="media.id" [type]="media.type" [name]="media.name" [widgetKind]="media.widget?.kind ?? null" />
              <app-pill class="kind" [icon]="kindIcon(media.type)">{{ typeLabel(media.type) }}</app-pill>
            </div>
            <div class="meta">
              <div class="line">
                <b class="name" [title]="media.name">{{ media.name }}</b>
                @if (auth.can('content')) {
                  <app-menu [label]="'Mais ações' | t">
                    @if (media.type === 'WIDGET') {
                      <button type="button" class="menu-item" (click)="editWidget(media)"><app-icon name="pencil" />{{ 'Editar' | t }}</button>
                    }
                    <button type="button" class="menu-item danger" (click)="remove(media)"><app-icon name="trash" />{{ 'Excluir' | t }}</button>
                  </app-menu>
                }
              </div>
              <small>
                @if (media.type === 'IMAGE' || media.type === 'VIDEO') { {{ size(media.fileSize) }} }
                @if (media.type === 'WIDGET') { {{ widgetLabel(media) | t }} }
                @if (media.durationSeconds) { @if (media.type !== 'WEB') { · } {{ '{n}s' | t: { n: media.durationSeconds } }} }
              </small>
              @if (media.type === 'WEB') {
                <small class="url" [title]="media.url">{{ media.url }}</small>
              }
            </div>
          </div>
        }
      </div>
    }

    @if (modal() === 'widget') {
      <app-widget-editor [media]="editingWidget()" (saved)="widgetSaved($event)" (closed)="closeModal()" />
    }

    @if (modal() === 'web') {
      <div class="modal" (click)="closeModal()">
        <form class="modal-box" (click)="$event.stopPropagation()" (ngSubmit)="saveWeb()">
          <h2 class="mh"><app-icon name="globe" [size]="22" />{{ 'Página da web' | t }}</h2>
          <p class="label">{{ 'A TV abre a página e a exibe pelo tempo escolhido (um painel de vendas, o cardápio online, a previsão do tempo…). Precisa de internet na TV e o endereço precisa ser' | t }} <b>https://</b>. {{ 'A página não fica guardada na TV.' | t }}</p>
          <div class="field">
            <label for="web-url">{{ 'Endereço da página' | t }}</label>
            <input id="web-url" name="url" type="url" placeholder="https://…" required autocomplete="off" [ngModel]="webUrl()" (ngModelChange)="webUrl.set($event)" />
          </div>
          <div class="field">
            <label for="web-name">{{ 'Nome (opcional)' | t }}</label>
            <input id="web-name" name="name" [placeholder]="'Ex.: Painel de vendas' | t" [ngModel]="webName()" (ngModelChange)="webName.set($event)" />
          </div>
          <div class="field">
            <label for="web-seconds">{{ 'Tempo na tela (segundos)' | t }}</label>
            <input id="web-seconds" name="seconds" type="number" min="5" max="3600" [ngModel]="webSeconds()" (ngModelChange)="webSeconds.set($event)" />
          </div>
          <div class="actions">
            <button type="button" class="secondary" (click)="closeModal()">{{ 'Cancelar' | t }}</button>
            <button type="submit" class="primary" [disabled]="busy() || !webUrl().trim()">{{ 'Adicionar' | t }}</button>
          </div>
        </form>
      </div>
    }

    @if (modal() === 'qr') {
      <div class="modal" (click)="closeModal()">
        <form class="modal-box qr" (click)="$event.stopPropagation()" (ngSubmit)="saveQr()">
          <h2 class="mh"><app-icon name="qr-code" [size]="22" />{{ 'QR Code' | t }}</h2>
          <p class="label">{{ 'Gera uma imagem com o QR Code (um endereço, um Wi‑Fi, um cardápio…) e a guarda na biblioteca, pronta para entrar numa playlist.' | t }}</p>
          <div class="qr-layout">
            <div>
              <div class="field">
                <label for="qr-text">{{ 'Endereço ou texto' | t }}</label>
                <input id="qr-text" name="text" placeholder="https://…" required autocomplete="off" [ngModel]="qrText()" (ngModelChange)="qrText.set($event)" />
              </div>
              <div class="field">
                <label for="qr-title">{{ 'Título (opcional)' | t }}</label>
                <input id="qr-title" name="title" [placeholder]="'Ex.: Cardápio digital' | t" [ngModel]="qrTitle()" (ngModelChange)="qrTitle.set($event)" />
              </div>
              <div class="field">
                <label for="qr-caption">{{ 'Legenda (opcional)' | t }}</label>
                <input id="qr-caption" name="caption" [ngModel]="qrCaption()" (ngModelChange)="qrCaption.set($event)" />
              </div>
              <div class="field">
                <label for="qr-theme">{{ 'Cores' | t }}</label>
                <select id="qr-theme" name="theme" [ngModel]="qrTheme()" (ngModelChange)="qrTheme.set($event)">
                  @for (theme of themes; track theme[0]) {
                    <option [value]="theme[0]">{{ theme[1].label | t }}</option>
                  }
                </select>
              </div>
            </div>
            <div class="preview">
              <canvas #qrCanvas [width]="imageSize" [height]="imageSize" [attr.aria-label]="'Pré-visualização do QR Code' | t"></canvas>
              @if (qrProblem(); as text) {
                <div class="error" role="alert"><app-icon name="circle-alert" [size]="18" /><span>{{ text }}</span></div>
              }
            </div>
          </div>
          <div class="actions">
            <button type="button" class="secondary" (click)="closeModal()">{{ 'Cancelar' | t }}</button>
            <button type="submit" class="primary" [disabled]="busy() || !qrText().trim() || !!qrProblem()">{{ 'Salvar na biblioteca' | t }}</button>
          </div>
        </form>
      </div>
    }
  `,
  styles: `
    .warnbox { display: flex; align-items: flex-start; gap: 10px; margin-bottom: 18px; padding: 12px 14px; border-radius: var(--r); background: var(--warn-bg); color: var(--warn-fg); border: 1px solid var(--warn-line); }
    .warnbox > app-icon { flex: none; margin-top: 2px; }
    .warn-list { flex: 1; min-width: 0; }
    .warn-list p { margin: 0 0 6px; }
    .warn-list p:last-child { margin-bottom: 0; }
    .upload { cursor: pointer; display: inline-flex; align-items: center; justify-content: center; gap: 8px; }
    .mh { display: flex; align-items: center; gap: 10px; }
    .mh app-icon { color: var(--primary); }
    :host-context(:root[data-theme='dark']) .mh app-icon { color: var(--link); }
    .prog-row { display: flex; align-items: center; gap: 10px; }
    .prog-text { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .prog-pct { font-weight: 700; color: var(--primary); }
    .head-actions { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
    .progress { margin-bottom: 16px; }
    .prog-note { margin: 8px 0 0; }
    .bar { height: 8px; background: var(--primary-soft); border-radius: 8px; margin-top: 10px; overflow: hidden; }
    .fill { height: 100%; background: var(--primary); transition: width 0.2s; }
    .item { padding: 0; display: flex; flex-direction: column; }
    .thumb { position: relative; height: 130px; overflow: hidden; border-radius: var(--r-lg) var(--r-lg) 0 0; background: var(--primary-soft); }
    .kind { position: absolute; left: 8px; top: 8px; box-shadow: var(--sh-1); }
    .meta { padding: 10px 12px 12px; display: flex; flex-direction: column; gap: 2px; min-width: 0; }
    .line { display: flex; align-items: center; justify-content: space-between; gap: 6px; min-width: 0; }
    .name, .url { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .meta small { color: var(--muted); min-height: 1.2em; }
    .modal-box.qr { max-width: 820px; }
    .qr-layout { display: grid; grid-template-columns: 1fr 300px; gap: 18px; align-items: start; }
    .preview canvas { width: 100%; height: auto; aspect-ratio: 1; border: 1px solid var(--line); border-radius: 10px; background: #fff; display: block; }
    .preview .error { margin-top: 10px; }
    @media (max-width: 720px) {
      .qr-layout { grid-template-columns: 1fr; }
      .upload { width: 100%; text-align: center; padding: 12px 16px; }
      .head-actions { display: grid; grid-template-columns: 1fr 1fr; }
      .head-actions .upload { grid-column: 1 / -1; }
    }
  `,
})
export class MediaPage implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);
  private readonly errors = inject(ErrorService);
  private readonly converter = inject(VIDEO_CONVERTER);

  readonly accepted = ACCEPTED;
  readonly items = signal<Media[]>([]);
  readonly uploading = signal(false);
  readonly progress = signal(0);
  /** O que está acontecendo com o arquivo atual: convertendo (no próprio aparelho) ou enviando. */
  readonly phase = signal<'converting' | 'uploading'>('uploading');
  readonly currentFile = signal('');
  readonly size = formatBytes;
  readonly typeLabel = mediaTypeLabel;
  readonly kindIcon = (type: MediaType): IconName => (type === 'VIDEO' ? 'film' : type === 'WEB' ? 'globe' : type === 'WIDGET' ? 'puzzle' : 'image');
  readonly widgetLabel = (media: Media): string => widgetInfo(media.widget?.kind)?.label ?? 'Widget';
  readonly notice = new Notice();
  /** Avisos sobre os vídeos escolhidos (HEVC, HDR…): não impedem o envio, só explicam o que pode acontecer na TV. */
  readonly warnings = signal<string[]>([]);

  readonly modal = signal<'web' | 'qr' | 'widget' | null>(null);
  /** O widget que está sendo editado (nulo ao criar um novo). */
  readonly editingWidget = signal<Media | null>(null);
  readonly busy = signal(false);

  readonly webUrl = signal('');
  readonly webName = signal('');
  readonly webSeconds = signal<number | null>(DEFAULT_WEB_SECONDS);

  readonly qrText = signal('');
  readonly qrTitle = signal('');
  readonly qrCaption = signal(DEFAULT_QR_CAPTION);
  readonly qrTheme = signal<QrTheme>('escuro');
  readonly qrProblem = signal<string | null>(null);
  readonly themes = Object.entries(QR_THEMES);
  readonly imageSize = QR_IMAGE_SIZE;
  private readonly qrCanvas = viewChild<ElementRef<HTMLCanvasElement>>('qrCanvas');

  constructor() {
    // A pré-visualização é redesenhada a cada tecla (e quando a janela abre e o canvas passa a existir).
    effect(() => {
      const canvas = this.qrCanvas()?.nativeElement;
      const drawing = { text: this.qrText(), title: this.qrTitle(), caption: this.qrCaption(), theme: this.qrTheme() };
      if (canvas) {
        this.paintQr(canvas, drawing);
      }
    });
  }

  ngOnInit(): void {
    this.load();
  }

  // ---------- widgets ----------

  openWidget(): void {
    this.editingWidget.set(null);
    this.modal.set('widget');
  }

  editWidget(media: Media): void {
    this.editingWidget.set(media);
    this.modal.set('widget');
  }

  widgetSaved(media: Media): void {
    this.closeModal();
    this.notice.show(tr('Widget "{name}" salvo na biblioteca. Coloque-o numa playlist para exibi-lo.', { name: media.name }));
    this.load();
  }

  // ---------- página web ----------

  openWeb(): void {
    this.webUrl.set('');
    this.webName.set('');
    this.webSeconds.set(DEFAULT_WEB_SECONDS);
    this.modal.set('web');
  }

  saveWeb(): void {
    const url = this.webUrl().trim();
    if (!url) {
      return;
    }
    this.busy.set(true);
    this.api
      .createWebContent({ name: this.webName().trim() || null, url, durationSeconds: this.webSeconds() ?? DEFAULT_WEB_SECONDS })
      .subscribe({
        next: (media) => {
          this.busy.set(false);
          this.closeModal();
          this.notice.show(tr('Página "{name}" adicionada à biblioteca. Coloque-a numa playlist para exibi-la.', { name: media.name }));
          this.load();
        },
        // O motivo (ex.: "precisa começar com https://") aparece no aviso de erro; a janela continua aberta.
        error: () => this.busy.set(false),
      });
  }

  // ---------- QR Code ----------

  openQr(): void {
    this.qrText.set('');
    this.qrTitle.set('');
    this.qrCaption.set(tr(DEFAULT_QR_CAPTION));
    this.qrTheme.set('escuro');
    this.qrProblem.set(null);
    this.modal.set('qr');
  }

  saveQr(): void {
    const canvas = this.qrCanvas()?.nativeElement;
    const drawing = { text: this.qrText(), title: this.qrTitle(), caption: this.qrCaption(), theme: this.qrTheme() };
    if (!canvas || this.qrProblem() || !drawing.text.trim()) {
      return;
    }
    this.busy.set(true);
    canvas.toBlob((blob) => {
      if (!blob) {
        this.busy.set(false);
        this.errors.show(tr('Não foi possível gerar a imagem do QR Code neste navegador.'));
        return;
      }
      const file = new File([blob], qrFileName(drawing), { type: 'image/png' });
      this.api.uploadMedia(file, null).subscribe({
        next: (event) => {
          if (event.type === HttpEventType.Response) {
            this.busy.set(false);
            this.closeModal();
            this.notice.show(
              tr('QR Code "{name}" salvo na biblioteca. Coloque-o numa playlist para exibi-lo.', { name: file.name.replace(/\.png$/, '') }),
            );
            this.load();
          }
        },
        error: () => this.busy.set(false),
      });
    }, 'image/png');
  }

  private paintQr(canvas: HTMLCanvasElement, drawing: { text: string; title: string; caption: string; theme: QrTheme }): void {
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      return;
    }
    if (!drawing.text.trim()) {
      this.qrProblem.set(null);
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      return;
    }
    try {
      drawQr(ctx, drawing);
      this.qrProblem.set(null);
    } catch (error) {
      this.qrProblem.set(error instanceof Error ? error.message : tr('Não foi possível gerar o QR Code.'));
    }
  }

  closeModal(): void {
    this.modal.set(null);
    this.editingWidget.set(null);
  }

  // ---------- arquivos ----------

  async onFiles(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    if (files.length === 0) {
      return;
    }
    this.uploading.set(true);
    this.warnings.set([]);
    const converted: string[] = [];
    for (const original of files) {
      let file = original;
      this.currentFile.set(file.name);
      this.progress.set(0);
      this.phase.set('uploading');
      try {
        let duration: number | null = null;
        if (looksLikeVideo(file)) {
          // O arquivo diz o que é (codec, tamanho, duração) sem precisar tocá-lo: o navegador nem sempre toca HEVC.
          let info = await probeVideo(file);
          if (info) {
            const result = await this.convertIfNeeded(file, info);
            if (result === 'skip') {
              continue;
            }
            if (result) {
              converted.push(tr('"{name}" virou MP4 Full HD (de {before} para {after}).', { name: original.name, before: formatBytes(original.size), after: formatBytes(result.file.size) }));
              file = result.file;
              info = (await probeVideo(file)) ?? info;
            }
          }
          const wasConverted = file !== original;
          if (info && !wasConverted && exceedsFullHd(info)) {
            this.errors.show(
              tr('O vídeo "{name}" tem {width}×{height}, mais que 1080p, e este navegador não conseguiu reduzi-lo. Regrave ou reduza para 1080p antes de enviar (no iPhone: Ajustes > Câmera > Gravar vídeo > 1080p HD).', {
                name: file.name,
                width: info.width,
                height: info.height,
              }),
            );
            continue;
          }
          if (info && !wasConverted) {
            this.warnAbout(file.name, info);
          }
          duration = info?.durationSeconds ? Math.max(1, Math.round(info.durationSeconds)) : await readVideoDuration(file);
        }
        this.phase.set('uploading');
        this.progress.set(0);
        await this.upload(file, duration);
      } catch {
        // O motivo já foi mostrado pelo interceptor; aqui dizemos qual arquivo falhou e o que aconteceu com a fila.
        this.errors.show(tr('Não foi possível enviar "{name}". Os arquivos seguintes da fila não foram enviados.', { name: original.name }));
        break;
      }
    }
    this.uploading.set(false);
    if (converted.length > 0) {
      this.notice.show(converted.join(' '));
    }
    this.load();
  }

  /**
   * Converte para MP4 Full HD o que precisa (MOV, HEVC, acima de Full HD, HDR, acima do limite de tamanho). Devolve o
   * resultado, nulo quando não precisa (ou não deu e o original ainda serve) e 'skip' quando o arquivo não pode ser enviado.
   */
  private async convertIfNeeded(file: File, info: VideoInfo): Promise<{ file: File } | null | 'skip'> {
    const plan = planConversion(file, info);
    if (!plan) {
      return null;
    }
    if (!plan.fits) {
      if (file.size > MAX_UPLOAD_BYTES) {
        this.errors.show(
          tr('O vídeo "{name}" tem {seconds} s e {size}: é longo demais para caber no limite de {limit} do servidor, mesmo convertido. Divida-o em partes menores.', {
            name: file.name,
            seconds: Math.round(info.durationSeconds ?? 0),
            size: formatBytes(file.size),
            limit: formatBytes(MAX_UPLOAD_BYTES),
          }),
        );
        return 'skip';
      }
      return null; // o original cabe no limite: vai como está, com os avisos de sempre
    }
    if (!this.converter.canConvert()) {
      return this.cannotConvert(file, info);
    }
    this.phase.set('converting');
    this.progress.set(0);
    try {
      const result = await this.converter.convert(file, plan, info, (fraction) => this.progress.set(Math.round(fraction * 100)));
      if (result.file.size > MAX_UPLOAD_BYTES) {
        if (file.size <= MAX_UPLOAD_BYTES) {
          return null;
        }
        this.errors.show(
          tr('"{name}" convertido ainda tem {size}, acima do limite de {limit}. Divida o vídeo em partes menores.', {
            name: file.name,
            size: formatBytes(result.file.size),
            limit: formatBytes(MAX_UPLOAD_BYTES),
          }),
        );
        return 'skip';
      }
      if (result.warnings.includes('no-audio')) {
        this.warnings.update((current) => [...current, tr('"{name}" ficou sem som: este navegador não consegue regravar o áudio desse vídeo.', { name: file.name })]);
      }
      return result;
    } catch {
      return this.cannotConvert(file, info);
    } finally {
      this.phase.set('uploading');
    }
  }

  /** O navegador não conseguiu converter: o original segue (com os avisos) se for aceito, senão a pessoa é avisada. */
  private cannotConvert(file: File, info: VideoInfo): null | 'skip' {
    if (file.size > MAX_UPLOAD_BYTES) {
      this.errors.show(
        tr('O vídeo "{name}" tem {size}, acima do limite de {limit}, e este navegador não conseguiu convertê-lo. Tente pelo celular (Safari ou Chrome atualizados) ou reduza o vídeo antes de enviar.', {
          name: file.name,
          size: formatBytes(file.size),
          limit: formatBytes(MAX_UPLOAD_BYTES),
        }),
      );
      return 'skip';
    }
    // Acima de Full HD o envio é recusado logo adiante, com a própria explicação: um segundo aviso só atrapalharia.
    if (!exceedsFullHd(info)) {
      this.warnings.update((current) => [
        ...current,
        tr('Este navegador não conseguiu converter "{name}" para MP4 Full HD ({codec}): o arquivo vai como está.', { name: file.name, codec: info.codecName }),
      ]);
    }
    return null;
  }

  private warnAbout(name: string, info: VideoInfo): void {
    const warnings: string[] = [];
    if (info.codec.startsWith('hvc') || info.codec.startsWith('hev') || info.codec.startsWith('dv')) {
      warnings.push(
        tr('"{name}" está em HEVC (H.265), o formato padrão do iPhone. Funciona na maioria das TVs, mas aparelhos mais antigos não tocam: se a TV mostrar erro, grave em "Mais compatível" (H.264) em Ajustes > Câmera > Formatos.', { name }),
      );
    }
    if (info.hdr) {
      warnings.push(
        tr('"{name}" tem HDR (Dolby Vision, HLG ou HDR10): em TVs sem HDR as cores podem ficar apagadas. Prefira gravar sem HDR.', { name }),
      );
    }
    if (warnings.length > 0) {
      this.warnings.update((current) => [...current, ...warnings]);
    }
  }

  remove(media: Media): void {
    if (!confirm(tr('Excluir "{name}"?', { name: media.name }))) {
      return;
    }
    this.api.deleteMedia(media.id).subscribe({
      next: () => this.load(),
      // 409: a mídia ainda está em uma playlist (mensagem vem da API)
      error: () => undefined,
    });
  }

  private upload(file: File, duration: number | null): Promise<void> {
    return new Promise((resolve, reject) => {
      this.api.uploadMedia(file, duration).subscribe({
        next: (event) => {
          if (event.type === HttpEventType.UploadProgress && event.total) {
            this.progress.set(Math.round((100 * event.loaded) / event.total));
          }
          if (event.type === HttpEventType.Response) {
            resolve();
          }
        },
        error: reject,
      });
    });
  }

  private load(): void {
    this.api.mediaList().subscribe({
      next: (media) => this.items.set(media),
      error: () => undefined,
    });
  }
}

/** O servidor não lê metadados de vídeo (decisão do projeto), então a duração vem do navegador. */
function readVideoDuration(file: File): Promise<number | null> {
  return new Promise((resolve) => {
    const video = document.createElement('video');
    const url = URL.createObjectURL(file);
    const finish = (value: number | null) => {
      URL.revokeObjectURL(url);
      resolve(value);
    };
    video.preload = 'metadata';
    video.onloadedmetadata = () => finish(Number.isFinite(video.duration) ? Math.max(1, Math.round(video.duration)) : null);
    video.onerror = () => finish(null);
    video.src = url;
  });
}
