package com.ayaneo.gamewindow;

/**
 * Binary-compatible subset of AYANEO GameWindow's exported control interface.
 *
 * CreteOS only needs transaction 1 (`send`). The vendor interface has two additional callback
 * methods after this one; omitting unused trailing methods keeps our client small while preserving
 * the descriptor and transaction number used by the service.
 */
interface AyaAidlInterface {
    void send(String message);
}
