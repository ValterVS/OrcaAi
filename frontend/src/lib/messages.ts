import { ApiError } from "./api/client";

export const TOO_MANY_ATTEMPTS = "Muitas tentativas. Aguarde alguns minutos e tente novamente.";
export const TRY_AGAIN_LATER = "Não foi possível concluir agora. Tente novamente em instantes.";

/** Message for failures not specific to a form: rate limiting, server and network errors. */
export function genericErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 429) {
    return TOO_MANY_ATTEMPTS;
  }
  return TRY_AGAIN_LATER;
}
