type CspOptions = {
  nonce: string;
  isDevelopment: boolean;
  httpsOnly: boolean;
};

export function buildContentSecurityPolicy({ nonce, isDevelopment, httpsOnly }: CspOptions): string {
  // Development tooling (React debugging, the Next.js overlay) needs eval and inline styles.
  // Browsers ignore 'unsafe-inline' when a nonce is present, so development drops the style nonce.
  const scriptSrc = `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${isDevelopment ? " 'unsafe-eval'" : ""}`;
  const styleSrc = isDevelopment ? "style-src 'self' 'unsafe-inline'" : `style-src 'self' 'nonce-${nonce}'`;

  const directives = [
    "default-src 'self'",
    scriptSrc,
    styleSrc,
    "img-src 'self' blob: data:",
    "font-src 'self'",
    "connect-src 'self'",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
  ];
  if (httpsOnly) {
    directives.push("upgrade-insecure-requests");
  }
  return directives.join("; ");
}

export function generateNonce(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return btoa(String.fromCharCode(...bytes));
}
