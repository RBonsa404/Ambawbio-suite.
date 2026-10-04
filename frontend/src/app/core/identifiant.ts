/** UUID v7 côté client (R-05) : horodatage en millisecondes + aléa, triable ; identique au générateur du serveur. */
export function uuid7(): string {
  const octets = new Uint8Array(16);
  crypto.getRandomValues(octets);
  const millis = BigInt(Date.now());
  for (let i = 0; i < 6; i++) {
    octets[i] = Number((millis >> BigInt(8 * (5 - i))) & 0xffn);
  }
  octets[6] = (octets[6] & 0x0f) | 0x70;
  octets[8] = (octets[8] & 0x3f) | 0x80;
  const hex = [...octets].map((o) => o.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
