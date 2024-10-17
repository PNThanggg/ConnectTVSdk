package com.connect.service.airplay.auth;

class PairSetupPin2Response {
    public final byte[] PROOF;

    public PairSetupPin2Response(byte[] proof) {
        this.PROOF = proof;
    }
}
