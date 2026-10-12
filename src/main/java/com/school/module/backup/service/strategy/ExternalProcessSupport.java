package com.school.module.backup.service.strategy;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 外部进程（pg_dump / docker exec）执行工具
 * <p>
 * ponytail: 密码只通过环境变量 PGPASSWORD 传递，绝不拼进命令行——命令行对同机所有用户的 ps 可见。
 * stderr 在独立线程读取，避免子进程写满管道后阻塞；stdout 要么丢弃要么直接重定向到文件（-Fc 是二进制，不能经过字符流）。
 *
 * @author School System
 */
@Slf4j
public final class ExternalProcessSupport {

    /** 探测命令（--version）的超时 */
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(15);
    /** stderr 最多保留的字符数（写进 sys_backup.message） */
    private static final int STDERR_CAP = 4000;

    private ExternalProcessSupport() {
    }

    /**
     * 进程结果：退出码 + stderr 文本
     */
    public record ProcessResult(int exitCode, String stderr) {
        public boolean ok() {
            return exitCode == 0;
        }
    }

    /**
     * 探测命令是否可执行且退出码为 0（找不到可执行文件、超时、非 0 全部视为不可用）
     */
    public static boolean probe(List<String> command) {
        try {
            return run(command, Map.of(), null, PROBE_TIMEOUT).ok();
        } catch (Exception e) {
            log.debug("探测命令不可用 {}: {}", command.get(0), e.toString());
            return false;
        }
    }

    /**
     * 运行命令并等待结束
     *
     * @param command    命令及参数
     * @param env        追加的环境变量
     * @param stdoutFile stdout 重定向目标；null 表示丢弃
     * @param timeout    最长等待时间，超时强杀并抛 IOException
     */
    public static ProcessResult run(List<String> command, Map<String, String> env, Path stdoutFile, Duration timeout)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().putAll(env);
        builder.redirectOutput(stdoutFile == null ? ProcessBuilder.Redirect.DISCARD
                : ProcessBuilder.Redirect.to(stdoutFile.toFile()));
        builder.redirectError(ProcessBuilder.Redirect.PIPE);

        Process process = builder.start();
        StderrReader stderrReader = new StderrReader(process.getErrorStream());
        Thread reader = new Thread(stderrReader, "backup-stderr-reader");
        reader.setDaemon(true);
        reader.start();

        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("命令超时（" + timeout.toSeconds() + "s）被终止: " + command.get(0));
        }
        reader.join(TimeUnit.SECONDS.toMillis(5));
        return new ProcessResult(process.exitValue(), stderrReader.text());
    }

    /**
     * 逐字节读 stderr 到有上限的缓冲
     */
    private static final class StderrReader implements Runnable {
        private final InputStream in;
        private final StringBuilder buffer = new StringBuilder();

        private StderrReader(InputStream in) {
            this.in = in;
        }

        @Override
        public void run() {
            try (in) {
                byte[] chunk = new byte[4096];
                int n;
                while ((n = in.read(chunk)) != -1) {
                    synchronized (buffer) {
                        if (buffer.length() < STDERR_CAP) {
                            buffer.append(new String(chunk, 0, n, StandardCharsets.UTF_8));
                        }
                    }
                }
            } catch (IOException e) {
                log.debug("读取子进程 stderr 中断: {}", e.toString());
            }
        }

        private String text() {
            synchronized (buffer) {
                String s = buffer.toString().trim();
                return s.length() > STDERR_CAP ? s.substring(0, STDERR_CAP) : s;
            }
        }
    }

}
