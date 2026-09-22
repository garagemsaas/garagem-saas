import { useEffect, useState } from 'react';
import { photoBlob } from './api';

/**
 * Carrega uma imagem da empresa autenticada e devolve uma URL local para exibi-la.
 *
 * <p>As imagens da empresa não são públicas: chegam pela API com a sessão, como qualquer outro
 * recurso do tenant, e por isso não podem ir direto no `src`. Vive aqui, e não dentro de um
 * componente, porque a identidade visual e a pré-visualização do site precisam exatamente do mesmo
 * carregamento — duplicá-lo daria duas formas de errar a revogação da URL.
 */
export function useImagemEmpresa(path?: string) {
  const [state, setState] = useState<{ path: string; url: string }>();
  useEffect(() => {
    if (!path) return;
    let active = true; let url: string | undefined;
    photoBlob(path).then(blob => {
      if (active) { url = URL.createObjectURL(blob); setState({ path, url }); }
    }).catch(() => { /* O nome da empresa continua visível se a imagem estiver indisponível. */ });
    return () => { active = false; if (url) URL.revokeObjectURL(url); };
  }, [path]);
  return state?.path === path ? state?.url : undefined;
}
