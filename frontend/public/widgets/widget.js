/*
 * Smartplay widgets: desenha relógio, clima, notícias, cotações, aviso e contagem regressiva.
 * O MESMO arquivo roda na TV (WebView do Player) e na pré-visualização do painel.
 *
 * Contrato: Smartplay.start({ kind, settings, data, error, nowMs, lang }).
 *  - nowMs: a hora de verdade (a do servidor) neste instante; daí em diante a hora anda pelo relógio monotônico, então um
 *    relógio de TV errado não faz o widget mostrar a hora errada.
 *  - data: o que o servidor entregou (clima, notícias, cotações) com fetchedAtMs; null se não havia nada (error diz por quê).
 * Texto de pessoa e de fontes de notícias entra SEMPRE por textContent (nunca innerHTML).
 * ES5 de propósito: WebViews de TVs antigas não entendem let, const nem funções de seta.
 */
(function () {
  'use strict';

  var ICONS = {
    'sun': '<circle cx="12" cy="12" r="4"/><path d="M12 2v2"/><path d="M12 20v2"/><path d="m4.93 4.93 1.41 1.41"/><path d="m17.66 17.66 1.41 1.41"/><path d="M2 12h2"/><path d="M20 12h2"/><path d="m6.34 17.66-1.41 1.41"/><path d="m19.07 4.93-1.41 1.41"/>',
    'moon': '<path d="M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401"/>',
    'cloud': '<path d="M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z"/>',
    'cloud-sun': '<path d="M12 2v2"/><path d="m4.93 4.93 1.41 1.41"/><path d="M20 12h2"/><path d="m19.07 4.93-1.41 1.41"/><path d="M15.947 12.65a4 4 0 0 0-5.925-4.128"/><path d="M13 22H7a5 5 0 1 1 4.9-6H13a3 3 0 0 1 0 6Z"/>',
    'cloud-moon': '<path d="M13 16a3 3 0 0 1 0 6H7a5 5 0 1 1 4.9-6z"/><path d="M18.376 14.512a6 6 0 0 0 3.461-4.127c.148-.625-.659-.97-1.248-.714a4 4 0 0 1-5.259-5.26c.255-.589-.09-1.395-.716-1.248a6 6 0 0 0-4.594 5.36"/>',
    'cloud-rain': '<path d="M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242"/><path d="M16 14v6"/><path d="M8 14v6"/><path d="M12 16v6"/>',
    'cloud-drizzle': '<path d="M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242"/><path d="M8 19v1"/><path d="M8 14v1"/><path d="M16 19v1"/><path d="M16 14v1"/><path d="M12 21v1"/><path d="M12 16v1"/>',
    'cloud-lightning': '<path d="M6 16.326A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 .5 8.973"/><path d="m13 12-3 5h4l-3 5"/>',
    'cloud-snow': '<path d="M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242"/><path d="M8 15h.01"/><path d="M8 19h.01"/><path d="M12 17h.01"/><path d="M12 21h.01"/><path d="M16 15h.01"/><path d="M16 19h.01"/>',
    'cloud-fog': '<path d="M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242"/><path d="M16 17H7"/><path d="M17 21H9"/>',
    'wind': '<path d="M12.8 19.6A2 2 0 1 0 14 16H2"/><path d="M17.5 8a2.5 2.5 0 1 1 2 4H2"/><path d="M9.8 4.4A2 2 0 1 1 11 8H2"/>',
    'droplets': '<path d="M7 16.3c2.2 0 4-1.83 4-4.05 0-1.16-.57-2.26-1.71-3.19S7.29 6.75 7 5.3c-.29 1.45-1.14 2.84-2.29 3.76S3 11.1 3 12.25c0 2.22 1.8 4.05 4 4.05z"/><path d="M12.56 6.6A10.97 10.97 0 0 0 14 3.02c.5 2.5 2 4.9 4 6.5s3 3.5 3 5.5a6.98 6.98 0 0 1-11.91 4.97"/>',
    'umbrella': '<path d="M12 13v7a2 2 0 0 0 4 0"/><path d="M12 2v2"/><path d="M20.992 13a1 1 0 0 0 .97-1.274 10.284 10.284 0 0 0-19.923 0A1 1 0 0 0 3 13z"/>',
    'trending-up': '<path d="M16 7h6v6"/><path d="m22 7-8.5 8.5-5-5L2 17"/>',
    'trending-down': '<path d="M16 17h6v-6"/><path d="m22 17-8.5-8.5-5 5L2 7"/>',
    'minus': '<path d="M5 12h14"/>',
    'megaphone': '<path d="M11 6a13 13 0 0 0 8.4-2.8A1 1 0 0 1 21 4v12a1 1 0 0 1-1.6.8A13 13 0 0 0 11 14H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z"/><path d="M6 14a12 12 0 0 0 2.4 7.2 2 2 0 0 0 3.2-2.4A8 8 0 0 1 10 14"/><path d="M8 6v8"/>',
    'info': '<circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/>',
    'triangle-alert': '<path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3"/><path d="M12 9v4"/><path d="M12 17h.01"/>',
    'gift': '<path d="M12 7v14"/><path d="M20 11v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-8"/><path d="M7.5 7a1 1 0 0 1 0-5A4.8 8 0 0 1 12 7a4.8 8 0 0 1 4.5-5 1 1 0 0 1 0 5"/><rect x="3" y="7" width="18" height="4" rx="1"/>',
    'tag': '<path d="M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z"/><circle cx="7.5" cy="7.5" r=".5" fill="currentColor"/>',
    'newspaper': '<path d="M15 18h-5"/><path d="M18 14h-8"/><path d="M4 22h16a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2H8a2 2 0 0 0-2 2v16a2 2 0 0 1-4 0v-9a2 2 0 0 1 2-2h2"/><rect width="8" height="4" x="10" y="6" rx="1"/>',
    'wifi-off': '<path d="M12 20h.01"/><path d="M8.5 16.429a5 5 0 0 1 7 0"/><path d="M5 12.859a10 10 0 0 1 5.17-2.69"/><path d="M19 12.859a10 10 0 0 0-2.007-1.523"/><path d="M2 8.82a15 15 0 0 1 4.177-2.643"/><path d="M22 8.82a15 15 0 0 0-11.288-3.764"/><path d="m2 2 20 20"/>'
  };

  var STR = {
    'pt-BR': {
      humidity: 'Umidade', wind: 'Vento', rain: 'chuva', updated: 'atualizado há', updatedAt: 'Atualizado às',
      noData: 'Sem dados no momento', noDataHint: 'Assim que a conexão voltar, o conteúdo aparece aqui.',
      noNews: 'Sem notícias no momento', next: 'A seguir', source: 'Fonte', ratesSource: 'Fonte: Banco Central Europeu', soldOut: 'Esgotado', noItems: 'Nenhum item na tabela', was: 'de',
      days: 'dias', hours: 'horas', minutes: 'min', seconds: 'seg', until: 'até', today: 'Hoje',
      m: 'min', h: 'h', d: 'd', ago: 'há', now: 'agora', news: 'Notícias', rates: 'Cotações',
      queueTitle: 'Chamadas', queueCalling: 'Senha chamada', queueNone: 'Aguardando a primeira chamada', queueNoneHint: 'As senhas chamadas aparecem aqui.',
      queueWaiting: 'Aguardando', queueLast: 'Últimas chamadas', queuePriority: 'Prioritária', queueAgain: 'Chamando de novo',
      names: { USD: 'Dólar americano', EUR: 'Euro', GBP: 'Libra esterlina', JPY: 'Iene japonês', CAD: 'Dólar canadense', AUD: 'Dólar australiano', CHF: 'Franco suíço', CNY: 'Yuan chinês', MXN: 'Peso mexicano' },
      wmo: { clear: 'Céu limpo', mostlyClear: 'Poucas nuvens', partly: 'Parcialmente nublado', cloudy: 'Nublado', fog: 'Neblina', drizzle: 'Garoa', rain: 'Chuva', snow: 'Neve', showers: 'Pancadas de chuva', snowShowers: 'Pancadas de neve', storm: 'Tempestade' }
    },
    'en': {
      humidity: 'Humidity', wind: 'Wind', rain: 'rain', updated: 'updated', updatedAt: 'Updated at',
      noData: 'No data right now', noDataHint: 'As soon as the connection is back, the content shows up here.',
      noNews: 'No news right now', next: 'Up next', source: 'Source', ratesSource: 'Source: European Central Bank', soldOut: 'Sold out', noItems: 'No items in the table', was: 'was',
      days: 'days', hours: 'hours', minutes: 'min', seconds: 'sec', until: 'until', today: 'Today',
      m: 'min', h: 'h', d: 'd', ago: 'ago', now: 'now', news: 'News', rates: 'Exchange rates',
      queueTitle: 'Calls', queueCalling: 'Now calling', queueNone: 'Waiting for the first call', queueNoneHint: 'Called tickets show up here.',
      queueWaiting: 'Waiting', queueLast: 'Latest calls', queuePriority: 'Priority', queueAgain: 'Calling again',
      names: { USD: 'US dollar', EUR: 'Euro', GBP: 'Pound sterling', JPY: 'Japanese yen', CAD: 'Canadian dollar', AUD: 'Australian dollar', CHF: 'Swiss franc', CNY: 'Chinese yuan', MXN: 'Mexican peso' },
      wmo: { clear: 'Clear sky', mostlyClear: 'Mostly clear', partly: 'Partly cloudy', cloudy: 'Cloudy', fog: 'Fog', drizzle: 'Drizzle', rain: 'Rain', snow: 'Snow', showers: 'Rain showers', snowShowers: 'Snow showers', storm: 'Thunderstorm' }
    }
  };

  var state = { clock: { base: 0, t0: 0 }, timers: [], init: null, lang: 'pt-BR', s: STR['pt-BR'] };

  // ---------- utilidades ----------

  function perfNow() {
    return window.performance && window.performance.now ? window.performance.now() : new Date().getTime();
  }

  function now() {
    return state.clock.base + (perfNow() - state.clock.t0);
  }

  function el(tag, cls, text) {
    var node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
  }

  function icon(name, cls) {
    var span = el('span', 'ico' + (cls ? ' ' + cls : ''));
    span.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true">' + (ICONS[name] || '') + '</svg>';
    return span;
  }

  function every(ms, fn) {
    state.timers.push(window.setInterval(fn, ms));
  }

  function clearTimers() {
    for (var i = 0; i < state.timers.length; i++) window.clearInterval(state.timers[i]);
    state.timers = [];
  }

  function pad(n) {
    return n < 10 ? '0' + n : '' + n;
  }

  /** Formatador por fuso; se o aparelho não souber o fuso, cai no horário dele (melhor que nada). */
  function formatter(options, tz) {
    var withZone = {};
    var k;
    for (k in options) withZone[k] = options[k];
    if (tz) withZone.timeZone = tz;
    try {
      return new Intl.DateTimeFormat(state.lang, withZone);
    } catch (e) {
      try {
        return new Intl.DateTimeFormat(state.lang, options);
      } catch (e2) {
        return { format: function (d) { return d.toISOString(); } };
      }
    }
  }

  /** O instante (ms) em que 'AAAA-MM-DDTHH:MM' acontece no fuso informado. */
  function zonedToMs(iso, tz) {
    var m = /^(\d+)-(\d+)-(\d+)T(\d+):(\d+)/.exec(iso);
    if (!m) return NaN;
    var asUtc = Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4], +m[5]);
    try {
      var off = tzOffsetMs(asUtc, tz);
      return asUtc - tzOffsetMs(asUtc - off, tz);
    } catch (e) {
      return new Date(+m[1], +m[2] - 1, +m[3], +m[4], +m[5]).getTime();
    }
  }

  function tzOffsetMs(ms, tz) {
    var f = new Intl.DateTimeFormat('en-US', { timeZone: tz, hour12: false, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' });
    var m = /(\d+)\/(\d+)\/(\d+),?\s+(\d+):(\d+):(\d+)/.exec(f.format(new Date(ms)));
    var hour = +m[4] === 24 ? 0 : +m[4];
    return Date.UTC(+m[3], +m[1] - 1, +m[2], hour, +m[5], +m[6]) - Math.floor(ms / 1000) * 1000;
  }

  function ago(ms) {
    var s = state.s;
    var minutes = Math.max(0, Math.floor((now() - ms) / 60000));
    if (minutes < 1) return s.now;
    if (minutes < 60) return s.ago + ' ' + minutes + ' ' + s.m;
    var hours = Math.floor(minutes / 60);
    if (hours < 48) return s.ago + ' ' + hours + ' ' + s.h;
    return s.ago + ' ' + Math.floor(hours / 24) + ' ' + s.d;
  }

  function number(value, digits) {
    try {
      return new Intl.NumberFormat(state.lang, { minimumFractionDigits: digits, maximumFractionDigits: digits }).format(value);
    } catch (e) {
      return value.toFixed(digits);
    }
  }

  function clear(root) {
    while (root.firstChild) root.removeChild(root.firstChild);
  }

  function emptyState(root, title, hint) {
    var box = el('div', 'w empty');
    box.appendChild(icon('wifi-off'));
    box.appendChild(el('div', 't', title));
    if (hint) box.appendChild(el('div', 's', hint));
    root.appendChild(box);
  }

  /** "atualizado há 3 h": só aparece quando os dados estão velhos (mais de 90 minutos), para não poluir a tela. */
  function staleFooter(root, data) {
    if (!data || !data.fetchedAtMs) return;
    if (now() - data.fetchedAtMs < 90 * 60000) return;
    root.appendChild(el('div', 'foot', state.s.updated + ' ' + ago(data.fetchedAtMs).replace(state.s.ago + ' ', '')));
  }

  // ---------- relógio ----------

  function renderClock(root, settings) {
    var tz = settings.timezone;
    var hour12 = !!settings.hour12;
    var hm = formatter({ hour: '2-digit', minute: '2-digit', hour12: hour12 }, tz);
    var secFmt = formatter({ second: '2-digit' }, tz);
    var dateFmt = formatter({ weekday: 'long', day: 'numeric', month: 'long' }, tz);

    var box = el('div', 'w clock');
    if (settings.label) box.appendChild(el('div', 'label', settings.label));
    var time = el('div', 'time');
    var hmNode = el('span', 'hm');
    time.appendChild(hmNode);
    var secNode = null;
    if (settings.showSeconds) {
      secNode = el('span', 'sec');
      time.appendChild(secNode);
    }
    var ampmNode = null;
    if (hour12) {
      ampmNode = el('span', 'ampm');
      time.appendChild(ampmNode);
    }
    box.appendChild(time);
    var dateNode = null;
    if (settings.showDate !== false) {
      dateNode = el('div', 'date');
      box.appendChild(dateNode);
    }
    root.appendChild(box);

    function tick() {
      var d = new Date(now());
      var text = hm.format(d);
      if (ampmNode) {
        var m = /\s*([AaPp]\.?\s?[Mm]\.?)\s*$/.exec(text);
        if (m) {
          ampmNode.textContent = m[1].replace(/\s/g, '').toUpperCase();
          text = text.replace(m[0], '');
        }
      }
      hmNode.textContent = text;
      if (secNode) secNode.textContent = ':' + pad(parseInt(secFmt.format(d), 10) || 0);
      if (dateNode) dateNode.textContent = dateFmt.format(d);
    }
    tick();
    every(secNode ? 250 : 1000, tick);
  }

  // ---------- previsão do tempo ----------

  function wmo(code, isDay) {
    var t = state.s.wmo;
    if (code === 0) return { icon: isDay ? 'sun' : 'moon', text: t.clear };
    if (code === 1) return { icon: isDay ? 'cloud-sun' : 'cloud-moon', text: t.mostlyClear };
    if (code === 2) return { icon: isDay ? 'cloud-sun' : 'cloud-moon', text: t.partly };
    if (code === 3) return { icon: 'cloud', text: t.cloudy };
    if (code === 45 || code === 48) return { icon: 'cloud-fog', text: t.fog };
    if (code >= 51 && code <= 57) return { icon: 'cloud-drizzle', text: t.drizzle };
    if (code >= 61 && code <= 67) return { icon: 'cloud-rain', text: t.rain };
    if (code >= 71 && code <= 77) return { icon: 'cloud-snow', text: t.snow };
    if (code >= 80 && code <= 82) return { icon: 'cloud-rain', text: t.showers };
    if (code === 85 || code === 86) return { icon: 'cloud-snow', text: t.snowShowers };
    if (code >= 95) return { icon: 'cloud-lightning', text: t.storm };
    return { icon: 'cloud', text: t.cloudy };
  }

  function renderWeather(root, settings, data) {
    var s = state.s;
    if (!data || !data.current) return emptyState(root, s.noData, s.noDataHint);
    var cur = data.current;
    var info = wmo(cur.code, cur.isDay);
    var box = el('div', 'w weather');
    box.appendChild(el('div', 'place', settings.city));

    var now_ = el('div', 'now');
    now_.appendChild(icon(info.icon));
    var temp = el('div', 'temp', Math.round(cur.temperature) + '');
    temp.appendChild(el('sup', null, '°C'));
    now_.appendChild(temp);
    box.appendChild(now_);
    box.appendChild(el('div', 'cond', info.text));

    var meta = el('div', 'meta');
    var humidity = el('span');
    humidity.appendChild(icon('droplets'));
    humidity.appendChild(document.createTextNode(s.humidity + ' ' + Math.round(cur.humidity) + '%'));
    meta.appendChild(humidity);
    var wind = el('span');
    wind.appendChild(icon('wind'));
    wind.appendChild(document.createTextNode(s.wind + ' ' + Math.round(cur.windKmh) + ' km/h'));
    meta.appendChild(wind);
    box.appendChild(meta);

    var count = Math.min(settings.days || 0, (data.daily || []).length - 1);
    if (count > 0) {
      var days = el('div', 'days');
      var weekday = formatter({ weekday: 'short' }, data.timezone && data.timezone !== 'auto' ? data.timezone : undefined);
      for (var i = 1; i <= count; i++) {
        var day = data.daily[i];
        var card = el('div', 'day');
        var parts = /^(\d+)-(\d+)-(\d+)/.exec(day.date);
        var label = parts ? weekday.format(new Date(Date.UTC(+parts[1], +parts[2] - 1, +parts[3], 12))) : '';
        card.appendChild(el('b', null, label.replace('.', '')));
        card.appendChild(icon(wmo(day.code, true).icon));
        var mm = el('div', 'mm', Math.round(day.max) + '°');
        mm.appendChild(el('i', null, Math.round(day.min) + '°'));
        card.appendChild(mm);
        card.appendChild(el('div', 'rain', day.rainChance + '% ' + s.rain));
        days.appendChild(card);
      }
      box.appendChild(days);
    }
    root.appendChild(box);
    staleFooter(root, data);
  }

  // ---------- notícias ----------

  function renderNews(root, settings, data) {
    var s = state.s;
    var items = data && data.items ? data.items : [];
    if (!items.length) return emptyState(root, s.noNews, s.noDataHint);

    var holder = el('div', 'news');
    holder.style.width = '100%';
    holder.style.height = '100%';
    var box = el('div', 'w fade');
    holder.appendChild(box);
    root.appendChild(holder);
    var index = 0;
    var seconds = Math.max(4, settings.secondsPerItem || 8);

    function draw() {
      clear(box);
      var item = items[index];
      var head = el('div', 'head');
      head.appendChild(icon('newspaper'));
      head.appendChild(el('span', null, data.source || s.news));
      box.appendChild(head);
      var size = item.title.length > 140 ? ' xlong' : item.title.length > 80 ? ' long' : '';
      box.appendChild(el('div', 'headline' + size, item.title));
      if (item.publishedAtMs) box.appendChild(el('div', 'meta', ago(item.publishedAtMs)));
      if (items.length > 1) {
        var next = el('div', 'next');
        next.appendChild(el('small', null, s.next));
        for (var i = 1; i <= Math.min(2, items.length - 1); i++) {
          next.appendChild(el('div', null, items[(index + i) % items.length].title));
        }
        box.appendChild(next);
        var dots = el('div', 'dots');
        for (var j = 0; j < items.length; j++) dots.appendChild(el('i', j === index ? 'on' : ''));
        box.appendChild(dots);
      }
    }
    draw();
    if (items.length > 1) {
      every(seconds * 1000, function () {
        box.className = 'w fade out';
        window.setTimeout(function () {
          index = (index + 1) % items.length;
          draw();
          box.className = 'w fade';
        }, 500);
      });
    }
    staleFooter(root, data);
  }

  // ---------- cotações ----------

  function renderRates(root, settings, data) {
    var s = state.s;
    var rates = data && data.rates ? data.rates : [];
    if (!rates.length) return emptyState(root, s.noData, s.noDataHint);
    var box = el('div', 'w rates');
    box.appendChild(el('div', 'title', s.rates + ' · BRL'));
    var grid = el('div', 'grid');
    for (var i = 0; i < rates.length; i++) {
      var rate = rates[i];
      var card = el('div', 'card');
      card.appendChild(el('div', 'code', rate.code));
      card.appendChild(el('div', 'name', s.names[rate.code] || ''));
      var value = el('div', 'value');
      value.appendChild(el('small', null, 'R$'));
      value.appendChild(document.createTextNode(number(rate.value, rate.value < 1 ? 4 : 2)));
      card.appendChild(value);
      var change = rate.changePercent;
      if (typeof change === 'number') {
        var tone = change > 0.004 ? 'up' : change < -0.004 ? 'down' : 'flat';
        var chg = el('div', 'chg ' + tone);
        chg.appendChild(icon(tone === 'up' ? 'trending-up' : tone === 'down' ? 'trending-down' : 'minus'));
        chg.appendChild(document.createTextNode((change > 0 ? '+' : '') + number(change, 2) + '%'));
        card.appendChild(chg);
      }
      grid.appendChild(card);
    }
    box.appendChild(grid);
    box.appendChild(el('div', 'src', s.ratesSource + (data.date ? ' · ' + data.date : '')));
    root.appendChild(box);
    staleFooter(root, data);
  }

  // ---------- tabela de preços ----------

  function moneyFormatter(currency) {
    try {
      return new Intl.NumberFormat(state.lang, { style: 'currency', currency: currency });
    } catch (e) {
      return { format: function (value) { return currency + ' ' + value.toFixed(2); } };
    }
  }

  /**
   * Linhas da tabela (cabeçalhos de categoria + itens) repartidas em colunas e em páginas. Um cabeçalho de categoria nunca fica
   * sozinho no pé de uma coluna: se for o último lugar, a coluna fecha mais curta e o cabeçalho abre a seguinte.
   */
  function pricePages(items, showCategories, perColumn, columns) {
    var entries = [];
    var last = null;
    for (var i = 0; i < items.length; i++) {
      var category = showCategories ? (items[i].category || '') : '';
      if (category !== last) {
        if (category) entries.push({ header: category });
        last = category;
      }
      entries.push({ item: items[i] });
    }
    var cols = [];
    var col = [];
    for (var j = 0; j < entries.length; j++) {
      if (col.length === perColumn - 1 && entries[j].header) {
        cols.push(col);
        col = [];
      }
      col.push(entries[j]);
      if (col.length === perColumn) {
        cols.push(col);
        col = [];
      }
    }
    if (col.length) cols.push(col);
    var pages = [];
    for (var k = 0; k < cols.length; k += columns) pages.push(cols.slice(k, k + columns));
    return pages;
  }

  function renderPrices(root, settings, data) {
    var s = state.s;
    if (!data || !data.segments || !data.segments.length) return emptyState(root, s.noData, s.noDataHint);
    var currency = (data.list && data.list.currency) || 'BRL';
    var money = moneyFormatter(currency);
    var columns = Math.min(3, Math.max(1, settings.columns || 1));
    var seconds = Math.max(4, settings.secondsPerPage || 8);

    // A altura de cada linha é a de uma tela 16:9 com "rowsPerColumn" linhas; numa tela em pé (ou numa zona alta) cabem mais
    // linhas com a MESMA altura, e a tabela em pé usa uma coluna só (o nome do produto precisa de largura).
    var width = window.innerWidth || 1280;
    var height = window.innerHeight || 720;
    var rowVmin = 66 / Math.min(14, Math.max(4, settings.rowsPerColumn || 8));
    var heightVmin = height / (Math.min(width, height) / 100);
    var perColumn = Math.max(3, Math.floor((heightVmin - 34) / rowVmin));
    if (height > width * 1.1) columns = 1;

    var holder = el('div', 'prices');
    holder.style.setProperty('--row', rowVmin + 'vmin');
    var box = el('div', 'w fade');
    holder.appendChild(box);
    root.appendChild(holder);

    var segment = null;
    var pages = [];
    var pageIndex = 0;

    function currentSegment() {
      var t = now();
      for (var i = 0; i < data.segments.length; i++) {
        if (t >= data.segments[i].fromMs && t < data.segments[i].toMs) return data.segments[i];
      }
      return data.segments[data.segments.length - 1];
    }

    function draw() {
      clear(box);
      var head = el('div', 'head');
      head.appendChild(el('div', 'ttl', settings.title || (data.list && data.list.name) || ''));
      if (segment.rule) {
        var tag = el('div', 'tag');
        tag.appendChild(icon('tag'));
        tag.appendChild(el('span', null, segment.rule));
        head.appendChild(tag);
      }
      box.appendChild(head);
      if (!segment.items.length) {
        box.appendChild(el('div', 'none', s.noItems));
        return;
      }
      var grid = el('div', 'grid cols' + columns);
      var page = pages[pageIndex] || [];
      for (var c = 0; c < columns; c++) {
        var col = el('div', 'col');
        var slice = page[c] || [];
        for (var r = 0; r < slice.length; r++) {
          var entry = slice[r];
          if (entry.header) {
            col.appendChild(el('div', 'cat', entry.header));
            continue;
          }
          var item = entry.item;
          var row = el('div', 'row' + (item.highlight ? ' hot' : '') + (item.available ? '' : ' off'));
          var name = el('div', 'nm');
          name.appendChild(el('b', null, item.name));
          if (item.description) name.appendChild(el('small', null, item.description));
          row.appendChild(name);
          var price = el('div', 'pr');
          if (!item.available) {
            price.appendChild(el('span', 'sold', s.soldOut));
          } else {
            if (item.basePrice !== null && item.basePrice !== undefined && item.basePrice !== item.price) {
              price.appendChild(el('s', null, money.format(item.basePrice)));
            }
            var now_ = el('span', item.rule ? 'now promo' : 'now', money.format(item.price));
            if (item.unit) now_.appendChild(el('small', null, ' / ' + item.unit));
            price.appendChild(now_);
          }
          row.appendChild(price);
          col.appendChild(row);
        }
        grid.appendChild(col);
      }
      box.appendChild(grid);
      if (pages.length > 1) {
        var dots = el('div', 'dots');
        for (var p = 0; p < pages.length; p++) dots.appendChild(el('i', p === pageIndex ? 'on' : ''));
        box.appendChild(dots);
      }
    }

    function load(next) {
      segment = next;
      pages = pricePages(segment.items, settings.showCategories !== false, perColumn, columns);
      pageIndex = 0;
      draw();
    }

    load(currentSegment());
    // Na hora em que o preço muda (início ou fim de uma variação), a tela troca sozinha, mesmo sem internet.
    every(1000, function () {
      var next = currentSegment();
      if (next !== segment) load(next);
    });
    every(seconds * 1000, function () {
      if (pages.length < 2) return;
      box.className = 'w fade out';
      window.setTimeout(function () {
        pageIndex = (pageIndex + 1) % pages.length;
        draw();
        box.className = 'w fade';
      }, 400);
    });
    staleFooter(root, data);
  }

  // ---------- cores da pessoa ----------
  // Por cima do tema, a pessoa pode escolher o fundo, o texto e o destaque (#rrggbb). O que faltar vem do tema.

  /** "#rrggbb" -> [r, g, b]; null se não for uma cor assim. */
  function parseHex(value) {
    var m = /^#([0-9a-fA-F]{2})([0-9a-fA-F]{2})([0-9a-fA-F]{2})$/.exec(typeof value === 'string' ? value : '');
    return m ? [parseInt(m[1], 16), parseInt(m[2], 16), parseInt(m[3], 16)] : null;
  }

  function lum(c) {
    var v = [];
    for (var i = 0; i < 3; i++) {
      var x = c[i] / 255;
      v.push(x <= 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4));
    }
    return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
  }

  function mix(a, b, t) {
    return [Math.round(a[0] + (b[0] - a[0]) * t), Math.round(a[1] + (b[1] - a[1]) * t), Math.round(a[2] + (b[2] - a[2]) * t)];
  }

  function css(c, alpha) {
    return alpha === undefined ? 'rgb(' + c.join(',') + ')' : 'rgba(' + c.join(',') + ',' + alpha + ')';
  }

  function applyColors(settings) {
    var bg = parseHex(settings.bgColor);
    var fg = parseHex(settings.textColor);
    var accent = parseHex(settings.accentColor);
    var st = document.body.style;
    var names = ['--bg1', '--bg2', '--fg', '--muted', '--card', '--line', '--accent', '--up', '--down'];
    for (var i = 0; i < names.length; i++) st.removeProperty(names[i]);
    if (!bg && !fg && !accent) return;
    var darkBg = null;
    if (bg) {
      darkBg = lum(bg) < 0.36;
      st.setProperty('--bg1', css(bg));
      // o segundo tom do degradê: um pouco mais claro num fundo escuro, um pouco mais escuro num fundo claro
      st.setProperty('--bg2', css(mix(bg, darkBg ? [255, 255, 255] : [0, 0, 0], darkBg ? 0.12 : 0.1)));
      st.setProperty('--down', darkBg ? '#fda4af' : '#c62828');
      st.setProperty('--up', darkBg ? '#86efac' : '#15803d');
      // sem cor de texto escolhida, o texto fica claro ou escuro conforme o fundo, para sempre dar para ler
      if (!fg) fg = darkBg ? [250, 250, 252] : [20, 16, 31];
    }
    if (fg) {
      st.setProperty('--fg', css(fg));
      st.setProperty('--muted', css(fg, 0.68));
      st.setProperty('--card', css(fg, 0.08));
      st.setProperty('--line', css(fg, 0.16));
    }
    if (accent) {
      st.setProperty('--accent', css(accent));
    }
  }

  // ---------- aviso ----------

  var TONES = { brand: '#a78bfa', blue: '#4b9bff', amber: '#ffb020', red: '#ff5d5d', green: '#4ade80' };

  function renderNotice(root, settings) {
    var holder = el('div', 'notice ' + (settings.align === 'left' ? 'left' : 'center'));
    holder.style.width = '100%';
    holder.style.height = '100%';
    holder.style.setProperty('--tone', parseHex(settings.accentColor) ? settings.accentColor : TONES[settings.accent] || TONES.brand);
    var box = el('div', 'w');
    box.appendChild(el('div', 'bar'));
    var inner = el('div', 'box');
    inner.appendChild(icon(settings.accent === 'red' || settings.accent === 'amber' ? 'triangle-alert' : 'megaphone'));
    if (settings.title) inner.appendChild(el('div', 't', settings.title));
    if (settings.message) inner.appendChild(el('div', 'm', settings.message));
    box.appendChild(inner);
    holder.appendChild(box);
    root.appendChild(holder);
  }

  // ---------- contagem regressiva ----------

  function renderCountdown(root, settings) {
    var s = state.s;
    var target = zonedToMs(settings.target, settings.timezone);
    var dateFmt = formatter({ day: 'numeric', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit' }, settings.timezone);
    var box = el('div', 'w count');
    box.appendChild(el('div', 'label', settings.label));
    var body = el('div');
    box.appendChild(body);
    var until = el('div', 'until', isNaN(target) ? '' : s.until + ' ' + dateFmt.format(new Date(target)));
    box.appendChild(until);
    root.appendChild(box);

    function tile(value, label) {
      var node = el('div', 'tile');
      node.appendChild(el('b', null, value));
      node.appendChild(el('span', null, label));
      return node;
    }

    function tick() {
      clear(body);
      var left = target - now();
      if (isNaN(left) || left <= 0) {
        body.appendChild(el('div', 'done', settings.finishedText));
        until.style.display = 'none';
        return;
      }
      var total = Math.floor(left / 1000);
      var tiles = el('div', 'tiles');
      tiles.appendChild(tile(Math.floor(total / 86400) + '', s.days));
      tiles.appendChild(tile(pad(Math.floor(total / 3600) % 24), s.hours));
      tiles.appendChild(tile(pad(Math.floor(total / 60) % 60), s.minutes));
      tiles.appendChild(tile(pad(total % 60), s.seconds));
      body.appendChild(tiles);
    }
    tick();
    every(500, tick);
  }

  // ---------- painel de chamadas (fila de atendimento) ----------

  /** A chamada é "fresca" (destacada) por este tempo depois de feita: o painel pisca para chamar a atenção. */
  var QUEUE_FRESH_MS = 20000;

  function renderQueue(root, settings, data) {
    var s = state.s;
    var box = el('div', 'w queue');
    var head = el('div', 'qhead');
    head.appendChild(el('div', 'qttl', settings.title || s.queueTitle));
    var wait = el('div', 'qwait');
    head.appendChild(wait);
    var cur = el('div', 'qnow');
    var list = el('div', 'qlist');
    box.appendChild(head);
    box.appendChild(cur);
    box.appendChild(list);
    root.appendChild(box);
    var shown = null;

    function paint(d) {
      shown = d;
      var calls = (d && d.calls) || [];
      clear(cur);
      clear(list);
      wait.textContent = s.queueWaiting + ': ' + ((d && d.waiting) || 0);
      if (!calls.length) {
        cur.className = 'qnow none';
        cur.appendChild(icon('megaphone', 'big'));
        cur.appendChild(el('div', 'qsmall', s.queueNone));
        cur.appendChild(el('div', 'qhint', s.queueNoneHint));
        return;
      }
      var first = calls[0];
      var fresh = first.status === 'CALLING' && now() - first.calledAtMs < QUEUE_FRESH_MS;
      cur.className = 'qnow' + (fresh ? ' fresh' : '');
      cur.appendChild(el('div', 'qsmall', first.callCount > 1 ? s.queueAgain : s.queueCalling));
      cur.appendChild(el('div', 'qlabel', first.label));
      cur.appendChild(el('div', 'qcounter', first.counter));
      if (first.priority) cur.appendChild(el('div', 'qpri', s.queuePriority));
      if (calls.length > 1) {
        list.appendChild(el('div', 'qsmall', s.queueLast));
        for (var i = 1; i < calls.length; i++) {
          var row = el('div', 'qrow');
          row.appendChild(el('b', null, calls[i].label));
          row.appendChild(el('span', null, calls[i].counter));
          list.appendChild(row);
        }
      }
    }

    paint(data);
    // O destaque some sozinho depois de alguns segundos, sem esperar a próxima consulta ao servidor.
    every(1000, function () {
      var first = shown && shown.calls && shown.calls[0];
      var stale = first && cur.className.indexOf('fresh') >= 0 && now() - first.calledAtMs >= QUEUE_FRESH_MS;
      if (stale) paint(shown);
    });
    // A TV entrega dados novos a cada poucos segundos: Smartplay.update({ data: { calls, waiting }, nowMs }).
    state.update = function (payload) {
      if (payload && payload.nowMs) {
        state.clock.base = payload.nowMs;
        state.clock.t0 = perfNow();
      }
      paint(payload && payload.data ? payload.data : payload);
    };
  }

  // ---------- entrada ----------

  var RENDERERS = {
    QUEUE: function (root, init) { renderQueue(root, init.settings, init.data); },
    CLOCK: function (root, init) { renderClock(root, init.settings); },
    WEATHER: function (root, init) { renderWeather(root, init.settings, init.data); },
    NEWS: function (root, init) { renderNews(root, init.settings, init.data); },
    RATES: function (root, init) { renderRates(root, init.settings, init.data); },
    NOTICE: function (root, init) { renderNotice(root, init.settings); },
    PRICES: function (root, init) { renderPrices(root, init.settings, init.data); },
    COUNTDOWN: function (root, init) { renderCountdown(root, init.settings); }
  };

  function start(init) {
    clearTimers();
    state.init = init;
    state.lang = (init.settings && init.settings.lang) || init.lang || 'pt-BR';
    state.s = STR[state.lang] || STR['pt-BR'];
    state.clock.base = init.nowMs || new Date().getTime();
    state.clock.t0 = perfNow();
    state.update = null;
    var theme = (init.settings && init.settings.theme) || 'dark';
    document.body.className = 'theme-' + (theme === 'light' || theme === 'brand' || theme === 'green' ? theme : 'dark');
    applyColors(init.settings || {});
    var root = document.getElementById('root');
    clear(root);
    var render = RENDERERS[init.kind];
    if (!render) {
      emptyState(root, state.s.noData);
      return;
    }
    try {
      render(root, init);
    } catch (e) {
      clear(root);
      emptyState(root, state.s.noData);
    }
  }

  window.Smartplay = { start: start, update: function (payload) { if (state.update) state.update(payload); } };
  // Pré-visualização do painel: o painel manda a configuração por postMessage.
  window.addEventListener('message', function (event) {
    if (event.data && event.data.smartplay === 'start') start(event.data.init);
  });
})();
