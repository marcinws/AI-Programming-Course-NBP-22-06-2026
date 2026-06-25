# System projektowy — styl NBP (nbp.pl)

System wyekstrahowany ze strony [nbp.pl](https://nbp.pl) w dniu **2026-06-24** i zastosowany w szablonie strony „O mnie" (`index.html`).

## Zasoby

| Plik | Opis |
|---|---|
| [`assets/homepage.png`](assets/homepage.png) | Zrzut ekranu strony głównej nbp.pl |
| [`assets/logo.svg`](assets/logo.svg) | Logotyp NBP (205×64) |
| [`assets/favicon.ico`](assets/favicon.ico) | Favicon |
| [`assets/design-tokens.json`](assets/design-tokens.json) | Tokeny projektowe (JSON) |
| [`index.html`](index.html) | Szablon strony „O mnie" |

## Kolory

| Token | Hex | Zastosowanie |
|---|---|---|
| `brand.primary` | `#152E52` | Granatowy NBP — nagłówki, tło sekcji ciemnych, nawigacja |
| `brand.accent` | `#4A74B0` | Niebieski — linki, przyciski podstawowe, akcenty |
| `brand.gold` | `#E8D499` | Złoty/piaskowy — akcenty dekoracyjne, podkreślenia |
| `background.default` | `#FFFFFF` | Tło główne |
| `background.light` | `#F7F7F7` | Tło sekcji naprzemiennych |
| `background.dark` | `#152E52` | Hero, blok kontaktowy |
| `border.default` | `#BFCEDD` | Obramowania kart, pól, separatory |
| `border.muted` | `#C4C4C4` | Subtelne obramowania |
| `text.primary` | `#464646` | Tekst podstawowy |
| `text.heading` | `#152E52` | Nagłówki |
| `text.link` | `#4A74B0` | Odnośniki |
| `text.muted` | `#6B7280` | Tekst pomocniczy |
| `text.onDark` | `#FFFFFF` | Tekst na ciemnym tle |

## Typografia

Dwie rodziny czcionek (dostępne w Google Fonts):

- **Nagłówki:** `"Brygada 1918"` — szeryfowa, elegancka (`font-weight` 400/500/600/700). Fallback: `Georgia, "Times New Roman", serif`.
- **Tekst:** `"Libre Franklin"` — bezszeryfowa (`font-weight` 400/500/600/700). Fallback: `-apple-system, Arial, sans-serif`.

**Skala rozmiarów:** sm `13px` · base `15.5px` · md `16px` · lg `20px` · xl `24px` · 2xl `32px` · 3xl `44px`.
**Interlinia:** tight `1.2` · base `1.55` · heading `1.33`.

## Odstępy

Bazowa jednostka **4px**. Wartości: 4 · 8 · 12 · 16 · 20 · 24 · 32 · 40 · 48 · 64 px.

## Promienie zaokrągleń

| Token | Wartość | Zastosowanie |
|---|---|---|
| `sm` | `2px` | Drobne elementy |
| `md` | `4px` | Przyciski |
| `lg` | `6px` | Karty, pola, bloki |
| `full` | `999px` | Pigułki/chipy |
| `circle` | `50%` | Awatary, kropki osi czasu |

## Komponenty

- **Nagłówek:** białe tło, dolne obramowanie `#BFCEDD`, logo po lewej, nawigacja `UPPERCASE` w kolorze `#152E52`, hover → `#4A74B0`. Sticky.
- **Pasek górny:** granatowe tło `#152E52`, drobne linki (PL/EN/Kontakt).
- **Przycisk podstawowy:** tło `#4A74B0`, biały tekst, `border-radius: 4px`, padding `10px 22px`, hover → `#3C6398`.
- **Przycisk obrysowany:** przezroczyste tło, obramowanie `#BFCEDD`, tekst `#152E52`.
- **Karty:** białe tło, obramowanie `#BFCEDD`, `border-radius: 6px`, delikatny cień.
- **Chipy umiejętności:** białe tło, obramowanie, zaokrąglenie `6px`.
- **Oś czasu:** pionowa linia `#BFCEDD` z kropkami w kolorze akcentu.

## Logo

Logotyp NBP (`assets/logo.svg`) — okrągły symbol + sygnatura. Stosować na jasnym tle w nagłówku (wysokość ~56px). Zachować obszar ochronny i nie zniekształcać proporcji.

## Charakterystyka wizualna

Styl NBP jest **instytucjonalny, stonowany i wiarygodny**. Dominuje głęboki granat zestawiony ze złotym akcentem, co buduje poczucie stabilności i prestiżu. Szeryfowe nagłówki (Brygada 1918) nadają powagi, a czysta bezszeryfowa treść (Libre Franklin) zapewnia czytelność. Układ jest przestronny, oparty na siatce, z oszczędnym użyciem koloru i wyraźną hierarchią — odpowiedni dla oficjalnej, zaufanej komunikacji.
