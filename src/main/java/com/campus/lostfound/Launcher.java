package com.campus.lostfound;

/**
 * 程序启动入口。
 * 单独提供一个不继承 Application 的 main 类,
 * 这样用普通方式(java -jar)运行时不会因 JavaFX 模块问题报错。
 */
public class Launcher {
    public static void main(String[] args) {
        App.main(args);
    }
}
