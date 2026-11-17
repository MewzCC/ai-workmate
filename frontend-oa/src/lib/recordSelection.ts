/** A route selection is an intent only; protected detail APIs still authorize the resource. */
export function parseRecordId(value: string | null): number | undefined {
  if (!value || !/^[1-9]\d*$/.test(value)) return undefined;
  const id = Number(value);
  return Number.isSafeInteger(id) ? id : undefined;
}
