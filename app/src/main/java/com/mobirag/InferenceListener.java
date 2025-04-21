package com.mobirag;

public interface InferenceListener {
    void onTokenGenerated(String token);
    void onComplete();
    void onError(String message);
}
