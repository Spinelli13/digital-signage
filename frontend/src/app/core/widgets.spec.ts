import { TestBed } from '@angular/core/testing';

import { WIDGET_KINDS, WidgetTemplates, defaultWidgetSettings, widgetInfo } from './widgets';

/** Os modelos sem rede: o teste entrega o texto dos três arquivos. */
class FakeFiles extends WidgetTemplates {
  loads = 0;
  protected override async fetchText(name: string): Promise<string> {
    this.loads++;
    return {
      'widget.html': '<html><style>/*__CSS__*/</style><script>/*__JS__*/</script></html>',
      'widget.css': 'body{color:red}',
      'widget.js': "var simbolo = 'R$'; var depois = \"$'\"; /* $& */",
    }[name]!;
  }
}

describe('WidgetTemplates', () => {
  function templates() {
    TestBed.configureTestingModule({ providers: [{ provide: WidgetTemplates, useClass: FakeFiles }] });
    return TestBed.inject(WidgetTemplates) as FakeFiles;
  }

  it('joins HTML, CSS and JavaScript into one page that starts with the given configuration', async () => {
    const page = await templates().page({ kind: 'CLOCK', settings: { theme: 'dark' } });

    expect(page).toContain('body{color:red}');
    expect(page).toContain('Smartplay.start({"kind":"CLOCK","settings":{"theme":"dark"}});');
    expect(page).not.toContain('/*__');
  });

  it('keeps the dollar signs of the template (they are not replacement patterns)', async () => {
    const page = await templates().page({});

    expect(page).toContain("var simbolo = 'R$'; var depois = \"$'\"; /* $& */");
  });

  it('a "<" in the data can never close the script', async () => {
    const page = await templates().page({ title: '</script><script>alert(1)</script>' });

    expect(page).not.toContain('</script><script>alert');
    expect(page).toContain('\\u003c/script>');
  });

  it('loads the three files only once', async () => {
    const files = templates();

    await files.page({});
    await files.page({ outra: true });

    expect(files.loads).toBe(3);
  });
});

describe('widget catalog', () => {
  it('has the seven kinds, each with icon, name and explanation', () => {
    expect(WIDGET_KINDS.map((info) => info.kind)).toEqual(['CLOCK', 'WEATHER', 'NEWS', 'RATES', 'NOTICE', 'COUNTDOWN', 'PRICES']);
    expect(WIDGET_KINDS.every((info) => info.icon && info.label && info.description.length > 20)).toBe(true);
    expect(widgetInfo('WEATHER')?.label).toBe('Previsão do tempo');
    expect(widgetInfo(null)).toBeUndefined();
  });

  it('starts every kind with the language of the panel and a theme', () => {
    for (const info of WIDGET_KINDS) {
      const settings = defaultWidgetSettings(info.kind, 'en');
      expect(settings['lang']).toBe('en');
      expect(settings['theme']).toBe('dark');
    }
  });
});
