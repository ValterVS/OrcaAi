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

export const NOT_ALLOWED = "Você não tem permissão para esta ação.";

/**
 * Message for a failed action on an existing record. 412 (changed by someone else) and 422
 * (business rule) carry a message written for users; everything else stays generic.
 */
export function actionErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if ((error.status === 412 || error.status === 422) && error.problem.detail) {
      return error.problem.detail;
    }
    if (error.status === 403) {
      return NOT_ALLOWED;
    }
  }
  return genericErrorMessage(error);
}
