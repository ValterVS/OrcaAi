export type FieldErrors = Partial<Record<string, string>>;

const PASSWORD_MIN_LENGTH = 12;
const PASSWORD_MAX_LENGTH = 64;
// Mirrors the backend: BCrypt only uses the first 72 bytes of a password.
const PASSWORD_MAX_BYTES = 72;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+$/;

export const PASSWORD_RULE = `A senha deve ter entre ${PASSWORD_MIN_LENGTH} e ${PASSWORD_MAX_LENGTH} caracteres.`;

function isValidPassword(password: string): boolean {
  const length = [...password].length;
  return (
    length >= PASSWORD_MIN_LENGTH &&
    length <= PASSWORD_MAX_LENGTH &&
    new TextEncoder().encode(password).length <= PASSWORD_MAX_BYTES
  );
}

function validateEmail(email: string): string | undefined {
  if (!email.trim()) {
    return "Informe o e-mail.";
  }
  return EMAIL_PATTERN.test(email.trim()) ? undefined : "Informe um e-mail válido.";
}

export function validateSignup(data: {
  companyName: string;
  ownerName: string;
  email: string;
  password: string;
}): FieldErrors {
  const errors: FieldErrors = {};
  if (!data.companyName.trim()) {
    errors.companyName = "Informe o nome da empresa.";
  }
  if (!data.ownerName.trim()) {
    errors.ownerName = "Informe seu nome.";
  }
  const emailError = validateEmail(data.email);
  if (emailError) {
    errors.email = emailError;
  }
  if (!isValidPassword(data.password)) {
    errors.password = PASSWORD_RULE;
  }
  return errors;
}

export function validateLogin(data: { email: string; password: string }): FieldErrors {
  const errors: FieldErrors = {};
  const emailError = validateEmail(data.email);
  if (emailError) {
    errors.email = emailError;
  }
  if (!data.password) {
    errors.password = "Informe a senha.";
  }
  return errors;
}

export function hasErrors(errors: FieldErrors): boolean {
  return Object.keys(errors).length > 0;
}
