/**
 * Clickable actions offered at the end of an assistant turn.
 *
 * Mirrors `ChatAction` on both backends — `ChatAction.java` (Spring) and
 * `chat_actions.rs` (Rust) — which serve this same front-end. The `type` field is the
 * discriminator: it is what lets TypeScript narrow the union, and what the template switches on.
 *
 * Written by hand rather than generated, and consumed as-is: no `parseAction()` helper, no
 * defensive `if (action.offerId)`. If the shape ever drifts from a backend, the fix belongs in that
 * backend, not in a coercion layer here.
 */

/**
 * A button label: an **i18n key** and its parameters, never a sentence.
 *
 * The backends build no displayable text. Parameters serve labels carrying a value — "View the 12
 * offers" is the `viewAllOffers` key with `{ count: 12 }`.
 */
export interface ChatLabel {
  /** Key in the Angular bundle, e.g. `chat.action.viewAllOffers`. */
  key: string;
  /** Interpolation parameters; absent when the key expects none. */
  params?: Record<string, string | number> | null;
}

/**
 * Operations the front knows how to trigger.
 *
 * A closed union rather than `string`: an operation the front cannot execute must not typecheck.
 * That is what removes the need for a `default` branch rendering a button that does nothing.
 */
export type ChatMutation = 'GENERATE_COVER_LETTER' | 'APPLY';

/** Sends `text` back into the conversation as a new user turn. */
export interface ChatPromptAction {
  type: 'prompt';
  label: ChatLabel;
  text: string;
}

/** Opens an application route. Always an internal path — see `isSafeRoute`. */
export interface ChatNavigateAction {
  type: 'navigate';
  label: ChatLabel;
  route: string;
}

/** Calls an API. `confirm` requires the user to validate before the call is made. */
export interface ChatMutateAction {
  type: 'mutate';
  label: ChatLabel;
  operation: ChatMutation;
  offerId?: string;
  confirm: boolean;
}

export type ChatAction = ChatPromptAction | ChatNavigateAction | ChatMutateAction;

/**
 * Route prefixes the chatbot may open.
 *
 * The backend is trusted today, but `route` is handed to `navigateByUrl`: a field that drives
 * navigation must not depend on that trust. An absolute URL, a protocol-relative `//host` or a path
 * outside the copilot area is dropped rather than followed.
 */
const ALLOWED_ROUTE_PREFIXES = ['/job-copilot/'] as const;

/** True when `route` is an internal path the chatbot is allowed to open. */
export function isSafeRoute(route: string): boolean {
  // `//evil.com` is protocol-relative: browsers treat it as an absolute URL even though it starts
  // with a slash, so the prefix check alone would let it through.
  if (route.startsWith('//')) {
    return false;
  }
  return ALLOWED_ROUTE_PREFIXES.some(prefix => route.startsWith(prefix));
}
