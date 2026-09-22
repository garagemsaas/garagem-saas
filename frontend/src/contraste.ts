/**
 * Luminância relativa de uma cor `#rrggbb`, na definição da WCAG.
 *
 * <p>A conta estava escrita duas vezes — uma para o sistema, outra para o site — e é a parte que
 * não admite variação: errar o gama produz um texto que parece legível na tela de quem escolheu a
 * cor e some na de quem lê. O limiar, esse sim, fica com quem chama: o sistema e o site pesam
 * diferente o ponto em que trocam texto claro por escuro, e essa é uma decisão de cada superfície.
 */
export function luminancia(hex: string) {
  const [r, g, b] = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255)
    .map(c => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/**
 * A mesma cor, escurecida até que texto branco sobre ela — ou ela como texto sobre branco — alcance
 * os 4,5:1 da WCAG AA.
 *
 * <p>Existe porque a cor da empresa é escolhida por quem vende carro, não por quem entende de
 * contraste: um amarelo bonito no seletor vira link ilegível sobre papel branco. Em vez de proibir
 * a escolha ou aceitar a página quebrada, escurece-se o tom o necessário e nada mais — o matiz
 * continua sendo o da empresa. Só quem vira texto pequeno passa por aqui; o resto usa a cor como
 * ela foi escolhida.
 */
export function legivel(hex: string) {
  const canais = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16));
  // 1.05 / (L + 0.05) >= 4.5  ⇒  L <= 0.1833…
  for (let i = 0; i < 60 && luminancia(hexDe(canais)) > 0.1833; i++)
    for (let c = 0; c < 3; c++) canais[c] = Math.floor(canais[c] * 0.92);
  return hexDe(canais);
}

function hexDe(canais: number[]) {
  return '#' + canais.map(c => Math.max(0, Math.min(255, c)).toString(16).padStart(2, '0')).join('');
}
