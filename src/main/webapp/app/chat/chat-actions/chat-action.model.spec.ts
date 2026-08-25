import { ChatAction, isSafeRoute } from './chat-action.model';

/**
 * The route guard is the front's own barrier: the backends assert their routes stay internal, but
 * this value is handed to `navigateByUrl`, and navigation must not hinge on that trust alone.
 */
describe('isSafeRoute', () => {
  it('accepts the copilot routes the chatbot may open', () => {
    expect(isSafeRoute('/job-copilot/search')).toBe(true);
    expect(isSafeRoute('/job-copilot/search?offerId=abc')).toBe(true);
    expect(isSafeRoute('/job-copilot/onboarding')).toBe(true);
  });

  it('refuses an absolute URL', () => {
    expect(isSafeRoute('https://evil.example/phishing')).toBe(false);
  });

  it('refuses a protocol-relative URL', () => {
    // `//evil.example` starts with a slash but browsers treat it as absolute: a prefix check alone
    // would let it through, which is the whole reason for the explicit case.
    expect(isSafeRoute('//evil.example/phishing')).toBe(false);
  });

  it('refuses an internal path outside the copilot area', () => {
    expect(isSafeRoute('/admin/user-management')).toBe(false);
    expect(isSafeRoute('/login')).toBe(false);
  });

  it('refuses a path that merely mentions the prefix later on', () => {
    expect(isSafeRoute('/redirect?to=/job-copilot/search')).toBe(false);
  });
});

/**
 * The union is what removes defensive parsing: narrowing on `type` must give access to the payload
 * of that variant and to nothing else. These assertions are compile-time in nature — the test would
 * fail to build before it ever runs.
 */
describe('ChatAction narrowing', () => {
  it('exposes each variant payload once narrowed', () => {
    const actions: ChatAction[] = [
      { type: 'prompt', label: { key: 'chat.action.widenSearch' }, text: 'Élargis la recherche' },
      { type: 'navigate', label: { key: 'chat.action.importCv' }, route: '/job-copilot/onboarding' },
      { type: 'mutate', label: { key: 'chat.action.apply' }, operation: 'APPLY', offerId: 'offer-1', confirm: true },
    ];

    const described = actions.map(action => {
      switch (action.type) {
        case 'prompt':
          return action.text;
        case 'navigate':
          return action.route;
        case 'mutate':
          return `${action.operation}:${action.confirm}`;
      }
    });

    expect(described).toEqual(['Élargis la recherche', '/job-copilot/onboarding', 'APPLY:true']);
  });

  it('carries interpolation parameters rather than a built sentence', () => {
    const action: ChatAction = {
      type: 'navigate',
      label: { key: 'chat.action.viewAllOffers', params: { count: 12 } },
      route: '/job-copilot/search',
    };

    // The backend never builds displayable text: the count travels as a parameter so the label
    // follows the UI language.
    expect(action.label.key).toBe('chat.action.viewAllOffers');
    expect(action.label.params).toEqual({ count: 12 });
  });
});
