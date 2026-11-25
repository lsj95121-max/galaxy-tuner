package com.sangjun.galaxytuner;

interface IShellService {
    void destroy() = 16777114;
    void exit() = 16777113;
    String exec(String command) = 1;

    // Streaming stdin, used by the one-tap update (pm install -S <bytes> reads the APK from stdin).
    void startStream(String command) = 2;
    void writeStream(in byte[] data) = 3;
    String finishStream() = 4;
}
