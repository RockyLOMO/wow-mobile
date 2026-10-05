package com.winlator.wowmobile;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;

/** Durable streaming download and staged ZIP installation. No Activity references. */
public final class ClientInstaller {
    public static final String URL = "https://cloud.f-li.cn:6500/wow/WoW-3.3.5a-zhCN.zip";
    public static final long RESERVE = 3L * 1024 * 1024 * 1024;
    private static final int BUFFER = 256 * 1024;
    public interface Progress { void update(String phase, long done, long total, String detail); }
    public interface ReadyCheck { boolean valid(File folder); }
    public static final class Paused extends IOException {}
    private static final class InvalidArchive extends IOException {
        InvalidArchive(String message) { super(message); }
    }

    /** Fast checks against the five immutable base files in the distributed 3.3.5a client. */
    public static boolean isClientReady(File root) {
        if (new File(root, "Wow.exe").length() < 1_000_000 && new File(root, "wow.exe").length() < 1_000_000) return false;
        String[] paths = {"Data/common.MPQ", "Data/common-2.MPQ", "Data/expansion.MPQ",
            "Data/lichking.MPQ", "Data/zhCN/base-zhCN.MPQ"};
        long[] sizes = {3014298814L, 1835428448L, 1908644445L, 2551828006L, 24517885L};
        for (int i = 0; i < paths.length; i++) if (new File(root, paths[i]).length() != sizes[i]) return false;
        for (String path : new String[]{"Data/patch.MPQ", "Data/patch-2.MPQ", "Data/patch-3.MPQ",
            "Data/zhCN/locale-zhCN.MPQ", "Data/zhCN/patch-zhCN.MPQ", "Data/zhCN/patch-zhCN-2.MPQ", "Data/zhCN/patch-zhCN-3.MPQ"})
            if (new File(root, path).length() <= 0) return false;
        return true;
    }

    public static void install(String url, File cache, File destination, AtomicBoolean paused,
                               Progress progress, ReadyCheck ready) throws IOException {
        if (ready.valid(destination)) return;
        if (destination.exists()) throw new IOException("指定目录已有文件且客户端不完整，请选择完整客户端或移走该目录后重试；不会覆盖已有文件。");
        if (!cache.isDirectory() && !cache.mkdirs()) throw new IOException("无法创建下载目录，请检查存储权限。");
        File stage = new File(cache, "staging");
        if (stage.exists()) removeOwnedStage(stage, cache);
        File archive = download(url, cache, paused, progress);
        if (!stage.mkdirs()) throw new IOException("无法创建解压目录。");
        try { extract(archive, stage, paused, progress); }
        catch (InvalidArchive e) {
            removeOwnedStage(stage, cache);
            Files.deleteIfExists(archive.toPath());
            Files.deleteIfExists(new File(cache, "download.properties").toPath());
            throw new IOException(e.getMessage() + "；已废弃损坏的安装缓存，请重新下载。", e);
        }
        File client = stage;
        if (!ready.valid(client)) {
            File[] children = stage.listFiles(File::isDirectory);
            if (children != null) for (File child : children) if (ready.valid(child)) { client = child; break; }
        }
        if (!ready.valid(client)) throw new IOException("解压完成，但缺少完整的 3.3.5a 简体中文客户端文件。请重新获取完整客户端。");
        checkPause(paused);
        if (destination.exists() || !client.renameTo(destination)) throw new IOException("无法完成安装目录切换；已有文件不会被覆盖。");
        progress.update("complete", 1, 1, "客户端已就绪");
        // Only the installer-owned cache is removed after a verified successful publish.
        if (stage.exists()) removeOwnedStage(stage, cache);
        Files.deleteIfExists(archive.toPath());
        Files.deleteIfExists(new File(cache, "download.properties").toPath());
    }

    public static File download(String url, File cache, AtomicBoolean paused, Progress progress) throws IOException {
        cache.mkdirs();
        File partial = new File(cache, "client.zip.part"), archive = new File(cache, "client.zip");
        File metadata = new File(cache, "download.properties");
        Properties saved = new Properties();
        if (metadata.isFile()) try (InputStream in = new FileInputStream(metadata)) { saved.load(in); }
        HttpURLConnection head = connection(url);
        head.setRequestMethod("HEAD");
        long total; String validator;
        try {
            int status = head.getResponseCode();
            if (status != 200) throw new IOException("下载服务器暂不可用：HTTP " + status);
            total = head.getContentLengthLong();
            validator = head.getHeaderField("ETag");
            if (validator == null || validator.startsWith("W/")) validator = head.getHeaderField("Last-Modified");
            if (validator == null) validator = "";
        }
        finally { head.disconnect(); }
        if (total <= 0 || total > 100L * 1024 * 1024 * 1024) throw new IOException("服务器文件大小异常，无法安全下载。");
        checkPause(paused);
        boolean same = url.equals(saved.getProperty("url")) && validator.equals(saved.getProperty("validator"))
            && Long.toString(total).equals(saved.getProperty("length")) && !validator.isEmpty();
        if (!same) {
            Files.deleteIfExists(partial.toPath());
            Files.deleteIfExists(archive.toPath());
        }
        if (same && archive.isFile() && archive.length() == total) return archive;
        long offset = partial.isFile() ? partial.length() : 0;
        if (offset > total) { Files.delete(partial.toPath()); offset = 0; }
        long required = Math.addExact(Math.multiplyExact(total, 2), RESERVE) - offset;
        if (cache.getUsableSpace() < required) throw new IOException("可用空间不足，下载安装需至少约 " +
            (long)Math.ceil(required / 1_000_000_000.0) + " GB 剩余空间。");
        saved.setProperty("url", url); saved.setProperty("validator", validator); saved.setProperty("length", Long.toString(total));
        File pendingMetadata = new File(cache, "download.properties.new");
        try (OutputStream out = new FileOutputStream(pendingMetadata)) { saved.store(out, "Old Dream resumable download"); }
        Files.move(pendingMetadata.toPath(), metadata.toPath(), StandardCopyOption.REPLACE_EXISTING);
        if (offset < total) {
            HttpURLConnection get = connection(url);
            if (offset > 0) { get.setRequestProperty("Range", "bytes=" + offset + "-"); get.setRequestProperty("If-Range", validator); }
            try {
                int status = get.getResponseCode();
                if (status == 200) offset = 0;
                else if (status != 206) throw new IOException("下载失败：HTTP " + status);
                if (status == 206) {
                    String range = get.getHeaderField("Content-Range");
                    if (range == null || !range.startsWith("bytes " + offset + "-") || !range.endsWith("/" + total))
                        throw new IOException("服务器返回了错误的续传范围，已保留原下载。");
                }
                if (offset == 0 && cache.getUsableSpace() + partial.length() < total * 2 + RESERVE)
                    throw new IOException("重新下载所需空间不足。");
                byte[] buffer = new byte[BUFFER]; long done = offset, last = 0;
                try (InputStream in = new BufferedInputStream(get.getInputStream(), BUFFER);
                     FileOutputStream out = new FileOutputStream(partial, offset > 0)) {
                    progress.update("download", done, total, "正在下载客户端");
                    int count;
                    while ((count = in.read(buffer)) >= 0) {
                        checkPause(paused);
                        if (count == 0) continue;
                        if (count > total - done) throw new IOException("下载数据超过服务器声明大小。");
                        out.write(buffer, 0, count); done += count;
                        long now = System.nanoTime();
                        if (now - last >= 250_000_000L) { progress.update("download", done, total, "正在下载客户端"); last = now; }
                    }
                    out.getFD().sync();
                }
                checkPause(paused);
                if (done != total) throw new IOException("网络中断，已保存下载进度，可以继续。");
                progress.update("download", done, total, "下载完成，正在校验 ZIP");
            }
            finally { get.disconnect(); }
        }
        Files.move(partial.toPath(), archive.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return archive;
    }

    public static void extract(File archive, File stage, AtomicBoolean paused, Progress progress) throws IOException {
        ZipFile opened;
        try { opened = new ZipFile(archive, "GBK"); }
        catch (IOException e) { throw new InvalidArchive("ZIP 文件结构损坏：" + e.getMessage()); }
        try (ZipFile zip = opened) {
            long total = 0; int count = 0;
            Enumeration<ZipArchiveEntry> entries = zip.getEntries(); Set<String> names = new HashSet<>();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement(); checkedEntry(stage, entry);
                if (++count > 100000) throw new IOException("ZIP 文件数过多。");
                if (!names.add(checkedEntry(stage, entry).getCanonicalPath().toLowerCase(Locale.ROOT))) throw new InvalidArchive("ZIP 存在重复路径。");
                if (!entry.isDirectory()) {
                    if (entry.getSize() < 0 || entry.getCrc() < 0 || !zip.canReadEntryData(entry)) throw new InvalidArchive("ZIP 格式不支持或文件已损坏。");
                    total = Math.addExact(total, entry.getSize());
                }
            }
            if (total > 100L * 1024 * 1024 * 1024 || stage.getUsableSpace() < total + RESERVE)
                throw new IOException("解压所需空间不足或文件大小异常。");
            entries = zip.getEntries(); byte[] buffer = new byte[BUFFER]; long done = 0, last = 0; int index = 0;
            while (entries.hasMoreElements()) {
                checkPause(paused); ZipArchiveEntry entry = entries.nextElement(); File file = checkedEntry(stage, entry);
                index++;
                if (entry.isDirectory()) { if (!file.isDirectory() && !file.mkdirs()) throw new IOException("无法创建解压目录。"); continue; }
                if (!file.getParentFile().isDirectory() && !file.getParentFile().mkdirs()) throw new IOException("无法创建解压目录。");
                CRC32 crc = new CRC32(); long written = 0;
                InputStream input;
                try { input = zip.getInputStream(entry); }
                catch (IOException e) { throw new InvalidArchive("ZIP 条目格式损坏：" + entry.getName()); }
                try (InputStream in = input; OutputStream out = new BufferedOutputStream(new FileOutputStream(file), BUFFER)) {
                    int n;
                    while (true) {
                        try { n = in.read(buffer); }
                        catch (IOException e) { throw new InvalidArchive("ZIP 条目数据损坏：" + entry.getName()); }
                        if (n < 0) break;
                        checkPause(paused); if (n == 0) continue;
                        if (n > entry.getSize() - written) throw new InvalidArchive("ZIP 条目大小异常。");
                        crc.update(buffer, 0, n); out.write(buffer, 0, n); written += n; done += n;
                        long now = System.nanoTime();
                        if (now - last >= 250_000_000L) { progress.update("extract", done, total, index + "/" + count + " · " + entry.getName()); last = now; }
                    }
                }
                if (written != entry.getSize() || crc.getValue() != entry.getCrc()) throw new InvalidArchive("ZIP 校验失败：" + entry.getName());
            }
            progress.update("extract", done, total, "解压完成，正在验收客户端");
        }
        catch (ArithmeticException e) { throw new IOException("ZIP 大小异常。", e); }
    }

    private static File checkedEntry(File stage, ZipArchiveEntry entry) throws IOException {
        String name = entry.getName().replace('\\', '/');
        for (String part : name.split("/")) if (part.equals(".") || part.equals("..")) throw new InvalidArchive("ZIP 包含不安全的路径。");
        if (name.startsWith("/") || name.contains(":") || entry.isUnixSymlink() || entry.getGeneralPurposeBit().usesEncryption())
            throw new InvalidArchive("ZIP 包含不安全的路径或加密文件。");
        File file = new File(stage, name).getCanonicalFile();
        if (!file.getPath().startsWith(stage.getCanonicalPath() + File.separator)) throw new IOException("ZIP 路径超出安装目录。");
        return file;
    }

    private static void removeOwnedStage(File stage, File cache) throws IOException {
        if (!stage.getCanonicalPath().equals(new File(cache, "staging").getAbsolutePath())) throw new IOException("安装缓存路径异常。");
        Files.walkFileTree(stage.toPath(), new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
            @Override public java.nio.file.FileVisitResult visitFile(java.nio.file.Path file, java.nio.file.attribute.BasicFileAttributes a) throws IOException {
                Files.delete(file); return java.nio.file.FileVisitResult.CONTINUE;
            }
            @Override public java.nio.file.FileVisitResult postVisitDirectory(java.nio.file.Path dir, IOException error) throws IOException {
                if (error != null) throw error; Files.delete(dir); return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    private static HttpURLConnection connection(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
        connection.setConnectTimeout(20000); connection.setReadTimeout(15000);
        connection.setRequestProperty("Accept-Encoding", "identity");
        return connection;
    }
    private static void checkPause(AtomicBoolean paused) throws Paused { if (paused.get()) throw new Paused(); }
}
