import { describe, expect, it } from 'vitest';
import { streamErrorMessage } from './streamErrors';
describe('stream error explanations', () => {
  it('gives specific actions for known connection, pairing and startup failures', () => {
    expect(streamErrorMessage('MoonDeck Buddy is unavailable on this host.')).toContain('MoonDeck Buddy service');
    expect(streamErrorMessage('MoonDeck Buddy did not authorize apiVersion (HTTP 401).')).toContain('pairing is separate');
    expect(streamErrorMessage('Host query timed out.')).toContain('SSH');
    expect(streamErrorMessage('Connection terminated: -1')).toContain('does not prove it crashed');
    expect(streamErrorMessage('Game startup is taking longer than expected')).toContain('cloud-save');
    expect(streamErrorMessage('Bosgame is already streaming a different game.')).toContain('left the existing session running');
  });
  it('preserves specific new messages and unrecognised details without generic wrappers', () => {
    const message = 'Steam on Bosgame is signed out. Sign in to continue.';
    expect(streamErrorMessage(new Error(message))).toBe(message);
    expect(streamErrorMessage('New provider failure 42')).toBe('New provider failure 42');
  });
});
