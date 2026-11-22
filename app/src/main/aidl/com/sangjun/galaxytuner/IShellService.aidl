package com.sangjun.galaxytuner;

interface IShellService {
    void destroy() = 16777114;
    void exit() = 16777113;
    String exec(String command) = 1;
}
