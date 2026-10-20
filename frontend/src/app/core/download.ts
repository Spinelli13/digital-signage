import { Injectable } from '@angular/core';

/** Entrega um arquivo gerado no navegador (como o CSV de um relatório) para a pessoa salvar. */
@Injectable({ providedIn: 'root' })
export class FileSaver {
  save(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 10_000);
  }
}
